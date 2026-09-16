package com.shreyas.url_shortner.url.Service;

import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shreyas.url_shortner.url.UrlRepository;

/** Writes aggregated click counts to the database inside a transaction. */
@Service
public class ClickCountPersister {

    private final UrlRepository urlRepository;

    public ClickCountPersister(UrlRepository urlRepository) {
        this.urlRepository = urlRepository;
    }

    @Transactional
    public void persistCounts(Map<String, Integer> clickCounts) {
        clickCounts.forEach(urlRepository::incrementClicksByShortCode);
    }
}