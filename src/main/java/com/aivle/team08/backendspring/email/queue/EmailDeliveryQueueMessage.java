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
        String sampleSha256,
        @JsonProperty("request_no")
        String requestNo,
        @JsonProperty("request_title")
        String requestTitle,
        @JsonProperty("client_company_name")
        String clientCompanyName,
        @JsonProperty("owner_name")
        String ownerName,
        @JsonProperty("owner_email")
        String ownerEmail,
        @JsonProperty("artifact_storage_key")
        String artifactStorageKey,
        @JsonProperty("artifact_filename")
        String artifactFilename,
        @JsonProperty("artifact_mime_type")
        String artifactMimeType,
        @JsonProperty("api_endpoint_url")
        String apiEndpointUrl,
        @JsonProperty("api_key")
        String apiKey) {}
