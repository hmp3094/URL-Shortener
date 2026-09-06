package com.urlshortener.integration;

import com.urlshortener.link.ShortLink;
import com.urlshortener.link.ShortLinkService;
import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CustomAliasIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private MockMvc mockMvc;

    @Test
    void aLinkCreatedWithAnAliasResolvesViaThatAliasToTheDestinationUrl() throws Exception {
        String longUrl = "https://example.com/alias-integration-test/" + UUID.randomUUID();
        String alias = "resolve-" + UUID.randomUUID().toString().substring(0, 8);

        ShortLink created = shortLinkService.create(longUrl, null, alias);

        assertThat(created.getShortCode()).isEqualTo(alias.toLowerCase());

        mockMvc.perform(get("/" + alias))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", longUrl));
    }

    @Test
    void aliasResolutionIsCaseInsensitive() throws Exception {
        String longUrl = "https://example.com/alias-case-test/" + UUID.randomUUID();
        String alias = "Promo" + UUID.randomUUID().toString().substring(0, 8);

        shortLinkService.create(longUrl, null, alias);

        mockMvc.perform(get("/" + alias.toLowerCase()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", longUrl));
        mockMvc.perform(get("/" + alias.toUpperCase()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", longUrl));
    }
}
