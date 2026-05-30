package com.example.chatapp.models;

public class AcceptRequestPayload {
    private String recipientPublicKey;

    public AcceptRequestPayload(String recipientPublicKey) {
        this.recipientPublicKey = recipientPublicKey;
    }

    public String getRecipientPublicKey() { return recipientPublicKey; }
}
