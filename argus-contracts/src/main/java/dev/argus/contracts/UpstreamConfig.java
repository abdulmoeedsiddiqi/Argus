package dev.argus.contracts;

/** Payload of a {@link ConfigChange} of type UPSTREAM: one backend copy of a service. */
public record UpstreamConfig(String id, String serviceId, String baseUri, int weight, String version) { }
