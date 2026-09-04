package com.urlshortener.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Shared base for contract/integration tests: boots the full Spring context against a real,
 * disposable Postgres container rather than mocks. Not transactional-rollback-wrapped on
 * purpose: the duplicate-URL concurrency test needs genuinely independent, concurrently
 * committed transactions, which a single wrapping test transaction would defeat. Tests instead
 * use unique long URLs per test method to avoid unique-constraint collisions within a class.
 */
@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");
}
