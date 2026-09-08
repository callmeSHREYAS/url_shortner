package com.shreyas.url_shortner.url.dto;

// Response DTO: exposes stable API fields while keeping entity internals private.
public class UrlResponse {
    private final Long id;
    private final String name;
    private final String url;
    private final String shortCode;
    private final int tot_Clicks;

    public UrlResponse(Long id, String name, String url, String shortCode, int tot_Clicks) {
        this.id = id;
        this.name = name;
        this.url = url;
        this.shortCode = shortCode;
        this.tot_Clicks = tot_Clicks;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getUrl() {
        return url;
    }

    public String getShortCode() {
        return shortCode;
    }

    public int getTot_Clicks() {
        return tot_Clicks;
    }
}