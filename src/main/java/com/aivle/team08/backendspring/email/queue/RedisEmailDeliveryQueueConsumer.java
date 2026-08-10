package com.aivle.team08.backendspring.email.queue;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** 로컬 Redis 요청 큐 consumer. 운영에서는 동일한 consumer 계약의 SQS adapter를 사용한다. */
@Component
@ConditionalOnProperty(name = "email.queue.enabled", havingValue = "true")
@ConditionalOnProperty(name = "email.queue.backend", havingValue = "redis", matchIfMissing = true)
public class RedisEmailDeliveryQueueConsumer {
    private final StringRedisTemplate redis;
    private final EmailDeliveryQueueConsumer consumer;
    private final String requestKey;

    public RedisEmailDeliveryQueueConsumer(
            StringRedisTemplate redis,
            EmailDeliveryQueueConsumer consumer,
            @Value("${email.queue.request-key:email:delivery:requests}") String requestKey) {
        this.redis = redis;
        this.consumer = consumer;
        this.requestKey = requestKey;
    }

    @Scheduled(fixedDelayString = "${email.queue.poll-delay-ms:1000}")
    public void poll() {
        String payload = redis.opsForList().leftPop(requestKey, Duration.ofMillis(250));
        if (payload != null) {
            consumer.consumeJson(payload);
        }
    }
}
