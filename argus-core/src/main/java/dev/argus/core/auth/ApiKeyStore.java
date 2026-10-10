package dev.argus.core.auth;

import dev.argus.core.model.ApiKeyRecord;
import java.util.Optional;

/** Port: looks up API keys by their SHA-256 hash. Keys are never stored in plain text. */
public interface ApiKeyStore {

    Optional<ApiKeyRecord> findByHash(String keyHash);
}
