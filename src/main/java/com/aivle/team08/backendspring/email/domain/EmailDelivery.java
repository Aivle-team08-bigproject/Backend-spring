package com.aivle.team08.backendspring.email.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;

@Entity
@Table(name = "email_deliveries", schema = "email_service")
public class EmailDelivery {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 128, unique = true)
    private String idempotencyKey;

    @Column(name = "request_fingerprint", nullable = false, length = 64)
    private String requestFingerprint;

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "stage_attempt_no", nullable = false)
    private Integer stageAttemptNo;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 40)
    private DeliveryType deliveryType;

    @Column(name = "recipient", nullable = false, length = 254)
    private String recipient;

    @Column(name = "recipient_normalized", nullable = false, length = 254)
    private String recipientNormalized;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private DeliveryStatus status;

    @Column(name = "provider", length = 30)
    private String provider;

    @Column(name = "provider_message_id", length = 255)
    private String providerMessageId;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "failure_code", length = 80)
    private String failureCode;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "sample_sha256", nullable = false, length = 64)
    private String sampleSha256;

    @Column(name = "template_version", nullable = false, length = 40)
    private String templateVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected EmailDelivery() {}

    public static EmailDelivery queued(
            String idempotencyKey,
            String requestFingerprint,
            long runId,
            int stageAttemptNo,
            long requestedBy,
            String recipient,
            String recipientNormalized,
            String sampleSha256,
            String templateVersion
    ) {
        EmailDelivery delivery = new EmailDelivery();
        delivery.idempotencyKey = idempotencyKey;
        delivery.requestFingerprint = requestFingerprint;
        delivery.runId = runId;
        delivery.stageAttemptNo = stageAttemptNo;
        delivery.requestedBy = requestedBy;
        delivery.deliveryType = DeliveryType.SELECTION_SAMPLE;
        delivery.recipient = recipient;
        delivery.recipientNormalized = recipientNormalized;
        delivery.status = DeliveryStatus.QUEUED;
        delivery.sampleSha256 = sampleSha256;
        delivery.templateVersion = templateVersion;
        return delivery;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getRequestFingerprint() { return requestFingerprint; }
    public Long getRunId() { return runId; }
    public Integer getStageAttemptNo() { return stageAttemptNo; }
    public Long getRequestedBy() { return requestedBy; }
    public DeliveryType getDeliveryType() { return deliveryType; }
    public String getRecipient() { return recipient; }
    public String getRecipientNormalized() { return recipientNormalized; }
    public DeliveryStatus getStatus() { return status; }
    public String getSampleSha256() { return sampleSha256; }
}
