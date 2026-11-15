package dev.argus.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-07: create, read, update and delete routes and upstream instances. */
class RouteAndUpstreamApiTest extends AdminIntegrationTest {

    @Test
    @DisplayName("TC-07.1: a route can be created, read, updated and deleted")
    void routeCrud() throws Exception {
        String routeId = id("route");
        Map<String, Object> body = TestData.route(routeId);
        body.put("methods", List.of("GET", "POST"));
        body.put("loadBalancing", "WEIGHTED");
        body.put("canaryVersion", "v2");
        body.put("canaryPercent", 10);

        AdminClient.Response created = admin.post("/admin/routes", body);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("version").asLong()).isEqualTo(1);

        AdminClient.Response read = admin.get("/admin/routes/" + routeId);
        assertThat(read.status()).isEqualTo(200);
        assertThat(read.body().get("pathPrefix").asText()).isEqualTo("/" + routeId);
        assertThat(read.body().get("methods")).extracting(node -> node.asText())
                .containsExactlyInAnyOrder("GET", "POST");
        assertThat(read.body().get("loadBalancing").asText()).isEqualTo("WEIGHTED");
        assertThat(read.body().get("canaryPercent").asInt()).isEqualTo(10);
        assertThat(admin.get("/admin/routes").text()).contains(routeId);

        AdminClient.Response updated =
                admin.put("/admin/routes/" + routeId, TestData.with(body, "timeoutMs", 2500));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(updated.body().get("timeoutMs").asInt()).isEqualTo(2500);
        assertThat(updated.body().get("version").asLong()).as("each update raises the version")
                .isEqualTo(2);

        assertThat(admin.delete("/admin/routes/" + routeId).status()).isEqualTo(204);
        assertThat(admin.get("/admin/routes/" + routeId).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("TC-07.1: an upstream instance can be created, read, updated and deleted")
    void upstreamCrud() throws Exception {
        String upstreamId = id("upstream");

        AdminClient.Response created = admin.post("/admin/upstreams", TestData.upstream(upstreamId));
        assertThat(created.status()).isEqualTo(201);

        AdminClient.Response read = admin.get("/admin/upstreams/" + upstreamId);
        assertThat(read.body().get("baseUri").asText()).isEqualTo("http://orders-a:8081");
        assertThat(read.body().get("weight").asInt()).isEqualTo(1);
        assertThat(admin.get("/admin/upstreams").text()).contains(upstreamId);

        AdminClient.Response updated = admin.put("/admin/upstreams/" + upstreamId,
                TestData.with(TestData.upstream(upstreamId), "weight", 3));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(admin.get("/admin/upstreams/" + upstreamId).body().get("weight").asInt())
                .isEqualTo(3);

        assertThat(admin.delete("/admin/upstreams/" + upstreamId).status()).isEqualTo(204);
        assertThat(admin.get("/admin/upstreams/" + upstreamId).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("FR-07: optional route fields get defaults: all methods, round robin, one attempt")
    void routeDefaults() throws Exception {
        String routeId = id("route");
        Map<String, Object> minimal = Map.of(
                "id", routeId, "pathPrefix", "/" + routeId, "serviceId", "orders", "timeoutMs", 5000);

        AdminClient.Response created = admin.post("/admin/routes", minimal);

        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("methods")).isEmpty();
        assertThat(created.body().get("loadBalancing").asText()).isEqualTo("ROUND_ROBIN");
        assertThat(created.body().get("retryAttempts").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("FR-07: two routes cannot share a path prefix (409)")
    void duplicatePathPrefixIsAConflict() throws Exception {
        String first = id("route");
        admin.post("/admin/routes", TestData.route(first));

        AdminClient.Response second = admin.post("/admin/routes",
                TestData.with(TestData.route(id("route")), "pathPrefix", "/" + first));

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body().get("reason").asText()).isEqualTo("CONFLICT");
    }

    @Test
    @DisplayName("FR-07: unknown ids give 404 and duplicate ids give 409, for routes and upstreams")
    void unknownAndDuplicateIds() throws Exception {
        String routeId = id("route");
        String upstreamId = id("upstream");
        admin.post("/admin/routes", TestData.route(routeId));
        admin.post("/admin/upstreams", TestData.upstream(upstreamId));

        assertThat(admin.post("/admin/routes", TestData.route(routeId)).status()).isEqualTo(409);
        assertThat(admin.post("/admin/upstreams", TestData.upstream(upstreamId)).status())
                .isEqualTo(409);
        assertThat(admin.get("/admin/routes/" + id("route")).status()).isEqualTo(404);
        assertThat(admin.delete("/admin/upstreams/" + id("upstream")).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("QR-13: route and upstream changes are audited")
    void changesAreAudited() throws Exception {
        long before = auditRows();
        String routeId = id("route");
        String upstreamId = id("upstream");

        admin.post("/admin/routes", TestData.route(routeId));
        admin.post("/admin/upstreams", TestData.upstream(upstreamId));
        admin.delete("/admin/routes/" + routeId);
        admin.delete("/admin/upstreams/" + upstreamId);

        assertThat(auditRows()).isEqualTo(before + 4);
        assertThat(jdbc.queryForList(
                "SELECT action || ' ' || entity_type FROM audit_log WHERE entity_id IN (?, ?) ORDER BY id",
                String.class, routeId, upstreamId))
                .containsExactly("CREATE ROUTE", "CREATE UPSTREAM", "DELETE ROUTE", "DELETE UPSTREAM");
    }
}
