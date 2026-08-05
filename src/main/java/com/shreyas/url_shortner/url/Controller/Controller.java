package com.shreyas.url_shortner.url.Controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import com.shreyas.url_shortner.url.URL;
import com.shreyas.url_shortner.url.UrlRepository;
import com.shreyas.url_shortner.url.Service.UrlService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/url") // Fixed: Added leading slash for explicit routing
public class Controller {

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
        urlRepository.deleteById(id);
    }

    // GET: Redirect short code to original target URL
    @GetMapping("/{shortned_url}")
    public RedirectView getMethodName(@PathVariable String shortned_url) {
        return urlRepository.findByShortCode(shortned_url)
                .map(url -> {
                    url.setTot_Clicks(url.getTot_Clicks() + 1);
                    urlRepository.save(url);
                    return new RedirectView(url.getUrl());
                })
                .orElse(null);
    }
}