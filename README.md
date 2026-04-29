# chat-service

## 개요

`chat-service`는 Socket.IO Gateway가 보낸 TALK 메시지를 검증하고, Redis Pub/Sub와 Redis Stream에 기록하는 Spring Boot 서비스다.

## 로컬 실행

설치된 Gradle을 기준으로 실행한다.

```bash
gradle bootRun
```

## 환경변수

- `SERVER_PORT`
- `SPRING_REDIS_CLUSTER_NODES`
- `ROOM_SERVICE_BASE_URL`
- `ROOM_SERVICE_CONNECT_TIMEOUT_MS`
- `ROOM_SERVICE_READ_TIMEOUT_MS`
- `CHAT_STREAM_REDIS_HOST`
- `CHAT_STREAM_REDIS_PORT`
- `CHAT_STREAM_KEY_PREFIX`
- `CHAT_PUBSUB_PUBLISH_RETRY_MAX_ATTEMPTS`
- `CHAT_PUBSUB_PUBLISH_RETRY_BACKOFF_MS`

기본값은 `src/main/resources/application.properties`에 있다.

## 주요 API

- `POST /chat/message`

## Redis 규칙

- 발행 채널: `chat:msg:{roomId}`
- 이력 스트림: `chat:stream:room:{roomId}`
- `CHAT_ROOM` 해시는 방 유효성 fallback 용도로만 읽는다.

