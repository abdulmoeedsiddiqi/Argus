package dev.argus.admin.api;

import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.LoadBalancingPolicy;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Set;

/**
 * A route as the Admin API receives and returns it. The limits are those of the SE431 report
 * (TC-07.2): timeout 100 to 60000 ms, retries 1 to 5, canary 0 to 100 percent.
 *
 * @param methods        accepted HTTP methods; empty or missing means every method
 * @param loadBalancing  missing means ROUND_ROBIN
 * @param retryAttempts  total attempts including the first; missing means 1 (no retry)
 * @param canaryVersion  version label that receives the canary share (with canaryPercent)
 * @param version        set by the server, raised by one on every update
 */
public record RouteDto(
        @NotBlank @Pattern(regexp = Ids.PATTERN, message = Ids.MESSAGE) String id,
        @NotBlank @Size(max = 255)
        @Pattern(regexp = "^/\\S*$", message = "must start with '/' and contain no spaces")
        String pathPrefix,
        Set<HttpMethod> methods,
        @NotBlank @Size(max = 64) String serviceId,
        LoadBalancingPolicy loadBalancing,
        @NotNull @Min(100) @Max(60000) Integer timeoutMs,
        @Min(1) @Max(5) Integer retryAttempts,
        @Size(max = 32) String canaryVersion,
        @Min(0) @Max(100) Integer canaryPercent,
        Long version) {

    /** Fills in the documented defaults for fields the client left out. */
    public RouteDto {
        methods = methods == null ? Set.of() : Set.copyOf(methods);
        loadBalancing = loadBalancing == null ? LoadBalancingPolicy.ROUND_ROBIN : loadBalancing;
        retryAttempts = retryAttempts == null ? Integer.valueOf(1) : retryAttempts;
    }
}
