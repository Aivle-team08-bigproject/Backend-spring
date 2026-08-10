package com.aivle.team08.backendspring.common.error;

import java.time.Instant;

public record ApiErrorResponse(Instant timestamp, int status, String code, String message, String path) {}
