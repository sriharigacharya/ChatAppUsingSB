package com.chat.realtime.service;

import com.chat.realtime.dto.ChatMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisMessagePublisher {

    private final StringRedisTemplate redisTemplate;
    private final ChannelTopic topic;
    private final ObjectMapper objectMapper;

    public void publish(ChatMessageDto message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            redisTemplate.convertAndSend(topic.getTopic(), json);
            log.info("Published message to Redis topic: {}", topic.getTopic());
        } catch (Exception e) {
            log.error("Failed to publish message to Redis", e);
        }
    }
}
