package com.shreyas.url_shortner.url.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.RedisStreamCommands.XClaimOptions;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** Publishes redirect events and counts them asynchronously in batches. */
@Service
public class ClickEventService {
    private static final Logger log = LoggerFactory.getLogger(ClickEventService.class);

    private static final String CLICK_STREAM = "url-click-events";
    private static final String CLICK_GROUP = "url-click-processors";
    private static final String SHORT_CODE_FIELD = "shortCode";

    private final RedisTemplate<String, String> redisTemplate;
    private final ClickCountPersister clickCountPersister;
    private final String consumerName;
    private final int batchSize;
    private final Duration claimIdleTime;
    private final Duration readBlockTime;
    private volatile boolean groupInitialized;

    public ClickEventService(
            RedisTemplate<String, String> redisTemplate,
            ClickCountPersister clickCountPersister,
            @Value("${INSTANCE_NAME:${spring.application.name:local}}") String instanceName,
            @Value("${click-events.batch-size:100}") int batchSize,
            @Value("${click-events.claim-idle-ms:30000}") long claimIdleMs,
            @Value("${click-events.read-block-ms:100}") long readBlockMs) {
        this.redisTemplate = redisTemplate;
        this.clickCountPersister = clickCountPersister;
        this.consumerName = instanceName + "-" + UUID.randomUUID();
        this.batchSize = batchSize;
        this.claimIdleTime = Duration.ofMillis(claimIdleMs);
        this.readBlockTime = Duration.ofMillis(readBlockMs);
    }

    /** Enqueue one click. The database is deliberately not touched here. */
    public void publishClick(String shortCode) {
        Map<String, String> event = Map.of(
                SHORT_CODE_FIELD, shortCode,
                "eventId", UUID.randomUUID().toString(),
                "timestamp", Long.toString(System.currentTimeMillis()));

        redisTemplate.opsForStream().add(CLICK_STREAM, event);
    }

    /**
     * Reads new events and retries events abandoned by a failed consumer.
     * Records are acknowledged only after their database update succeeds.
     * No database transaction is held during the Redis I/O; each batch of
     * DB writes is committed independently by ClickCountPersister.
     */
    @Scheduled(fixedDelayString = "${click-events.poll-delay-ms:100}")
    public void processClickEvents() {
        if (!ensureConsumerGroup()) {
            return;
        }

        List<MapRecord<String, Object, Object>> records = new ArrayList<>();
        records.addAll(claimAbandonedEvents());

        if (records.size() < batchSize) {
            List<MapRecord<String, Object, Object>> freshRecords = redisTemplate.opsForStream().read(
                    Consumer.from(CLICK_GROUP, consumerName),
                    StreamReadOptions.empty()
                            .count(batchSize - records.size())
                            .block(readBlockTime),
                    StreamOffset.create(CLICK_STREAM, ReadOffset.lastConsumed()));

            if (freshRecords != null) {
                records.addAll(freshRecords);
            }
        }

        if (records.isEmpty()) {
            return;
        }

        Map<String, Integer> clickCounts = countByShortCode(records);
        clickCountPersister.persistCounts(clickCounts);

        // If persistence fails, records remain pending and will be reclaimed later.
        Set<RecordId> recordIds = new HashSet<>();
        records.forEach(record -> recordIds.add(record.getId()));
        redisTemplate.opsForStream().acknowledge(
                CLICK_STREAM,
                CLICK_GROUP,
                recordIds.toArray(new RecordId[0]));
    }

    private List<MapRecord<String, Object, Object>> claimAbandonedEvents() {
        PendingMessages pending = redisTemplate.opsForStream().pending(
                CLICK_STREAM,
                CLICK_GROUP,
                Range.unbounded(),
                batchSize,
                claimIdleTime);

        if (pending == null || pending.isEmpty()) {
            return List.of();
        }

        RecordId[] ids = new RecordId[pending.size()];
        int index = 0;
        for (PendingMessage message : pending) {
            ids[index++] = message.getId();
        }

        return redisTemplate.opsForStream().claim(
                CLICK_STREAM,
                CLICK_GROUP,
                consumerName,
                XClaimOptions.minIdle(claimIdleTime).ids(ids));
    }

    private Map<String, Integer> countByShortCode(List<MapRecord<String, Object, Object>> records) {
        Map<String, Integer> clickCounts = new HashMap<>();
        for (MapRecord<String, Object, Object> record : records) {
            Object shortCodeValue = record.getValue().get(SHORT_CODE_FIELD);
            if (shortCodeValue != null && !shortCodeValue.toString().isBlank()) {
                clickCounts.merge(shortCodeValue.toString(), 1, Integer::sum);
            }
        }
        return clickCounts;
    }

    private boolean ensureConsumerGroup() {
        if (groupInitialized) {
            return true;
        }

        try {
            redisTemplate.opsForStream().createGroup(
                    CLICK_STREAM,
                    ReadOffset.from("0-0"),
                    CLICK_GROUP);
            groupInitialized = true;
            return true;
        } catch (DataAccessException exception) {
            // BUSYGROUP means another application instance created it first.
            try {
                redisTemplate.opsForStream().read(
                        Consumer.from(CLICK_GROUP, consumerName),
                        StreamReadOptions.empty().count(1),
                        StreamOffset.create(CLICK_STREAM, ReadOffset.lastConsumed()));
                groupInitialized = true;
                return true;
            } catch (DataAccessException ignored) {
                log.debug("Click stream is not ready yet", ignored);
                return false;
            }
        }
    }
}
