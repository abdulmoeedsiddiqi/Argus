package dev.argus.gateway.auth;

import static dev.argus.gateway.TestTokens.ISSUER;
import static dev.argus.gateway.TestTokens.SECRET;
import static dev.argus.gateway.TestTokens.claims;
import static dev.argus.gateway.TestTokens.sign;
import static dev.argus.gateway.TestTokens.valid;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.PlainJWT;
import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.Principal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-05 JWT authentication: a token is accepted only with a valid HS256 signature, the
 * expected issuer and an expiry time that is still ahead. The clock is fixed, so the expiry
 * boundary is tested to the second without waiting.
 */
class JwtAuthenticatorTest {

    private static final Instant NOW = Instant.parse("2026-11-15T12:00:00Z");

    private final JwtAuthenticator authenticator = new JwtAuthenticator(
            SECRET.getBytes(StandardCharsets.UTF_8), ISSUER, () -> NOW);

    @Test
    @DisplayName("TC-05.1: a valid token gives a principal built from its subject and plan")
    void validTokenGivesPrincipal() {
        String token = valid("partner-api", "pro", NOW.plusSeconds(3600));

        assertThat(authenticate(token)).contains(new Principal("partner-api", null, "pro"));
    }

    @Test
    @DisplayName("TC-05.2 (BVA): a token that expires in 1 second is still accepted")
    void tokenExpiringInOneSecondIsAccepted() {
        assertThat(authenticate(valid("partner-api", "pro", NOW.plusSeconds(1)))).isPresent();
    }

    @Test
    @DisplayName("TC-05.2 (BVA): a token that expires exactly now is refused")
    void tokenExpiringNowIsRefused() {
        assertThat(authenticate(valid("partner-api", "pro", NOW))).isEmpty();
    }

    @Test
    @DisplayName("TC-05.2 (BVA): a token that expired 1 second ago is refused")
    void tokenExpiredOneSecondAgoIsRefused() {
        assertThat(authenticate(valid("partner-api", "pro", NOW.minusSeconds(1)))).isEmpty();
    }

    @Test
    @DisplayName("TC-05.3: text that is not a JWT is refused")
    void garbageIsRefused() {
        assertThat(authenticate("not-a-jwt")).isEmpty();
        assertThat(authenticate("a.b.c")).isEmpty();
        assertThat(authenticate("")).isEmpty();
    }

    @Test
    @DisplayName("TC-05.3: a token signed with a different secret is refused")
    void wrongSignatureIsRefused() {
        String otherSecret = "another-secret-0123456789-another-secret-0123456789-abcdefghijkl";
        String token = sign(JWSAlgorithm.HS256, otherSecret,
                claims(ISSUER, "partner-api", "pro", NOW.plusSeconds(3600)));

        assertThat(authenticate(token)).isEmpty();
    }

    @Test
    @DisplayName("TC-05.3: a token whose payload was changed after signing is refused")
    void tamperedPayloadIsRefused() {
        String[] parts = valid("partner-api", "free", NOW.plusSeconds(3600)).split("\\.");
        String forgedPayload = valid("partner-api", "pro", NOW.plusSeconds(3600)).split("\\.")[1];

        assertThat(authenticate(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    @DisplayName("TC-05.3: an unsigned token (alg none) is refused")
    void unsignedTokenIsRefused() {
        String token = new PlainJWT(claims(ISSUER, "partner-api", "pro", NOW.plusSeconds(3600)))
                .serialize();

        assertThat(authenticate(token)).isEmpty();
    }

    @Test
    @DisplayName("TC-05.3: a token signed with another algorithm (HS384) is refused")
    void otherAlgorithmIsRefused() {
        String token = sign(JWSAlgorithm.HS384, SECRET,
                claims(ISSUER, "partner-api", "pro", NOW.plusSeconds(3600)));

        assertThat(authenticate(token)).isEmpty();
    }

    @Test
    @DisplayName("TC-05.3: a token from another issuer is refused")
    void wrongIssuerIsRefused() {
        String token = sign(JWSAlgorithm.HS256, SECRET,
                claims("someone-else", "partner-api", "pro", NOW.plusSeconds(3600)));

        assertThat(authenticate(token)).isEmpty();
    }

    @Test
    @DisplayName("TC-05.3: a token without subject, plan or expiry is refused")
    void missingClaimsAreRefused() {
        Instant later = NOW.plusSeconds(3600);

        assertThat(authenticate(sign(JWSAlgorithm.HS256, SECRET, claims(ISSUER, null, "pro", later))))
                .isEmpty();
        assertThat(authenticate(sign(JWSAlgorithm.HS256, SECRET,
                claims(ISSUER, "partner-api", null, later)))).isEmpty();
        assertThat(authenticate(sign(JWSAlgorithm.HS256, SECRET,
                claims(ISSUER, "partner-api", "pro", null)))).isEmpty();
    }

    @Test
    @DisplayName("supports requests with an Authorization: Bearer header only")
    void supportsBearerRequests() {
        assertThat(authenticator.supports(request("authorization", "Bearer abc"))).isTrue();
        assertThat(authenticator.supports(request("Authorization", "bearer abc"))).isTrue();
        assertThat(authenticator.supports(request("authorization", "Basic dXNlcjpwYXNz"))).isFalse();
        assertThat(authenticator.supports(request("x-api-key", "ak_test_123"))).isFalse();
    }

    @Test
    @DisplayName("a signing secret shorter than 32 bytes is rejected at start-up")
    void rejectsShortSecret() {
        byte[] tooShort = "only-16-bytes!!!".getBytes(StandardCharsets.UTF_8);

        assertThatThrownBy(() -> new JwtAuthenticator(tooShort, ISSUER, () -> NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageNotContaining("only-16-bytes");
    }

    private Optional<Principal> authenticate(String token) {
        return authenticator.authenticate(request("authorization", "Bearer " + token));
    }

    private static IncomingRequest request(String header, String value) {
        return new IncomingRequest("req-1", HttpMethod.GET, "/orders/42",
                Map.of(header, List.of(value)), null, "127.0.0.1", NOW);
    }
}
