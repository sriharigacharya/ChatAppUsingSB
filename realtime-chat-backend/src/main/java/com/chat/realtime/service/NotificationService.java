package com.chat.realtime.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {
    
    // In a real scenario, you'd fetch the recipient's exact FCM Device token from the database.
    // For simplicity, we can use Firebase Topics and subscribe the Android client to "user_{id}".
    public void sendPushNotification(String recipientId, String senderName, String body) {
        try {
            String userTopic = "user_" + recipientId;
            
            Message message = Message.builder()
                    .setNotification(Notification.builder()
                            .setTitle("New message from " + senderName)
                            .setBody(body)
                            .build())
                    .setTopic(userTopic)
                    .build();

            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Successfully sent FCM message: {}", response);
        } catch (Exception e) {
            log.warn("Failed to send push notification to {}: {}", recipientId, e.getMessage());
        }
    }
}
