package dev.argus.gateway.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.argus.contracts.ConfigChange;
import dev.argus.contracts.ConfigChange.EntityType;
import java.time.Instant;

/**
 * Turns the records of the configuration topic into {@link ConfigChange} objects and their
 * payloads into the shared payload records. Record key: {@code entityType:entityId}; record
 * value: the change as JSON, or nothing at all (a tombstone) when the entity was deleted.
 */
public final class ConfigChangeReader {

    // Unknown fields are ignored so that a newer Admin API can add fields without breaking
    // gateways that have not been updated yet.
    private final ObjectMapper json = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    /** Reads one topic record. Throws {@link IllegalArgumentException} if it cannot be read. */
    public ConfigChange fromRecord(String key, String value) {
        if (value == null) {
            return tombstone(key);
        }
        try {
            return json.readValue(value, ConfigChange.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("unreadable configuration record " + key, e);
        }
    }

    /** Reads the payload of a change as one of the payload records, for example RouteConfig. */
    public <T> T payload(ConfigChange change, Class<T> type) {
        try {
            return json.readValue(change.payloadJson(), type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("unreadable payload of " + change.key(), e);
        }
    }

    private static ConfigChange tombstone(String key) {
        int separator = key == null ? -1 : key.indexOf(':');
        if (separator < 1) {
            throw new IllegalArgumentException("configuration record key is not type:id: " + key);
        }
        EntityType type = EntityType.valueOf(key.substring(0, separator));
        return new ConfigChange(type, key.substring(separator + 1), 0, null, Instant.EPOCH);
    }
}
