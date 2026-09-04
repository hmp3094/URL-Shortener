package com.urlshortener.integration;

import com.urlshortener.link.ShortLink;
import com.urlshortener.link.ShortLinkService;
import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ClickAnalyticsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShortLinkService shortLinkService;

    @Test
    void eachRedirectIncrementsTheClickCountExactly() {
        String longUrl = "https://example.com/click-analytics/" + UUID.randomUUID();
        ShortLink created = shortLinkService.create(longUrl);

        for (int i = 0; i < 5; i++) {
            shortLinkService.resolve(created.getShortCode());
            shortLinkService.recordClick(created.getShortCode());
        }

        ShortLink stats = shortLinkService.getStatsSnapshot(created.getShortCode());
        assertThat(stats.getClickCount()).isEqualTo(5);
        assertThat(stats.getLastAccessedAt()).isNotNull();
    }

    @Test
    void statsReflectClicksEvenWhenTheResolveLookupWasServedFromCache() {
        String longUrl = "https://example.com/click-analytics-cache/" + UUID.randomUUID();
        ShortLink created = shortLinkService.create(longUrl);

        // Warm the cache.
        shortLinkService.resolve(created.getShortCode());
        shortLinkService.recordClick(created.getShortCode());

        // Second redirect is served from cache, but the click still has to land in Postgres.
        shortLinkService.resolve(created.getShortCode());
        shortLinkService.recordClick(created.getShortCode());

        ShortLink stats = shortLinkService.getStatsSnapshot(created.getShortCode());
        assertThat(stats.getClickCount()).isEqualTo(2);
    }
}
