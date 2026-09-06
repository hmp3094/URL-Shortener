package com.urlshortener.support;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;

/**
 * Shared base for contract/integration tests: boots the full Spring context against a real
 * Postgres instance rather than mocks. Not transactional-rollback-wrapped on purpose: the
 * duplicate-URL concurrency test needs genuinely independent, concurrently committed
 * transactions, which a single wrapping test transaction would defeat. Tests instead use unique
 * long URLs per test method to avoid unique-constraint collisions within a class.
 *
 * <p>Runs against a real Postgres binary (Zonky's embedded-postgres), not a Docker container:
 * every migration and query in this project is genuine Postgres SQL (regex CHECK constraints,
 * native {@code ON CONFLICT ... RETURNING}, sequences), so a lighter substitute like H2 wouldn't
 * faithfully exercise it — but requiring Docker to be installed and version-compatible on every
 * machine that runs {@code mvn test} is its own reliability problem this project doesn't need
 * when a real Postgres binary can run directly on the host instead. Started once in a static
 * initializer and never stopped (the JVM exiting cleans it up) — one shared instance for the
 * whole test run, not one per test class, both for speed and to avoid Spring's
 * {@code @SpringBootTest} context cache ever serving a {@code DataSource} pointing at a
 * since-restarted instance on a different port.
 *
 * <p>Sharing one context also means the {@code RateLimiter} bean (a per-IP token bucket) is
 * genuinely shared across every test in the whole run, not reset per class the way it was
 * accidentally reset before (a side effect of the old per-class container restart, not a
 * deliberate design). Only {@code capacity} is raised here, well beyond what the whole suite
 * could plausibly consume, so ordinary functional tests never trip it — {@code refill-tokens} is
 * deliberately left at its production-like default (slow). {@link
 * com.urlshortener.contract.StatsRateLimitContractTest} still exercises the real
 * rate-limit-exceeded path correctly regardless of the raised capacity: it reads the configured
 * value via {@code @Value} and loops exactly that many times on its own synthetic per-test
 * client IP before asserting the next request is rejected. Raising {@code refill-tokens} by the
 * same factor as capacity was tried first and broke that test: the bucket refills continuously
 * (tokens/nanosecond, not a discrete per-window reset — see {@code RateLimiter.refill}), so a
 * larger capacity's correspondingly longer loop, combined with a correspondingly faster refill
 * rate, refilled enough tokens during the loop that the bucket was never actually empty by the
 * final request. Keeping the refill rate slow means even a long loop can't outrun it.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    private static final EmbeddedPostgres POSTGRES;

    static {
        try {
            POSTGRES = EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start embedded Postgres", e);
        }
    }

    @DynamicPropertySource
    static void registerTestProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
        registry.add("app.rate-limit.capacity", () -> 1000);
    }
}
