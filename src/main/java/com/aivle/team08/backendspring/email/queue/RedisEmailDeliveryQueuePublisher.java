package com.aivle.team08.backendspring.email.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/** 로컬 Redis 결과 큐 publisher. 운영에서는 SQS publisher로 교체한다. */
@Component
@ConditionalOnProperty(name = "email.queue.enabled", havingValue = "true")
@ConditionalOnProperty(name = "email.queue.backend", havingValue = "redis", matchIfMissing = true)
public class RedisEmailDeliveryQueuePublisher implements EmailDeliveryQueuePublisher {
    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String resultKey;

    public RedisEmailDeliveryQueuePublisher(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            @Value("${email.queue.result-key:email:delivery:results}") String resultKey) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.resultKey = resultKey;
    }

    @Override
    public void publish(EmailDeliveryQueueResult result) {
        try {
            redis.opsForList().rightPush(resultKey, objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("email result serialization failed", exception);
        }
    }
}
