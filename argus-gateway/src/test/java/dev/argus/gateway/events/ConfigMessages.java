package dev.argus.gateway.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.argus.contracts.ApiKeyConfig;
import dev.argus.contracts.ConfigChange;
import dev.argus.contracts.ConfigChange.EntityType;
import dev.argus.contracts.RouteConfig;
import dev.argus.contracts.UpstreamConfig;
import java.time.Instant;
import java.util.List;

/** Test helper: builds the configuration changes that the Admin API would publish. */
public final class ConfigMessages {

    private static final ObjectMapper JSON = new ObjectMapper().registerModule(new JavaTimeModule());
    private static final Instant CHANGED_AT = Instant.parse("2026-11-15T10:00:00Z");

    private ConfigMessages() { }

    public static ConfigChange route(String id, String pathPrefix, String serviceId) {
        return change(EntityType.ROUTE, id, new RouteConfig(
                id, pathPrefix, List.of(), serviceId, "ROUND_ROBIN", 5000, 1, null, null, 1));
    }

    public static ConfigChange upstream(String id, String serviceId, String baseUri) {
        return change(EntityType.UPSTREAM, id, new UpstreamConfig(id, serviceId, baseUri, 1, "v1"));
    }

    public static ConfigChange apiKey(String id, String keyHash, boolean revoked) {
        return change(EntityType.API_KEY, id, new ApiKeyConfig(
                id, keyHash, "ak_test_xxxx", "shop-app", "free", revoked, null));
    }

    /** A tombstone: the entity was deleted. */
    public static ConfigChange deleted(EntityType type, String id) {
        return new ConfigChange(type, id, 2, null, CHANGED_AT);
    }

    public static ConfigChange change(EntityType type, String id, Object payload) {
        return new ConfigChange(type, id, 1, toJson(payload), CHANGED_AT);
    }

    /** The Kafka record value of a change, exactly as the Admin API writes it. */
    public static String toJson(Object value) {
        try {
            return JSON.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
