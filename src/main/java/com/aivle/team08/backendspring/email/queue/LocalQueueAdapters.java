package com.aivle.team08.backendspring.email.queue;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 로컬에서는 AWS adapter가 없어도 애플리케이션이 기동되도록 하는 기본 구현. */
@Configuration
public class LocalQueueAdapters {
    @Bean
    @ConditionalOnMissingBean(EmailDeliverySender.class)
    EmailDeliverySender localSender() {
        return message -> new EmailDeliverySender.SendResult("FAILED", null, "SENDER_NOT_CONFIGURED");
    }

    @Bean
    @ConditionalOnMissingBean(EmailDeliveryQueuePublisher.class)
    EmailDeliveryQueuePublisher localPublisher() {
        return result -> { /* 로컬에서는 결과 큐를 연결하지 않는다. */ };
    }
}
