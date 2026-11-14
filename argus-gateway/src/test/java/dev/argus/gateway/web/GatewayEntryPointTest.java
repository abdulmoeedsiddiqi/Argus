package dev.argus.gateway.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    /** A chain whose answer the test chooses and whose last input the test can inspect. */
    static final class StubChain implements FilterChain {
        private volatile FilterResult answer;
        private volatile RequestContext seen;

        @Override
        public FilterResult proceed(RequestContext context) {
            seen = context;
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
