package dev.argus.gateway.config;

import dev.argus.core.model.ApiKeyRecord;
import java.time.Instant;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Authentication settings read from configuration ({@code argus.auth}).
 *
 * <p>API keys are configured by their SHA-256 hash only, so a plaintext key never has to be
 * written into a configuration file. This is a stand-in until the Admin API (module M5)
 * issues keys and sends them through the configuration topic.
 */
@ConfigurationProperties("argus.auth")
public record AuthProperties(List<ApiKeyEntry> apiKeys, Jwt jwt) {

    public AuthProperties {
        apiKeys = apiKeys == null ? List.of() : List.copyOf(apiKeys);
    }

    public List<ApiKeyRecord> toRecords() {
        return apiKeys.stream().map(ApiKeyEntry::toRecord).toList();
    }

    /** True if a JWT secret is configured; without one, only API keys are accepted. */
    public boolean jwtEnabled() {
        return jwt != null && jwt.secret() != null && !jwt.secret().isBlank();
    }

    /** One API key: its hash, a short display prefix, and who it belongs to. */
    public record ApiKeyEntry(
            String id,
            String keyHash,
            String displayPrefix,
            String consumer,
            String plan,
            @DefaultValue("false") boolean revoked,
            Instant expiresAt) {

        ApiKeyRecord toRecord() {
            return new ApiKeyRecord(id, keyHash, displayPrefix, consumer, plan, revoked, expiresAt);
        }
    }

    /** JWT verification: the shared HS256 secret (from the environment) and the expected issuer. */
    public record Jwt(String secret, String issuer) {

        /** Never prints the secret, so it cannot leak through a log line or an error message. */
        @Override
        public String toString() {
            return "Jwt[secret=<hidden>, issuer=" + issuer + "]";
        }
    }
}
