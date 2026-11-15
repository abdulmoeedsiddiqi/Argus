package dev.argus.core.auth;

import static dev.argus.core.pipeline.PipelineFixtures.forwarded;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.argus.core.exception.AuthenticationFailedException;
import dev.argus.core.exception.ClientRejectionException;
import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.Principal;
import dev.argus.core.pipeline.FilterChain;
import dev.argus.core.pipeline.FilterResult;
import dev.argus.core.pipeline.RequestContext;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * AuthenticationFilter is the first step of the chain (Chain of Responsibility, order 100).
 * It picks the authenticator that supports the request; a request without a valid credential
 * goes no further and fails with AuthenticationFailedException (401).
 */
class AuthenticationFilterTest {

    private static final Principal SHOP_APP = new Principal("shop-app", "key-1", "free");
    private static final String SECRET_KEY = "ak_test_shopapp_0123456789";
    private static final String SECRET_TOKEN = "eyJhbGciOiJIUzI1NiJ9.payload.signature";

    private final RecordingChain chain = new RecordingChain();
    private final List<String> asked = new ArrayList<>();

    @Test
    @DisplayName("TC-06.1: a request with a valid API key continues with its principal")
    void validApiKeyContinuesWithPrincipal() {
        AuthenticationFilter filter = new AuthenticationFilter(List.of(
                authenticator("api-key", "x-api-key", Optional.of(SHOP_APP)),
                authenticator("jwt", "authorization", Optional.empty())));

        FilterResult result = filter.filter(context("x-api-key", SECRET_KEY), chain);

        assertThat(result).isSameAs(chain.answer);
        assertThat(chain.seen.authenticatedPrincipal()).contains(SHOP_APP);
        assertThat(asked).containsExactly("api-key");
    }

    @Test
    @DisplayName("TC-05.1: a request with a bearer token is handled by the JWT authenticator")
    void bearerTokenUsesTheJwtAuthenticator() {
        Principal partner = new Principal("partner-api", null, "pro");
        AuthenticationFilter filter = new AuthenticationFilter(List.of(
                authenticator("api-key", "x-api-key", Optional.empty()),
                authenticator("jwt", "authorization", Optional.of(partner))));

        filter.filter(context("authorization", "Bearer " + SECRET_TOKEN), chain);

        assertThat(chain.seen.authenticatedPrincipal()).contains(partner);
        assertThat(asked).containsExactly("jwt");
    }

    @Test
    @DisplayName("TC-06.2 / TC-06.3: an invalid credential fails with 401 and goes no further")
    void invalidCredentialFails() {
        AuthenticationFilter filter = new AuthenticationFilter(List.of(
                authenticator("api-key", "x-api-key", Optional.empty())));

        assertThatThrownBy(() -> filter.filter(context("x-api-key", SECRET_KEY), chain))
                .isInstanceOf(AuthenticationFailedException.class)
                .isInstanceOfSatisfying(ClientRejectionException.class,
                        rejection -> assertThat(rejection.httpStatus()).isEqualTo(401));
        assertThat(chain.calls).isZero();
    }

    @Test
    @DisplayName("TC-05.4: a request without any credential fails with 401 and goes no further")
    void missingCredentialFails() {
        AuthenticationFilter filter = new AuthenticationFilter(List.of(
                authenticator("api-key", "x-api-key", Optional.of(SHOP_APP)),
                authenticator("jwt", "authorization", Optional.of(SHOP_APP))));

        assertThatThrownBy(() -> filter.filter(RequestContext.of(request(Map.of())), chain))
                .isInstanceOf(AuthenticationFailedException.class);
        assertThat(asked).isEmpty();
        assertThat(chain.calls).isZero();
    }

    @Test
    @DisplayName("QR-11: the failure message never repeats the key or the token")
    void failureMessageHidesTheCredential() {
        AuthenticationFilter filter = new AuthenticationFilter(List.of(
                authenticator("api-key", "x-api-key", Optional.empty()),
                authenticator("jwt", "authorization", Optional.empty())));

        assertThatThrownBy(() -> filter.filter(context("x-api-key", SECRET_KEY), chain))
                .hasMessageNotContaining(SECRET_KEY);
        assertThatThrownBy(
                () -> filter.filter(context("authorization", "Bearer " + SECRET_TOKEN), chain))
                .hasMessageNotContaining(SECRET_TOKEN);
    }

    @Test
    @DisplayName("runs first in the chain: order 100, before routing (400) and proxying (500)")
    void hasOrder100() {
        assertThat(new AuthenticationFilter(List.of(
                authenticator("api-key", "x-api-key", Optional.empty()))).order()).isEqualTo(100);
    }

    @Test
    @DisplayName("a filter without any authenticator is a wiring error")
    void rejectsEmptyAuthenticatorList() {
        assertThatThrownBy(() -> new AuthenticationFilter(List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** A fake authenticator that supports requests carrying {@code header} and answers as told. */
    private Authenticator authenticator(String name, String header, Optional<Principal> answer) {
        return new Authenticator() {
            @Override
            public boolean supports(IncomingRequest request) {
                return request.header(header).isPresent();
            }

            @Override
            public Optional<Principal> authenticate(IncomingRequest request) {
                asked.add(name);
                return answer;
            }
        };
    }

    private static RequestContext context(String header, String value) {
        return RequestContext.of(request(Map.of(header, List.of(value))));
    }

    private static IncomingRequest request(Map<String, List<String>> headers) {
        return new IncomingRequest("req-1", HttpMethod.GET, "/orders/42", headers, null,
                "127.0.0.1", Instant.parse("2026-11-15T12:00:00Z"));
    }

    private static final class RecordingChain implements FilterChain {
        private final FilterResult answer = forwarded("ok");
        private RequestContext seen;
        private int calls;

        @Override
        public FilterResult proceed(RequestContext context) {
            seen = context;
            calls++;
            return answer;
        }
    }
}
