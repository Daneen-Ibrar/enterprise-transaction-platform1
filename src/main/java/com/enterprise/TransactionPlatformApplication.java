package com.enterprise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;   // <-- ADD THIS
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAspectJAutoProxy
@EnableScheduling
@EnableAsync    // <-- ADD THIS
public class TransactionPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransactionPlatformApplication.class, args);
    }
}