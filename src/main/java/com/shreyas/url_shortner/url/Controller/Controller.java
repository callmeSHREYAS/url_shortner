package com.shreyas.url_shortner.url.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import com.shreyas.url_shortner.url.URL;
import com.shreyas.url_shortner.url.UrlRepository;
import com.shreyas.url_shortner.url.Service.RedisService;
import com.shreyas.url_shortner.url.Service.UrlService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/url") // Fixed: Added leading slash for explicit routing
public class Controller {

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
        String shortCode = urlService.generateShortCode(url.getUrl());
        url.setShortCode(shortCode);
        urlRepository.save(url);
        return shortCode;
    }

    // GET: Retrieve all URLs
    @GetMapping
    public List<URL> getAllURl() {
        return urlRepository.findAll();
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
                .orElseThrow(() -> new RuntimeException("Short URL not found"));
    }
}