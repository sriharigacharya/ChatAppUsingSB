package com.example.chatapp.models;

public class FriendRequestPayload {
    private Long senderId;
    private Long recipientId;
    private String senderPublicKey;

    public FriendRequestPayload(Long senderId, Long recipientId, String senderPublicKey) {
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.senderPublicKey = senderPublicKey;
    }

    public Long getSenderId() { return senderId; }
    public Long getRecipientId() { return recipientId; }
    public String getSenderPublicKey() { return senderPublicKey; }
}
