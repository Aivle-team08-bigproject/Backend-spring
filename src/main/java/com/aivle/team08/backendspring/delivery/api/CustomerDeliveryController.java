package com.aivle.team08.backendspring.delivery.api;

import com.aivle.team08.backendspring.delivery.application.CustomerDeliveryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.context.annotation.Profile;

/** 고객이 계약 산출물을 재다운로드하는 유일한 외부 API. 인증은 X-API-Key 헤더로 한다(직원 JWT 아님). */
@RestController
@Profile("!worker")
@RequestMapping("/api/external/v1/deliveries")
public class CustomerDeliveryController {
    private final CustomerDeliveryService service;

    public CustomerDeliveryController(CustomerDeliveryService service) {
        this.service = service;
    }

    @GetMapping("/{contractNo}")
    public CustomerDeliveryResponse getDownloadUrl(
            @PathVariable String contractNo,
            @RequestHeader("X-API-Key") String apiKey) {
        return service.getDownloadUrl(contractNo, apiKey);
    }
}
