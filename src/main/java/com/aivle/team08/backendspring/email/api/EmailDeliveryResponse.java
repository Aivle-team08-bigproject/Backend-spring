package com.aivle.team08.backendspring.email.api;

import com.aivle.team08.backendspring.email.domain.DeliveryStatus;

public record EmailDeliveryResponse(
        long deliveryId, long runId, DeliveryStatus status, boolean duplicate, String message) {}
