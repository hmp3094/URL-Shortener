package com.urlshortener.contract;

import com.urlshortener.link.ShortLinkService;
import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Validates the two creation-time conflict responses introduced by custom aliases (FR-007, FR-009). */
class CustomAliasConflictContractTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkService shortLinkService;

    @Test
    void requestingAnAliasAlreadyInUseReturns409AliasTaken() throws Exception {
        String alias = "taken-" + UUID.randomUUID().toString().substring(0, 8);
        shortLinkService.create("https://example.com/conflict-original/" + UUID.randomUUID(), null, alias);

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/conflict-attempt/" + UUID.randomUUID()
                                + "\",\"alias\":\"" + alias + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("ALIAS_TAKEN"));
    }

    @Test
    void requestingAnAliasForAUrlThatAlreadyHasALiveLinkReturns409UrlAlreadyShortened() throws Exception {
        String longUrl = "https://example.com/already-shortened/" + UUID.randomUUID();
        shortLinkService.create(longUrl);
        String newAlias = "second-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + longUrl + "\",\"alias\":\"" + newAlias + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("URL_ALREADY_SHORTENED"));
    }
}
