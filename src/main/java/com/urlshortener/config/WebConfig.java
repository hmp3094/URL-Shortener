package com.urlshortener.config;

import com.urlshortener.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Rate limiting applies to link creation and the stats (analytics-read) endpoint, sharing
        // one per-IP budget — not a separate one per endpoint, which would be more precise but
        // isn't needed at this scale. Deliberately excludes the redirect endpoint itself: the
        // constitution treats the redirect path as distinct and minimal, and only requires
        // limiting creation and analytics-read traffic.
        registry.addInterceptor(rateLimitInterceptor).addPathPatterns("/api/links", "/api/links/*/stats");
    }
}
