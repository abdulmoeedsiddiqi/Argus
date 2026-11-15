package dev.argus.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * TC-07.2: boundary-value tests of the configuration limits. Each limit is tried just outside
 * (rejected with 400 and the field name) and exactly on the boundary (accepted).
 */
class ValidationLimitsTest extends AdminIntegrationTest {

    @ParameterizedTest(name = "route {0} = {1} gives {2}")
    @CsvSource({
            "timeoutMs,        99, 400",
            "timeoutMs,       100, 201",
            "timeoutMs,     60000, 201",
            "timeoutMs,     60001, 400",
            "retryAttempts,     0, 400",
            "retryAttempts,     1, 201",
            "retryAttempts,     5, 201",
            "retryAttempts,     6, 400",
            "canaryPercent,    -1, 400",
            "canaryPercent,     0, 201",
            "canaryPercent,   100, 201",
            "canaryPercent,   101, 400"})
    @DisplayName("TC-07.2: route limits: timeout 100..60000 ms, retries 1..5, canary 0..100 %")
    void routeLimits(String field, int value, int expectedStatus) throws Exception {
        Map<String, Object> body = TestData.route(id("route"));
        body.put("canaryVersion", "v2");
        body.put("canaryPercent", 10);
        body.put(field, value);

        AdminClient.Response response = admin.post("/admin/routes", body);

        assertThat(response.status()).isEqualTo(expectedStatus);
        if (expectedStatus == 400) {
            assertThat(response.body().get("reason").asText()).isEqualTo("INVALID_CONFIGURATION");
            assertThat(response.body().get("fields").has(field)).isTrue();
        }
    }

    @ParameterizedTest(name = "upstream weight = {0} gives {1}")
    @CsvSource({"0, 400", "1, 201", "2, 201"})
    @DisplayName("TC-07.2: upstream weight must be at least 1")
    void upstreamWeightLimit(int weight, int expectedStatus) throws Exception {
        AdminClient.Response response = admin.post("/admin/upstreams",
                TestData.with(TestData.upstream(id("upstream")), "weight", weight));

        assertThat(response.status()).isEqualTo(expectedStatus);
        if (expectedStatus == 400) {
            assertThat(response.body().get("fields").has("weight")).isTrue();
        }
    }

    @ParameterizedTest(name = "plan {0} = {1} gives {2}")
    @CsvSource({
            "rateCapacity, 0, 400", "rateCapacity, 1, 201",
            "ratePeriodMs, 0, 400", "ratePeriodMs, 1, 201",
            "monthlyQuota, 0, 400", "monthlyQuota, 1, 201"})
    @DisplayName("TC-07.2: every plan limit must be positive")
    void planLimits(String field, int value, int expectedStatus) throws Exception {
        AdminClient.Response response = admin.post("/admin/plans",
                TestData.with(TestData.plan(id("plan")), field, value));

        assertThat(response.status()).isEqualTo(expectedStatus);
    }

    @Test
    @DisplayName("FR-07: a path prefix must start with '/', and unknown enum values are rejected")
    void routeFormatRules() throws Exception {
        Map<String, Object> route = TestData.route(id("route"));

        AdminClient.Response badPrefix =
                admin.post("/admin/routes", TestData.with(route, "pathPrefix", "orders"));
        AdminClient.Response badPolicy =
                admin.post("/admin/routes", TestData.with(route, "loadBalancing", "RANDOM"));
        AdminClient.Response badMethod = admin.post("/admin/routes",
                TestData.with(route, "methods", java.util.List.of("FETCH")));

        assertThat(badPrefix.status()).isEqualTo(400);
        assertThat(badPrefix.body().get("fields").has("pathPrefix")).isTrue();
        assertThat(badPolicy.status()).isEqualTo(400);
        assertThat(badMethod.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("FR-07: canary version and canary percent must be given together")
    void canaryNeedsBothFields() throws Exception {
        Map<String, Object> route = TestData.route(id("route"));

        AdminClient.Response onlyPercent =
                admin.post("/admin/routes", TestData.with(route, "canaryPercent", 10));
        AdminClient.Response onlyVersion =
                admin.post("/admin/routes", TestData.with(route, "canaryVersion", "v2"));

        assertThat(onlyPercent.status()).isEqualTo(400);
        assertThat(onlyVersion.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("FR-07: an upstream address must be an http or https URL with a host")
    void upstreamAddressRules() throws Exception {
        Map<String, Object> upstream = TestData.upstream(id("upstream"));

        for (String bad : new String[] {"orders-a:8081", "ftp://orders-a", "http://", "not a url"}) {
            AdminClient.Response response =
                    admin.post("/admin/upstreams", TestData.with(upstream, "baseUri", bad));
            assertThat(response.status()).as(bad).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("FR-07: the database CHECK constraints are a second line of defence behind the API")
    void databaseRejectsOutOfRangeValues() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO routes (id, path_prefix, methods, service_id, lb_policy, timeout_ms)"
                        + " VALUES (?, ?, '', 'orders', 'ROUND_ROBIN', 99)", id("route"), "/" + id("r")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO upstream_instances (id, service_id, base_uri, weight)"
                        + " VALUES (?, 'orders', 'http://x', 0)", id("upstream")))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO plans (id, name, rate_capacity, rate_period_ms, monthly_quota)"
                        + " VALUES (?, 'Bad', 0, 1, 1)", id("plan")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
