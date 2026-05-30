package com.example.chatapp.models;

public class ChatMessage {
    private Long id;
    private Long senderId;
    private Long recipientId;
    private String content;
    private String senderContent;
    private String status;
    private String createdAt; // backend sends LocalDateTime as ISO string

    public ChatMessage() {}

    // Convenience constructor: AndroidActivity passes IDs as Strings from SessionManager
    public ChatMessage(String senderId, String recipientId, String content) {
        this.senderId = Long.parseLong(senderId);
        this.recipientId = Long.parseLong(recipientId);
        this.content = content;
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
}
