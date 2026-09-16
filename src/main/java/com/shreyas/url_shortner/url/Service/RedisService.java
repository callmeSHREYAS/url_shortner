package com.shreyas.url_shortner.url.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.RedisTemplate;

@Service
public class RedisService {

    /** Sentinel value cached when a key is known not to exist upstream. */
    public static final String NOT_FOUND = "__NOT_FOUND__";

    private static final Duration NOT_FOUND_TTL = Duration.ofSeconds(30);

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    public void save(String key, String value) {
        redisTemplate.opsForValue().set(key, value, 1, TimeUnit.HOURS);
    }

    public void cacheNotFound(String key) {
        redisTemplate.opsForValue().set(key, NOT_FOUND, NOT_FOUND_TTL);
    }

    public boolean isNotFound(String value) {
        return NOT_FOUND.equals(value);
    }

    public String get(String key) {
        return redisTemplate.opsForValue().get(key);
    }

    public void delete(String key) {
        redisTemplate.delete(key);
    }
}