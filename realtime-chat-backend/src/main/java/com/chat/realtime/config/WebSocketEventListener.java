package com.chat.realtime.config;

import com.chat.realtime.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final UserStatusService userStatusService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        
        // Use principal if authenticated, else we rely on clients setting their status via REST API for simplicity
        Principal user = headerAccessor.getUser();
        if (user != null) {
            log.info("User connected to WebSocket: {}", user.getName());
            userStatusService.setUserOnline(user.getName());
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        
        Principal user = headerAccessor.getUser();
        if (user != null) {
            log.info("User disconnected from WebSocket: {}", user.getName());
            userStatusService.setUserOffline(user.getName());
        }
    }
}
