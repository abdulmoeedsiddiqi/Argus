package dev.argus.gateway.config;

import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.LoadBalancingPolicy;
import dev.argus.core.model.Route;
import dev.argus.core.model.UpstreamInstance;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Routes and backend instances read from configuration ({@code argus.routes},
 * {@code argus.upstreams}) when the gateway starts.
 *
 * <p>This is a stand-in until module M5 exists: then routes arrive from the Admin API through
 * the configuration topic and are applied with the same {@code replaceAll} calls.
 */
@ConfigurationProperties("argus")
public record StaticRoutingProperties(
        List<UpstreamEntry> upstreams,
        List<RouteEntry> routes,
        @DefaultValue HealthCheck healthCheck) {

    public StaticRoutingProperties {
        upstreams = upstreams == null ? List.of() : List.copyOf(upstreams);
        routes = routes == null ? List.of() : List.copyOf(routes);
    }

    public List<UpstreamInstance> toInstances() {
        return upstreams.stream().map(UpstreamEntry::toInstance).toList();
    }

    public List<Route> toRoutes() {
        return routes.stream().map(RouteEntry::toRoute).toList();
    }

    /** One backend copy, for example orders-a at http://orders-a:8081 with weight 3. */
    public record UpstreamEntry(
            String id, String service, URI url, @DefaultValue("1") int weight, String version) {

        UpstreamInstance toInstance() {
            return new UpstreamInstance(id, service, url, weight, version);
        }
    }

    /** One route. Validation happens in {@link Route.Builder#build()}. */
    public record RouteEntry(
            String id,
            String pathPrefix,
            String service,
            List<HttpMethod> methods,
            @DefaultValue("ROUND_ROBIN") LoadBalancingPolicy loadBalancing,
            @DefaultValue("5s") Duration timeout,
            String canaryVersion,
            @DefaultValue("0") int canaryPercent) {

        Route toRoute() {
            Route.Builder builder = Route.builder()
                    .id(id)
                    .pathPrefix(pathPrefix)
                    .service(service)
                    .loadBalancing(loadBalancing)
                    .timeout(timeout);
            if (methods != null) {
                builder.methods(methods.toArray(HttpMethod[]::new));
            }
            if (canaryVersion != null) {
                builder.canary(canaryVersion, canaryPercent);
            }
            return builder.build();
        }
    }

    /** Active health checking of the backend copies (FR-03). */
    public record HealthCheck(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("/health") String path,
            @DefaultValue("5s") Duration interval,
            @DefaultValue("2s") Duration timeout,
            @DefaultValue("3") int failureThreshold) { }
}
