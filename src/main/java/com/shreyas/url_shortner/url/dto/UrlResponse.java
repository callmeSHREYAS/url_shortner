package com.shreyas.url_shortner.url.dto;

/** Public URL representation. Persistence-only fields stay inside the entity. */
public record UrlResponse(
        Long id,
        String name,
        String url,
        String shortCode,
        int totalClicks) {
}
