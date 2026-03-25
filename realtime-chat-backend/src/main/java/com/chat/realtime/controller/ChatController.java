package com.chat.realtime.controller;

import com.chat.realtime.dto.ChatMessageDto;
import com.chat.realtime.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatMessageService chatMessageService;

    // This handles messages sent by Android clients to /app/chat
    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessageDto chatMessageDto) {
        log.info("Received STOMP message from user {} to user {}", 
                 chatMessageDto.getSenderId(), chatMessageDto.getRecipientId());
        chatMessageService.processMessage(chatMessageDto);
    }

    // REST endpoint to fetch chat history when opening a chat window
    @GetMapping("/api/messages/{senderId}/{recipientId}")
    public ResponseEntity<List<ChatMessageDto>> getChatHistory(
            @PathVariable Long senderId,
            @PathVariable Long recipientId) {
        return ResponseEntity.ok(chatMessageService.getChatHistory(senderId, recipientId));
    }

    // WebSocket endpoint for read receipts
    @MessageMapping("/chat/receipt")
    public void processReceipt(@Payload ReadReceiptRequest request) {
        chatMessageService.updateMessageStatus(request.getMessageId(), request.getStatus());
    }

    @lombok.Data
    public static class ReadReceiptRequest {
        private Long messageId;
        private com.chat.realtime.model.ChatMessage.MessageStatus status;
    }
}
