package com.aivle.team08.backendspring.email.application;

import com.aivle.team08.backendspring.common.error.ApiException;
import com.aivle.team08.backendspring.email.api.CreateEmailDeliveryRequest.SamplePayload;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class SamplePayloadValidator {
    public void validate(SamplePayload sample) {
        if (!Boolean.TRUE.equals(sample.metadata().isSynthetic())) {
            invalid("합성 샘플만 이메일로 발송할 수 있습니다.");
        }
        if (sample.metadata().sampleCount() != 5 || sample.rows().size() != 5) {
            invalid("샘플 행과 metadata.sampleCount는 정확히 5여야 합니다.");
        }
        Set<String> names = sample.columns().stream()
                .map(column -> column.name()).collect(Collectors.toSet());
        if (names.size() != sample.columns().size()) {
            invalid("샘플 컬럼 이름은 중복될 수 없습니다.");
        }
        for (var row : sample.rows()) {
            if (row == null || !new HashSet<>(row.keySet()).equals(names)) {
                invalid("모든 샘플 행의 키는 컬럼 정의와 정확히 일치해야 합니다.");
            }
        }
    }

    private void invalid(String message) {
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_SAMPLE_PAYLOAD", message);
    }
}
