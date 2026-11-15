package dev.argus.admin.api;

import jakarta.validation.constraints.Future;
import java.time.Instant;

/**
 * Optional body of "issue a key".
 *
 * @param expiresAt when the key stops working; leave it out for a key that never expires
 */
public record IssueKeyRequest(@Future Instant expiresAt) { }
