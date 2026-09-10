package com.shreyas.url_shortner.url.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.view.RedirectView;

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
    @GetMapping("/{shortCode}")
    public RedirectView redirect(@PathVariable String shortCode) {

        // 1. Check Redis
        String cachedUrl = redisService.get(shortCode);

        if (cachedUrl != null) {
            System.out.println("✅ Cache HIT");
            clickEventService.publishClick(shortCode);
            return new RedirectView(cachedUrl);
        }

        System.out.println("❌ Cache MISS");

        // 2. Cache miss -> Query MySQL
        return urlRepository.findByShortCode(shortCode)
                .map(url -> {
                    // 3. Save into Redis
                    redisService.save(shortCode, url.getUrl());
                    clickEventService.publishClick(shortCode);

                    System.out.println("Stored in Redis");

                    // 4. Redirect
                    return new RedirectView(url.getUrl());

                })
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Short URL not found"));
    }
}