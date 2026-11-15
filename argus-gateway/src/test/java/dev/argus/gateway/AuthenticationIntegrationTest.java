package dev.argus.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Module M2 end to end (UC-01): the real gateway in front of a real backend. Only requests
 * with a valid API key or JWT reach the backend; everything else is answered 401 by the
 * gateway itself (QR-12).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class AuthenticationIntegrationTest {

    private static final MockBackend ORDERS = MockBackend.start("orders");

    private final HttpClient client = HttpClient.newHttpClient();
    private final ObjectMapper json = new ObjectMapper();

    @LocalServerPort
    private int gatewayPort;

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("argus.upstreams[0].id", () -> "orders-1");
        registry.add("argus.upstreams[0].service", () -> "orders");
        registry.add("argus.upstreams[0].url", ORDERS::url);
        registry.add("argus.routes[0].path-prefix", () -> "/orders");
        registry.add("argus.routes[0].service", () -> "orders");
        registry.add("argus.health-check.enabled", () -> "false");
        TestCredentials.register(registry);
    }

    @AfterAll
    static void stopBackend() {
        ORDERS.close();
    }

    @Test
    @DisplayName("TC-06.1: a request with a valid X-API-Key is forwarded and answered 200")
    void validApiKeyIsAccepted() throws Exception {
        HttpResponse<String> response = get("/orders/42", "X-API-Key", TestCredentials.VALID_KEY);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(json.readTree(response.body()).get("id").asInt()).isEqualTo(42);
    }

    @ParameterizedTest(name = "{0} is refused")
    @ValueSource(strings = {
            TestCredentials.UNKNOWN_KEY, TestCredentials.REVOKED_KEY, TestCredentials.EXPIRED_KEY})
    @DisplayName("TC-06.2, TC-06.3, TC-06.4: unknown, revoked and expired keys are refused with 401")
    void badApiKeysAreRefused(String key) throws Exception {
        HttpResponse<String> response = get("/orders/42", "X-API-Key", key);

        assertUnauthorized(response);
    }

    @Test
    @DisplayName("TC-05.4: a request without any credential is refused with 401")
    void missingCredentialIsRefused() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(uri("/orders/42")).GET().build();

        assertUnauthorized(client.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    @DisplayName("TC-05.1: a request with a valid JWT is forwarded and answered 200")
    void validJwtIsAccepted() throws Exception {
        String token = TestTokens.valid("partner-api", "pro", Instant.now().plusSeconds(300));

        HttpResponse<String> response = get("/orders/42", "Authorization", "Bearer " + token);

        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    @DisplayName("TC-05.2: a request with an expired JWT is refused with 401")
    void expiredJwtIsRefused() throws Exception {
        String token = TestTokens.valid("partner-api", "pro", Instant.now().minusSeconds(1));

        assertUnauthorized(get("/orders/42", "Authorization", "Bearer " + token));
    }

    @Test
    @DisplayName("TC-05.3: a request with a malformed JWT is refused with 401")
    void malformedJwtIsRefused() throws Exception {
        assertUnauthorized(get("/orders/42", "Authorization", "Bearer not-a-jwt"));
    }

    @Test
    @DisplayName("QR-12: authentication comes first, so an unknown path without a key is 401, not 404")
    void authenticationRunsBeforeRouting() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(uri("/payments/1")).GET().build();

        assertUnauthorized(client.send(request, HttpResponse.BodyHandlers.ofString()));
    }

    @Test
    @DisplayName("TC-Q11.1: neither API keys nor tokens appear in the gateway log")
    void credentialsNeverAppearInLogs(CapturedOutput output) throws Exception {
        String token = TestTokens.valid("partner-api", "pro", Instant.now().plusSeconds(300));
        String expired = TestTokens.valid("partner-api", "pro", Instant.now().minusSeconds(60));

        get("/orders/42", "X-API-Key", TestCredentials.VALID_KEY);
        get("/orders/42", "X-API-Key", TestCredentials.REVOKED_KEY);
        get("/orders/42", "X-API-Key", TestCredentials.UNKNOWN_KEY);
        get("/orders/42", "Authorization", "Bearer " + token);
        get("/orders/42", "Authorization", "Bearer " + expired);

        assertThat(output.getAll())
                .doesNotContain(TestCredentials.VALID_KEY)
                .doesNotContain(TestCredentials.REVOKED_KEY)
                .doesNotContain(TestCredentials.UNKNOWN_KEY)
                .doesNotContain(token)
                .doesNotContain(expired)
                .doesNotContain(TestTokens.SECRET);
    }

    private void assertUnauthorized(HttpResponse<String> response) throws Exception {
        assertThat(response.statusCode()).isEqualTo(401);
        JsonNode body = json.readTree(response.body());
        assertThat(body.get("reason").asText()).isEqualTo("AUTHENTICATION_FAILED");
        // The backend sets this header; its absence shows the request never reached it.
        assertThat(response.headers().firstValue("X-Mock-Service")).isEmpty();
    }

    private HttpResponse<String> get(String path, String header, String value) throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(uri(path)).header(header, value).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private URI uri(String path) {
        return URI.create("http://127.0.0.1:" + gatewayPort + path);
    }
}
