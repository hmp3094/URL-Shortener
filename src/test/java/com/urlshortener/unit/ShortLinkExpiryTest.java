package com.urlshortener.unit;

import com.urlshortener.link.ShortLink;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkExpiryTest {

    @Test
    void aLinkWithNoExpiryIsNeverExpired() {
        ShortLink link = new ShortLink(1L, "abc123", "https://example.com", OffsetDateTime.now(), 0, null, null);

        assertThat(link.isExpired()).isFalse();
    }

    @Test
    void aLinkWithAFutureExpiryIsNotYetExpired() {
        OffsetDateTime future = OffsetDateTime.now().plusSeconds(60);
        ShortLink link = new ShortLink(1L, "abc123", "https://example.com", OffsetDateTime.now(), 0, null, future);

        assertThat(link.isExpired()).isFalse();
    }

    @Test
    void aLinkWithAPastExpiryIsExpired() {
        OffsetDateTime past = OffsetDateTime.now().minusSeconds(1);
        ShortLink link = new ShortLink(1L, "abc123", "https://example.com", OffsetDateTime.now(), 0, null, past);

        assertThat(link.isExpired()).isTrue();
    }
}
