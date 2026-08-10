package com.aivle.team08.backendspring.email.api;

import com.aivle.team08.backendspring.common.error.ApiException;
import com.aivle.team08.backendspring.email.application.EmailDeliveryService;
import jakarta.validation.Valid;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

@RestController
@Profile("legacy-db")
@RequestMapping("/internal/v1/email-deliveries")
public class InternalEmailDeliveryController {
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("^[A-Za-z0-9._:-]{16,128}$");
    private final EmailDeliveryService service;

    public InternalEmailDeliveryController(EmailDeliveryService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EmailDeliveryResponse> create(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateEmailDeliveryRequest request) {
        if (!IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_IDEMPOTENCY_KEY",
                    "Idempotency-Key는 16~128자의 허용된 문자로 구성해야 합니다.");
        }
        return ResponseEntity.accepted().body(service.accept(idempotencyKey, request));
    }
}
