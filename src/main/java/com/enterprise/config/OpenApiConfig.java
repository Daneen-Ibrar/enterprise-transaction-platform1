package com.enterprise.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
            .info(new Info()
                .title("Enterprise Transaction Orchestration & Audit Platform API")
                .description("""
                    A policy-driven virtual transaction engine with immutable audit trails,
                    idempotent processing, reconciliation workflows, and enterprise-grade
                    operational reliability.
                    
                    ## Simulator Tokens
                    Include these in the description field to trigger deterministic outcomes:
                    - `sim_approve` - Approve payment
                    - `sim_decline` - Decline payment
                    - `sim_timeout` - Simulate timeout
                    - `sim_delay_5s` - 5 second delay
                    - `sim_delay_10s` - 10 second delay
                    - `sim_failure` - Simulate failure
                    - `sim_duplicate` - Simulate duplicate
                    """)
                .version("v1.0")
                .contact(new Contact()
                    .name("Enterprise Transaction Platform")
                    .email("support@example.com"))
                .license(new License()
                    .name("Apache 2.0")
                    .url("https://www.apache.org/licenses/LICENSE-2.0")))
            .servers(List.of(
                new Server().url("http://localhost:8080").description("Local development"),
                new Server().url("https://api.example.com").description("Production")
            ));
    }
}