package com.chat.realtime.service;

import com.chat.realtime.dto.ChatMessageDto;
import com.chat.realtime.model.ChatMessage;
import com.chat.realtime.repository.ChatMessageRepository;
import com.chat.realtime.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatMessageService {
    private final ChatMessageRepository repository;
    private final UserRepository userRepository;
    private final RedisMessagePublisher redisPublisher;
    private final NotificationService notificationService;
    private final UserStatusService userStatusService;

    public ChatMessageDto processMessage(ChatMessageDto chatMessageDto) {
        // 1. Save message to PostgreSQL Database
        ChatMessage message = ChatMessage.builder()
                .senderId(chatMessageDto.getSenderId())
                .recipientId(chatMessageDto.getRecipientId())
                .content(chatMessageDto.getContent())
                .status(ChatMessage.MessageStatus.SENT)
                .createdAt(LocalDateTime.now())
                .build();
        message = repository.save(message);

        // 2. Map generated properties back to DTO
        chatMessageDto.setId(message.getId());
        chatMessageDto.setStatus(message.getStatus());
        chatMessageDto.setCreatedAt(message.getCreatedAt());

        // 3. Publish to Redis so the correct server node can send it to the recipient's WebSocket
        redisPublisher.publish(chatMessageDto);

        // 4. Send Push Notification if recipient is offline (FCM)
        userRepository.findById(chatMessageDto.getRecipientId()).ifPresent(recipient -> {
            boolean isOnline = userStatusService.isUserOnline(recipient.getUsername());
            if (!isOnline) {
                userRepository.findById(chatMessageDto.getSenderId()).ifPresent(sender -> {
                    notificationService.sendPushNotification(
                            recipient.getId().toString(),
                            sender.getUsername(),
                            chatMessageDto.getContent()
                    );
                });
            }
        });

        return chatMessageDto;
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
                .status(msg.getStatus())
                .createdAt(msg.getCreatedAt())
                .build()
        ).collect(Collectors.toList());
    }
}
