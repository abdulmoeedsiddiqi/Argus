package dev.argus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.core.model.ApiKeyRecord;
import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-06 API-key authentication: keys are looked up by SHA-256 hash; unknown, revoked and
 * expired keys are refused. The store is a fake map and the clock is fixed.
 */
class ApiKeyAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");
    private static final String VALID_KEY = "ak_test_shopapp_0123456789";

    private final ApiKeyHasher hasher = new ApiKeyHasher();
    private final Map<String, ApiKeyRecord> stored = new HashMap<>();
    /** Every hash the authenticator asked the store for. */
    private final List<String> lookups = new ArrayList<>();
    private final ApiKeyStore store = hash -> {
        lookups.add(hash);
        return Optional.ofNullable(stored.get(hash));
    };
    private final ApiKeyAuthenticator authenticator =
            new ApiKeyAuthenticator(store, hasher, () -> NOW);

    @Test
    @DisplayName("TC-06.1: a valid key gives the principal of its consumer")
    void validKeyGivesPrincipal() {
        store(VALID_KEY, false, null);

        Optional<Principal> principal = authenticator.authenticate(requestWithKey(VALID_KEY));

        assertThat(principal).contains(new Principal("shop-app", "key-1", "free"));
    }

    @Test
    @DisplayName("QR-11: the store is asked for the SHA-256 hash, never for the key itself")
    void looksUpByHashOnly() {
        store(VALID_KEY, false, null);

        authenticator.authenticate(requestWithKey(VALID_KEY));

        assertThat(lookups).containsExactly(hasher.hash(VALID_KEY));
        assertThat(lookups).doesNotContain(VALID_KEY);
    }

    @Test
    @DisplayName("TC-06.2: an unknown key is refused")
    void unknownKeyIsRefused() {
        store(VALID_KEY, false, null);

        assertThat(authenticator.authenticate(requestWithKey("ak_test_someone_else"))).isEmpty();
    }

    @Test
    @DisplayName("TC-06.2: an empty key is refused without asking the store")
    void blankKeyIsRefused() {
        assertThat(authenticator.authenticate(requestWithKey("   "))).isEmpty();
        assertThat(lookups).isEmpty();
    }

    @Test
    @DisplayName("TC-06.3: a revoked key is refused")
    void revokedKeyIsRefused() {
        store(VALID_KEY, true, null);

        assertThat(authenticator.authenticate(requestWithKey(VALID_KEY))).isEmpty();
    }

    @Test
    @DisplayName("TC-06.4 (BVA): a key that expires in 1 second is still accepted")
    void keyExpiringLaterIsAccepted() {
        store(VALID_KEY, false, NOW.plusSeconds(1));

        assertThat(authenticator.authenticate(requestWithKey(VALID_KEY))).isPresent();
    }

    @Test
    @DisplayName("TC-06.4 (BVA): a key that expires exactly now is refused")
    void keyExpiringNowIsRefused() {
        store(VALID_KEY, false, NOW);

        assertThat(authenticator.authenticate(requestWithKey(VALID_KEY))).isEmpty();
    }

    @Test
    @DisplayName("TC-06.4 (BVA): a key that expired 1 second ago is refused")
    void expiredKeyIsRefused() {
        store(VALID_KEY, false, NOW.minusSeconds(1));

        assertThat(authenticator.authenticate(requestWithKey(VALID_KEY))).isEmpty();
    }

    @Test
    @DisplayName("supports requests that carry X-API-Key, whatever the case of the header name")
    void supportsRequestsWithApiKeyHeader() {
        assertThat(authenticator.supports(request(Map.of("x-api-key", List.of(VALID_KEY))))).isTrue();
        assertThat(authenticator.supports(request(Map.of("X-API-Key", List.of(VALID_KEY))))).isTrue();
    }

    @Test
    @DisplayName("does not support requests without X-API-Key")
    void doesNotSupportOtherRequests() {
        assertThat(authenticator.supports(request(Map.of()))).isFalse();
        assertThat(authenticator.supports(
                request(Map.of("authorization", List.of("Bearer abc"))))).isFalse();
    }

    @Test
    @DisplayName("an API key record keeps a short display prefix and requires one")
    void recordKeepsDisplayPrefix() {
        ApiKeyRecord record = store(VALID_KEY, false, null);

        assertThat(record.displayPrefix()).isEqualTo("ak_test_shop");
        assertThatThrownBy(() -> new ApiKeyRecord(
                "key-1", hasher.hash(VALID_KEY), null, "shop-app", "free", false, null))
                .isInstanceOf(NullPointerException.class);
    }

    private ApiKeyRecord store(String key, boolean revoked, Instant expiresAt) {
        ApiKeyRecord record = new ApiKeyRecord(
                "key-1", hasher.hash(key), key.substring(0, 12), "shop-app", "free",
                revoked, expiresAt);
        stored.put(record.keyHash(), record);
        return record;
    }

    private static IncomingRequest requestWithKey(String key) {
        return request(Map.of("x-api-key", List.of(key)));
    }

    private static IncomingRequest request(Map<String, List<String>> headers) {
        return new IncomingRequest(
                "req-1", HttpMethod.GET, "/orders/42", headers, null, "127.0.0.1", NOW);
    }
}
