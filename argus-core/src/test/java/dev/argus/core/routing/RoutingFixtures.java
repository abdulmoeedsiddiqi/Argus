package dev.argus.core.routing;

import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.UpstreamInstance;
import java.net.URI;
import java.time.Instant;
import java.util.Map;

/** Small builders shared by the routing and load-balancing tests. */
public final class RoutingFixtures {

    private RoutingFixtures() { }

    public static IncomingRequest request(HttpMethod method, String path) {
        return new IncomingRequest("req-1", method, path, Map.of(), null, "127.0.0.1",
                Instant.parse("2026-11-14T00:00:00Z"));
    }

    public static IncomingRequest get(String path) {
        return request(HttpMethod.GET, path);
    }

    public static UpstreamInstance instance(String id, String serviceId) {
        return instance(id, serviceId, 1, "v1");
    }

    public static UpstreamInstance instance(String id, String serviceId, int weight, String version) {
        return new UpstreamInstance(id, serviceId, URI.create("http://" + id + ":8081"), weight, version);
    }
}
