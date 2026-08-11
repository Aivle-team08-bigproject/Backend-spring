package com.aivle.team08.backendspring.delivery.application;

import com.aivle.team08.backendspring.common.error.ApiException;
import com.aivle.team08.backendspring.delivery.api.CustomerDeliveryResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

/**
 * 고객이 API Key로 최종 산출물을 재다운로드하는 유일한 경로.
 *
 * 이 서비스는 사내 DB에 직접 접근하지 않는다 — Spring은 인터넷에 노출된 서비스라
 * 민감 DB(직원·고객 정보) 자격증명을 갖지 않는다. API Key 검증·계약/산출물 조회는
 * FastAPI 내부 전용 엔드포인트(X-Internal-Service-Key로 보호)에 위임하고,
 * 그 응답의 storage_key로 S3 presigned URL만 이 서비스가 직접 서명한다
 * ("S3 -> Spring" 원칙은 유지, DB만 FastAPI 뒤로 숨긴다).
 */
@Service
@Profile("!worker")
public class CustomerDeliveryService {
    private final RestClient internalApiClient;
    private final String internalServiceKey;
    private final ObjectMapper objectMapper;
    private final S3Presigner s3Presigner;
    private final String artifactsBucket;
    private final long presignExpiresSeconds;

    public CustomerDeliveryService(
            RestClient internalApiClient,
            @Value("${internal.auth.service-key}") String internalServiceKey,
            ObjectMapper objectMapper,
            S3Presigner s3Presigner,
            @Value("${s3.artifacts-bucket:}") String artifactsBucket,
            @Value("${api.delivery-presign-expires-seconds:600}") long presignExpiresSeconds) {
        this.internalApiClient = internalApiClient;
        this.internalServiceKey = internalServiceKey;
        this.objectMapper = objectMapper;
        this.s3Presigner = s3Presigner;
        this.artifactsBucket = artifactsBucket;
        this.presignExpiresSeconds = presignExpiresSeconds;
    }

    public CustomerDeliveryResponse getDownloadUrl(String contractNo, String rawApiKey) {
        InternalArtifactLookup lookup = lookupArtifact(contractNo, rawApiKey);

        if (artifactsBucket.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ARTIFACT_STORAGE_NOT_CONFIGURED", "산출물 저장소 설정이 없습니다.");
        }

        Instant expiresAt = Instant.now().plusSeconds(presignExpiresSeconds);
        String downloadUrl;
        try {
            var presigned = s3Presigner.presignGetObject(GetObjectPresignRequest.builder()
                    .signatureDuration(Duration.ofSeconds(presignExpiresSeconds))
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(artifactsBucket)
                            .key(lookup.storageKey())
                            .responseContentType(lookup.mimeType())
                            .responseContentDisposition("attachment; filename*=UTF-8''"
                                    + java.net.URLEncoder.encode(lookup.artifactFilename(), java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"))
                            .build())
                    .build());
            downloadUrl = presigned.url().toString();
        } catch (RuntimeException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "ARTIFACT_PRESIGN_FAILED", "다운로드 URL 생성에 실패했습니다.");
        }

        return new CustomerDeliveryResponse(downloadUrl, expiresAt.toString());
    }

    private InternalArtifactLookup lookupArtifact(String contractNo, String rawApiKey) {
        try {
            return internalApiClient.get()
                    .uri("/internal/v1/deliveries/{contractNo}/artifact", contractNo)
                    .header("X-API-Key", rawApiKey)
                    .header("X-Internal-Service-Key", internalServiceKey)
                    .retrieve()
                    .body(InternalArtifactLookup.class);
        } catch (RestClientResponseException exception) {
            throw translateUpstreamError(exception);
        }
    }

    /** FastAPI는 {"detail": {"code": "...", "message": "..."}} 형태로 에러를 내려준다. */
    private ApiException translateUpstreamError(RestClientResponseException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsByteArray());
            JsonNode detail = body.path("detail");
            String code = detail.path("code").asText("UPSTREAM_ERROR");
            String message = detail.path("message").asText("내부 조회에 실패했습니다.");
            return new ApiException(status, code, message);
        } catch (Exception parseError) {
            return new ApiException(status, "UPSTREAM_ERROR", "내부 조회에 실패했습니다.");
        }
    }
}
