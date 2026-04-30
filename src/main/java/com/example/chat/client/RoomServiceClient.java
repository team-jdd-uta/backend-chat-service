package com.example.chat.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Component
@Slf4j
public class RoomServiceClient {

    private static final String CHAT_ROOMS = "CHAT_ROOM";

    public record RoomSnapshot(
            String roomId,
            String name,
            String broadcasterId,
            String status,
            Long createdAt,
            Long updatedAt,
            Long startedAt,
            Long endedAt
    ) {
    }

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
        if (roomId == null || roomId.isBlank()) {
            return false;
        }
        return getRoomState(roomId) != null;
    }

    public boolean roomIsOpen(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return false;
        }
        String status = getRoomStatus(roomId);
        return "READY".equals(status) || "LIVE".equals(status);
    }

    public RoomSnapshot getRoomSnapshot(String roomId) {
        if (roomId == null || roomId.isBlank()) {
            return null;
        }
        Object state = getRoomState(roomId);
        if (state == null) {
            return null;
        }
        if (state instanceof Map<?, ?> map) {
            return new RoomSnapshot(
                    value(map.get("roomId")),
                    value(map.get("name")),
                    value(map.get("broadcasterId")),
                    value(map.get("status")),
                    toLong(map.get("createdAt")),
                    toLong(map.get("updatedAt")),
                    toLong(map.get("startedAt")),
                    toLong(map.get("endedAt"))
            );
        }
        try {
            return new RoomSnapshot(
                    value(state.getClass().getMethod("getRoomId").invoke(state)),
                    value(state.getClass().getMethod("getName").invoke(state)),
                    value(state.getClass().getMethod("getBroadcasterId").invoke(state)),
                    value(state.getClass().getMethod("getStatus").invoke(state)),
                    toLong(state.getClass().getMethod("getCreatedAt").invoke(state)),
                    toLong(state.getClass().getMethod("getUpdatedAt").invoke(state)),
                    toLong(state.getClass().getMethod("getStartedAt").invoke(state)),
                    toLong(state.getClass().getMethod("getEndedAt").invoke(state))
            );
        } catch (Exception ignored) {
            return null;
        }
    }

    private Object getRoomState(String roomId) {
        try {
            return restClient.get()
                    .uri("/rooms/{roomId}", roomId)
                    .retrieve()
                    .body(Map.class);
        } catch (HttpClientErrorException.NotFound e) {
            return null;
        } catch (Exception ex) {
            log.warn("Room Service unavailable, falling back to Redis. roomId={}", roomId, ex);
            return redisTemplate.opsForHash().get(CHAT_ROOMS, roomId);
        }
    }

    private String getRoomStatus(String roomId) {
        RoomSnapshot snapshot = getRoomSnapshot(roomId);
        if (snapshot == null) {
            return null;
        }
        return snapshot.status();
    }

    private String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private Long toLong(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
