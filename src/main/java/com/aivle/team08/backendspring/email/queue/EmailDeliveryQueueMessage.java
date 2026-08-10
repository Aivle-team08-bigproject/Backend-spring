package com.aivle.team08.backendspring.email.queue;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.Map;

/** FastAPI가 SQS에 발행하는 발송 명령. Spring은 이 메시지를 DB에 저장하지 않는다. */
public record EmailDeliveryQueueMessage(
        @JsonProperty("delivery_id")
        String deliveryId,
        @JsonProperty("idempotency_key")
        String idempotencyKey,
        @JsonProperty("run_id")
        long runId,
        @JsonProperty("stage_attempt_no")
        int stageAttemptNo,
        @JsonProperty("delivery_type")
        String deliveryType,
        @JsonProperty("template_version")
        String templateVersion,
        @JsonProperty("recipient")
        String recipient,
        @JsonProperty("sample_columns")
        List<Map<String, Object>> sampleColumns,
        @JsonProperty("sample_rows")
        List<Map<String, Object>> sampleRows,
        @JsonProperty("sample_metadata")
        Map<String, Object> sampleMetadata,
        @JsonProperty("sample_sha256")
        String sampleSha256) {}
