package com.urlshortener.contract;

import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Validates that the web UI's page and assets are served with the expected shape. */
class WebUiContractTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // MockMvc records Spring Boot's welcome-page handling for "/" as a "forward:index.html"
    // ModelAndView but, unlike a real servlet container, doesn't actually re-dispatch that
    // forward through the resource handler to produce real content — this is documented MockMvc
    // behavior, not a bug in this configuration (confirmed serving correctly against a real
    // running instance). So this test only verifies the welcome-page route resolves without
    // error; every test that needs the actual rendered content requests /index.html directly,
    // which MockMvc does execute fully through the real resource handler.
    @Test
    void rootResolvesToTheWelcomePageWithoutError() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isOk());
    }

    @Test
    void indexHtmlServesTheFullPage() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));
    }

    @Test
    void assetsAreServedWithTheExpectedContentTypes() throws Exception {
        mockMvc.perform(get("/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.valueOf("text/css")));
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk());
    }

    @Test
    void pageContainsTheShorteningFormControls() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(content().string(containsString("id=\"url-input\"")))
                .andExpect(content().string(containsString("id=\"alias-input\"")))
                .andExpect(content().string(containsString("id=\"expiration-select\"")))
                .andExpect(content().string(containsString("id=\"shorten-submit\"")))
                .andExpect(content().string(containsString("id=\"shorten-result\"")));
    }

    @Test
    void pageContainsAnErrorMessageContainerForTheShorteningForm() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(content().string(containsString("id=\"shorten-error\"")));
    }

    @Test
    void pageContainsTheStatsLookupControls() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(content().string(containsString("id=\"code-input\"")))
                .andExpect(content().string(containsString("id=\"stats-submit\"")))
                .andExpect(content().string(containsString("id=\"stats-result\"")));
    }
}
