package dev.argus.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.argus.core.exception.CircuitOpenException;
import dev.argus.core.exception.NoHealthyUpstreamException;
import dev.argus.core.exception.RouteNotFoundException;
import dev.argus.core.exception.UpstreamTimeoutException;
import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.UpstreamResponse;
import dev.argus.core.pipeline.FilterChain;
import dev.argus.core.pipeline.FilterResult;
import dev.argus.core.pipeline.RejectionReason;
import dev.argus.core.pipeline.RequestContext;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The HTTP entry point (SE423 report, Section 5.6 step 1) turns a servlet request into an
 * immutable IncomingRequest, runs the filter chain, and turns the FilterResult back into HTTP.
 * The chain is a stub here, so only the translation is tested.
 */
@WebMvcTest(GatewayEntryPoint.class)
class GatewayEntryPointTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private StubChain chain;

    @Test
    @DisplayName("a Forwarded result becomes the upstream's status, headers and body")
    void forwardedResultBecomesTheHttpResponse() throws Exception {
        chain.answer = new FilterResult.Forwarded(new UpstreamResponse(
                200,
                Map.of("content-type", List.of("application/json"),
                        "x-mock-service", List.of("orders"),
                        // Describes the upstream connection, not ours: must not be copied.
                        "transfer-encoding", List.of("chunked")),
                "{\"id\":42}".getBytes(StandardCharsets.UTF_8), "orders-a", Duration.ofMillis(5)));

        mvc.perform(get("/orders/42"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("x-mock-service", "orders"))
                .andExpect(header().doesNotExist("transfer-encoding"))
                .andExpect(content().json("{\"id\":42}"));
    }

    @Test
    @DisplayName("the chain receives method, path with query, headers and body of the request")
    void requestIsHandedToTheChain() throws Exception {
        chain.answer = new FilterResult.Forwarded(new UpstreamResponse(
                201, Map.of(), new byte[0], "orders-a", Duration.ZERO));

        mvc.perform(post("/orders?expand=items")
                        .header("X-API-Key", "ak_test_123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"item\":\"Pizza\"}"))
                .andExpect(status().isCreated());

        IncomingRequest seen = chain.seen.request();
        assertThat(seen.method()).isEqualTo(HttpMethod.POST);
        assertThat(seen.path()).isEqualTo("/orders?expand=items");
        assertThat(seen.headers()).containsEntry("x-api-key", List.of("ak_test_123"));
        assertThat(new String(seen.body(), StandardCharsets.UTF_8)).isEqualTo("{\"item\":\"Pizza\"}");
        assertThat(seen.requestId()).isNotBlank();
        assertThat(seen.receivedAt()).isNotNull();
    }

    @Test
    @DisplayName("QR-18: a Rejected result becomes its status with a JSON body naming the reason")
    void rejectedResultBecomesJsonWithReasonCode() throws Exception {
        chain.answer = new FilterResult.Rejected(
                429, RejectionReason.RATE_LIMITED, "rate limit exceeded");

        mvc.perform(get("/orders/42"))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reason").value("RATE_LIMITED"))
                .andExpect(jsonPath("$.message").value("rate limit exceeded"));
    }

    @Test
    @DisplayName("the response names the backend copy that answered in X-Argus-Upstream")
    void namesTheBackendCopy() throws Exception {
        chain.answer = new FilterResult.Forwarded(new UpstreamResponse(
                200, Map.of(), new byte[0], "orders-a", Duration.ZERO));

        mvc.perform(get("/orders/42"))
                .andExpect(header().string("X-Argus-Upstream", "orders-a"));
    }

    @Test
    @DisplayName("QR-18: an upstream timeout becomes 504 with reason UPSTREAM_TIMEOUT, not a stack trace")
    void upstreamTimeoutBecomes504() throws Exception {
        chain.failure = new UpstreamTimeoutException("orders-a did not answer within PT2S", null);

        mvc.perform(get("/orders/42"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.reason").value("UPSTREAM_TIMEOUT"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("QR-18: an unreachable upstream becomes 503 with reason NO_HEALTHY_UPSTREAM")
    void unreachableUpstreamBecomes503() throws Exception {
        chain.failure = new NoHealthyUpstreamException("orders-a could not be reached", null);

        mvc.perform(get("/orders/42"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("NO_HEALTHY_UPSTREAM"));
    }

    @Test
    @DisplayName("QR-18: an open circuit becomes 503 with reason CIRCUIT_OPEN")
    void openCircuitBecomes503() throws Exception {
        chain.failure = new CircuitOpenException("circuit open for orders", null);

        mvc.perform(get("/orders/42"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.reason").value("CIRCUIT_OPEN"));
    }

    @Test
    @DisplayName("QR-18: a client rejection thrown as an exception keeps its status and reason")
    void clientRejectionKeepsItsStatus() throws Exception {
        chain.failure = new RouteNotFoundException("no route for /payments");

        mvc.perform(get("/payments/1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.reason").value("ROUTE_NOT_FOUND"));
    }

    @Test
    @DisplayName("an unexpected error becomes a plain 500 JSON body without internal details")
    void unexpectedErrorHidesDetails() throws Exception {
        chain.failure = new IllegalStateException("secret internal detail");

        mvc.perform(get("/orders/42"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.reason").value("INTERNAL_ERROR"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("secret internal detail"))));
    }

    /** A chain whose answer the test chooses and whose last input the test can inspect. */
    static final class StubChain implements FilterChain {
        private volatile FilterResult answer;
        private volatile RuntimeException failure;
        private volatile RequestContext seen;

        @Override
        public FilterResult proceed(RequestContext context) {
            seen = context;
            RuntimeException toThrow = failure;
            if (toThrow != null) {
                failure = null;
                throw toThrow;
            }
            return answer;
        }
    }

    @TestConfiguration
    static class StubChainConfiguration {
        @Bean
        StubChain stubChain() {
            return new StubChain();
        }
    }
}
