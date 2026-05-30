package com.example.chatapp.models;

public class FriendRequest {
    private Long id;
    private Long senderId;
    private Long recipientId;
    private String senderUsername;
    private String recipientUsername;
    private String senderPublicKey;
    private String recipientPublicKey;
    private String status;
    private String createdAt;

    public FriendRequest() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }

    public Long getRecipientId() { return recipientId; }
    public void setRecipientId(Long recipientId) { this.recipientId = recipientId; }

    public String getSenderUsername() { return senderUsername; }
    public void setSenderUsername(String senderUsername) { this.senderUsername = senderUsername; }

    public String getRecipientUsername() { return recipientUsername; }
    public void setRecipientUsername(String recipientUsername) { this.recipientUsername = recipientUsername; }

    public String getSenderPublicKey() { return senderPublicKey; }
    public void setSenderPublicKey(String senderPublicKey) { this.senderPublicKey = senderPublicKey; }

    public String getRecipientPublicKey() { return recipientPublicKey; }
    public void setRecipientPublicKey(String recipientPublicKey) { this.recipientPublicKey = recipientPublicKey; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getCreatedAt() { return createdAt; }
    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}
