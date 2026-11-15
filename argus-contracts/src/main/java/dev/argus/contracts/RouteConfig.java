package dev.argus.contracts;

import java.util.List;

/**
 * Payload of a {@link ConfigChange} of type ROUTE: one route as the Admin API stores it.
 * Plain data only; the gateway turns it into its own validated {@code Route}.
 *
 * @param methods        HTTP method names; empty means every method
 * @param loadBalancing  ROUND_ROBIN, WEIGHTED or LEAST_CONNECTIONS
 * @param canaryVersion  version label that receives the canary share, or null for no canary
 * @param canaryPercent  0 to 100, or null for no canary
 */
public record RouteConfig(
        String id,
        String pathPrefix,
        List<String> methods,
        String serviceId,
        String loadBalancing,
        int timeoutMs,
        int retryAttempts,
        String canaryVersion,
        Integer canaryPercent,
        long version) {

    public RouteConfig {
        methods = methods == null ? List.of() : List.copyOf(methods);
    }
}
