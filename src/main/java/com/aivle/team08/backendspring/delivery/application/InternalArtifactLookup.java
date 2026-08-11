package com.aivle.team08.backendspring.delivery.application;

import com.fasterxml.jackson.annotation.JsonProperty;

/** FastAPI 내부 API(/internal/v1/deliveries/{contractNo}/artifact) 응답. */
public record InternalArtifactLookup(
        @JsonProperty("storage_key") String storageKey,
        @JsonProperty("mime_type") String mimeType,
        @JsonProperty("artifact_filename") String artifactFilename) {}
