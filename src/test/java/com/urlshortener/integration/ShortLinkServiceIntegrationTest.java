package com.urlshortener.integration;

import com.urlshortener.link.ShortLink;
import com.urlshortener.link.ShortLinkRepository;
import com.urlshortener.link.ShortLinkService;
import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ShortLinkServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private ShortLinkRepository shortLinkRepository;

    @Test
    void creatingAShortLinkPersistsItAndItIsImmediatelyResolvable() {
        String longUrl = "https://example.com/service-test/" + UUID.randomUUID();

        ShortLink created = shortLinkService.create(longUrl);

        assertThat(created.getShortCode()).matches("^[a-z0-9]{6}$");
        assertThat(created.getLongUrl()).isEqualTo(longUrl);

        Optional<ShortLink> found = shortLinkRepository.findByShortCode(created.getShortCode());
        assertThat(found).isPresent();
        assertThat(found.get().getLongUrl()).isEqualTo(longUrl);
    }
}
