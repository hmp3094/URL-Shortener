package com.urlshortener.contract;

import com.urlshortener.link.ShortLink;
import com.urlshortener.link.ShortLinkService;
import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RedirectContractTest extends AbstractIntegrationTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

    @Autowired
    private ShortLinkService shortLinkService;

    @Test
    void redirectsToTheLongUrlForAnExistingCode() throws Exception {
        String longUrl = "https://example.com/redirect-contract-test/" + UUID.randomUUID();
        ShortLink created = shortLinkService.create(longUrl);

        mockMvc.perform(get("/" + created.getShortCode()))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", longUrl));
    }

    @Test
    void returnsNotFoundForAnUnknownCode() throws Exception {
        mockMvc.perform(get("/zzzzzz"))
                .andExpect(status().isNotFound())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.error").value("NOT_FOUND"));
    }
}
