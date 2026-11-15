package dev.argus.gateway.events;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Readiness gate (QR-09): closed while a gateway instance is still replaying the configuration
 * topic, open once it holds the current routes and keys. While it is closed the instance
 * answers 503 and reports "not ready", so it never serves with missing or outdated
 * configuration. Once open it stays open.
 */
public final class ReadinessGate {

    private final AtomicBoolean open;

    private ReadinessGate(boolean open) {
        this.open = new AtomicBoolean(open);
    }

    /** For configuration that arrives from the topic: closed until the replay has finished. */
    public static ReadinessGate closed() {
        return new ReadinessGate(false);
    }

    /** For static configuration from a file: there is nothing to wait for. */
    public static ReadinessGate alreadyOpen() {
        return new ReadinessGate(true);
    }

    public void open() {
        open.set(true);
    }

    public boolean isOpen() {
        return open.get();
    }
}
