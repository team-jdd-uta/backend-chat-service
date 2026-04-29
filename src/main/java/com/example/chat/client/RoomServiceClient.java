package com.example.chat.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
@Slf4j
public class RoomServiceClient {

    private static final String CHAT_ROOMS = "CHAT_ROOM";

    private final RestClient restClient;
    private final RedisTemplate<String, Object> redisTemplate;

    public RoomServiceClient(
            @Value("${room.service.base-url:http://localhost:8082}") String baseUrl,
            @Value("${room.service.connect-timeout-ms:1000}") int connectTimeoutMs,
            @Value("${room.service.read-timeout-ms:3000}") int readTimeoutMs,
            RedisTemplate<String, Object> redisTemplate
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeoutMs);
        factory.setReadTimeout(readTimeoutMs);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        this.redisTemplate = redisTemplate;
    }

    public boolean roomExists(String roomId) {
        try {
            restClient.get()
                    .uri("/rooms/{roomId}", roomId)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (HttpClientErrorException.NotFound e) {
            return false;
        } catch (Exception ex) {
            log.warn("Room Service unavailable, falling back to Redis. roomId={}", roomId, ex);
            Object value = redisTemplate.opsForHash().get(CHAT_ROOMS, roomId);
            return value != null;
        }
    }
}

