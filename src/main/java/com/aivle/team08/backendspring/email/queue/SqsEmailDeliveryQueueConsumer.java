package com.aivle.team08.backendspring.email.queue;

import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/** 운영용 SQS 요청 큐 consumer. 성공적으로 소비한 메시지만 삭제한다. */
@Component
@ConditionalOnProperty(name = "email.queue.enabled", havingValue = "true")
@ConditionalOnProperty(name = "email.queue.backend", havingValue = "sqs")
public class SqsEmailDeliveryQueueConsumer {
    private final SqsClient sqs;
    private final EmailDeliveryQueueConsumer consumer;
    private final String requestUrl;
    private final int visibilityTimeoutSeconds;
    private final int waitTimeSeconds;

    public SqsEmailDeliveryQueueConsumer(
            SqsClient sqs,
            EmailDeliveryQueueConsumer consumer,
            @Value("${email.queue.request-url}") String requestUrl,
            @Value("${email.queue.visibility-timeout-seconds:60}") int visibilityTimeoutSeconds,
            @Value("${email.queue.wait-time-seconds:20}") int waitTimeSeconds) {
        this.sqs = sqs;
        this.consumer = consumer;
        this.requestUrl = requestUrl;
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
        this.waitTimeSeconds = waitTimeSeconds;
    }

    @Scheduled(fixedDelayString = "${email.queue.poll-delay-ms:1000}")
    public void poll() {
        if (requestUrl == null || requestUrl.isBlank()) {
            throw new IllegalStateException("EMAIL_REQUEST_QUEUE_URL is required for SQS backend");
        }
        List<Message> messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(requestUrl)
                .maxNumberOfMessages(1)
                .waitTimeSeconds(waitTimeSeconds)
                .visibilityTimeout(visibilityTimeoutSeconds)
                .build()).messages();
        for (Message message : messages) {
            boolean processed = consumer.consumeJson(message.body());
            if (processed) {
                sqs.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(requestUrl)
                        .receiptHandle(message.receiptHandle())
                        .build());
            }
        }
    }
}
