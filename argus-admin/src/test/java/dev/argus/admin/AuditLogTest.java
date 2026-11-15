package dev.argus.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** QR-13: every change made through the Admin API writes an audit record: who, what, when. */
class AuditLogTest extends AdminIntegrationTest {

    @Test
    @DisplayName("QR-13: create, update and delete each write one audit record with actor and time")
    void createUpdateDeleteAreAudited() throws Exception {
        String planId = id("plan");
        Instant before = Instant.now().minusSeconds(1);
        long rowsBefore = auditRows();

        admin.post("/admin/plans", TestData.plan(planId));
        admin.put("/admin/plans/" + planId, TestData.with(TestData.plan(planId), "name", "Basic"));
        admin.delete("/admin/plans/" + planId);

        assertThat(auditRows()).isEqualTo(rowsBefore + 3);
        List<JsonNode> entries = auditEntriesFor(planId);
        assertThat(entries).extracting(entry -> entry.get("action").asText())
                .containsExactly("DELETE", "UPDATE", "CREATE");
        for (JsonNode entry : entries) {
            assertThat(entry.get("actor").asText()).isEqualTo(USERNAME);
            assertThat(entry.get("entityType").asText()).isEqualTo("PLAN");
            assertThat(Instant.parse(entry.get("occurredAt").asText())).isAfter(before);
        }
    }

    @Test
    @DisplayName("QR-13: issuing, rotating and revoking a key are audited, without the key itself")
    void keyLifecycleIsAudited() throws Exception {
        String planId = id("plan");
        String consumerId = id("consumer");
        admin.post("/admin/plans", TestData.plan(planId));
        admin.post("/admin/consumers", TestData.consumer(consumerId, planId));

        AdminClient.Response issued = admin.post("/admin/consumers/" + consumerId + "/keys");
        String firstId = issued.body().get("id").asText();
        AdminClient.Response rotated = admin.post("/admin/keys/" + firstId + "/rotate");
        String secondId = rotated.body().get("id").asText();
        admin.post("/admin/keys/" + secondId + "/revoke");

        assertThat(auditEntriesFor(consumerId)).extracting(e -> e.get("action").asText())
                .containsExactly("CREATE");
        assertThat(auditEntriesFor(firstId)).extracting(e -> e.get("action").asText())
                .containsExactly("ROTATE", "ISSUE");
        assertThat(auditEntriesFor(secondId)).extracting(e -> e.get("action").asText())
                .containsExactly("REVOKE", "ISSUE");
        assertThat(admin.get("/admin/audit").text())
                .doesNotContain(issued.body().get("key").asText())
                .doesNotContain(rotated.body().get("key").asText());
    }

    @Test
    @DisplayName("QR-13: a rejected request and a repeated revoke change nothing and write no record")
    void rejectedRequestsAreNotAudited() throws Exception {
        String planId = id("plan");
        admin.post("/admin/plans", TestData.plan(planId));
        long rowsBefore = auditRows();

        admin.post("/admin/plans", TestData.plan(planId));                       // 409
        admin.post("/admin/plans",
                TestData.with(TestData.plan(id("plan")), "rateCapacity", 0));   // 400
        admin.delete("/admin/plans/" + id("plan"));                              // 404
        admin.get("/admin/plans");                                               // a read

        assertThat(auditRows()).isEqualTo(rowsBefore);
    }

    /** Audit entries of one entity, newest first (the order of GET /admin/audit). */
    private List<JsonNode> auditEntriesFor(String entityId) throws Exception {
        AdminClient.Response response = admin.get("/admin/audit");
        assertThat(response.status()).isEqualTo(200);
        List<JsonNode> matching = new ArrayList<>();
        for (JsonNode entry : response.body()) {
            if (entry.get("entityId").asText().equals(entityId)) {
                matching.add(entry);
            }
        }
        return matching;
    }
}
