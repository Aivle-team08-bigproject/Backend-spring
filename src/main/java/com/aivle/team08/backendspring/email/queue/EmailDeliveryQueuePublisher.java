package com.aivle.team08.backendspring.email.queue;

public interface EmailDeliveryQueuePublisher {
    void publish(EmailDeliveryQueueResult result);
}
