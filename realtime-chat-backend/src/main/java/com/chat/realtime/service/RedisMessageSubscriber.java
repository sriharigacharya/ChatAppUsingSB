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
            
            // By default, Spring intercepts any destination starting with "/user/" and tries to 
            // resolve it to a specific authenticated session. Since Android clients don't authenticate 
            // the WebSocket (no Principal), it silently drops those messages.
            // Bypassing it entirely by using a standard /topic route.
            messagingTemplate.convertAndSend(
                    "/topic/messages/" + chatMessage.getRecipientId(),
                    chatMessage
            );
        } catch (Exception e) {
            log.error("Failed to parse Redis message", e);
        }
    }
}
