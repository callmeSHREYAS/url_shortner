package com.shreyas.url_shortner.url.Service;

import java.time.Instant;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

/** Distributed fixed-window rate limiter backed by Redis. */
@Service
public class CreateRateLimitService {
    private static final String KEY_PREFIX = "rate-limit:create:";

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('INCR', KEYS[1]) "
                    + "if count == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return count",
            Long.class);

    private final RedisTemplate<String, String> redisTemplate;
    private final int maxRequests;
    private final long windowSeconds;

    public CreateRateLimitService(
            RedisTemplate<String, String> redisTemplate,
            @Value("${rate-limit.create.max-requests:10}") int maxRequests,
            @Value("${rate-limit.create.window-seconds:60}") long windowSeconds) {
        if (maxRequests < 1 || windowSeconds < 1) {
            throw new IllegalArgumentException("Rate-limit values must be positive");
        }

        this.redisTemplate = redisTemplate;
        this.maxRequests = maxRequests;
        this.windowSeconds = windowSeconds;
    }

    public RateLimitDecision check(String clientKey) {
        long epochSecond = Instant.now().getEpochSecond();
        long window = epochSecond / windowSeconds;
        String redisKey = KEY_PREFIX + clientKey + ":" + window;

        Long count = redisTemplate.execute(
                INCREMENT_SCRIPT,
                List.of(redisKey),
                Long.toString(windowSeconds));

        long retryAfter = windowSeconds - (epochSecond % windowSeconds);
        long currentCount = count == null ? maxRequests + 1L : count;

        return new RateLimitDecision(
                currentCount <= maxRequests,
                maxRequests,
                currentCount,
                retryAfter);
    }

    public record RateLimitDecision(
            boolean allowed,
            int limit,
            long count,
            long retryAfterSeconds) {
    }
}
