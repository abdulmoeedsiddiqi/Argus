package dev.argus.admin;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** FR-07: create, read, update and delete plans and consumers through the Admin API. */
class PlanAndConsumerApiTest extends AdminIntegrationTest {

    @Test
    @DisplayName("TC-07.1: a plan can be created, read, updated and deleted")
    void planCrud() throws Exception {
        String planId = id("plan");

        AdminClient.Response created = admin.post("/admin/plans", TestData.plan(planId));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("id").asText()).isEqualTo(planId);

        AdminClient.Response read = admin.get("/admin/plans/" + planId);
        assertThat(read.status()).isEqualTo(200);
        assertThat(read.body().get("rateCapacity").asInt()).isEqualTo(60);
        assertThat(admin.get("/admin/plans").text()).contains(planId);

        AdminClient.Response updated = admin.put("/admin/plans/" + planId,
                TestData.with(TestData.plan(planId), "rateCapacity", 120));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(admin.get("/admin/plans/" + planId).body().get("rateCapacity").asInt())
                .isEqualTo(120);

        assertThat(admin.delete("/admin/plans/" + planId).status()).isEqualTo(204);
        assertThat(admin.get("/admin/plans/" + planId).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("TC-07.1: a consumer can be created, read, updated and deleted")
    void consumerCrud() throws Exception {
        String freePlan = id("plan");
        String proPlan = id("plan");
        String consumerId = id("consumer");
        admin.post("/admin/plans", TestData.plan(freePlan));
        admin.post("/admin/plans", TestData.plan(proPlan));

        AdminClient.Response created =
                admin.post("/admin/consumers", TestData.consumer(consumerId, freePlan));
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.body().get("planId").asText()).isEqualTo(freePlan);
        assertThat(created.body().get("createdAt").asText()).isNotBlank();

        AdminClient.Response updated = admin.put("/admin/consumers/" + consumerId,
                TestData.consumer(consumerId, proPlan));
        assertThat(updated.status()).isEqualTo(200);
        assertThat(admin.get("/admin/consumers/" + consumerId).body().get("planId").asText())
                .isEqualTo(proPlan);
        assertThat(admin.get("/admin/consumers").text()).contains(consumerId);

        assertThat(admin.delete("/admin/consumers/" + consumerId).status()).isEqualTo(204);
        assertThat(admin.get("/admin/consumers/" + consumerId).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("FR-07: creating an id that already exists is refused with 409 CONFLICT")
    void duplicateIdIsAConflict() throws Exception {
        String planId = id("plan");
        admin.post("/admin/plans", TestData.plan(planId));

        AdminClient.Response second = admin.post("/admin/plans", TestData.plan(planId));

        assertThat(second.status()).isEqualTo(409);
        assertThat(second.body().get("reason").asText()).isEqualTo("CONFLICT");
    }

    @Test
    @DisplayName("FR-07: reading, updating or deleting an unknown id gives 404 NOT_FOUND")
    void unknownIdIsNotFound() throws Exception {
        String unknown = id("plan");

        AdminClient.Response read = admin.get("/admin/plans/" + unknown);

        assertThat(read.status()).isEqualTo(404);
        assertThat(read.body().get("reason").asText()).isEqualTo("NOT_FOUND");
        assertThat(admin.put("/admin/plans/" + unknown, TestData.plan(unknown)).status())
                .isEqualTo(404);
        assertThat(admin.delete("/admin/plans/" + unknown).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("FR-07: a plan with a non-positive limit is rejected with 400 and names the field")
    void invalidPlanIsRejected() throws Exception {
        String planId = id("plan");

        AdminClient.Response response = admin.post("/admin/plans",
                TestData.with(TestData.plan(planId), "rateCapacity", 0));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("reason").asText()).isEqualTo("INVALID_CONFIGURATION");
        assertThat(response.body().get("fields").has("rateCapacity")).isTrue();
        assertThat(admin.get("/admin/plans/" + planId).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("FR-07: missing fields, a bad id and unreadable JSON are rejected with 400")
    void malformedRequestsAreRejected() throws Exception {
        AdminClient.Response noName = admin.post("/admin/plans",
                TestData.with(TestData.plan(id("plan")), "name", null));
        AdminClient.Response badId = admin.post("/admin/plans", TestData.plan("Not A Valid Id!"));
        AdminClient.Response notJson = admin.postRaw("/admin/plans", "application/json", "{oops");
        AdminClient.Response wrongType = admin.post("/admin/plans",
                TestData.with(TestData.plan(id("plan")), "rateCapacity", "many"));

        assertThat(noName.status()).isEqualTo(400);
        assertThat(noName.body().get("fields").has("name")).isTrue();
        assertThat(badId.status()).isEqualTo(400);
        assertThat(badId.body().get("fields").has("id")).isTrue();
        assertThat(notJson.status()).isEqualTo(400);
        assertThat(wrongType.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("FR-07: the id in the body must match the id in the path of an update")
    void updateWithAnotherIdIsRejected() throws Exception {
        String planId = id("plan");
        admin.post("/admin/plans", TestData.plan(planId));

        AdminClient.Response response =
                admin.put("/admin/plans/" + planId, TestData.plan(id("plan")));

        assertThat(response.status()).isEqualTo(400);
    }

    @Test
    @DisplayName("FR-07: a consumer must refer to an existing plan")
    void consumerNeedsAnExistingPlan() throws Exception {
        AdminClient.Response response =
                admin.post("/admin/consumers", TestData.consumer(id("consumer"), id("plan")));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.body().get("reason").asText()).isEqualTo("INVALID_CONFIGURATION");
    }

    @Test
    @DisplayName("FR-07: a plan that consumers still use cannot be deleted (409)")
    void planInUseCannotBeDeleted() throws Exception {
        String planId = id("plan");
        admin.post("/admin/plans", TestData.plan(planId));
        admin.post("/admin/consumers", TestData.consumer(id("consumer"), planId));

        AdminClient.Response response = admin.delete("/admin/plans/" + planId);

        assertThat(response.status()).isEqualTo(409);
        assertThat(admin.get("/admin/plans/" + planId).status()).isEqualTo(200);
    }

    @Test
    @DisplayName("an error answer never contains a stack trace or class names")
    void errorsHideInternals() throws Exception {
        AdminClient.Response response = admin.post("/admin/plans", Map.of("id", id("plan")));

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.text()).doesNotContain("Exception").doesNotContain("dev.argus");
    }
}
