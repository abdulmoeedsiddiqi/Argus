package dev.argus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-06 / QR-11: API keys are stored and looked up only as SHA-256 hashes, so a leaked
 * database or configuration file does not reveal a usable key.
 */
class ApiKeyHasherTest {

    private final ApiKeyHasher hasher = new ApiKeyHasher();

    @Test
    @DisplayName("QR-11: hashes with SHA-256 (matches the published test vector for \"abc\")")
    void matchesKnownSha256Vector() {
        assertThat(hasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }

    @Test
    @DisplayName("the hash is 64 lower-case hex characters and does not contain the key")
    void hashIsHexAndHidesTheKey() {
        String key = "ak_live_shopapp_0123456789";

        String hash = hasher.hash(key);

        assertThat(hash).hasSize(64).matches("[0-9a-f]+").doesNotContain(key);
    }

    @Test
    @DisplayName("the same key always gives the same hash, different keys give different hashes")
    void isDeterministic() {
        assertThat(hasher.hash("ak_live_one")).isEqualTo(hasher.hash("ak_live_one"));
        assertThat(hasher.hash("ak_live_one")).isNotEqualTo(hasher.hash("ak_live_two"));
    }

    @Test
    @DisplayName("a missing key is rejected")
    void rejectsNull() {
        assertThatThrownBy(() -> hasher.hash(null)).isInstanceOf(NullPointerException.class);
    }
}
