package com.urlshortener.ratelimit;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * A small in-memory per-key token bucket — hand-rolled instead of pulling in a rate-limiting
 * library, since the algorithm needed here is a few dozen lines. Single-instance only; a
 * distributed limiter would be needed if this ever runs across multiple app instances.
 */
public class RateLimiter {

    private final int capacity;
    private final double refillTokensPerNano;
    private final LongSupplier nanoClock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int capacity, int refillTokens, Duration refillPeriod) {
        this(capacity, refillTokens, refillPeriod, System::nanoTime);
    }

    public RateLimiter(int capacity, int refillTokens, Duration refillPeriod, LongSupplier nanoClock) {
        this.capacity = capacity;
        this.refillTokensPerNano = (double) refillTokens / refillPeriod.toNanos();
        this.nanoClock = nanoClock;
    }

    public boolean tryConsume(String key) {
        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(capacity, nanoClock.getAsLong()));
        synchronized (bucket) {
            refill(bucket);
            if (bucket.tokens >= 1.0) {
                bucket.tokens -= 1.0;
                return true;
            }
            return false;
        }
    }

    public long getRetryAfterSeconds(String key) {
        Bucket bucket = buckets.get(key);
        if (bucket == null || bucket.tokens >= 1.0) {
            return 0;
        }
        synchronized (bucket) {
            double tokensNeeded = 1.0 - bucket.tokens;
            double nanosNeeded = tokensNeeded / refillTokensPerNano;
            return Math.max(1, Duration.ofNanos((long) nanosNeeded).toSeconds());
        }
    }

    private void refill(Bucket bucket) {
        long now = nanoClock.getAsLong();
        long elapsed = now - bucket.lastRefillNanos;
        if (elapsed > 0) {
            bucket.tokens = Math.min(capacity, bucket.tokens + elapsed * refillTokensPerNano);
            bucket.lastRefillNanos = now;
        }
    }

    private static final class Bucket {
        double tokens;
        long lastRefillNanos;

        Bucket(double tokens, long lastRefillNanos) {
            this.tokens = tokens;
            this.lastRefillNanos = lastRefillNanos;
        }
    }
}
