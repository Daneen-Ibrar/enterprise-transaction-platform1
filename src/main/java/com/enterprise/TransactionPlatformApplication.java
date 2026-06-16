package com.enterprise;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class TransactionPlatformApplication {
    public static void main(String[] args) {
        SpringApplication.run(TransactionPlatformApplication.class, args);
    }
}