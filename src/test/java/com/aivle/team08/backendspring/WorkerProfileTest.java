package com.aivle.team08.backendspring;

import static org.assertj.core.api.Assertions.assertThat;

import com.aivle.team08.backendspring.delivery.api.CustomerDeliveryController;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

class WorkerProfileTest {

    @Test
    void workerProfileStartsWithoutHttpCustomerApi() {
        try (var context = new SpringApplicationBuilder(BackendSpringApplication.class)
                .profiles("worker")
                .web(WebApplicationType.NONE)
                .properties("email.queue.enabled=false")
                .run()) {
            assertThat(context.getEnvironment().getProperty("spring.main.web-application-type"))
                    .isEqualTo("none");
            assertThat(context.getBeansOfType(CustomerDeliveryController.class)).isEmpty();
        }
    }
}
