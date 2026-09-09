package com.shreyas.url_shortner.url.dto;

import org.springframework.stereotype.Component;

import com.shreyas.url_shortner.url.URL;

/** Converts internal JPA entities into stable API DTOs. */
@Component
public class UrlDtoMapper {

    public UrlResponse toResponse(URL url) {
        return new UrlResponse(
                url.getId(),
                url.getName(),
                url.getUrl(),
                url.getShortCode(),
                url.getTot_Clicks());
    }
}
