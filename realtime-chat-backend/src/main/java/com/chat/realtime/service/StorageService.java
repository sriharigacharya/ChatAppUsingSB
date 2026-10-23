package com.chat.realtime.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@Slf4j
public class StorageService {

    @Value("${r2.account-id:}")
    private String accountId;

    @Value("${r2.access-key:${AWS_ACCESS_KEY_ID:}}")
    private String accessKey;

    @Value("${r2.secret-key:${AWS_SECRET_ACCESS_KEY:}}")
    private String secretKey;

    @Value("${r2.bucket:${AWS_S3_BUCKET:chat-files}}")
    private String bucket;

    @Value("${s3.endpoint:${S3_ENDPOINT:}}")
    private String customEndpoint;

    @Value("${s3.region:${AWS_REGION:}}")
    private String regionStr;

    private S3Client s3Client;
    private Path localStorageDir;

    @PostConstruct
    public void init() {
        boolean hasCredentials = accessKey != null && !accessKey.trim().isEmpty() &&
                                 secretKey != null && !secretKey.trim().isEmpty();

        if (hasCredentials) {
            try {
                var s3Builder = S3Client.builder()
                        .credentialsProvider(StaticCredentialsProvider.create(
                                AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())
                        ));

                // 1. Cloudflare R2
                if (accountId != null && !accountId.trim().isEmpty()) {
                    String endpoint = "https://" + accountId.trim() + ".r2.cloudflarestorage.com";
                    log.info("Configuring Cloudflare R2 storage client: {}", endpoint);
                    s3Builder.endpointOverride(URI.create(endpoint))
                             .region(Region.of("auto"));
                }
                // 2. Custom S3 Endpoint (MinIO, Wasabi, etc.)
                else if (customEndpoint != null && !customEndpoint.trim().isEmpty()) {
                    log.info("Configuring Custom S3 storage endpoint: {}", customEndpoint);
                    s3Builder.endpointOverride(URI.create(customEndpoint.trim()))
                             .region(Region.of(regionStr != null && !regionStr.isEmpty() ? regionStr : "us-east-1"));
                }
                // 3. Standard AWS S3
                else {
                    String region = (regionStr != null && !regionStr.trim().isEmpty()) ? regionStr.trim() : "us-east-1";
                    log.info("Configuring standard AWS S3 client in region: {}", region);
                    s3Builder.region(Region.of(region));
                }

                this.s3Client = s3Builder.build();
                log.info("S3 storage client initialized successfully for bucket '{}'", bucket);
            } catch (Exception e) {
                log.error("Failed to initialize S3Client, falling back to local storage", e);
                initLocalStorage();
            }
        } else {
            log.info("No S3/R2 credentials provided; using local filesystem storage at ./uploads");
            initLocalStorage();
        }
    }

    private void initLocalStorage() {
        try {
            this.localStorageDir = Paths.get(System.getProperty("user.dir"), "uploads");
            Files.createDirectories(this.localStorageDir);
            log.info("Local storage directory: {}", this.localStorageDir.toAbsolutePath());
        } catch (Exception e) {
            log.error("Could not create local storage directory", e);
        }
    }

    public void store(String key, InputStream inputStream, long contentLength) throws Exception {
        if (s3Client != null) {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType("application/octet-stream")
                    .contentLength(contentLength)
                    .build();
            s3Client.putObject(putRequest, RequestBody.fromInputStream(inputStream, contentLength));
            log.debug("Stored file {} in R2 bucket {}", key, bucket);
        } else {
            Path target = localStorageDir.resolve(key);
            try (FileOutputStream fos = new FileOutputStream(target.toFile())) {
                inputStream.transferTo(fos);
            }
            log.debug("Stored file {} in local storage {}", key, target.toAbsolutePath());
        }
    }

    public InputStream load(String key) throws Exception {
        if (s3Client != null) {
            GetObjectRequest getRequest = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            return s3Client.getObject(getRequest);
        } else {
            Path target = localStorageDir.resolve(key);
            File file = target.toFile();
            if (!file.exists()) {
                throw new java.io.FileNotFoundException("File not found: " + key);
            }
            return new FileInputStream(file);
        }
    }
}
