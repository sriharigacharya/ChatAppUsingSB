package com.chat.realtime.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "messages", uniqueConstraints = {
    @UniqueConstraint(name = "uk_sender_client_message_id", columnNames = {"senderId", "clientMessageId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long senderId;

    @Column(nullable = false)
    private Long recipientId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(columnDefinition = "TEXT")
    private String senderContent;

    @Enumerated(EnumType.STRING)
    private MessageStatus status;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private MessageType messageType = MessageType.TEXT;

    private UUID mediaFileId;

    private String fileName;

    private Long fileSize;

    private String clientMessageId;

    private LocalDateTime createdAt;

    public enum MessageStatus {
        SENT, DELIVERED, READ
    }

    public enum MessageType {
        TEXT, IMAGE, FILE
    }
}
