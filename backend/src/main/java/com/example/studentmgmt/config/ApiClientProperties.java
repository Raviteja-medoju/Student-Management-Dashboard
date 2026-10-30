package com.example.studentmgmt.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/** Settings for {@link com.example.studentmgmt.client.StudentApiClient} ({@code app.api-client.*}). */
@ConfigurationProperties(prefix = "app.api-client")
public record ApiClientProperties(
        @DefaultValue("http://localhost:8080") String baseUrl,
        @DefaultValue("5s") Duration connectTimeout,
        @DefaultValue("10s") Duration readTimeout
) {
}
