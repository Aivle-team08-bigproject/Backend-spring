package com.aivle.team08.backendspring.email.queue;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Spring이 FastAPI 결과 큐로 회신하는 상태 이벤트. */
public record EmailDeliveryQueueResult(
        @JsonProperty("delivery_id")
        String deliveryId,
        String status,
        @JsonProperty("provider_message_id")
        String providerMessageId,
        @JsonProperty("failure_code")
        String failureCode) {}
