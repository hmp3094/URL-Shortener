package com.urlshortener.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sets the springdoc-generated API metadata (title, version, description) shown at
 * /swagger-ui.html.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI urlShortenerOpenApi() {
        return new OpenAPI().info(new Info()
                .title("URL Shortener - Shorten & Redirect Core API")
                .version("1.0.0")
                .description("Core API for creating short links from long URLs and resolving "
                        + "them via redirect. Analytics endpoints are out of scope for this "
                        + "contract (separate feature)."));
    }
}
