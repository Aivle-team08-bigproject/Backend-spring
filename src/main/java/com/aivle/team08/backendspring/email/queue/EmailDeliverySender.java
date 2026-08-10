package com.aivle.team08.backendspring.email.queue;

public interface EmailDeliverySender {
    SendResult send(EmailDeliveryQueueMessage message);

    record SendResult(String status, String providerMessageId, String failureCode) {}
}
