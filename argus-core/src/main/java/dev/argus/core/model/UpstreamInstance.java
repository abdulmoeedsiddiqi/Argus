package dev.argus.core.model;

import java.net.URI;
import java.util.Objects;

/** One running replica of an upstream service. */
public record UpstreamInstance(String id, String serviceId, URI baseUri, int weight, String version) {

    public UpstreamInstance {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(baseUri, "baseUri");
        if (weight < 1) {
            throw new IllegalArgumentException("weight must be >= 1");
        }
    }
}
