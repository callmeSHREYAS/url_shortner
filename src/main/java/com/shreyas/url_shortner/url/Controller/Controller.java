package com.shreyas.url_shortner.url.Controller;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import com.shreyas.url_shortner.url.URL;
import com.shreyas.url_shortner.url.UrlRepository;
import com.shreyas.url_shortner.url.Service.RedisService;
import com.shreyas.url_shortner.url.Service.UrlService;
import com.shreyas.url_shortner.url.Service.ClickEventService;
import com.shreyas.url_shortner.url.Service.CreateRateLimitService;
import com.shreyas.url_shortner.url.dto.CreateUrlRequest;
import com.shreyas.url_shortner.url.dto.CreateUrlResponse;
import com.shreyas.url_shortner.url.dto.UrlDtoMapper;
import com.shreyas.url_shortner.url.dto.UrlResponse;

@RestController
@RequestMapping("/api/v1/url") // Fixed: Added leading slash for explicit routing
public class Controller {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_URL_LENGTH = 2048;

    @Autowired
    private RedisService redisService;

    private final UrlService urlService;
    private final UrlRepository urlRepository;
    private final ClickEventService clickEventService;
    private final UrlDtoMapper urlDtoMapper;
    private final CreateRateLimitService createRateLimitService;

    // Constructor Injection
    public Controller(
            UrlService urlService,
            UrlRepository urlRepository,
            ClickEventService clickEventService,
            UrlDtoMapper urlDtoMapper,
            CreateRateLimitService createRateLimitService) {
        this.urlService = urlService;
        this.urlRepository = urlRepository;
        this.clickEventService = clickEventService;
        this.urlDtoMapper = urlDtoMapper;
        this.createRateLimitService = createRateLimitService;
    }

    // POST: Create a short URL
    @PostMapping
    public CreateUrlResponse createURL(
            @RequestBody CreateUrlRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse) {
        CreateRateLimitService.RateLimitDecision decision = createRateLimitService.check(clientKey(httpRequest));
        httpResponse.setHeader("X-RateLimit-Limit", Integer.toString(decision.limit()));
        httpResponse.setHeader("X-RateLimit-Remaining",
                Long.toString(Math.max(0, decision.limit() - decision.count())));

        if (!decision.allowed()) {
            httpResponse.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            throw new ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "Create URL rate limit exceeded. Try again in "
                            + decision.retryAfterSeconds() + " seconds");
        }

        validateCreateUrlRequest(request);

        // Build the entity here so clients cannot set id, shortCode, or click counts.
        URL url = new URL();
        url.setName(request.getName().trim());
        url.setUrl(request.getUrl().trim());

        String shortCode = urlService.generateShortCode(url.getUrl());
        url.setShortCode(shortCode);
        urlRepository.save(url);
        redisService.save(shortCode, url.getUrl());
        return new CreateUrlResponse(shortCode);
    }

    private String clientKey(HttpServletRequest request) {
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }

        return request.getRemoteAddr();
    }

    // GET: Retrieve URLs page by page
    @GetMapping
    public Page<UrlResponse> getAllURl(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be 0 or greater");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and " + MAX_PAGE_SIZE);
        }

        // Map entities at the controller boundary so persistence fields are not exposed as the API contract.
        return urlRepository.findAll(PageRequest.of(page, size, Sort.by("id").ascending()))
            .map(urlDtoMapper::toResponse);
    }

    // GET: Retrieve specific URL by ID
    @GetMapping("/id/{id}")
    public UrlResponse getUrlByID(@PathVariable Long id) {
        return urlRepository.findById(id)
                .map(urlDtoMapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "URL not found with id " + id));
    }

    // DELETE: Remove a URL by ID
    @DeleteMapping("/delete/{id}")
    public void deleteUrl(@PathVariable Long id) {
        URL url = urlRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "URL not found with id " + id));
        redisService.delete(url.getShortCode());
        urlRepository.deleteById(id);
    }

    // GET: Redirect short code to original target URL
    @GetMapping("/{shortned_url}")
    public RedirectView redirect(@PathVariable String shortned_url) {

        // 1. Check Redis
        String cachedUrl = redisService.get(shortned_url);

        if (cachedUrl != null) {
            System.out.println("✅ Cache HIT");
            clickEventService.publishClick(shortned_url);
            return new RedirectView(cachedUrl);
        }

        System.out.println("❌ Cache MISS");

        // 2. Cache miss -> Query MySQL
        return urlRepository.findByShortCode(shortned_url)
                .map(url -> {
                    // 3. Save into Redis
                    redisService.save(shortned_url, url.getUrl());
                    clickEventService.publishClick(shortned_url);

                    System.out.println("Stored in Redis");

                    // 4. Redirect
                    return new RedirectView(url.getUrl());

                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found"));
    }

    private void validateCreateUrlRequest(CreateUrlRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        if (isBlank(request.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }

        if (isBlank(request.getUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is required");
        }

        String originalUrl = request.getUrl().trim();
        if (originalUrl.length() > MAX_URL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "URL must be " + MAX_URL_LENGTH + " characters or fewer");
        }

        if (!isValidHttpUrl(originalUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must be a valid http or https URL");
        }

        request.setUrl(originalUrl);
        request.setName(request.getName().trim());
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isValidHttpUrl(String value) {
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme();
            return ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))
                    && uri.getHost() != null;
        } catch (URISyntaxException e) {
            return false;
        }
    }
}
