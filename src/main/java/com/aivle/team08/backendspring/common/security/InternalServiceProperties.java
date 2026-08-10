package com.aivle.team08.backendspring.common.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "internal.auth")
public record InternalServiceProperties(String serviceKey) {}
