package com.example.studentmgmt.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Metadata for the generated API docs at /swagger-ui.html. */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI studentManagementOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Student Management API")
                .version("v1")
                .contact(new Contact().name("Ravi Teja"))
                .description("""
                        REST API for managing students, courses and enrollments.
                        Every error uses the same ApiError JSON shape (timestamp, status, error, message, path, fieldErrors)."""));
    }
}
