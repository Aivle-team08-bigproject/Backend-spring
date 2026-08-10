package com.aivle.team08.backendspring.email.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

/**
 * SQS adapter와 발송 도메인을 분리하는 무상태 consumer.
 * 실제 SQS receive/delete는 다음 AWS adapter가 담당하며, 이 클래스는 DB를 사용하지 않는다.
 */
@Service
public class EmailDeliveryQueueConsumer {
    private final ObjectMapper objectMapper;
    private final EmailDeliverySender sender;
    private final EmailDeliveryQueuePublisher resultPublisher;

    public EmailDeliveryQueueConsumer(
            ObjectMapper objectMapper,
            EmailDeliverySender sender,
            EmailDeliveryQueuePublisher resultPublisher) {
        this.objectMapper = objectMapper;
        this.sender = sender;
        this.resultPublisher = resultPublisher;
    }

    public boolean consume(EmailDeliveryQueueMessage message) {
        try {
            validate(message);
            EmailDeliverySender.SendResult result = sender.send(message);
            resultPublisher.publish(new EmailDeliveryQueueResult(
                    message.deliveryId(), result.status(), result.providerMessageId(), result.failureCode()));
            return !"FAILED".equalsIgnoreCase(result.status());
        } catch (RuntimeException exception) {
            resultPublisher.publish(new EmailDeliveryQueueResult(
                    message.deliveryId(), "FAILED", null, "MESSAGE_VALIDATION_FAILED"));
            return false;
        }
    }

    /** Redis/SQS adapter가 받은 JSON을 동일한 계약으로 변환하는 진입점. */
    public boolean consumeJson(String json) {
        try {
            return consume(objectMapper.readValue(json, EmailDeliveryQueueMessage.class));
        } catch (Exception exception) {
            resultPublisher.publish(new EmailDeliveryQueueResult(
                    "unknown", "FAILED", null, "MESSAGE_DESERIALIZATION_FAILED"));
            return false;
        }
    }

    private void validate(EmailDeliveryQueueMessage message) {
        if (message.deliveryId() == null || message.deliveryId().isBlank()
                || message.idempotencyKey() == null || message.idempotencyKey().isBlank()
                || message.recipient() == null || message.recipient().isBlank()
                || message.sampleRows() == null || message.sampleRows().size() != 5
                || message.sampleColumns() == null || message.sampleColumns().isEmpty()
                || message.sampleSha256() == null || message.sampleSha256().isBlank()) {
            throw new IllegalArgumentException("email queue message contract is invalid");
        }
    }
}
