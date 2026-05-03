package com.example.chat.controller;

import com.example.chat.client.RoomServiceClient;
import com.example.chat.model.ChatMessage;
import com.example.chat.service.MessageBrokerService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class ChatMessageControllerTest {

    private final MessageBrokerService messageBrokerService = mock(MessageBrokerService.class);
    private final RoomServiceClient roomServiceClient = mock(RoomServiceClient.class);
    private final ChatMessageController controller = new ChatMessageController(messageBrokerService, roomServiceClient);

    @Test
    void rejectsMessageWithoutGatewayUserWhenGatewayAuthRequired() {
        ReflectionTestUtils.setField(controller, "gatewayAuthRequired", true);

        var response = controller.receiveMessage(validMessage(), null);

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        verify(messageBrokerService, never()).publish(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsMessageWhenGatewayUserDoesNotMatchSender() {
        ReflectionTestUtils.setField(controller, "gatewayAuthRequired", true);

        var response = controller.receiveMessage(validMessage(), "other-user");

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        verify(messageBrokerService, never()).publish(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    private ChatMessage validMessage() {
        ChatMessage message = new ChatMessage();
        message.setType(ChatMessage.MessageType.TALK);
        message.setRoomId("room-1");
        message.setSender("member1");
        message.setSenderUserId("user-1");
        message.setMessage("hello");
        return message;
    }
}
