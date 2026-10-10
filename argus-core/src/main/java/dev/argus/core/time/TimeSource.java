package dev.argus.core.time;

import java.time.Instant;

/** Abstraction over the clock, so time-based logic can be unit-tested with a fake clock. */
@FunctionalInterface
public interface TimeSource {

    Instant now();

    static TimeSource system() {
        return Instant::now;
    }
}
