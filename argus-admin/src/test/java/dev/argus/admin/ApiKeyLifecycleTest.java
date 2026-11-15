package dev.argus.admin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.argus.core.auth.ApiKeyHasher;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-07 / QR-11: API key lifecycle (issue, rotate, revoke). The full key is returned exactly
 * once, when it is issued; the database holds only its SHA-256 hash and a display prefix.
 */
class ApiKeyLifecycleTest extends AdminIntegrationTest {

    private String consumerId;

    @BeforeEach
    void createConsumer() throws Exception {
        String planId = id("plan");
        consumerId = id("consumer");
        admin.post("/admin/plans", TestData.plan(planId));
        admin.post("/admin/consumers", TestData.consumer(consumerId, planId));
    }

    @Test
    @DisplayName("FR-07: issuing a key returns the full key once, with its id and display prefix")
    void issueReturnsTheFullKeyOnce() throws Exception {
        AdminClient.Response issued = issue();

        assertThat(issued.status()).isEqualTo(201);
        String key = issued.body().get("key").asText();
        assertThat(key).startsWith("ak_live_").hasSizeGreaterThanOrEqualTo(40);
        assertThat(issued.body().get("prefix").asText()).isEqualTo(key.substring(0, 12));
        assertThat(issued.body().get("consumerId").asText()).isEqualTo(consumerId);
        assertThat(issued.body().get("revoked").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("QR-11: the database stores the SHA-256 hash and the prefix, never the key")
    void onlyTheHashIsStored() throws Exception {
        AdminClient.Response issued = issue();
        String key = issued.body().get("key").asText();
        String keyId = issued.body().get("id").asText();

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM api_keys WHERE id = ?", keyId);

        assertThat(row.get("key_hash")).isEqualTo(new ApiKeyHasher().hash(key));
        assertThat(row.get("key_prefix")).isEqualTo(key.substring(0, 12));
        assertThat(row.toString()).doesNotContain(key);
    }

    @Test
    @DisplayName("QR-11: reading or listing keys afterwards never shows the key or its hash")
    void laterReadsNeverShowTheKey() throws Exception {
        AdminClient.Response issued = issue();
        String key = issued.body().get("key").asText();
        String keyId = issued.body().get("id").asText();
        String hash = new ApiKeyHasher().hash(key);

        AdminClient.Response single = admin.get("/admin/keys/" + keyId);
        AdminClient.Response list = admin.get("/admin/keys");
        AdminClient.Response ofConsumer = admin.get("/admin/consumers/" + consumerId + "/keys");

        assertThat(single.status()).isEqualTo(200);
        assertThat(single.body().has("key")).isFalse();
        assertThat(single.body().get("prefix").asText()).isEqualTo(key.substring(0, 12));
        for (AdminClient.Response response : new AdminClient.Response[] {single, list, ofConsumer}) {
            assertThat(response.text()).contains(keyId).doesNotContain(key).doesNotContain(hash);
        }
    }

    @Test
    @DisplayName("FR-07: two issued keys are different, random values")
    void keysAreUnique() throws Exception {
        assertThat(issue().body().get("key").asText())
                .isNotEqualTo(issue().body().get("key").asText());
    }

    @Test
    @DisplayName("FR-07: revoking a key marks it revoked; revoking it again changes nothing")
    void revoke() throws Exception {
        String keyId = issue().body().get("id").asText();

        AdminClient.Response revoked = admin.post("/admin/keys/" + keyId + "/revoke");

        assertThat(revoked.status()).isEqualTo(200);
        assertThat(revoked.body().get("revoked").asBoolean()).isTrue();
        assertThat(jdbc.queryForObject(
                "SELECT revoked FROM api_keys WHERE id = ?", Boolean.class, keyId)).isTrue();
        assertThat(admin.post("/admin/keys/" + keyId + "/revoke").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("FR-07: rotating a key returns a new full key and revokes the old one")
    void rotate() throws Exception {
        AdminClient.Response old = issue();
        String oldId = old.body().get("id").asText();

        AdminClient.Response rotated = admin.post("/admin/keys/" + oldId + "/rotate");

        assertThat(rotated.status()).isEqualTo(201);
        assertThat(rotated.body().get("id").asText()).isNotEqualTo(oldId);
        assertThat(rotated.body().get("key").asText())
                .startsWith("ak_live_").isNotEqualTo(old.body().get("key").asText());
        assertThat(rotated.body().get("consumerId").asText()).isEqualTo(consumerId);
        assertThat(admin.get("/admin/keys/" + oldId).body().get("revoked").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("FR-07: a revoked key cannot be rotated (409)")
    void revokedKeyCannotBeRotated() throws Exception {
        String keyId = issue().body().get("id").asText();
        admin.post("/admin/keys/" + keyId + "/revoke");

        assertThat(admin.post("/admin/keys/" + keyId + "/rotate").status()).isEqualTo(409);
    }

    @Test
    @DisplayName("FR-07: an expiry time can be set when a key is issued; a past time is rejected")
    void expiry() throws Exception {
        AdminClient.Response future = admin.post("/admin/consumers/" + consumerId + "/keys",
                Map.of("expiresAt", "2099-01-01T00:00:00Z"));
        AdminClient.Response past = admin.post("/admin/consumers/" + consumerId + "/keys",
                Map.of("expiresAt", "2020-01-01T00:00:00Z"));

        assertThat(future.status()).isEqualTo(201);
        assertThat(future.body().get("expiresAt").asText()).startsWith("2099-01-01");
        assertThat(past.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("FR-07: unknown consumers and unknown keys give 404")
    void unknownIds() throws Exception {
        assertThat(admin.post("/admin/consumers/" + id("consumer") + "/keys").status())
                .isEqualTo(404);
        assertThat(admin.post("/admin/keys/" + id("key") + "/revoke").status()).isEqualTo(404);
        assertThat(admin.post("/admin/keys/" + id("key") + "/rotate").status()).isEqualTo(404);
        assertThat(admin.get("/admin/keys/" + id("key")).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("FR-07: a consumer that has keys cannot be deleted (409)")
    void consumerWithKeysCannotBeDeleted() throws Exception {
        issue();

        assertThat(admin.delete("/admin/consumers/" + consumerId).status()).isEqualTo(409);
    }

    private AdminClient.Response issue() throws Exception {
        return admin.post("/admin/consumers/" + consumerId + "/keys");
    }
}
