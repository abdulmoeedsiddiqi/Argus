package dev.argus.core.events;

import java.util.Optional;

/** Read-only port used by EnforcementFilter to ask whether a key is blocked or over quota. */
public interface EnforcementView {

    Optional<String> blockReason(String apiKeyId);
}
