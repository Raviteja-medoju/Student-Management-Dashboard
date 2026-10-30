package com.example.studentmgmt.config;

import com.example.studentmgmt.client.StudentApiClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ApiClientProperties.class)
public class AppConfig {

    /** Injected wherever "today" matters, so tests can pin the date. */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }

    /** RestTemplate pre-configured with the API base URL and timeouts. */
    @Bean
    public RestTemplate studentApiRestTemplate(RestTemplateBuilder builder, ApiClientProperties properties) {
        return builder
                .rootUri(properties.baseUrl())
                .connectTimeout(properties.connectTimeout())
                .readTimeout(properties.readTimeout())
                .build();
    }

    @Bean
    public StudentApiClient studentApiClient(RestTemplate studentApiRestTemplate) {
        return new StudentApiClient(studentApiRestTemplate);
    }
}
