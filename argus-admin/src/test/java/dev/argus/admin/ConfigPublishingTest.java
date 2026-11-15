package dev.argus.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import dev.argus.admin.ConfigTopicReader.Published;
import dev.argus.contracts.ConfigChange;
import dev.argus.contracts.ConfigChange.EntityType;
import dev.argus.core.auth.ApiKeyHasher;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-12 on the admin side, against a real Kafka broker: every committed change is published to
 * the compacted configuration topic, keyed by entity. The test reads the topic the way a
 * gateway does.
 */
class ConfigPublishingTest extends AdminIntegrationTest {

    @Test
    @DisplayName("FR-12: the configuration topic is compacted and has one partition (total order)")
    void topicIsCompacted() throws Exception {
        assertThat(configTopic().cleanupPolicy()).isEqualTo("compact");
        assertThat(configTopic().partitions()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-12: a created route is published under the key ROUTE:<id> with its settings")
    void createdRouteIsPublished() throws Exception {
        String routeId = id("route");
        Map<String, Object> body = TestData.route(routeId);
        body.put("methods", List.of("GET"));
        body.put("loadBalancing", "WEIGHTED");
        body.put("timeoutMs", 2500);
        body.put("retryAttempts", 3);
        body.put("canaryVersion", "v2");
        body.put("canaryPercent", 10);

        admin.post("/admin/routes", body);

        Published published = configTopic().latest("ROUTE:" + routeId);
        ConfigChange change = published.change();
        assertThat(change.entityType()).isEqualTo(EntityType.ROUTE);
        assertThat(change.entityId()).isEqualTo(routeId);
        assertThat(change.changedAt()).isNotNull();
        JsonNode payload = published.payload();
        assertThat(payload.get("pathPrefix").asText()).isEqualTo("/" + routeId);
        assertThat(payload.get("methods").get(0).asText()).isEqualTo("GET");
        assertThat(payload.get("serviceId").asText()).isEqualTo("orders");
        assertThat(payload.get("loadBalancing").asText()).isEqualTo("WEIGHTED");
        assertThat(payload.get("timeoutMs").asInt()).isEqualTo(2500);
        assertThat(payload.get("retryAttempts").asInt()).isEqualTo(3);
        assertThat(payload.get("canaryVersion").asText()).isEqualTo("v2");
        assertThat(payload.get("canaryPercent").asInt()).isEqualTo(10);
        assertThat(payload.get("version").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-12: an update is published with a higher version; a delete as a tombstone")
    void updateAndDeleteArePublished() throws Exception {
        String routeId = id("route");
        admin.post("/admin/routes", TestData.route(routeId));
        admin.put("/admin/routes/" + routeId,
                TestData.with(TestData.route(routeId), "timeoutMs", 900));
        admin.delete("/admin/routes/" + routeId);

        List<Published> records = configTopic().recordsFor("ROUTE:" + routeId);

        assertThat(records).hasSize(3);
        assertThat(records.get(1).payload().get("timeoutMs").asInt()).isEqualTo(900);
        assertThat(records.get(1).change().version())
                .isGreaterThan(records.get(0).change().version());
        assertThat(records.get(2).isTombstone()).isTrue();
    }

    @Test
    @DisplayName("FR-12: upstream instances and plans are published and deleted the same way")
    void upstreamsAndPlansArePublished() throws Exception {
        String upstreamId = id("upstream");
        String planId = id("plan");
        admin.post("/admin/upstreams", TestData.upstream(upstreamId));
        admin.post("/admin/plans", TestData.plan(planId));
        admin.delete("/admin/upstreams/" + upstreamId);
        admin.delete("/admin/plans/" + planId);

        List<Published> upstream = configTopic().recordsFor("UPSTREAM:" + upstreamId);
        List<Published> plan = configTopic().recordsFor("PLAN:" + planId);

        assertThat(upstream).hasSize(2);
        assertThat(upstream.get(0).payload().get("baseUri").asText()).isEqualTo("http://orders-a:8081");
        assertThat(upstream.get(0).payload().get("weight").asInt()).isEqualTo(1);
        assertThat(upstream.get(1).isTombstone()).isTrue();
        assertThat(plan).hasSize(2);
        assertThat(plan.get(0).payload().get("rateCapacity").asInt()).isEqualTo(60);
        assertThat(plan.get(1).isTombstone()).isTrue();
    }

    @Test
    @DisplayName("FR-12 / QR-11: an issued key is published as its hash with consumer and plan, never the key")
    void issuedKeyIsPublishedAsHash() throws Exception {
        String planId = id("plan");
        String consumerId = id("consumer");
        admin.post("/admin/plans", TestData.plan(planId));
        admin.post("/admin/consumers", TestData.consumer(consumerId, planId));

        AdminClient.Response issued = admin.post("/admin/consumers/" + consumerId + "/keys");
        String key = issued.body().get("key").asText();
        String keyId = issued.body().get("id").asText();

        Published published = configTopic().latest("API_KEY:" + keyId);
        JsonNode payload = published.payload();
        assertThat(payload.get("keyHash").asText()).isEqualTo(new ApiKeyHasher().hash(key));
        assertThat(payload.get("displayPrefix").asText()).isEqualTo(key.substring(0, 12));
        assertThat(payload.get("consumerId").asText()).isEqualTo(consumerId);
        assertThat(payload.get("planId").asText()).isEqualTo(planId);
        assertThat(payload.get("revoked").asBoolean()).isFalse();
        assertThat(published.value()).doesNotContain(key);
    }

    @Test
    @DisplayName("QR-12: revoking a key publishes it again as revoked")
    void revocationIsPublished() throws Exception {
        String keyId = issueKeyForNewConsumer();

        admin.post("/admin/keys/" + keyId + "/revoke");

        List<Published> records = configTopic().recordsFor("API_KEY:" + keyId);
        assertThat(records).hasSize(2);
        assertThat(records.get(1).payload().get("revoked").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("FR-12: rotating publishes the new key and the old key as revoked")
    void rotationIsPublished() throws Exception {
        String oldId = issueKeyForNewConsumer();

        String newId = admin.post("/admin/keys/" + oldId + "/rotate").body().get("id").asText();

        assertThat(configTopic().latest("API_KEY:" + oldId).payload().get("revoked").asBoolean())
                .isTrue();
        assertThat(configTopic().latest("API_KEY:" + newId).payload().get("revoked").asBoolean())
                .isFalse();
    }

    @Test
    @DisplayName("FR-12: moving a consumer to another plan publishes its keys with the new plan")
    void planChangeRepublishesKeys() throws Exception {
        String freePlan = id("plan");
        String proPlan = id("plan");
        String consumerId = id("consumer");
        admin.post("/admin/plans", TestData.plan(freePlan));
        admin.post("/admin/plans", TestData.plan(proPlan));
        admin.post("/admin/consumers", TestData.consumer(consumerId, freePlan));
        String keyId = admin.post("/admin/consumers/" + consumerId + "/keys")
                .body().get("id").asText();

        admin.put("/admin/consumers/" + consumerId, TestData.consumer(consumerId, proPlan));

        assertThat(configTopic().latest("API_KEY:" + keyId).payload().get("planId").asText())
                .isEqualTo(proPlan);
    }

    @Test
    @DisplayName("FR-12: a rejected request publishes nothing")
    void rejectedRequestsPublishNothing() throws Exception {
        String routeId = id("route");
        String invalidId = id("route");
        admin.post("/admin/routes", TestData.route(routeId));

        admin.post("/admin/routes", TestData.route(routeId));                               // 409
        admin.post("/admin/routes", TestData.with(TestData.route(invalidId), "timeoutMs", 1)); // 400

        assertThat(configTopic().recordsFor("ROUTE:" + routeId)).hasSize(1);
        assertThat(configTopic().recordsFor("ROUTE:" + invalidId)).isEmpty();
    }

    @Test
    @DisplayName("FR-12: POST /admin/config/republish publishes the current state of every entity again")
    void republishSendsTheCurrentState() throws Exception {
        String routeId = id("route");
        String keyId = issueKeyForNewConsumer();
        admin.post("/admin/routes", TestData.route(routeId));

        AdminClient.Response response = admin.post("/admin/config/republish");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("published").asInt()).isGreaterThanOrEqualTo(2);
        assertThat(configTopic().recordsFor("ROUTE:" + routeId)).hasSize(2);
        assertThat(configTopic().recordsFor("API_KEY:" + keyId)).hasSize(2);
    }

    private String issueKeyForNewConsumer() throws Exception {
        String planId = id("plan");
        String consumerId = id("consumer");
        admin.post("/admin/plans", TestData.plan(planId));
        admin.post("/admin/consumers", TestData.consumer(consumerId, planId));
        return admin.post("/admin/consumers/" + consumerId + "/keys").body().get("id").asText();
    }
}
