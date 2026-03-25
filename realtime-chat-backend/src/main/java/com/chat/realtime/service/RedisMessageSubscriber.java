package com.chat.realtime.service;

import com.chat.realtime.dto.ChatMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisMessageSubscriber {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void onMessage(String message, String pattern) {
        try {
            ChatMessageDto chatMessage = objectMapper.readValue(message, ChatMessageDto.class);
            log.info("Received message from Redis topic: {}", chatMessage.getContent());
            
            // Forward the message to the specific user via STOMP SimpleBroker
            // The Android client sublimely subscribes to /user/{myId}/queue/messages
            messagingTemplate.convertAndSendToUser(
                    String.valueOf(chatMessage.getRecipientId()),
                    "/queue/messages",
                    chatMessage
            );
        } catch (Exception e) {
            log.error("Failed to parse Redis message", e);
        }
    }
}
