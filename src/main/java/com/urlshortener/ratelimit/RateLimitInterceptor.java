package com.urlshortener.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/** Applies the per-IP {@link RateLimiter} to whatever path it's registered on (see WebConfig). */
@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimiter rateLimiter;

    public RateLimitInterceptor(RateLimiter rateLimiter) {
        this.rateLimiter = rateLimiter;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        // Direct client IP; this service is assumed to have no reverse proxy in front of it in
        // this feature's scope, so X-Forwarded-For handling is not needed here.
        String clientIp = request.getRemoteAddr();
        if (!rateLimiter.tryConsume(clientIp)) {
            throw new RateLimitExceededException(rateLimiter.getRetryAfterSeconds(clientIp));
        }
        return true;
    }
}
