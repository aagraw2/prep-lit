package com.preplit.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.preplit.model.InterviewContext;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Redis-based repository for storing interview contexts.
 * Contexts expire after 24 hours.
 */
@Repository
public class InterviewContextRepository {
    
    private static final String KEY_PREFIX = "interview:context:";
    private static final long TTL_HOURS = 24;
    
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public InterviewContextRepository(RedisTemplate<String, Object> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }
    
    /**
     * Saves interview context to Redis with 24-hour expiration.
     */
    public void save(InterviewContext context) {
        String key = KEY_PREFIX + context.getSessionId();
        redisTemplate.opsForValue().set(key, context, TTL_HOURS, TimeUnit.HOURS);
    }
    
    /**
     * Retrieves interview context from Redis.
     */
    public InterviewContext findById(UUID sessionId) {
        String key = KEY_PREFIX + sessionId;
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        
        // Handle deserialization properly
        try {
            if (value instanceof InterviewContext) {
                return (InterviewContext) value;
            } else {
                // Convert LinkedHashMap or other types to InterviewContext
                return objectMapper.convertValue(value, InterviewContext.class);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize InterviewContext for session: " + sessionId, e);
        }
    }
    
    /**
     * Deletes interview context from Redis.
     */
    public void deleteById(UUID sessionId) {
        String key = KEY_PREFIX + sessionId;
        redisTemplate.delete(key);
    }
    
    /**
     * Checks if context exists in Redis.
     */
    public boolean existsById(UUID sessionId) {
        String key = KEY_PREFIX + sessionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }
}
