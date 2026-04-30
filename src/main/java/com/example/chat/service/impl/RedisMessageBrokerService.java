package com.example.chat.service.impl;

import com.example.chat.model.ChatMessage;
import com.example.chat.service.MessageBrokerService;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@Slf4j
public class RedisMessageBrokerService implements MessageBrokerService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final StringRedisTemplate streamStringRedisTemplate;
    private final ExecutorService streamAppendExecutor = new ThreadPoolExecutor(
            2,
            2,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(20_000),
            new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "chat-stream-append-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.AbortPolicy()
    );
    private final ExecutorService pubSubRetryExecutor = new ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(2_000),
            new ThreadFactory() {
                private final AtomicInteger seq = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    Thread thread = new Thread(r, "chat-pubsub-retry-" + seq.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }
            },
            new ThreadPoolExecutor.AbortPolicy()
    );

    @Value("${chat.stream.key-prefix:chat:stream:room:}")
    private String streamKeyPrefix;

    @Value("${chat.pubsub.publish.retry.max-attempts:3}")
    private int pubSubRetryMaxAttempts;

    @Value("${chat.pubsub.publish.retry.backoff-ms:200}")
    private long pubSubRetryBackoffMs;

    public RedisMessageBrokerService(
            RedisTemplate<String, Object> redisTemplate,
            @Qualifier("streamStringRedisTemplate") StringRedisTemplate streamStringRedisTemplate
    ) {
        this.redisTemplate = redisTemplate;
        this.streamStringRedisTemplate = streamStringRedisTemplate;
    }

    @Override
    public void publish(String topic, Object message) {
        log.info("Publishing chat message topic={}", topic);
        enqueueStreamAppend(topic, message);
        try {
            redisTemplate.convertAndSend("chat:msg:" + topic, message);
        } catch (Exception e) {
            log.warn("Failed to publish message to Redis Pub/Sub on first attempt. topic={}", topic, e);
            enqueuePubSubRetry(topic, message);
        }
    }

    private void enqueueStreamAppend(String topic, Object message) {
        try {
            streamAppendExecutor.execute(() -> {
                try {
                    appendToStream(topic, message);
                } catch (Exception e) {
                    log.warn("Failed to append chat message to stream. topic={}", topic, e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Stream append queue full, dropping message. topic={}", topic);
        }
    }

    private void enqueuePubSubRetry(String topic, Object message) {
        try {
            pubSubRetryExecutor.execute(() -> retryPublish(topic, message));
        } catch (RejectedExecutionException e) {
            log.warn("PubSub retry queue full, dropping retry. topic={}", topic);
        }
    }

    private void retryPublish(String topic, Object message) {
        int attempts = Math.max(pubSubRetryMaxAttempts, 0);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                TimeUnit.MILLISECONDS.sleep(pubSubRetryBackoffMs);
                redisTemplate.convertAndSend("chat:msg:" + topic, message);
                log.info("Recovered Redis Pub/Sub publish after retry. topic={}, attempt={}", topic, attempt);
                return;
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
                log.warn("Pub/Sub retry interrupted. topic={}", topic, interruptedException);
                return;
            } catch (Exception e) {
                if (attempt == attempts) {
                    log.error("Failed Redis Pub/Sub publish after retries. topic={}, attempts={}", topic, attempts, e);
                    return;
                }
                log.warn("Redis Pub/Sub retry failed. topic={}, attempt={}", topic, attempt, e);
            }
        }
    }

    private void appendToStream(String topic, Object message) {
        String streamKey = streamKeyPrefix + topic;
        Map<String, String> fields = new LinkedHashMap<>();

        if (message instanceof ChatMessage chatMessage) {
            fields.put("type", chatMessage.getType() == null ? "" : chatMessage.getType().name());
            fields.put("roomId", safe(chatMessage.getRoomId()));
            fields.put("sender", safe(chatMessage.getSender()));
            fields.put("msgId", safe(chatMessage.getMsgId()));
            fields.put("message", safe(chatMessage.getMessage()));
        } else {
            fields.put("payload", String.valueOf(message));
        }
        fields.put("publishedAt", String.valueOf(System.currentTimeMillis()));

        MapRecord<String, String, String> record = StreamRecords.mapBacked(fields).withStreamKey(streamKey);
        streamStringRedisTemplate.opsForStream().add(record);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    @PreDestroy
    public void shutdownExecutor() {
        streamAppendExecutor.shutdown();
        pubSubRetryExecutor.shutdown();
    }
}
