package com.chat.realtime.dto;

import com.chat.realtime.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {
    private Long id;
    private Long senderId;
    private Long recipientId;
    private String content;
    private String senderContent;
    private ChatMessage.MessageStatus status;
    private ChatMessage.MessageType messageType;
    private UUID mediaFileId;
    private String fileName;
    private Long fileSize;
    private String clientMessageId;
    private LocalDateTime createdAt;
}
