package com.chat.realtime.config;

import com.chat.realtime.model.User;
import com.chat.realtime.service.UserService;
import com.chat.realtime.service.UserStatusService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final UserStatusService userStatusService;
    private final UserService userService;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        
        // Retrieve client-supplied username header from native headers
        String username = headerAccessor.getFirstNativeHeader("username");
        if (username != null && !username.isEmpty()) {
            if (headerAccessor.getSessionAttributes() != null) {
                headerAccessor.getSessionAttributes().put("username", username);
            }
            log.info("User connected to WebSocket: {}", username);
            userStatusService.setUserOnline(username);
            userService.updateUserStatus(username, User.Status.ONLINE);
        } else {
            // Fallback to principal if available
            Principal user = headerAccessor.getUser();
            if (user != null) {
                log.info("User connected to WebSocket (Principal): {}", user.getName());
                userStatusService.setUserOnline(user.getName());
                userService.updateUserStatus(user.getName(), User.Status.ONLINE);
            }
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        
        String username = null;
        if (headerAccessor.getSessionAttributes() != null) {
            username = (String) headerAccessor.getSessionAttributes().get("username");
        }
        
        if (username != null) {
            log.info("User disconnected from WebSocket: {}", username);
            userStatusService.setUserOffline(username);
            userService.updateUserStatus(username, User.Status.OFFLINE);
        } else {
            Principal user = headerAccessor.getUser();
            if (user != null) {
                log.info("User disconnected from WebSocket (Principal): {}", user.getName());
                userStatusService.setUserOffline(user.getName());
                userService.updateUserStatus(user.getName(), User.Status.OFFLINE);
            }
        }
    }
}
