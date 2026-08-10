package com.enterprise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAspectJAutoProxy
@EnableScheduling
@EnableAsync
@EnableJpaRepositories(basePackages = "com.enterprise")  // 👈 ADD THIS
public class TransactionPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransactionPlatformApplication.class, args);
    }
}