package com.urlshortener.integration;

import com.urlshortener.link.ShortLinkRepository;
import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class InvalidUrlIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShortLinkRepository shortLinkRepository;

    @ParameterizedTest
    @ValueSource(strings = {
            "not-a-url",
            "javascript:alert(1)",
            "ftp://example.com/file",
            "http://127.0.0.1/admin",
            "http://10.0.0.5/internal",
            "http://169.254.169.254/latest/meta-data"
    })
    void noRowIsCreatedForAnyRejectedSubmission(String badUrl) throws Exception {
        long countBefore = shortLinkRepository.count();

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + badUrl + "\"}"))
                .andExpect(status().isBadRequest());

        assertThat(shortLinkRepository.count()).isEqualTo(countBefore);
    }

    @org.junit.jupiter.api.Test
    void noRowIsCreatedForAMissingUrlField() throws Exception {
        long countBefore = shortLinkRepository.count();

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        assertThat(shortLinkRepository.count()).isEqualTo(countBefore);
    }
}
