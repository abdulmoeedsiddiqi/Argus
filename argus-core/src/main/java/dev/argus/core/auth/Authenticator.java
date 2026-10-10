package dev.argus.core.auth;

import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.Principal;
import java.util.Optional;

/**
 * Verifies one kind of credential. {@code supports} lets a composite authenticator pick
 * the right implementation (JwtAuthenticator or ApiKeyAuthenticator) without type checks.
 */
public interface Authenticator {

    boolean supports(IncomingRequest request);

    /** Returns the principal, or empty if the credential is invalid, expired or revoked. */
    Optional<Principal> authenticate(IncomingRequest request);
}
