package com.shreyas.url_shortner.url;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<URL, Integer> {
    // Custom query to find a URL by its short code
    Optional<URL> findByShortCode(String shortCode);
}