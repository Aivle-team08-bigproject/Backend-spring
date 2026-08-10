package com.aivle.team08.backendspring.email.infrastructure;

import com.aivle.team08.backendspring.email.domain.DeliveryStatus;
import com.aivle.team08.backendspring.email.domain.DeliveryType;
import com.aivle.team08.backendspring.email.domain.EmailDelivery;
import java.util.Collection;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.context.annotation.Profile;

@Profile("legacy-db")
public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, Long> {
    Optional<EmailDelivery> findByIdempotencyKey(String idempotencyKey);

    Optional<EmailDelivery> findFirstByRunIdAndStageAttemptNoAndRecipientNormalizedAndDeliveryTypeAndStatusInOrderByIdDesc(
            long runId,
            int stageAttemptNo,
            String recipientNormalized,
            DeliveryType deliveryType,
            Collection<DeliveryStatus> statuses
    );

    Optional<EmailDelivery> findFirstByRunIdAndStageAttemptNoAndRecipientNormalizedAndDeliveryTypeOrderByIdDesc(
            long runId,
            int stageAttemptNo,
            String recipientNormalized,
            DeliveryType deliveryType
    );
}
