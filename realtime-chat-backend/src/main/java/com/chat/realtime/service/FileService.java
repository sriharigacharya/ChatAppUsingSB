package com.chat.realtime.service;

import com.chat.realtime.dto.MediaFileDto;
import com.chat.realtime.model.MediaFile;
import com.chat.realtime.repository.MediaFileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class FileService {

    private static final long MAX_FILE_SIZE = 26L * 1024 * 1024; // 26 MB limit for ciphertext

    private final MediaFileRepository mediaFileRepository;
    private final StorageService storageService;

    @Transactional
    public MediaFileDto uploadFile(
            Long uploaderId,
            Long recipientId,
            String fileType,
            String originalFileName,
            String senderEncryptedAesKey,
            String recipientEncryptedAesKey,
            MultipartFile file) {

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File is empty");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "File size exceeds maximum allowed 26MB");
        }

        String normalizedFileType = fileType != null ? fileType.toUpperCase().trim() : "FILE";
        if (!"IMAGE".equals(normalizedFileType) && !"FILE".equals(normalizedFileType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid fileType. Allowed values: IMAGE, FILE");
        }

        if (senderEncryptedAesKey == null || senderEncryptedAesKey.trim().isEmpty() ||
            recipientEncryptedAesKey == null || recipientEncryptedAesKey.trim().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Missing participant encrypted AES keys");
        }

        // Store ciphertext using bare UUID key without file extension (as per design for E2E files)
        String storageKey = UUID.randomUUID().toString();

        try (InputStream is = file.getInputStream()) {
            storageService.store(storageKey, is, file.getSize());
        } catch (Exception e) {
            log.error("Failed to store file in storage", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to save file contents");
        }

        MediaFile mediaFile = MediaFile.builder()
                .uploaderId(uploaderId)
                .recipientId(recipientId)
                .originalFileName(originalFileName != null ? originalFileName : "file")
                .fileType(normalizedFileType)
                .fileSizeBytes(file.getSize())
                .storageKey(storageKey)
                .senderEncryptedAesKey(senderEncryptedAesKey)
                .recipientEncryptedAesKey(recipientEncryptedAesKey)
                .createdAt(LocalDateTime.now())
                .build();

        mediaFile = mediaFileRepository.save(mediaFile);
        log.info("MediaFile created: id={}, uploaderId={}, recipientId={}, size={}",
                mediaFile.getId(), uploaderId, recipientId, mediaFile.getFileSizeBytes());

        return MediaFileDto.builder()
                .id(mediaFile.getId())
                .uploaderId(mediaFile.getUploaderId())
                .recipientId(mediaFile.getRecipientId())
                .originalFileName(mediaFile.getOriginalFileName())
                .fileType(mediaFile.getFileType())
                .fileSizeBytes(mediaFile.getFileSizeBytes())
                .encryptedAesKey(senderEncryptedAesKey)
                .createdAt(mediaFile.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public MediaFileDto getMediaMetadata(UUID mediaFileId, Long requestingUserId) {
        MediaFile mediaFile = getAuthorizedMediaFile(mediaFileId, requestingUserId);

        // Select the encrypted key corresponding to the caller
        String keyForUser = requestingUserId.equals(mediaFile.getUploaderId())
                ? mediaFile.getSenderEncryptedAesKey()
                : mediaFile.getRecipientEncryptedAesKey();

        return MediaFileDto.builder()
                .id(mediaFile.getId())
                .uploaderId(mediaFile.getUploaderId())
                .recipientId(mediaFile.getRecipientId())
                .originalFileName(mediaFile.getOriginalFileName())
                .fileType(mediaFile.getFileType())
                .fileSizeBytes(mediaFile.getFileSizeBytes())
                .encryptedAesKey(keyForUser)
                .createdAt(mediaFile.getCreatedAt())
                .build();
    }

    @Transactional(readOnly = true)
    public Resource loadFileResource(UUID mediaFileId, Long requestingUserId) {
        MediaFile mediaFile = getAuthorizedMediaFile(mediaFileId, requestingUserId);
        try {
            InputStream stream = storageService.load(mediaFile.getStorageKey());
            return new InputStreamResource(stream);
        } catch (Exception e) {
            log.error("Failed to load file resource for {}", mediaFileId, e);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "File content not found");
        }
    }

    private MediaFile getAuthorizedMediaFile(UUID mediaFileId, Long requestingUserId) {
        MediaFile mediaFile = mediaFileRepository.findById(mediaFileId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Media file not found"));

        if (!requestingUserId.equals(mediaFile.getUploaderId()) &&
            !requestingUserId.equals(mediaFile.getRecipientId())) {
            log.warn("Unauthorized access attempt to mediaFile {} by user {}", mediaFileId, requestingUserId);
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You are not authorized to access this media file");
        }
        return mediaFile;
    }
}
