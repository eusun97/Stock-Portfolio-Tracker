package com.eusun97.stocktracker.infrastructure.client.publicdata;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.publicdata")
public record PublicDataProperties(
        String baseUrl,
        String serviceKey,
        int connectTimeoutMs,
        int readTimeoutMs
) {
}
