package com.aivle.team08.backendspring.email.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;

public record CreateEmailDeliveryRequest(
        @NotNull @Positive Long runId,
        @NotNull @Positive Integer stageAttemptNo,
        @NotNull @Positive Long requestedBy,
        @NotBlank @Email @Size(max = 254) String recipient,
        boolean resend,
        @NotNull @Valid RequestInformation request,
        @NotNull @Valid SamplePayload sample
) {
    public record RequestInformation(
            @NotBlank @Size(max = 100) String requestNo,
            @NotBlank @Size(max = 200) String title,
            @Size(max = 100) String requesterName) {}

    public record SamplePayload(
            @NotEmpty @Valid List<SampleColumn> columns,
            @NotNull @Size(min = 5, max = 5) List<Map<String, Object>> rows,
            @NotNull @Valid SampleMetadata metadata) {}

    public record SampleColumn(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 50) String dataType,
            @NotNull Boolean derived,
            @Size(max = 500) String description) {}

    public record SampleMetadata(
            @NotNull Boolean isSynthetic,
            @NotNull @Positive Integer sampleCount,
            @Size(max = 1000) String notice) {}
}
