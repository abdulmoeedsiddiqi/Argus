package dev.argus.gateway.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.contracts.ApiKeyConfig;
import dev.argus.contracts.ConfigChange;
import dev.argus.contracts.ConfigChange.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Reading the records of the configuration topic (key = entityType:entityId, value = JSON). */
class ConfigChangeReaderTest {

    private final ConfigChangeReader reader = new ConfigChangeReader();

    @Test
    @DisplayName("FR-12: a record value is read back as the ConfigChange that was written")
    void readsAChange() {
        ConfigChange written = ConfigMessages.apiKey("key-1", "hash-1", true);

        ConfigChange read = reader.fromRecord(written.key(), ConfigMessages.toJson(written));

        assertThat(read).isEqualTo(written);
        ApiKeyConfig payload = reader.payload(read, ApiKeyConfig.class);
        assertThat(payload.keyHash()).isEqualTo("hash-1");
        assertThat(payload.revoked()).isTrue();
    }

    @Test
    @DisplayName("FR-12: a record without a value (tombstone) is a deletion of the entity in its key")
    void nullValueIsADeletion() {
        ConfigChange read = reader.fromRecord("ROUTE:orders", null);

        assertThat(read.isDeletion()).isTrue();
        assertThat(read.entityType()).isEqualTo(EntityType.ROUTE);
        assertThat(read.entityId()).isEqualTo("orders");
    }

    @Test
    @DisplayName("an entity id may itself contain a colon")
    void entityIdMayContainAColon() {
        ConfigChange read = reader.fromRecord("UPSTREAM:orders:a", null);

        assertThat(read.entityId()).isEqualTo("orders:a");
    }

    @Test
    @DisplayName("fields added by a newer Admin API are ignored, not an error")
    void unknownFieldsAreIgnored() {
        String value = "{\"entityType\":\"PLAN\",\"entityId\":\"free\",\"version\":3,"
                + "\"payloadJson\":\"{}\",\"changedAt\":\"2026-11-15T10:00:00Z\",\"future\":1}";

        assertThat(reader.fromRecord("PLAN:free", value).version()).isEqualTo(3);
    }

    @Test
    @DisplayName("an unreadable record is rejected with an exception that names its key")
    void unreadableRecordIsRejected() {
        assertThatThrownBy(() -> reader.fromRecord("ROUTE:orders", "{not json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ROUTE:orders");
        assertThatThrownBy(() -> reader.fromRecord("no-colon", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
