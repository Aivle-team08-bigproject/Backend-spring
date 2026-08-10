package com.aivle.team08.backendspring.email.application;

import com.aivle.team08.backendspring.common.error.ApiException;
import com.aivle.team08.backendspring.email.api.CreateEmailDeliveryRequest;
import com.aivle.team08.backendspring.email.api.EmailDeliveryResponse;
import com.aivle.team08.backendspring.email.domain.DeliveryStatus;
import com.aivle.team08.backendspring.email.domain.DeliveryType;
import com.aivle.team08.backendspring.email.domain.EmailDelivery;
import com.aivle.team08.backendspring.email.infrastructure.EmailDeliveryRepository;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Profile;

@Service
@Profile("legacy-db")
public class EmailDeliveryService {
    private static final List<DeliveryStatus> ACTIVE = List.of(
            DeliveryStatus.QUEUED, DeliveryStatus.SENDING, DeliveryStatus.RETRYING);
    private static final String TEMPLATE_VERSION = "selection-sample-v1";

    private final SamplePayloadValidator sampleValidator;
    private final DeliveryFingerprint fingerprint;
    private final EmailDeliveryRepository repository;

    public EmailDeliveryService(
            SamplePayloadValidator sampleValidator,
            DeliveryFingerprint fingerprint,
            EmailDeliveryRepository repository
    ) {
        this.sampleValidator = sampleValidator;
        this.fingerprint = fingerprint;
        this.repository = repository;
    }

    @Transactional
    public EmailDeliveryResponse accept(String idempotencyKey, CreateEmailDeliveryRequest request) {
        sampleValidator.validate(request.sample());
        String requestFingerprint = fingerprint.request(request);

        var sameKey = repository.findByIdempotencyKey(idempotencyKey);
        if (sameKey.isPresent()) {
            EmailDelivery existing = sameKey.get();
            if (!existing.getRequestFingerprint().equals(requestFingerprint)) {
                throw new ApiException(HttpStatus.CONFLICT, "IDEMPOTENCY_KEY_REUSED",
                        "동일한 Idempotency-Key를 다른 요청에 사용할 수 없습니다.");
            }
            return response(existing, true, "이미 접수된 이메일 발송 요청입니다.");
        }

        String normalizedRecipient = normalizeRecipient(request.recipient());
        var active = repository
                .findFirstByRunIdAndStageAttemptNoAndRecipientNormalizedAndDeliveryTypeAndStatusInOrderByIdDesc(
                        request.runId(), request.stageAttemptNo(), normalizedRecipient,
                        DeliveryType.SELECTION_SAMPLE, ACTIVE);
        if (active.isPresent()) {
            return response(active.get(), true, "동일한 이메일 발송이 이미 처리 중입니다.");
        }

        if (!request.resend()) {
            var latest = repository
                    .findFirstByRunIdAndStageAttemptNoAndRecipientNormalizedAndDeliveryTypeOrderByIdDesc(
                            request.runId(), request.stageAttemptNo(), normalizedRecipient,
                            DeliveryType.SELECTION_SAMPLE);
            if (latest.isPresent()) {
                return response(latest.get(), true, "기존 이메일 발송 내역을 반환합니다.");
            }
        }

        EmailDelivery delivery = EmailDelivery.queued(
                idempotencyKey,
                requestFingerprint,
                request.runId(),
                request.stageAttemptNo(),
                request.requestedBy(),
                request.recipient().trim(),
                normalizedRecipient,
                fingerprint.sample(request.sample()),
                TEMPLATE_VERSION
        );
        return response(repository.saveAndFlush(delivery), false, "이메일 발송이 접수되었습니다.");
    }

    static String normalizeRecipient(String recipient) {
        String trimmed = recipient.trim();
        int separator = trimmed.lastIndexOf('@');
        if (separator < 1 || separator == trimmed.length() - 1) {
            return trimmed;
        }
        return trimmed.substring(0, separator) + "@"
                + trimmed.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private EmailDeliveryResponse response(EmailDelivery delivery, boolean duplicate, String message) {
        return new EmailDeliveryResponse(
                delivery.getId(), delivery.getRunId(), delivery.getStatus(), duplicate, message);
    }
}
