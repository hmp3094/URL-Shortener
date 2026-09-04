package com.urlshortener.contract;

import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Constitution's Principle V requires rate limiting on "link-creation and analytics-read
 * endpoints" — this covers the analytics-read half (GET .../stats), which had no rate limiting
 * at all until this test/fix. A nonexistent code is used deliberately: the interceptor runs in
 * {@code preHandle}, before the controller's 404 logic, so a rate-limited request short-circuits
 * to 429 regardless of what the code maps to.
 */
class StatsRateLimitContractTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Value("${app.rate-limit.capacity}")
    private int capacity;

    @Test
    void exceedingTheLimitOnTheStatsEndpointReturns429WithRetryAfter() throws Exception {
        String syntheticClient = UUID.randomUUID().toString();

        for (int i = 0; i < capacity; i++) {
            mockMvc.perform(get("/api/links/zzzzzz/stats").with(remoteAddr(syntheticClient)));
        }

        mockMvc.perform(get("/api/links/zzzzzz/stats").with(remoteAddr(syntheticClient)))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"));
    }

    private static RequestPostProcessor remoteAddr(String value) {
        return request -> {
            request.setRemoteAddr(value);
            return request;
        };
    }
}
