package com.urlshortener.contract;

import com.urlshortener.link.ShortLink;
import com.urlshortener.link.ShortLinkService;
import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StatsContractTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkService shortLinkService;

    @Test
    void returnsClickStatsForAnExistingCode() throws Exception {
        String longUrl = "https://example.com/stats-contract-test/" + UUID.randomUUID();
        ShortLink created = shortLinkService.create(longUrl);

        mockMvc.perform(get("/" + created.getShortCode()));
        mockMvc.perform(get("/" + created.getShortCode()));

        mockMvc.perform(get("/api/links/" + created.getShortCode() + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shortCode").value(created.getShortCode()))
                .andExpect(jsonPath("$.longUrl").value(longUrl))
                .andExpect(jsonPath("$.clickCount").value(2))
                .andExpect(jsonPath("$.lastAccessedAt").exists());
    }

    @Test
    void returnsNotFoundForAnUnknownCode() throws Exception {
        mockMvc.perform(get("/api/links/zzzzzz/stats"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
