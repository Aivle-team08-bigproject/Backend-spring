package com.aivle.team08.backendspring.email.queue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

@Configuration
@ConditionalOnProperty(name = "email.queue.backend", havingValue = "sqs")
public class SqsClientConfiguration {
    @Bean
    SqsClient sqsClient(@Value("${aws.region:ap-northeast-2}") String region) {
        return SqsClient.builder().region(Region.of(region)).build();
    }
}
