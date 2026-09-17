package com.shreyas.url_shortner.url.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.transaction.annotation.Transactional;

import com.shreyas.url_shortner.url.URL;
import com.shreyas.url_shortner.url.UrlRepository;
import com.shreyas.url_shortner.url.Service.ClickEventService;
import com.shreyas.url_shortner.url.Service.RedisService;

@RestController
public class RedirectController {

    @Autowired
    private RedisService redisService;

    private final UrlRepository urlRepository;
    private final ClickEventService clickEventService;

    public RedirectController(UrlRepository urlRepository, ClickEventService clickEventService) {
        this.urlRepository = urlRepository;
        this.clickEventService = clickEventService;
    }

    // GET: Redirect short code to original target URL
    @GetMapping("/{shortCode:[A-Za-z0-9]+}")
    @Transactional(readOnly = true)
    public RedirectView redirect(@PathVariable String shortCode) {

        // 1. Check Redis
        String cachedUrl = redisService.get(shortCode);

        if (cachedUrl != null) {
            // Cached miss -> short code is known to not exist; skip the DB query.
            if (redisService.isNotFound(cachedUrl)) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found error 1");
            }

            System.out.println("✅ Cache HIT");
            clickEventService.publishClick(shortCode);
            return new RedirectView(cachedUrl);
        }

        System.out.println("❌ Cache MISS");

        // 2. Cache miss -> Query MySQL; on a miss, cache the sentinel for a short TTL.
        URL url = urlRepository.findByShortCode(shortCode).orElse(null);
        if (url == null) {
            redisService.cacheNotFound(shortCode);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found error 2");
        }

        // 3. Save into Redis
        redisService.save(shortCode, url.getUrl());
        clickEventService.publishClick(shortCode);

        System.out.println("Stored in Redis");

        // 4. Redirect
        return new RedirectView(url.getUrl());
    }
}