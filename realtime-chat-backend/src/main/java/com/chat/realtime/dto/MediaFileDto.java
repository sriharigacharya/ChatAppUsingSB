package com.chat.realtime.dto;

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
public class MediaFileDto {
    private UUID id;
    private Long uploaderId;
    private Long recipientId;
    private String originalFileName;
    private String fileType;
    private Long fileSizeBytes;
    private String encryptedAesKey;
    private LocalDateTime createdAt;
}
