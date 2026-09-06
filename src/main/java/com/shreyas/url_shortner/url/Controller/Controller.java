package com.shreyas.url_shortner.url.Controller;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

import com.shreyas.url_shortner.url.URL;
import com.shreyas.url_shortner.url.UrlRepository;
import com.shreyas.url_shortner.url.Service.RedisService;
import com.shreyas.url_shortner.url.Service.UrlService;

@RestController
@RequestMapping("/api/v1/url") // Fixed: Added leading slash for explicit routing
public class Controller {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_URL_LENGTH = 2048;

    @Autowired
    private RedisService redisService;

    private final UrlService urlService;
    private final UrlRepository urlRepository;

    // Constructor Injection
    public Controller(UrlService urlService, UrlRepository urlRepository) {
        this.urlService = urlService;
        this.urlRepository = urlRepository;
    }

    // POST: Create a short URL
    @PostMapping
    public String createURL(@RequestBody URL url) {
        validateCreateUrlRequest(url);

        String shortCode = urlService.generateShortCode(url.getUrl());
        url.setShortCode(shortCode);
        urlRepository.save(url);
        redisService.save(shortCode, url.getUrl());
        return shortCode;
    }

    // GET: Retrieve URLs page by page
    @GetMapping
    public Page<URL> getAllURl(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {

        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Page must be 0 or greater");
        }

        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Size must be between 1 and " + MAX_PAGE_SIZE);
        }

        return urlRepository.findAll(PageRequest.of(page, size, Sort.by("id").ascending()));
    }

    // GET: Retrieve specific URL by ID
    @GetMapping("/id/{id}")
    public URL getUrlByID(@PathVariable int id) {
        return urlRepository.findById(id).orElse(null);
    }

    // DELETE: Remove a URL by ID
    @DeleteMapping("/delete/{id}")
    public void deleteUrl(@PathVariable int id) {
        URL url = urlRepository.findById(id).orElseThrow(() -> new RuntimeException("URL not found"));
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
            return new RedirectView(cachedUrl);
        }

        System.out.println("❌ Cache MISS");

        // 2. Cache miss -> Query MySQL
        return urlRepository.findByShortCode(shortned_url)
                .map(url -> {

                    // Increment click count
                    url.setTot_Clicks(url.getTot_Clicks() + 1);
                    urlRepository.save(url);

                    // 3. Save into Redis
                    redisService.save(shortned_url, url.getUrl());

                    System.out.println("Stored in Redis");

                    // 4. Redirect
                    return new RedirectView(url.getUrl());

                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found"));
    }

    private void validateCreateUrlRequest(URL url) {
        if (url == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required");
        }

        if (isBlank(url.getName())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name is required");
        }

        if (isBlank(url.getUrl())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL is required");
        }

        String originalUrl = url.getUrl().trim();
        if (originalUrl.length() > MAX_URL_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "URL must be " + MAX_URL_LENGTH + " characters or fewer");
        }

        if (!isValidHttpUrl(originalUrl)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "URL must be a valid http or https URL");
        }

        url.setUrl(originalUrl);
        url.setName(url.getName().trim());
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
