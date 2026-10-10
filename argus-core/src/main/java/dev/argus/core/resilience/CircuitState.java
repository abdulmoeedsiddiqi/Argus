package dev.argus.core.resilience;

/**
 * State pattern: each circuit-breaker state decides whether a call may proceed and how
 * results move the breaker to another state. Planned states: ClosedState, OpenState,
 * HalfOpenState. The CircuitBreaker context holds the current state in an AtomicReference.
 */
public interface CircuitState {

    CircuitStateName name();

    /** Whether a call may be attempted now. */
    boolean allowsCall(CircuitBreakerContext context);

    void onSuccess(CircuitBreakerContext context);

    void onFailure(CircuitBreakerContext context);
}
