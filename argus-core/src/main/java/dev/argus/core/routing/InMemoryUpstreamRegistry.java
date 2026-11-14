package dev.argus.core.routing;

import dev.argus.core.model.HealthStatus;
import dev.argus.core.model.UpstreamInstance;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keeps the registered instances of every service and their last known health (FR-03).
 *
 * <p>Thread-safety: the instance list is an immutable snapshot behind an
 * {@link AtomicReference}, replaced as a whole. Health is a {@link ConcurrentHashMap} from
 * instance id to an immutable {@link HealthStatus}; request threads read it while the health
 * checker writes it, without locks.
 *
 * <p>An instance that has not been checked yet is {@code UNKNOWN} and still receives traffic:
 * only an instance explicitly marked {@code UNHEALTHY} is left out (QR-01).
 */
public final class InMemoryUpstreamRegistry implements UpstreamRegistry {

    private final AtomicReference<List<UpstreamInstance>> instances = new AtomicReference<>(List.of());
    private final ConcurrentHashMap<String, HealthStatus> health = new ConcurrentHashMap<>();

    /** Replaces the registered instances. Health of instances that remain is kept. */
    public void replaceAll(Collection<UpstreamInstance> newInstances) {
        Objects.requireNonNull(newInstances, "newInstances");
        Set<String> ids = new HashSet<>();
        for (UpstreamInstance instance : newInstances) {
            if (!ids.add(instance.id())) {
                throw new IllegalArgumentException("two instances share the id " + instance.id());
            }
        }
        instances.set(List.copyOf(newInstances));
        health.keySet().retainAll(ids);
    }

    /** Every registered instance, whatever its health. Used by the health checker. */
    public List<UpstreamInstance> allInstances() {
        return instances.get();
    }

    public HealthStatus healthOf(String instanceId) {
        return health.getOrDefault(instanceId, HealthStatus.UNKNOWN);
    }

    @Override
    public List<UpstreamInstance> healthyInstances(String serviceId) {
        return instances.get().stream()
                .filter(instance -> instance.serviceId().equals(serviceId))
                .filter(instance -> healthOf(instance.id()) != HealthStatus.UNHEALTHY)
                .toList();
    }

    @Override
    public void updateHealth(String instanceId, HealthStatus status) {
        Objects.requireNonNull(status, "status");
        boolean registered = instances.get().stream()
                .anyMatch(instance -> instance.id().equals(instanceId));
        if (registered) {
            health.put(instanceId, status);
        }
    }
}
