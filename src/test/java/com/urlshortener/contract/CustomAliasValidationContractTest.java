package com.urlshortener.contract;

import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Validates that malformed or reserved aliases are rejected distinctly from a 409 conflict (FR-003, FR-005). */
class CustomAliasValidationContractTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"ab", "a"})
    void rejectsAnAliasShorterThanTheMinimumLengthWith400(String alias) throws Exception {
        assertRejectedAsValidationError(alias);
    }

    @Test
    void rejectsAnAliasLongerThanTheMaximumLengthWith400() throws Exception {
        assertRejectedAsValidationError("a".repeat(33));
    }

    @ParameterizedTest
    @ValueSource(strings = {"has a space", "has/slash", "has.dot", "has$symbol"})
    void rejectsAnAliasWithADisallowedCharacterWith400(String alias) throws Exception {
        assertRejectedAsValidationError(alias);
    }

    @ParameterizedTest
    @ValueSource(strings = {"api", "actuator", "health", "error", "swagger-ui", "ACTUATOR"})
    void rejectsAReservedAliasWith400DistinctFromAConflict(String alias) throws Exception {
        assertRejectedAsValidationError(alias);
    }

    private void assertRejectedAsValidationError(String alias) throws Exception {
        String longUrl = "https://example.com/invalid-alias-contract-test/" + UUID.randomUUID();

        mockMvc.perform(post("/api/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"" + longUrl + "\",\"alias\":\"" + alias + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
