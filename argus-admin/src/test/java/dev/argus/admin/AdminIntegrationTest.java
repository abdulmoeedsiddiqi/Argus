package dev.argus.admin;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class of the Admin API integration tests: the real application on a random port, in
 * front of a real PostgreSQL (Testcontainers, the same image as in Docker Compose) whose schema
 * is created by the Flyway migrations. The container is started once and shared by all test
 * classes, so every test uses its own entity ids ({@link #id}).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class AdminIntegrationTest {

    /** Test values only; not real credentials. */
    public static final String USERNAME = "admin-test";
    public static final String PASSWORD = "test-password-not-a-secret";
    public static final String JWT_SECRET = "test-admin-jwt-secret-0123456789-abcdefgh";

    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16");

    static {
        POSTGRES.start();
    }

    @LocalServerPort
    protected int port;

    @Autowired
    protected JdbcTemplate jdbc;

    /** An HTTP client that is signed in as the test administrator. */
    protected AdminClient admin;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("argus.admin.username", () -> USERNAME);
        registry.add("argus.admin.password", () -> PASSWORD);
        registry.add("argus.admin.jwt-secret", () -> JWT_SECRET);
    }

    @BeforeEach
    void signIn() throws Exception {
        admin = AdminClient.signedIn(port, USERNAME, PASSWORD);
    }

    /** A fresh id such as "plan-3f9a1c2e", so tests never collide in the shared database. */
    protected static String id(String kind) {
        return kind + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected long auditRows() {
        return jdbc.queryForObject("SELECT count(*) FROM audit_log", Long.class);
    }
}
