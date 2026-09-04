package com.urlshortener.integration;

import com.urlshortener.config.CacheConfig;
import com.urlshortener.link.ShortLink;
import com.urlshortener.link.ShortLinkRepository;
import com.urlshortener.link.ShortLinkService;
import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RedirectCacheIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private ShortLinkRepository shortLinkRepository;

    @Autowired
    private CacheManager cacheManager;

    @Test
    void redirectResolvesOnACacheMissAndIsServedFromCacheOnASubsequentRequest() {
        String longUrl = "https://example.com/cache-test/" + UUID.randomUUID();
        ShortLink created = shortLinkService.create(longUrl);

        // First resolve: cache miss, hits Postgres.
        ShortLink firstResolve = shortLinkService.resolve(created.getShortCode());
        assertThat(firstResolve.getLongUrl()).isEqualTo(longUrl);

        Cache cache = cacheManager.getCache(CacheConfig.SHORT_LINKS_CACHE);
        assertThat(cache).isNotNull();
        assertThat(cache.get(created.getShortCode())).isNotNull();

        // Remove the row directly (bypassing the cache) to prove the second resolve is served
        // from the cache rather than Postgres.
        shortLinkRepository.deleteById(created.getId());

        ShortLink secondResolve = shortLinkService.resolve(created.getShortCode());
        assertThat(secondResolve.getLongUrl()).isEqualTo(longUrl);
    }

    @Test
    void redirectMatchesShortCodesCaseInsensitively() {
        String longUrl = "https://example.com/case-test/" + UUID.randomUUID();
        ShortLink created = shortLinkService.create(longUrl);

        ShortLink resolved = shortLinkService.resolve(created.getShortCode().toUpperCase());

        assertThat(resolved.getLongUrl()).isEqualTo(longUrl);
    }
}
