package com.example.chatapp.models;

public class ChatMessage {
    private String id;
    private String chatId;
    private String senderId;
    private String recipientId;
    private String content;
    private String timestamp;
    private String status;

    public ChatMessage() {}

    public ChatMessage(String senderId, String recipientId, String content) {
        this.senderId = senderId;
        this.recipientId = recipientId;
        this.content = content;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getChatId() { return chatId; }
    public void setChatId(String chatId) { this.chatId = chatId; }
    
    public String getSenderId() { return senderId; }
    public void setSenderId(String senderId) { this.senderId = senderId; }
    
    public String getRecipientId() { return recipientId; }
    public void setRecipientId(String recipientId) { this.recipientId = recipientId; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
