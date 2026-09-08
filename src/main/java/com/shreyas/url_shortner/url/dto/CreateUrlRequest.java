package com.shreyas.url_shortner.url.dto;

// Request DTO: clients can provide only the fields needed to create a URL.
public class CreateUrlRequest {
    private String name;
    private String url;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}