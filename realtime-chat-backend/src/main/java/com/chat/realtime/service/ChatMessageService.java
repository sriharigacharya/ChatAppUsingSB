package com.chat.realtime.service;

import com.chat.realtime.dto.ChatMessageDto;
import com.chat.realtime.model.ChatMessage;
import com.chat.realtime.repository.ChatMessageRepository;
import com.chat.realtime.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageService {
    private final ChatMessageRepository repository;
    private final UserRepository userRepository;
    private final RedisMessagePublisher redisPublisher;
    private final NotificationService notificationService;
    private final UserStatusService userStatusService;
    private final TransactionTemplate transactionTemplate;

    public ChatMessageDto processMessage(ChatMessageDto chatMessageDto) {
        ChatMessage.MessageType type = chatMessageDto.getMessageType() != null
                ? chatMessageDto.getMessageType()
                : ChatMessage.MessageType.TEXT;

        // 1. Build entity
        ChatMessage message = ChatMessage.builder()
                .senderId(chatMessageDto.getSenderId())
                .recipientId(chatMessageDto.getRecipientId())
                .content(chatMessageDto.getContent() != null ? chatMessageDto.getContent() : "")
                .senderContent(chatMessageDto.getSenderContent())
                .status(ChatMessage.MessageStatus.SENT)
                .messageType(type)
                .mediaFileId(chatMessageDto.getMediaFileId())
                .fileName(chatMessageDto.getFileName())
                .fileSize(chatMessageDto.getFileSize())
                .clientMessageId(chatMessageDto.getClientMessageId())
                .createdAt(LocalDateTime.now())
                .build();

        // 2. Persist with idempotency guard outside rollback-only transaction boundary
        ChatMessage savedMessage = saveMessageSafely(message);

        // 3. Map persisted properties back to DTO
        chatMessageDto.setId(savedMessage.getId());
        chatMessageDto.setStatus(savedMessage.getStatus());
        chatMessageDto.setCreatedAt(savedMessage.getCreatedAt());
        chatMessageDto.setMessageType(savedMessage.getMessageType());
        chatMessageDto.setMediaFileId(savedMessage.getMediaFileId());
        chatMessageDto.setFileName(savedMessage.getFileName());
        chatMessageDto.setFileSize(savedMessage.getFileSize());

        // 4. Publish to Redis so recipient node sends to WebSocket
        redisPublisher.publish(chatMessageDto);

        // 5. Send Push Notification if recipient is offline (FCM)
        userRepository.findById(chatMessageDto.getRecipientId()).ifPresent(recipient -> {
            boolean isOnline = userStatusService.isUserOnline(recipient.getUsername());
            if (!isOnline) {
                userRepository.findById(chatMessageDto.getSenderId()).ifPresent(sender -> {
                    String notificationBody = type == ChatMessage.MessageType.IMAGE
                            ? "[Image]"
                            : (type == ChatMessage.MessageType.FILE ? "[File] " + savedMessage.getFileName() : chatMessageDto.getContent());

                    notificationService.sendPushNotification(
                            recipient.getId().toString(),
                            sender.getUsername(),
                            notificationBody
                    );
                });
            }
        });

        return chatMessageDto;
    }

    private ChatMessage saveMessageSafely(ChatMessage message) {
        if (message.getClientMessageId() != null && !message.getClientMessageId().trim().isEmpty()) {
            try {
                return transactionTemplate.execute(status -> repository.saveAndFlush(message));
            } catch (DataIntegrityViolationException ex) {
                log.info("Duplicate message detected for senderId={} clientMessageId={}. Loading existing.",
                        message.getSenderId(), message.getClientMessageId());
                return repository.findBySenderIdAndClientMessageId(message.getSenderId(), message.getClientMessageId())
                        .orElseThrow(() -> ex);
            }
        } else {
            return repository.save(message);
        }
    }
    
    public void updateMessageStatus(Long messageId, ChatMessage.MessageStatus newStatus) {
        repository.findById(messageId).ifPresent(message -> {
            message.setStatus(newStatus);
            repository.save(message);

            // Publish back to original sender so they see the blue tick / delivered status
            ChatMessageDto receiptDto = ChatMessageDto.builder()
                    .id(message.getId())
                    .senderId(message.getRecipientId()) // The user who is sending the receipt
                    .recipientId(message.getSenderId()) // The original sender who needs the update
                    .content(message.getContent())
                    .status(message.getStatus())
                    .messageType(message.getMessageType())
                    .mediaFileId(message.getMediaFileId())
                    .fileName(message.getFileName())
                    .fileSize(message.getFileSize())
                    .createdAt(message.getCreatedAt())
                    .build();
            redisPublisher.publish(receiptDto);
        });
    }

    public List<ChatMessageDto> getChatHistory(Long user1Id, Long user2Id) {
        return repository.findBySenderIdAndRecipientIdOrRecipientIdAndSenderIdOrderByCreatedAtAsc(
                user1Id, user2Id, user1Id, user2Id
        ).stream().map(msg -> ChatMessageDto.builder()
                .id(msg.getId())
                .senderId(msg.getSenderId())
                .recipientId(msg.getRecipientId())
                .content(msg.getContent())
                .senderContent(msg.getSenderContent())
                .status(msg.getStatus())
                .messageType(msg.getMessageType())
                .mediaFileId(msg.getMediaFileId())
                .fileName(msg.getFileName())
                .fileSize(msg.getFileSize())
                .clientMessageId(msg.getClientMessageId())
                .createdAt(msg.getCreatedAt())
                .build()
        ).collect(Collectors.toList());
    }
}
