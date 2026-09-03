package com.urlshortener.unit;

import com.urlshortener.ratelimit.RateLimiter;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

    @Test
    void allowsRequestsUpToCapacityThenBlocks() {
        AtomicLong nowNanos = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(3, 3, Duration.ofSeconds(60), nowNanos::get);

        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
        assertThat(limiter.tryConsume("1.2.3.4")).isTrue();
        assertThat(limiter.tryConsume("1.2.3.4")).isFalse();
    }

    @Test
    void refillsTokensAfterTheConfiguredWindowElapses() {
        AtomicLong nowNanos = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(2, 2, Duration.ofSeconds(60), nowNanos::get);

        assertThat(limiter.tryConsume("5.6.7.8")).isTrue();
        assertThat(limiter.tryConsume("5.6.7.8")).isTrue();
        assertThat(limiter.tryConsume("5.6.7.8")).isFalse();

        nowNanos.addAndGet(Duration.ofSeconds(61).toNanos());

        assertThat(limiter.tryConsume("5.6.7.8")).isTrue();
    }

    @Test
    void tracksEachKeyIndependently() {
        AtomicLong nowNanos = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(1, 1, Duration.ofSeconds(60), nowNanos::get);

        assertThat(limiter.tryConsume("1.1.1.1")).isTrue();
        assertThat(limiter.tryConsume("1.1.1.1")).isFalse();
        assertThat(limiter.tryConsume("2.2.2.2")).isTrue();
    }

    @Test
    void reportsASensibleRetryAfterWhenExhausted() {
        AtomicLong nowNanos = new AtomicLong(0);
        RateLimiter limiter = new RateLimiter(1, 1, Duration.ofSeconds(60), nowNanos::get);

        assertThat(limiter.tryConsume("9.9.9.9")).isTrue();
        assertThat(limiter.tryConsume("9.9.9.9")).isFalse();
        assertThat(limiter.getRetryAfterSeconds("9.9.9.9")).isGreaterThan(0);
    }
}
