package com.aivle.team08.backendspring.email.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.ActiveProfiles;
import com.aivle.team08.backendspring.email.infrastructure.EmailDeliveryRepository;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "legacy-db"})
class InternalEmailDeliveryControllerTest {

    private static final String SERVICE_KEY = "test-internal-service-key-2026";
    private static final String VALID_REQUEST = """
            {
              "runId": 123,
              "stageAttemptNo": 1,
              "requestedBy": 7,
              "recipient": "customer@example.com",
              "resend": false,
              "request": {
                "requestNo": "REQ-20260806-ABC123",
                "title": "서울 지역 결제 분석",
                "requesterName": "홍길동"
              },
              "sample": {
                "columns": [
                  {"name": "region", "dataType": "string", "derived": false},
                  {"name": "payment_count", "dataType": "integer", "derived": true}
                ],
                "rows": [
                  {"region": "서울", "payment_count": 1},
                  {"region": "서울", "payment_count": 2},
                  {"region": "서울", "payment_count": 3},
                  {"region": "서울", "payment_count": 4},
                  {"region": "서울", "payment_count": 5}
                ],
                "metadata": {
                  "isSynthetic": true,
                  "sampleCount": 5,
                  "notice": "형식 확인용 합성 샘플입니다."
                }
              }
            }
            """;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    EmailDeliveryRepository repository;

    @BeforeEach
    void clearDeliveries() {
        repository.deleteAll();
    }

    @Test
    void acceptsValidInternalRequestAndDeduplicatesSameKey() throws Exception {
        String key = "test-idempotency-key-0001";
        mockMvc.perform(request(key, SERVICE_KEY, VALID_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value(123))
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.duplicate").value(false));

        mockMvc.perform(request(key, SERVICE_KEY, VALID_REQUEST))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.runId").value(123))
                .andExpect(jsonPath("$.duplicate").value(true));
    }

    @Test
    void rejectsInvalidInternalServiceKey() throws Exception {
        mockMvc.perform(request("test-idempotency-key-0002", "wrong-key", VALID_REQUEST))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_INTERNAL_SERVICE_KEY"));
    }

    @Test
    void rejectsNonSyntheticSample() throws Exception {
        String payload = VALID_REQUEST.replace("\"isSynthetic\": true", "\"isSynthetic\": false");
        mockMvc.perform(request("test-idempotency-key-0003", SERVICE_KEY, payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_SAMPLE_PAYLOAD"));
    }

    @Test
    void rejectsRowsWhoseKeysDoNotMatchColumns() throws Exception {
        String payload = VALID_REQUEST.replace(
                "{\"region\": \"서울\", \"payment_count\": 5}",
                "{\"region\": \"서울\", \"unknown\": 5}"
        );
        mockMvc.perform(request("test-idempotency-key-0004", SERVICE_KEY, payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_SAMPLE_PAYLOAD"));
    }

    @Test
    void rejectsSameIdempotencyKeyWithDifferentRequest() throws Exception {
        String key = "test-idempotency-key-0005";
        mockMvc.perform(request(key, SERVICE_KEY, VALID_REQUEST))
                .andExpect(status().isAccepted());

        String changed = VALID_REQUEST.replace("\"runId\": 123", "\"runId\": 124");
        mockMvc.perform(request(key, SERVICE_KEY, changed))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(
            String idempotencyKey, String serviceKey, String body) {
        return post("/internal/v1/email-deliveries")
                .header("X-Internal-Service-Key", serviceKey)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
