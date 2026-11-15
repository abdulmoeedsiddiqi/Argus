package dev.argus.admin.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The admin sign-in token (HS256): issued at login, verified on every Admin API call. */
class AdminTokenServiceTest {

    private static final byte[] SECRET =
            "unit-test-admin-secret-0123456789-abcdef".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
    private static final Duration TTL = Duration.ofHours(1);

    private final AdminTokenService tokens = serviceAt(NOW);

    @Test
    @DisplayName("a token issued at sign-in is accepted and names the administrator")
    void issuedTokenIsAccepted() {
        AdminTokenService.IssuedToken issued = tokens.issue("alice");

        assertThat(issued.expiresAt()).isEqualTo(NOW.plus(TTL));
        assertThat(tokens.verify(issued.token())).contains("alice");
    }

    @Test
    @DisplayName("boundary: a token is valid one second before its expiry, not at or after it")
    void expiryBoundary() {
        String token = tokens.issue("alice").token();
        Instant expiry = NOW.plus(TTL);

        assertThat(serviceAt(expiry.minusSeconds(1)).verify(token)).contains("alice");
        assertThat(serviceAt(expiry).verify(token)).isEmpty();
        assertThat(serviceAt(expiry.plusSeconds(1)).verify(token)).isEmpty();
    }

    @Test
    @DisplayName("a token signed with another secret is refused")
    void wrongSignatureIsRefused() {
        byte[] other = "another-secret-0123456789-abcdefghijklmn".getBytes(StandardCharsets.UTF_8);
        String forged = new AdminTokenService(other, TTL, Clock.fixed(NOW, ZoneOffset.UTC))
                .issue("mallory").token();

        assertThat(tokens.verify(forged)).isEmpty();
    }

    @Test
    @DisplayName("a token from another issuer (for example a gateway client token) is refused")
    void wrongIssuerIsRefused() throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("argus-demo")
                .subject("partner-api")
                .expirationTime(Date.from(NOW.plusSeconds(300)))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        jwt.sign(new MACSigner(SECRET));

        assertThat(tokens.verify(jwt.serialize())).isEmpty();
    }

    @Test
    @DisplayName("an unsigned token (alg none) and plain garbage are refused")
    void unsignedAndMalformedTokensAreRefused() {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer("argus-admin")
                .subject("mallory")
                .expirationTime(Date.from(NOW.plusSeconds(300)))
                .build();

        assertThat(tokens.verify(new PlainJWT(claims).serialize())).isEmpty();
        assertThat(tokens.verify("not-a-token")).isEmpty();
        assertThat(tokens.verify(null)).isEmpty();
    }

    private static AdminTokenService serviceAt(Instant now) {
        return new AdminTokenService(SECRET, TTL, Clock.fixed(now, ZoneOffset.UTC));
    }
}
