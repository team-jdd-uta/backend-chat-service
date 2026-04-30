package com.example.chat.controller;

import com.example.chat.client.RoomServiceClient;
import com.example.chat.model.ChatMessage;
import com.example.chat.service.MessageBrokerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequiredArgsConstructor
@RestController
@RequestMapping("/message")
@Slf4j
public class ChatMessageController {

    private final MessageBrokerService messageBrokerService;
    private final RoomServiceClient roomServiceClient;

    @PostMapping
    public ResponseEntity<Void> receiveMessage(@RequestBody ChatMessage message) {
        if (message == null) {
            log.warn("Invalid message payload: payload required");
            return ResponseEntity.badRequest().build();
        }

        RoomServiceClient.RoomSnapshot roomSnapshot;
        try {
            roomSnapshot = roomServiceClient.getRoomSnapshot(message.getRoomId());
            validate(message, roomSnapshot);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid message payload: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
        if (roomSnapshot != null) {
            message.setRoomOwnerUserId(roomSnapshot.broadcasterId());
            message.setRoomName(roomSnapshot.name());
        }

        log.info(
                "Accepted chat message roomId={} sender={} senderUserId={} owner={} type={}",
                message.getRoomId(),
                message.getSender(),
                message.getSenderUserId(),
                message.getRoomOwnerUserId(),
                message.getType()
        );
        messageBrokerService.publish(message.getRoomId(), message);
        return ResponseEntity.ok().build();
    }

    private void validate(ChatMessage message, RoomServiceClient.RoomSnapshot roomSnapshot) {
        if (message == null) {
            throw new IllegalArgumentException("payload required");
        }
        if (message.getType() != ChatMessage.MessageType.TALK) {
            throw new IllegalArgumentException("only TALK type accepted");
        }
        if (isBlank(message.getRoomId())) {
            throw new IllegalArgumentException("roomId required");
        }
        if (isBlank(message.getSender())) {
            throw new IllegalArgumentException("sender required");
        }
        if (isBlank(message.getSenderUserId())) {
            throw new IllegalArgumentException("senderUserId required");
        }
        if (isBlank(message.getMessage())) {
            throw new IllegalArgumentException("message required");
        }
        if (roomSnapshot == null) {
            throw new IllegalArgumentException("chat room not found: " + message.getRoomId());
        }
        String status = roomSnapshot.status();
        if (!"READY".equals(status) && !"LIVE".equals(status)) {
            throw new IllegalArgumentException("chat room is not open: " + message.getRoomId());
        }
        if (isBlank(roomSnapshot.broadcasterId())) {
            throw new IllegalArgumentException("room broadcasterId required for chat history projection");
        }
    }

    private boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
