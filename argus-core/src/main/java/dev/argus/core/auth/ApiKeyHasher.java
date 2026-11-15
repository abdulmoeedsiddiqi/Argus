package dev.argus.core.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Turns an API key into its SHA-256 hash (64 lower-case hex characters).
 *
 * <p>Only this hash is stored and compared. A hash cannot be turned back into the key, so a
 * leaked key table does not give an attacker anything to send. A fast hash is enough here
 * (no salt, no bcrypt) because API keys are long random strings, not human passwords: they
 * cannot be guessed by trying a dictionary.
 *
 * <p>Thread-safe: a new {@link MessageDigest} is created for every call.
 */
public final class ApiKeyHasher {

    private static final String ALGORITHM = "SHA-256";

    public String hash(String apiKey) {
        Objects.requireNonNull(apiKey, "apiKey");
        try {
            MessageDigest sha256 = MessageDigest.getInstance(ALGORITHM);
            return HexFormat.of().formatHex(sha256.digest(apiKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            // Every Java platform is required to provide SHA-256.
            throw new IllegalStateException(ALGORITHM + " is not available", impossible);
        }
    }
}
