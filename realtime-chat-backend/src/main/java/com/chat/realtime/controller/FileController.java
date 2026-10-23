package com.chat.realtime.controller;

import com.chat.realtime.dto.MediaFileDto;
import com.chat.realtime.model.User;
import com.chat.realtime.repository.UserRepository;
import com.chat.realtime.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.UUID;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
@Slf4j
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaFileDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam("recipientId") Long recipientId,
            @RequestParam(value = "fileType", defaultValue = "FILE") String fileType,
            @RequestParam(value = "originalFileName", required = false) String originalFileName,
            @RequestParam("senderEncryptedAesKey") String senderEncryptedAesKey,
            @RequestParam("recipientEncryptedAesKey") String recipientEncryptedAesKey,
            Principal principal) {

        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User uploader = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        log.info("File upload request from uploaderId={} (username={}) to recipientId={}",
                uploader.getId(), principal.getName(), recipientId);

        MediaFileDto result = fileService.uploadFile(
                uploader.getId(),
                recipientId,
                fileType,
                originalFileName,
                senderEncryptedAesKey,
                recipientEncryptedAesKey,
                file
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @GetMapping("/{id}/metadata")
    public ResponseEntity<MediaFileDto> getMetadata(
            @PathVariable("id") UUID id,
            Principal principal) {

        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        MediaFileDto metadata = fileService.getMediaMetadata(id, user.getId());
        return ResponseEntity.ok(metadata);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadCiphertext(
            @PathVariable("id") UUID id,
            Principal principal) {

        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        User user = userRepository.findByUsername(principal.getName())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        MediaFileDto metadata = fileService.getMediaMetadata(id, user.getId());
        Resource resource = fileService.loadFileResource(id, user.getId());

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + id + ".enc\"")
                .header("X-Original-Filename", metadata.getOriginalFileName())
                .header("X-File-Type", metadata.getFileType())
                .header("X-Encrypted-Key", metadata.getEncryptedAesKey())
                .body(resource);
    }
}
