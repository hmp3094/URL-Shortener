package com.urlshortener.contract;

import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Validates POST /api/links with a custom alias against the documented API contract. */
class CustomAliasCreationContractTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createShortLinkWithAnAvailableAliasReturns201WithThatAliasAsTheShortCode() throws Exception {
        String longUrl = "https://example.com/alias-contract-test/" + UUID.randomUUID();
        String alias = "alias-" + UUID.randomUUID().toString().substring(0, 8);

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + longUrl + "\",\"alias\":\"" + alias + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(alias)))
                .andExpect(jsonPath("$.shortCode").value(alias))
                .andExpect(jsonPath("$.longUrl").value(longUrl));
    }

    @Test
    void createShortLinkWithNoAliasStillAutoGeneratesASixCharacterCode() throws Exception {
        String longUrl = "https://example.com/no-alias-contract-test/" + UUID.randomUUID();

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + longUrl + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.shortCode").value(org.hamcrest.Matchers.matchesPattern("^[a-z0-9]{6}$")));
    }
}
