package dev.argus.core.routing;

import dev.argus.core.model.HealthStatus;
import dev.argus.core.model.UpstreamInstance;
import java.util.List;

/** Knows the instances of each service and which of them are currently healthy. */
public interface UpstreamRegistry {

    List<UpstreamInstance> healthyInstances(String serviceId);

    void updateHealth(String instanceId, HealthStatus status);
}
