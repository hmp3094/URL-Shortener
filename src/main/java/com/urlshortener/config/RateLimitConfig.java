package com.urlshortener.config;

import com.urlshortener.ratelimit.RateLimiter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Defines the {@link RateLimiter} bean separately from {@link WebConfig}: WebConfig's
 * constructor depends on RateLimitInterceptor, which depends on RateLimiter, so declaring the
 * RateLimiter @Bean method inside WebConfig itself creates a circular reference.
 */
@Configuration
public class RateLimitConfig {

    @Bean
    public RateLimiter rateLimiter(
            @Value("${app.rate-limit.capacity}") int capacity,
            @Value("${app.rate-limit.refill-tokens}") int refillTokens,
            @Value("${app.rate-limit.refill-period-seconds}") long refillPeriodSeconds) {
        return new RateLimiter(capacity, refillTokens, Duration.ofSeconds(refillPeriodSeconds));
    }
}
