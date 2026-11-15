package dev.argus.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;

import dev.argus.contracts.ConfigChange;
import dev.argus.contracts.ConfigChange.EntityType;
import dev.argus.core.model.ApiKeyRecord;
import dev.argus.gateway.events.ConfigMessages;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-12: API-key changes from the configuration topic update the key cache. */
class ApiKeyCacheListenerTest {

    private final ApiKeyCache cache = new ApiKeyCache();

    @Test
    @DisplayName("FR-12: an API_KEY change makes the key known by its hash")
    void keyChangeAddsTheKey() {
        cache.onChange(ConfigMessages.apiKey("key-1", "hash-1", false));

        ApiKeyRecord found = cache.findByHash("hash-1").orElseThrow();
        assertThat(found.apiKeyId()).isEqualTo("key-1");
        assertThat(found.consumerId()).isEqualTo("shop-app");
        assertThat(found.planId()).isEqualTo("free");
        assertThat(found.revoked()).isFalse();
    }

    @Test
    @DisplayName("QR-12: a revocation replaces the record, so the key is known as revoked")
    void revocationReplacesTheRecord() {
        cache.onChange(ConfigMessages.apiKey("key-1", "hash-1", false));

        cache.onChange(ConfigMessages.apiKey("key-1", "hash-1", true));

        assertThat(cache.findByHash("hash-1").orElseThrow().revoked()).isTrue();
    }

    @Test
    @DisplayName("FR-12: a tombstone removes the key; other keys stay")
    void tombstoneRemovesTheKey() {
        cache.onChange(ConfigMessages.apiKey("key-1", "hash-1", false));
        cache.onChange(ConfigMessages.apiKey("key-2", "hash-2", false));

        cache.onChange(ConfigMessages.deleted(EntityType.API_KEY, "key-1"));

        assertThat(cache.findByHash("hash-1")).isEmpty();
        assertThat(cache.findByHash("hash-2")).isPresent();
    }

    @Test
    @DisplayName("an unreadable key payload is skipped and the known keys stay")
    void invalidPayloadKeepsTheKeys() {
        cache.onChange(ConfigMessages.apiKey("key-1", "hash-1", false));

        cache.onChange(new ConfigChange(EntityType.API_KEY, "key-9", 1, "{not json", Instant.now()));

        assertThat(cache.findByHash("hash-1")).isPresent();
    }

    @Test
    @DisplayName("changes for other entity types are ignored")
    void otherEntityTypesAreIgnored() {
        cache.onChange(ConfigMessages.apiKey("key-1", "hash-1", false));

        cache.onChange(ConfigMessages.deleted(EntityType.ROUTE, "key-1"));

        assertThat(cache.findByHash("hash-1")).isPresent();
    }
}
