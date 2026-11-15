package dev.argus.admin.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * One backend copy of a service as the Admin API receives and returns it.
 *
 * @param baseUri  where the copy listens, for example http://orders-a:8081
 * @param weight   share of traffic under weighted balancing; missing means 1
 * @param version  version label used by canary routing; missing means v1
 */
public record UpstreamDto(
        @NotBlank @Pattern(regexp = Ids.PATTERN, message = Ids.MESSAGE) String id,
        @NotBlank @Size(max = 64) String serviceId,
        @NotBlank @Size(max = 255)
        @Pattern(regexp = "^https?://[^\\s/?#]+\\S*$", message = "must be an http or https URL with a host")
        String baseUri,
        @Min(1) Integer weight,
        @Size(max = 32) String version) {

    /** Fills in the documented defaults for fields the client left out. */
    public UpstreamDto {
        weight = weight == null ? Integer.valueOf(1) : weight;
        version = version == null || version.isBlank() ? "v1" : version;
    }
}
