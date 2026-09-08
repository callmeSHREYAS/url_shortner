package com.shreyas.url_shortner.url.dto;

// Response DTO: return the generated code without exposing the database entity.
public class CreateUrlResponse {
    private final String shortCode;

    public CreateUrlResponse(String shortCode) {
        this.shortCode = shortCode;
    }

    public String getShortCode() {
        return shortCode;
    }
}