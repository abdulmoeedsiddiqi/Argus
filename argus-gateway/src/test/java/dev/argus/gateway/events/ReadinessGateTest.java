package dev.argus.gateway.events;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** QR-09: the gate that keeps a gateway from serving before it has its configuration. */
class ReadinessGateTest {

    @Test
    @DisplayName("TC-12.3: a gate created closed stays closed until it is opened")
    void closedUntilOpened() {
        ReadinessGate gate = ReadinessGate.closed();
        assertThat(gate.isOpen()).isFalse();

        gate.open();

        assertThat(gate.isOpen()).isTrue();
    }

    @Test
    @DisplayName("a gate created open is open at once (static configuration needs no replay)")
    void openGateIsOpen() {
        assertThat(ReadinessGate.alreadyOpen().isOpen()).isTrue();
    }
}
