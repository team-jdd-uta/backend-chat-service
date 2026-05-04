package com.example.chat.model;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Setter
public class ChatMessage {
    public enum MessageType {
        ENTER, TALK, QUIT
    }

    private MessageType type;
    private String roomId;
    private String sender;
    private String senderUserId;
    private String roomOwnerUserId;
    private String roomName;
    private String msgId;
    private String message;
    @JsonProperty("isSuperChat")
    @JsonAlias({"superChat", "is_super_chat"})
    private Boolean isSuperChat;
}
