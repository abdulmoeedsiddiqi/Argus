package dev.argus.gateway.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.core.model.ApiKeyRecord;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The gateway-side store behind the ApiKeyStore port: API key records, found by hash. */
class ApiKeyCacheTest {

    private static final ApiKeyRecord SHOP = record("key-1", "hash-of-shop-key");
    private static final ApiKeyRecord PARTNER = record("key-2", "hash-of-partner-key");

    private final ApiKeyCache cache = new ApiKeyCache();

    @Test
    @DisplayName("FR-06: a stored key record is found by its hash")
    void findsRecordByHash() {
        cache.replaceAll(List.of(SHOP, PARTNER));

        assertThat(cache.findByHash("hash-of-shop-key")).contains(SHOP);
        assertThat(cache.findByHash("hash-of-partner-key")).contains(PARTNER);
    }

    @Test
    @DisplayName("TC-06.2: an unknown hash finds nothing")
    void unknownHashFindsNothing() {
        cache.replaceAll(List.of(SHOP));

        assertThat(cache.findByHash("hash-of-nothing")).isEmpty();
    }

    @Test
    @DisplayName("a new cache is empty")
    void startsEmpty() {
        assertThat(cache.findByHash("hash-of-shop-key")).isEmpty();
    }

    @Test
    @DisplayName("replaceAll swaps the whole set: removed keys stop working at once")
    void replaceAllSwapsTheWholeSet() {
        cache.replaceAll(List.of(SHOP, PARTNER));
        cache.replaceAll(List.of(PARTNER));

        assertThat(cache.findByHash("hash-of-shop-key")).isEmpty();
        assertThat(cache.findByHash("hash-of-partner-key")).contains(PARTNER);
    }

    @Test
    @DisplayName("two records with the same hash are rejected and the old set is kept")
    void rejectsDuplicateHashes() {
        cache.replaceAll(List.of(SHOP));
        ApiKeyRecord duplicate = record("key-3", "hash-of-shop-key");

        assertThatThrownBy(() -> cache.replaceAll(List.of(PARTNER, duplicate, SHOP)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(cache.findByHash("hash-of-shop-key")).contains(SHOP);
        assertThat(cache.findByHash("hash-of-partner-key")).isEmpty();
    }

    private static ApiKeyRecord record(String id, String hash) {
        return new ApiKeyRecord(id, hash, "ak_test_xxxx", "shop-app", "free", false, null);
    }
}
