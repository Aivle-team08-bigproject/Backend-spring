package com.aivle.team08.backendspring.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@EnableConfigurationProperties(InternalServiceProperties.class)
public class WebConfiguration implements WebMvcConfigurer {
    private final InternalServiceInterceptor interceptor;

    public WebConfiguration(InternalServiceInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns("/internal/**");
    }
}
