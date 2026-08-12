package com.enterprise.xero.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;

@Configuration
public class XeroConfig {

    private static final Logger log = LoggerFactory.getLogger(XeroConfig.class);

    // Default values for global app (optional)
    private static String DEFAULT_CLIENT_ID;
    private static String DEFAULT_CLIENT_SECRET;

    @PostConstruct
    public void init() {
        // These are only used if a tenant hasn't configured their own
        DEFAULT_CLIENT_ID = System.getenv("XERO_CLIENT_ID");
        DEFAULT_CLIENT_SECRET = System.getenv("XERO_CLIENT_SECRET");
        log.info("🔍 XeroConfig loaded with default credentials");
    }

    public static String getDefaultClientId() {
        return DEFAULT_CLIENT_ID;
    }

    public static String getDefaultClientSecret() {
        return DEFAULT_CLIENT_SECRET;
    }
}