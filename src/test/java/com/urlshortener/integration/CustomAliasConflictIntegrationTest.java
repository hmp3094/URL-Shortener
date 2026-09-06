package com.urlshortener.integration;

import com.urlshortener.link.AliasAlreadyTakenException;
import com.urlshortener.link.ShortLink;
import com.urlshortener.link.ShortLinkRepository;
import com.urlshortener.link.ShortLinkService;
import com.urlshortener.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class CustomAliasConflictIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private ShortLinkRepository shortLinkRepository;

    @Test
    void twoConcurrentRequestsForTheSameAliasResultInExactlyOneSuccess() throws Exception {
        String alias = "race-" + UUID.randomUUID().toString().substring(0, 8);
        int concurrentRequests = 8;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        try {
            List<Callable<ShortLink>> tasks = IntStream.range(0, concurrentRequests)
                    .<Callable<ShortLink>>mapToObj(i -> () -> shortLinkService.create(
                            "https://example.com/alias-race/" + UUID.randomUUID(), null, alias))
                    .collect(Collectors.toList());

            List<Future<ShortLink>> futures = executor.invokeAll(tasks);

            long successes = futures.stream().filter(f -> {
                try {
                    f.get();
                    return true;
                } catch (Exception e) {
                    assertThat(e.getCause()).isInstanceOf(AliasAlreadyTakenException.class);
                    return false;
                }
            }).count();

            assertThat(successes).isEqualTo(1);
            assertThat(shortLinkRepository.findByShortCode(alias.toLowerCase())).isPresent();
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void aRejectedConflictLeavesTheExistingMappingUnchanged() {
        String alias = "protected-" + UUID.randomUUID().toString().substring(0, 8);
        String originalUrl = "https://example.com/protected-original/" + UUID.randomUUID();
        ShortLink original = shortLinkService.create(originalUrl, null, alias);

        String attemptedUrl = "https://example.com/protected-attempt/" + UUID.randomUUID();
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> shortLinkService.create(attemptedUrl, null, alias))
                .isInstanceOf(AliasAlreadyTakenException.class);

        ShortLink stillThere = shortLinkRepository.findByShortCode(alias.toLowerCase()).orElseThrow();
        assertThat(stillThere.getLongUrl()).isEqualTo(originalUrl);
        assertThat(stillThere.getId()).isEqualTo(original.getId());
    }
}
