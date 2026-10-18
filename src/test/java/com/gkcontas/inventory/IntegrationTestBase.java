package com.gkcontas.inventory;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * One container for the whole suite, started from a static initialiser.
 * Not {@code @Testcontainers}: Spring caches application contexts across test
 * classes, and a container tied to a class is stopped while a cached context
 * still points at it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("inventory")
            .withUsername("inventory")
            .withPassword("inventory")
            // Same server settings as the compose file: every concurrent worker
            // holds a connection for the length of its transaction, a
            // pessimistic lock would otherwise wait forever, and the deadlock
            // detector has to fire fast enough for a test to wait on it.
            .withCommand("postgres",
                    "-c", "max_connections=300",
                    "-c", "lock_timeout=2000",
                    "-c", "deadlock_timeout=200");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
