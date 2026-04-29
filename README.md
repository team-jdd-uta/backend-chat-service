# backend-chat-service

채팅 메시지 처리 서비스입니다. Socket.IO Gateway가 전달한 TALK 메시지를 검증하고, Redis Pub/Sub로 실시간 fan-out을 요청하며 Redis Stream에 메시지를 저장합니다.

## 역할

- TALK 메시지의 타입과 방 존재 여부를 검증합니다.
- `room-service`를 우선 호출해 방 존재 여부를 확인합니다.
- `room-service` 호출 실패 시 Redis `CHAT_ROOM` 해시를 fallback으로 확인합니다.
- Redis Pub/Sub 채널 `chat:msg:{roomId}`로 메시지를 발행합니다.
- Redis Stream `chat:stream:room:{roomId}`에 메시지를 비동기로 append합니다.

Socket 연결 자체는 `backend-socket-io-gateway`가 담당합니다.

## 기술 스택

- Java 21
- Spring Boot 4
- Spring Web
- Spring Data Redis
- Redis Cluster
- Redis Standalone Stream

## 주요 API

| Method | Path | 설명 |
| --- | --- | --- |
| `POST` | `/chat/message` | TALK 메시지 검증 후 Redis Pub/Sub와 Stream에 발행 |

요청 예:

```bash
curl -X POST http://localhost:8083/chat/message \
  -H "Content-Type: application/json" \
  -d '{
    "type": "TALK",
    "roomId": "room-id",
    "sender": "alice",
    "message": "hello"
  }'
```

## 메시지 모델

```json
{
  "type": "TALK",
  "roomId": "room-id",
  "sender": "alice",
  "message": "hello",
  "msgId": "optional-message-id"
}
```

현재 `chat-service`는 `TALK` 타입만 처리합니다. `ENTER`, `QUIT`은 Socket.IO Gateway 내부 이벤트로 처리합니다.

## 환경변수

| 변수 | 기본값 | 설명 |
| --- | --- | --- |
| `SERVER_PORT` | `8083` | HTTP 서버 포트 |
| `SPRING_REDIS_CLUSTER_NODES` | `localhost:7000,...,localhost:7005` | Redis Cluster 노드 목록 |
| `ROOM_SERVICE_BASE_URL` | `http://localhost:8082` | room-service base URL |
| `ROOM_SERVICE_CONNECT_TIMEOUT_MS` | `1000` | room-service 연결 timeout |
| `ROOM_SERVICE_READ_TIMEOUT_MS` | `3000` | room-service read timeout |
| `CHAT_STREAM_REDIS_HOST` | `localhost` | Redis Stream용 Redis host |
| `CHAT_STREAM_REDIS_PORT` | `6379` | Redis Stream용 Redis port |
| `CHAT_STREAM_KEY_PREFIX` | `chat:stream:room:` | Stream key prefix |
| `CHAT_PUBSUB_PUBLISH_RETRY_MAX_ATTEMPTS` | `3` | Pub/Sub 발행 실패 재시도 횟수 |
| `CHAT_PUBSUB_PUBLISH_RETRY_BACKOFF_MS` | `200` | Pub/Sub 재시도 간격(ms) |

## Redis Key / Channel 규칙

| 대상 | 이름 | 설명 |
| --- | --- | --- |
| Pub/Sub channel | `chat:msg:{roomId}` | Gateway가 구독하는 실시간 메시지 채널 |
| Stream key | `chat:stream:room:{roomId}` | MongoDB 저장 consumer가 읽는 stream |
| Room hash | `CHAT_ROOM` | room-service 장애 시 방 존재 확인 fallback |

## 로컬 실행

`room-service`, Redis Cluster, Redis Stream용 Redis가 먼저 떠 있어야 합니다.

```bash
gradle bootRun
```

Docker 이미지 빌드:

```bash
docker build -t team9-chat-service:local .
```

## 운영 주의점

- Stream append는 비동기 queue로 처리합니다. queue가 가득 차면 stream 저장이 drop될 수 있습니다.
- Pub/Sub 발행 실패 시 별도 retry queue에서 재시도합니다.
- Pub/Sub와 Stream은 목적이 다릅니다. Pub/Sub는 실시간 전달, Stream은 MongoDB 영속화 경로입니다.
