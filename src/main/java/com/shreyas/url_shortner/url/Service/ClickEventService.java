package com.shreyas.url_shortner.url.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.shreyas.url_shortner.url.UrlRepository;

@Service
public class ClickEventService {
    private static final String CLICK_STREAM = "url-click-events";
    private static final String CLICK_GROUP = "url-click-processors";

    private final RedisTemplate<String, String> redisTemplate;
    private final UrlRepository urlRepository;
    private final String consumerName;
    private volatile boolean groupInitialized;

    public ClickEventService(
            RedisTemplate<String, String> redisTemplate,
            UrlRepository urlRepository,
            @Value("${INSTANCE_NAME:${spring.application.name:local}}") String instanceName) {
        this.redisTemplate = redisTemplate;
        this.urlRepository = urlRepository;
        this.consumerName = instanceName + "-" + UUID.randomUUID();
    }

    public void publishClick(String shortCode) {
        Map<String, String> event = new HashMap<>();
        event.put("shortCode", shortCode);
        event.put("eventId", UUID.randomUUID().toString());
        event.put("timestamp", Long.toString(System.currentTimeMillis()));
        redisTemplate.opsForStream().add(CLICK_STREAM, event);
    }

    @Transactional
    @Scheduled(fixedDelayString = "${click-events.poll-delay-ms:100}")
    public void processClickEvents() {
        initializeGroup();
        if (!groupInitialized) {
            return;
        }

        List<MapRecord<String, Object, Object>> records = redisTemplate.opsForStream().read(
                Consumer.from(CLICK_GROUP, consumerName),
                StreamReadOptions.empty().count(100).block(Duration.ofMillis(100)),
                StreamOffset.create(CLICK_STREAM, ReadOffset.lastConsumed()));

        if (records == null || records.isEmpty()) {
            return;
        }

        Map<String, Integer> clickCounts = new HashMap<>();
        for (MapRecord<String, Object, Object> record : records) {
            Object shortCodeValue = record.getValue().get("shortCode");
            String shortCode = shortCodeValue == null ? null : shortCodeValue.toString();
            if (shortCode != null && !shortCode.isBlank()) {
                clickCounts.merge(shortCode, 1, Integer::sum);
            }
        }

        persistCounts(clickCounts);
        records.forEach(record -> redisTemplate.opsForStream().acknowledge(CLICK_GROUP, record));
    }

    private void initializeGroup() {
        if (groupInitialized) {
            return;
        }

        try {
            redisTemplate.opsForStream().createGroup(
                    CLICK_STREAM,
                    ReadOffset.from("0-0"),
                    CLICK_GROUP);
            groupInitialized = true;
        } catch (DataAccessException exception) {
            try {
                redisTemplate.opsForStream().read(
                        Consumer.from(CLICK_GROUP, consumerName),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(CLICK_STREAM, ReadOffset.lastConsumed()));
                groupInitialized = true;
            } catch (DataAccessException ignored) {
                // Redis may still be starting. The next scheduled poll retries.
            }
        }
    }

    protected void persistCounts(Map<String, Integer> clickCounts) {
        clickCounts.forEach(urlRepository::incrementClicksByShortCode);
    }
}