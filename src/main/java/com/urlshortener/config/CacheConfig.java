package com.urlshortener.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * In-process cache-aside layer in front of the redirect lookup, so a repeated request for the
 * same code doesn't have to hit Postgres every time. Caffeine was chosen over something like
 * Redis since this runs as a single instance with no separate cache service to operate; a cold
 * cache miss during a database outage would still fail, which is an accepted trade-off at this
 * scale.
 */
@Configuration
public class CacheConfig {

    public static final String SHORT_LINKS_CACHE = "shortLinks";

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager cacheManager = new CaffeineCacheManager(SHORT_LINKS_CACHE);
        cacheManager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(100_000)
                .expireAfterWrite(1, TimeUnit.HOURS));
        return cacheManager;
    }
}
