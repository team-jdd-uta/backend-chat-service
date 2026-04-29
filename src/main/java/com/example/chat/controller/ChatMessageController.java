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
@RequestMapping("/chat")
@Slf4j
public class ChatMessageController {

    private final MessageBrokerService messageBrokerService;
    private final RoomServiceClient roomServiceClient;

    @PostMapping("/message")
    public ResponseEntity<Void> receiveMessage(@RequestBody ChatMessage message) {
        try {
            validate(message);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid message payload: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }

        log.info("Accepted chat message roomId={} sender={} type={}", message.getRoomId(), message.getSender(), message.getType());
        messageBrokerService.publish(message.getRoomId(), message);
        return ResponseEntity.ok().build();
    }

    private void validate(ChatMessage message) {
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
        if (isBlank(message.getMessage())) {
            throw new IllegalArgumentException("message required");
        }
        if (!roomServiceClient.roomExists(message.getRoomId())) {
            throw new IllegalArgumentException("chat room not found: " + message.getRoomId());
        }
    }

    private boolean isBlank(String v) {
        return v == null || v.isBlank();
    }
}
