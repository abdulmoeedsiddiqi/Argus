package dev.argus.admin.config;

import dev.argus.admin.security.AdminTokenService;
import dev.argus.core.auth.ApiKeyHasher;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Objects that the Admin API shares, created once and handed out by dependency injection. */
@Configuration
@EnableConfigurationProperties(AdminProperties.class)
public class AdminConfiguration {

    /** The clock is a bean so that tests can replace it with a fixed one. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    AdminTokenService adminTokenService(AdminProperties properties, Clock clock) {
        return new AdminTokenService(
                properties.jwtSecret().getBytes(StandardCharsets.UTF_8), properties.tokenTtl(), clock);
    }

    /** The same SHA-256 hashing as the gateway uses when it looks a key up. */
    @Bean
    ApiKeyHasher apiKeyHasher() {
        return new ApiKeyHasher();
    }
}
