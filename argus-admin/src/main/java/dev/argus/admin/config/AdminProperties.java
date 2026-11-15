package dev.argus.admin.config;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Admin sign-in settings ({@code argus.admin}), supplied through the environment.
 *
 * @param username   the administrator's user name
 * @param password   the administrator's password
 * @param jwtSecret  HS256 signing secret for admin tokens, at least 32 bytes
 * @param tokenTtl   how long a token stays valid after sign-in
 */
@ConfigurationProperties("argus.admin")
public record AdminProperties(
        String username,
        String password,
        String jwtSecret,
        @DefaultValue("1h") Duration tokenTtl) {

    private static final int MIN_SECRET_BYTES = 32;

    /** Fails at start-up, with a clear message, if the service is not configured safely. */
    public AdminProperties {
        if (isBlank(username) || isBlank(password)) {
            throw new IllegalStateException(
                    "argus.admin.username and argus.admin.password must be set "
                            + "(ARGUS_ADMIN_USERNAME, ARGUS_ADMIN_PASSWORD)");
        }
        if (isBlank(jwtSecret)
                || jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "argus.admin.jwt-secret must be at least 32 bytes (ARGUS_ADMIN_JWT_SECRET)");
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Never prints the password or the secret. */
    @Override
    public String toString() {
        return "AdminProperties[username=" + username + ", password=<hidden>, jwtSecret=<hidden>,"
                + " tokenTtl=" + tokenTtl + "]";
    }
}
