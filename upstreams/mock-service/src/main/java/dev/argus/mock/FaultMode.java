package dev.argus.mock;

import java.util.Locale;
import java.util.Optional;

/** How this copy of the mock backend behaves: normally, slowly, or failing. */
public enum FaultMode {
    OK, SLOW, FAIL;

    /** Parses "ok", "slow" or "fail" (any case). Empty for anything else. */
    public static Optional<FaultMode> parse(String text) {
        if (text == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(text.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
