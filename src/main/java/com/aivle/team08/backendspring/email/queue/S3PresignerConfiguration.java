package com.aivle.team08.backendspring.email.queue;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** FINAL_ARTIFACT 메일의 다운로드 링크를 발송 시점에 새로 서명하기 위한 presigner. */
@Configuration
public class S3PresignerConfiguration {
    @Bean
    S3Presigner s3Presigner(@Value("${aws.region:ap-northeast-2}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }
}
