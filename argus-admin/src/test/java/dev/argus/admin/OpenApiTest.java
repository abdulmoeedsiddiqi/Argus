package dev.argus.admin;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The Admin API documents itself: OpenAPI description and Swagger UI (springdoc). */
class OpenApiTest extends AdminIntegrationTest {

    @Test
    @DisplayName("the OpenAPI document is served without a token and lists the admin endpoints")
    void openApiDocumentListsTheEndpoints() throws Exception {
        AdminClient.Response response = AdminClient.anonymous(port).get("/v3/api-docs");

        assertThat(response.status()).isEqualTo(200);
        JsonNode paths = response.body().get("paths");
        assertThat(paths.has("/admin/routes")).isTrue();
        assertThat(paths.has("/admin/upstreams/{id}")).isTrue();
        assertThat(paths.has("/admin/plans")).isTrue();
        assertThat(paths.has("/admin/consumers/{id}/keys")).isTrue();
        assertThat(paths.has("/admin/keys/{id}/rotate")).isTrue();
        assertThat(paths.has("/admin/keys/{id}/revoke")).isTrue();
        assertThat(paths.has("/admin/audit")).isTrue();
        assertThat(paths.has("/admin/login")).isTrue();
    }

    @Test
    @DisplayName("the OpenAPI document declares the bearer token, so Swagger UI can sign in")
    void openApiDocumentDeclaresBearerAuthentication() throws Exception {
        JsonNode document = AdminClient.anonymous(port).get("/v3/api-docs").body();

        JsonNode scheme = document.get("components").get("securitySchemes").get("adminToken");
        assertThat(scheme.get("type").asText()).isEqualTo("http");
        assertThat(scheme.get("scheme").asText()).isEqualTo("bearer");
    }

    @Test
    @DisplayName("Swagger UI is served")
    void swaggerUiIsServed() throws Exception {
        AdminClient.Response response = AdminClient.anonymous(port).get("/swagger-ui/index.html");

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.text()).contains("swagger-ui");
    }
}
