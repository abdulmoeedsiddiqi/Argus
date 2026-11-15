package dev.argus.gateway;

import dev.argus.core.auth.ApiKeyHasher;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Test credentials for the gateway integration tests. Test values only.
 *
 * <p>Like the real configuration, the gateway is given only the SHA-256 hash of each key; the
 * plaintext keys exist only here, on the "client" side of the tests.
 */
public final class TestCredentials {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String VALID_KEY = "ak_test_shopapp_0123456789";
    public static final String REVOKED_KEY = "ak_test_revoked_0123456789";
    public static final String EXPIRED_KEY = "ak_test_expired_0123456789";
    public static final String UNKNOWN_KEY = "ak_test_unknown_0123456789";

    private TestCredentials() { }

    /** Registers one valid, one revoked and one expired key, plus the JWT settings. */
    public static void register(DynamicPropertyRegistry registry) {
        ApiKeyHasher hasher = new ApiKeyHasher();
        key(registry, 0, "key-valid", hasher.hash(VALID_KEY), "ak_test_shop");
        key(registry, 1, "key-revoked", hasher.hash(REVOKED_KEY), "ak_test_revo");
        registry.add("argus.auth.api-keys[1].revoked", () -> "true");
        key(registry, 2, "key-expired", hasher.hash(EXPIRED_KEY), "ak_test_expi");
        registry.add("argus.auth.api-keys[2].expires-at", () -> "2020-01-01T00:00:00Z");
        registry.add("argus.auth.jwt.secret", () -> TestTokens.SECRET);
        registry.add("argus.auth.jwt.issuer", () -> TestTokens.ISSUER);
    }

    private static void key(
            DynamicPropertyRegistry registry, int index, String id, String hash, String prefix) {
        String base = "argus.auth.api-keys[" + index + "].";
        registry.add(base + "id", () -> id);
        registry.add(base + "key-hash", () -> hash);
        registry.add(base + "display-prefix", () -> prefix);
        registry.add(base + "consumer", () -> "shop-app");
        registry.add(base + "plan", () -> "free");
    }
}
