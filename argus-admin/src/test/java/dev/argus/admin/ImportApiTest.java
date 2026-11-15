package dev.argus.admin;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The seed path: POST /admin/import takes one YAML document (deploy/seed/demo-config.yaml) and
 * applies it through the same services as the single endpoints, so everything is validated,
 * audited and published. Nothing is ever written to the database directly.
 */
class ImportApiTest extends AdminIntegrationTest {

    private static final String YAML = "application/yaml";

    @Test
    @DisplayName("seed: a YAML document creates plans, upstreams, routes and consumers, and issues keys")
    void importCreatesEverything() throws Exception {
        String suffix = id("x");
        long auditBefore = auditRows();

        AdminClient.Response response = admin.postRaw("/admin/import", YAML, document(suffix, 5000));

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.body().get("created").asInt()).isEqualTo(4);
        assertThat(response.body().get("updated").asInt()).isZero();
        assertThat(admin.get("/admin/plans/plan-" + suffix).status()).isEqualTo(200);
        assertThat(admin.get("/admin/upstreams/up-" + suffix).body().get("weight").asInt())
                .isEqualTo(3);
        assertThat(admin.get("/admin/routes/route-" + suffix).body().get("loadBalancing").asText())
                .isEqualTo("WEIGHTED");
        assertThat(admin.get("/admin/consumers/consumer-" + suffix).status()).isEqualTo(200);

        assertThat(response.body().get("issuedKeys")).hasSize(1);
        assertThat(response.body().get("issuedKeys").get(0).get("key").asText()).startsWith("ak_live_");
        assertThat(response.body().get("issuedKeys").get(0).get("consumerId").asText())
                .isEqualTo("consumer-" + suffix);

        // Same path as the single endpoints: audited (4 entities + 1 key) and published.
        assertThat(auditRows()).isEqualTo(auditBefore + 5);
        assertThat(configTopic().recordsFor("ROUTE:route-" + suffix)).hasSize(1);
        assertThat(configTopic().recordsFor("UPSTREAM:up-" + suffix)).hasSize(1);
    }

    @Test
    @DisplayName("seed: importing the same document again updates the entities and issues no second key")
    void importIsRepeatable() throws Exception {
        String suffix = id("x");
        admin.postRaw("/admin/import", YAML, document(suffix, 5000));

        AdminClient.Response again = admin.postRaw("/admin/import", YAML, document(suffix, 2500));

        assertThat(again.status()).isEqualTo(200);
        assertThat(again.body().get("created").asInt()).isZero();
        assertThat(again.body().get("updated").asInt()).isEqualTo(4);
        assertThat(again.body().get("issuedKeys")).isEmpty();
        assertThat(admin.get("/admin/routes/route-" + suffix).body().get("timeoutMs").asInt())
                .isEqualTo(2500);
        assertThat(admin.get("/admin/consumers/consumer-" + suffix + "/keys").body()).hasSize(1);
    }

    @Test
    @DisplayName("seed: one invalid entry rejects the whole document; nothing is stored or published")
    void invalidDocumentChangesNothing() throws Exception {
        String suffix = id("x");

        AdminClient.Response response = admin.postRaw("/admin/import", YAML, document(suffix, 50));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("reason").asText()).isEqualTo("INVALID_CONFIGURATION");
        assertThat(response.body().get("message").asText()).contains("routes[0]").contains("timeoutMs");
        assertThat(admin.get("/admin/plans/plan-" + suffix).status()).isEqualTo(404);
        assertThat(configTopic().recordsFor("PLAN:plan-" + suffix)).isEmpty();
    }

    @Test
    @DisplayName("seed: text that is not YAML, unknown fields and unknown plans are rejected with 400")
    void malformedDocumentsAreRejected() throws Exception {
        String suffix = id("x");
        String unknownField = "plans:\n  - { id: plan-" + suffix + ", nmae: Free }\n";
        String unknownPlan = "consumers:\n  - { id: consumer-" + suffix
                + ", name: Shop, planId: no-such-plan }\n";

        assertThat(admin.postRaw("/admin/import", YAML, "plans: [ {").status()).isEqualTo(400);
        assertThat(admin.postRaw("/admin/import", YAML, unknownField).status()).isEqualTo(400);
        assertThat(admin.postRaw("/admin/import", YAML, unknownPlan).status()).isEqualTo(400);
    }

    @Test
    @DisplayName("seed: the import needs an admin token like every other change")
    void importNeedsAToken() throws Exception {
        AdminClient.Response response = AdminClient.anonymous(port)
                .postRaw("/admin/import", YAML, document(id("x"), 5000));

        assertThat(response.status()).isEqualTo(401);
    }

    /** A small document in the format of deploy/seed/demo-config.yaml. */
    private static String document(String suffix, int routeTimeoutMs) {
        return String.join("\n",
                "plans:",
                "  - { id: plan-" + suffix + ", name: Free, rateCapacity: 60, ratePeriodMs: 60000,"
                        + " monthlyQuota: 10000 }",
                "upstreams:",
                "  - { id: up-" + suffix + ", serviceId: svc-" + suffix
                        + ", baseUri: \"http://orders-a:8081\", weight: 3 }",
                "routes:",
                "  - id: route-" + suffix,
                "    pathPrefix: /route-" + suffix,
                "    serviceId: svc-" + suffix,
                "    loadBalancing: WEIGHTED",
                "    timeoutMs: " + routeTimeoutMs,
                "consumers:",
                "  - { id: consumer-" + suffix + ", name: Shop App, planId: plan-" + suffix
                        + ", issueKey: true }",
                "");
    }
}
