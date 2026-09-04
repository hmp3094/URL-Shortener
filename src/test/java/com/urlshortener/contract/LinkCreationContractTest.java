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

/** Validates POST /api/links against the documented API contract. */
class LinkCreationContractTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void createShortLinkReturns201WithLinkResponseShape() throws Exception {
        String longUrl = "https://example.com/contract-test/" + UUID.randomUUID();

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + longUrl + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.shortCode").value(org.hamcrest.Matchers.matchesPattern("^[a-z0-9]{6}$")))
                .andExpect(jsonPath("$.shortUrl").value(org.hamcrest.Matchers.containsString("/")))
                .andExpect(jsonPath("$.longUrl").value(longUrl))
                .andExpect(jsonPath("$.createdAt").exists());
    }
}
