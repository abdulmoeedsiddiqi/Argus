package dev.argus.core.routing;

import dev.argus.core.model.UpstreamInstance;

/**
 * Port: asks one upstream instance whether it is able to serve requests. The gateway module
 * provides an HTTP implementation; tests use a lambda.
 */
@FunctionalInterface
public interface HealthProbe {

    /** Returns true if the instance answered the check successfully. */
    boolean isHealthy(UpstreamInstance instance);
}
