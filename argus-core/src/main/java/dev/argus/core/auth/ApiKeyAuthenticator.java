package dev.argus.core.auth;

import dev.argus.core.model.ApiKeyRecord;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.Principal;
import dev.argus.core.time.TimeSource;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Authenticates requests that carry an {@code X-API-Key} header (FR-06).
 *
 * <p>The key is hashed with SHA-256 and the hash is looked up through the {@link ApiKeyStore}
 * port. The key itself is never stored, compared or logged. Unknown, revoked and expired keys
 * are refused. The clock is a {@link TimeSource}, so expiry can be tested without waiting.
 *
 * <p>Thread-safe: it keeps no state of its own.
 */
public final class ApiKeyAuthenticator implements Authenticator {

    public static final String HEADER = "X-API-Key";

    private final ApiKeyStore store;
    private final ApiKeyHasher hasher;
    private final TimeSource time;

    public ApiKeyAuthenticator(ApiKeyStore store, ApiKeyHasher hasher, TimeSource time) {
        this.store = Objects.requireNonNull(store, "store");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.time = Objects.requireNonNull(time, "time");
    }

    @Override
    public boolean supports(IncomingRequest request) {
        return request.header(HEADER).isPresent();
    }

    @Override
    public Optional<Principal> authenticate(IncomingRequest request) {
        return request.header(HEADER)
                .filter(key -> !key.isBlank())
                .map(hasher::hash)
                .flatMap(store::findByHash)
                .filter(this::isUsable)
                .map(key -> new Principal(key.consumerId(), key.apiKeyId(), key.planId()));
    }

    /** A key is usable if it is not revoked and its expiry time, if any, is still ahead. */
    private boolean isUsable(ApiKeyRecord key) {
        if (key.revoked()) {
            return false;
        }
        Instant expiresAt = key.expiresAt();
        return expiresAt == null || time.now().isBefore(expiresAt);
    }
}
