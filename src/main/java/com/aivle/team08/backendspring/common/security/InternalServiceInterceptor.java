package com.aivle.team08.backendspring.common.security;

import com.aivle.team08.backendspring.common.error.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Profile("!worker")
public class InternalServiceInterceptor implements HandlerInterceptor {
    public static final String HEADER_NAME = "X-Internal-Service-Key";
    private final byte[] expectedKey;

    public InternalServiceInterceptor(InternalServiceProperties properties) {
        String key = properties.serviceKey();
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("internal.auth.service-key must be configured");
        }
        this.expectedKey = key.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String actual = request.getHeader(HEADER_NAME);
        boolean matches = actual != null && MessageDigest.isEqual(
                expectedKey, actual.getBytes(StandardCharsets.UTF_8));
        if (!matches) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_INTERNAL_SERVICE_KEY",
                    "내부 서비스 인증에 실패했습니다.");
        }
        return true;
    }
}
