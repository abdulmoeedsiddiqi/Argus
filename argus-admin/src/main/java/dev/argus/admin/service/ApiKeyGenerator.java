package dev.argus.admin.service;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/**
 * Creates new API keys: {@code ak_live_} followed by 32 random bytes in URL-safe Base64.
 * The bytes come from {@link SecureRandom}, so a key cannot be guessed (256 bits).
 */
@Component
public class ApiKeyGenerator {

    static final String KEY_START = "ak_live_";
    /** Length of the display prefix; the api_keys.key_prefix column holds 12 characters. */
    static final int PREFIX_LENGTH = 12;
    private static final int RANDOM_BYTES = 32;

    private final SecureRandom random = new SecureRandom();

    public String newKey() {
        return KEY_START + randomText(RANDOM_BYTES);
    }

    /** A key id such as {@code key_3q2-7w...}; it identifies a key without revealing it. */
    public String newKeyId() {
        return "key_" + randomText(12);
    }

    /** The first characters of a key, safe to show in lists and logs. */
    public String prefixOf(String key) {
        return key.substring(0, PREFIX_LENGTH);
    }

    private String randomText(int bytes) {
        byte[] buffer = new byte[bytes];
        random.nextBytes(buffer);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(buffer);
    }
}
