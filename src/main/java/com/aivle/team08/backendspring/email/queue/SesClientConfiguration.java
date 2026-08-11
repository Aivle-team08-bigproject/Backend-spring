package com.aivle.team08.backendspring.email.queue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sesv2.SesV2Client;

@Configuration
@ConditionalOnProperty(name = "email.sender", havingValue = "ses")
public class SesClientConfiguration {
    @Bean
    SesV2Client sesV2Client(@Value("${aws.region:ap-northeast-2}") String region) {
        return SesV2Client.builder().region(Region.of(region)).build();
    }
}
