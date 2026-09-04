package com.urlshortener.integration;

import com.urlshortener.link.ShortLink;
import com.urlshortener.link.ShortLinkNotFoundException;
import com.urlshortener.link.ShortLinkService;
import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LinkExpirationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShortLinkService shortLinkService;

    @Test
    void aLinkCreatedWithNoExpiryStaysResolvableIndefinitely() {
        String longUrl = "https://example.com/no-expiry/" + UUID.randomUUID();
        ShortLink created = shortLinkService.create(longUrl);

        ShortLink resolved = shortLinkService.resolve(created.getShortCode());

        assertThat(resolved.isExpired()).isFalse();
        assertThat(resolved.getExpiresAt()).isNull();
    }

    @Test
    void anExpiredLinkStopsResolvingAndItsStatsAreHiddenBehindTheSame404() throws InterruptedException {
        String longUrl = "https://example.com/expires-soon/" + UUID.randomUUID();
        ShortLink created = shortLinkService.create(longUrl, 1L);

        assertThat(shortLinkService.resolve(created.getShortCode()).isExpired()).isFalse();

        Thread.sleep(1200);

        ShortLink afterExpiry = shortLinkService.resolve(created.getShortCode());
        assertThat(afterExpiry.isExpired()).isTrue();

        ShortLink stats = shortLinkService.getStatsSnapshot(created.getShortCode());
        assertThat(stats.isExpired()).isTrue();
    }

    @Test
    void resubmittingAnExpiredLinksUrlIssuesAGenuinelyNewShortCodeAndRetiresTheOldOne()
            throws InterruptedException {
        String longUrl = "https://example.com/resubmit-after-expiry/" + UUID.randomUUID();
        ShortLink original = shortLinkService.create(longUrl, 1L);
        String originalCode = original.getShortCode();

        Thread.sleep(1200);

        ShortLink reissued = shortLinkService.create(longUrl);

        assertThat(reissued.getShortCode()).isNotEqualTo(originalCode);
        assertThat(reissued.isExpired()).isFalse();
        assertThat(reissued.getLongUrl()).isEqualTo(longUrl);

        // The old code is gone, not reactivated — resolving it is indistinguishable from a code
        // that never existed.
        assertThatThrownBy(() -> shortLinkService.resolve(originalCode))
                .isInstanceOf(ShortLinkNotFoundException.class);

        // The new code works.
        ShortLink resolvedNew = shortLinkService.resolve(reissued.getShortCode());
        assertThat(resolvedNew.getLongUrl()).isEqualTo(longUrl);
    }

    @Test
    void resubmittingAStillLiveLinksUrlReturnsTheSameShortCode() {
        String longUrl = "https://example.com/resubmit-while-live/" + UUID.randomUUID();
        ShortLink first = shortLinkService.create(longUrl, 3600L);

        ShortLink second = shortLinkService.create(longUrl);

        assertThat(second.getShortCode()).isEqualTo(first.getShortCode());
    }
}
