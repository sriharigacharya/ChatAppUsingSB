package com.chat.realtime.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserStatusService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String ONLINE_USERS_KEY = "online_users";

    public void setUserOnline(String username) {
        redisTemplate.opsForSet().add(ONLINE_USERS_KEY, username);
    }

    public void setUserOffline(String username) {
        redisTemplate.opsForSet().remove(ONLINE_USERS_KEY, username);
    }

    public boolean isUserOnline(String username) {
        return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(ONLINE_USERS_KEY, username));
    }

    public Set<Object> getOnlineUsers() {
        return redisTemplate.opsForSet().members(ONLINE_USERS_KEY);
    }
}
