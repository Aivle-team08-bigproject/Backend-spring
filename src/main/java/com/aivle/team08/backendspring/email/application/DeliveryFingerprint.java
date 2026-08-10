package com.aivle.team08.backendspring.email.application;

import com.aivle.team08.backendspring.common.error.ApiException;
import com.aivle.team08.backendspring.email.api.CreateEmailDeliveryRequest;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class DeliveryFingerprint {
    private final ObjectMapper canonicalMapper;

    public DeliveryFingerprint() {
        this.canonicalMapper = new ObjectMapper()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public String request(CreateEmailDeliveryRequest request) {
        return hash(request);
    }

    public String sample(CreateEmailDeliveryRequest.SamplePayload sample) {
        return hash(sample);
    }

    private String hash(Object value) {
        try {
            byte[] canonical = canonicalMapper.writeValueAsBytes(value);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "FINGERPRINT_FAILED",
                    "이메일 요청 무결성 값을 생성하지 못했습니다.");
        }
    }
}
