package dev.argus.mock;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * The current fault mode of this copy. Many request threads read it while POST /fault writes
 * it, so it is held in an AtomicReference: readers always see a complete, up-to-date value
 * without locking.
 */
@Component
public class FaultState {

    private final AtomicReference<FaultMode> mode = new AtomicReference<>(FaultMode.OK);
    private final Duration slowDelay;

    public FaultState(@Value("${mock.slow-delay:3s}") Duration slowDelay) {
        this.slowDelay = slowDelay;
    }

    public FaultMode current() {
        return mode.get();
    }

    public void switchTo(FaultMode next) {
        mode.set(next);
    }

    public Duration slowDelay() {
        return slowDelay;
    }
}
