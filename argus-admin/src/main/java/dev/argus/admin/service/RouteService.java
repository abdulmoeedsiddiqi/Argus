package dev.argus.admin.service;

import dev.argus.admin.api.RouteDto;
import dev.argus.admin.persistence.RouteEntity;
import dev.argus.admin.persistence.RouteRepository;
import dev.argus.core.exception.InvalidConfigurationException;
import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.LoadBalancingPolicy;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Routes: the rules behind create, read, update and delete. One transaction per change. */
@Service
public class RouteService {

    static final String ENTITY = "ROUTE";

    private final RouteRepository routes;
    private final AuditRecorder audit;

    public RouteService(RouteRepository routes, AuditRecorder audit) {
        this.routes = routes;
        this.audit = audit;
    }

    @Transactional
    public RouteDto create(RouteDto request, String actor) {
        if (routes.existsById(request.id())) {
            throw new ResourceConflictException("route " + request.id() + " already exists");
        }
        RouteEntity route = new RouteEntity(request.id());
        apply(request, route);
        routes.save(route);
        audit.record(actor, "CREATE", ENTITY, route.getId());
        return toDto(route);
    }

    @Transactional
    public RouteDto update(String id, RouteDto request, String actor) {
        RouteEntity route = find(id);
        apply(request, route);
        audit.record(actor, "UPDATE", ENTITY, id);
        return toDto(route);
    }

    @Transactional
    public void delete(String id, String actor) {
        routes.delete(find(id));
        audit.record(actor, "DELETE", ENTITY, id);
    }

    @Transactional(readOnly = true)
    public RouteDto get(String id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public List<RouteDto> list() {
        return routes.findAll().stream().map(RouteService::toDto).toList();
    }

    private RouteEntity find(String id) {
        return routes.findById(id).orElseThrow(() -> new ResourceNotFoundException("route", id));
    }

    /** Checks the rules that span several fields or rows, then copies the request. */
    private void apply(RouteDto request, RouteEntity route) {
        if ((request.canaryVersion() == null) != (request.canaryPercent() == null)) {
            throw new InvalidConfigurationException(
                    "canaryVersion and canaryPercent must be given together");
        }
        if (routes.existsByPathPrefixAndIdNot(request.pathPrefix(), request.id())) {
            // The gateway picks a route by its path prefix, so a prefix can belong to one route.
            throw new ResourceConflictException(
                    "another route already uses the path prefix " + request.pathPrefix());
        }
        String methods = request.methods().stream()
                .map(HttpMethod::name).sorted().collect(Collectors.joining(","));
        route.update(
                request.pathPrefix(), methods, request.serviceId(), request.loadBalancing().name(),
                request.timeoutMs(), request.retryAttempts(),
                request.canaryVersion(), request.canaryPercent());
    }

    static RouteDto toDto(RouteEntity route) {
        Set<HttpMethod> methods = route.getMethods().isBlank()
                ? Set.of()
                : Arrays.stream(route.getMethods().split(","))
                        .map(HttpMethod::valueOf).collect(Collectors.toSet());
        return new RouteDto(
                route.getId(), route.getPathPrefix(), methods, route.getServiceId(),
                LoadBalancingPolicy.valueOf(route.getLbPolicy()), route.getTimeoutMs(),
                route.getRetryAttempts(), route.getCanaryVersion(), route.getCanaryPercent(),
                route.getVersion());
    }
}
