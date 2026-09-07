package com.shreyas.url_shortner.url;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface UrlRepository extends JpaRepository<URL, Long> {
    // Custom query to find a URL by its short code
    Optional<URL> findByShortCode(String shortCode);

    @Modifying
    @Query("update URL url set url.tot_Clicks = url.tot_Clicks + :clicks where url.shortCode = :shortCode")
    int incrementClicksByShortCode(@Param("shortCode") String shortCode, @Param("clicks") int clicks);
}
