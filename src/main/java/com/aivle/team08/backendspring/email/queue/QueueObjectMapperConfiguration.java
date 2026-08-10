package com.aivle.team08.backendspring.email.queue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Queue adapter가 HTTP 자동설정과 무관하게 사용할 JSON mapper. */
@Configuration
public class QueueObjectMapperConfiguration {
    @Bean
    ObjectMapper queueObjectMapper() {
        return JsonMapper.builder().findAndAddModules().build();
    }
}
