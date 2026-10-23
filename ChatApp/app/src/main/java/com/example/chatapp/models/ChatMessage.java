package com.example.chatapp.models;

public class ChatMessage {
    private Long id;
    private Long senderId;
    private Long recipientId;
    private String content;
    private String senderContent;
    private String status;
    private String messageType; // "TEXT", "IMAGE", "FILE"
    private String mediaFileId;
    private String fileName;
    private Long fileSize;
    private String clientMessageId;
    private String createdAt; // backend sends LocalDateTime as ISO string

    // Local transient state for decrypted file path on device
    private transient String localFilePath;

    public ChatMessage() {
        this.messageType = "TEXT";
    }

    // Convenience constructor for text messages
    public ChatMessage(String senderId, String recipientId, String content) {
        this.senderId = Long.parseLong(senderId);
        this.recipientId = Long.parseLong(recipientId);
        this.content = content;
        this.messageType = "TEXT";
    }

    public boolean isImage() {
        return "IMAGE".equalsIgnoreCase(messageType);
    }

    public boolean isFile() {
        return "FILE".equalsIgnoreCase(messageType);
    }

    public boolean isText() {
        return messageType == null || "TEXT".equalsIgnoreCase(messageType);
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }

    public String getSenderContent() { return senderContent; }
    public void setSenderContent(String senderContent) { this.senderContent = senderContent; }

    public String getMessageType() { return messageType; }
    public void setMessageType(String messageType) { this.messageType = messageType; }

    public String getMediaFileId() { return mediaFileId; }
    public void setMediaFileId(String mediaFileId) { this.mediaFileId = mediaFileId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getClientMessageId() { return clientMessageId; }
    public void setClientMessageId(String clientMessageId) { this.clientMessageId = clientMessageId; }

    public String getLocalFilePath() { return localFilePath; }
    public void setLocalFilePath(String localFilePath) { this.localFilePath = localFilePath; }
}
