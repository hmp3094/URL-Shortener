package com.urlshortener.integration;

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

class DuplicateUrlIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ShortLinkService shortLinkService;

    @Autowired
    private ShortLinkRepository shortLinkRepository;

    @Test
    void submittingTheSameUrlTwiceSequentiallyReturnsTheSameShortLink() {
        String longUrl = "https://example.com/duplicate-sequential/" + UUID.randomUUID();

        ShortLink first = shortLinkService.create(longUrl);
        ShortLink second = shortLinkService.create(longUrl);

        assertThat(second.getShortCode()).isEqualTo(first.getShortCode());
        assertThat(shortLinkRepository.findByLongUrl(longUrl)).isPresent();
        assertThat(countRowsFor(longUrl)).isEqualTo(1);
    }

    @Test
    void submittingTheSameUrlConcurrentlyCreatesExactlyOneRow() throws Exception {
        String longUrl = "https://example.com/duplicate-concurrent/" + UUID.randomUUID();
        int concurrentRequests = 8;
        ExecutorService executor = Executors.newFixedThreadPool(concurrentRequests);
        try {
            List<Callable<ShortLink>> tasks = IntStream.range(0, concurrentRequests)
                    .<Callable<ShortLink>>mapToObj(i -> () -> shortLinkService.create(longUrl))
                    .collect(Collectors.toList());

            List<Future<ShortLink>> futures = executor.invokeAll(tasks);

            List<String> shortCodes = futures.stream()
                    .map(f -> {
                        try {
                            return f.get().getShortCode();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    })
                    .distinct()
                    .collect(Collectors.toList());

            assertThat(shortCodes).hasSize(1);
            assertThat(countRowsFor(longUrl)).isEqualTo(1);
        } finally {
            executor.shutdown();
        }
    }

    private long countRowsFor(String longUrl) {
        return shortLinkRepository.findAll().stream()
                .filter(link -> link.getLongUrl().equals(longUrl))
                .count();
    }
}
