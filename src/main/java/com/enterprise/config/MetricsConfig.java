package com.enterprise.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MetricsConfig {

    @Bean
    public Timer paymentProcessingTimer(MeterRegistry registry) {
        return Timer.builder("payment.processing.time")
            .description("Time taken to process a payment")
            .publishPercentiles(0.5, 0.95, 0.99)
            .sla(java.time.Duration.ofMillis(100), java.time.Duration.ofMillis(500), java.time.Duration.ofSeconds(1))
            .register(registry);
    }

    @Bean
    public Timer refundProcessingTimer(MeterRegistry registry) {
        return Timer.builder("refund.processing.time")
            .description("Time taken to process a refund")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(registry);
    }
}