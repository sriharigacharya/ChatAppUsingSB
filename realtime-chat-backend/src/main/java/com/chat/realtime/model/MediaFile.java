package com.chat.realtime.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "media_files")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaFile {

    @Id
    @GeneratedValue
    @UuidGenerator
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false)
    private Long uploaderId;

    @Column(nullable = false)
    private Long recipientId;

    @Column(nullable = false)
    private String originalFileName;

    @Column(nullable = false)
    private String fileType; // "IMAGE" or "FILE"

    @Column(nullable = false)
    private Long fileSizeBytes;

    @Column(nullable = false, unique = true)
    private String storageKey; // bare UUID stored in Cloudflare R2 / S3

    @Column(nullable = false, columnDefinition = "TEXT")
    private String senderEncryptedAesKey;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String recipientEncryptedAesKey;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}
