package com.aivle.team08.backendspring.email.queue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/** 운영용 SQS 결과 큐 publisher. */
@Component
@ConditionalOnProperty(name = "email.queue.enabled", havingValue = "true")
@ConditionalOnProperty(name = "email.queue.backend", havingValue = "sqs")
public class SqsEmailDeliveryQueuePublisher implements EmailDeliveryQueuePublisher {
    private final SqsClient sqs;
    private final ObjectMapper objectMapper;
    private final String resultUrl;

    public SqsEmailDeliveryQueuePublisher(
            SqsClient sqs,
            ObjectMapper objectMapper,
            @Value("${email.queue.result-url}") String resultUrl) {
        this.sqs = sqs;
        this.objectMapper = objectMapper;
        this.resultUrl = resultUrl;
    }

    @Override
    public void publish(EmailDeliveryQueueResult result) {
        if (resultUrl == null || resultUrl.isBlank()) {
            throw new IllegalStateException("EMAIL_RESULT_QUEUE_URL is required for SQS backend");
        }
        try {
            sqs.sendMessage(SendMessageRequest.builder()
                    .queueUrl(resultUrl)
                    .messageBody(objectMapper.writeValueAsString(result))
                    .build());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("email result serialization failed", exception);
        }
    }
}
