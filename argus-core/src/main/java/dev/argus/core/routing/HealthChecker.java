package dev.argus.core.routing;

import dev.argus.core.model.HealthStatus;
import dev.argus.core.model.UpstreamInstance;
import java.time.Duration;
import java.util.Collection;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

/**
 * Active health checking (FR-03). Every round it probes each registered instance. An instance
 * is marked UNHEALTHY after {@code failureThreshold} failed checks in a row, which removes it
 * from rotation, and HEALTHY again after one successful check.
 *
 * <p>Requiring several failures in a row stops one lost packet from removing a good instance.
 *
 * <p>Thread-safety: rounds run one at a time on a single scheduler thread. The failure counts
 * are atomic, and results are published through the thread-safe {@link UpstreamRegistry}.
 */
public final class HealthChecker {

    private final Supplier<? extends Collection<UpstreamInstance>> instances;
    private final HealthProbe probe;
    private final UpstreamRegistry registry;
    private final int failureThreshold;
    private final ConcurrentHashMap<String, AtomicInteger> consecutiveFailures = new ConcurrentHashMap<>();
    private final AtomicReference<ScheduledExecutorService> scheduler = new AtomicReference<>();

    public HealthChecker(
            Supplier<? extends Collection<UpstreamInstance>> instances,
            HealthProbe probe,
            UpstreamRegistry registry,
            int failureThreshold) {
        this.instances = Objects.requireNonNull(instances, "instances");
        this.probe = Objects.requireNonNull(probe, "probe");
        this.registry = Objects.requireNonNull(registry, "registry");
        if (failureThreshold < 1) {
            throw new IllegalArgumentException("failureThreshold must be >= 1");
        }
        this.failureThreshold = failureThreshold;
    }

    /** Runs one round: probes every registered instance once. */
    public void checkAll() {
        for (UpstreamInstance instance : instances.get()) {
            check(instance);
        }
    }

    private void check(UpstreamInstance instance) {
        AtomicInteger failures =
                consecutiveFailures.computeIfAbsent(instance.id(), id -> new AtomicInteger());
        if (answers(instance)) {
            failures.set(0);
            registry.updateHealth(instance.id(), HealthStatus.HEALTHY);
        } else if (failures.incrementAndGet() >= failureThreshold) {
            registry.updateHealth(instance.id(), HealthStatus.UNHEALTHY);
        }
    }

    /** A probe that throws is treated as a failed check, so one bad instance cannot stop a round. */
    private boolean answers(UpstreamInstance instance) {
        try {
            return probe.isHealthy(instance);
        } catch (RuntimeException probeFailed) {
            return false;
        }
    }

    /** Starts periodic rounds. Calling it twice has no further effect. */
    public void start(Duration interval) {
        Objects.requireNonNull(interval, "interval");
        ScheduledExecutorService created = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "argus-health-checker");
            thread.setDaemon(true);
            return thread;
        });
        if (scheduler.compareAndSet(null, created)) {
            created.scheduleWithFixedDelay(
                    this::checkAll, 0, interval.toMillis(), TimeUnit.MILLISECONDS);
        } else {
            created.shutdown();
        }
    }

    /** Stops periodic rounds. */
    public void stop() {
        ScheduledExecutorService running = scheduler.getAndSet(null);
        if (running != null) {
            running.shutdownNow();
        }
    }
}
