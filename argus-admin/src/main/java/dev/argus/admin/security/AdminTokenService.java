package dev.argus.admin.security;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.text.ParseException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

/**
 * Issues and verifies the admin sign-in token: a JWT signed with HS256 (Nimbus JOSE + JWT).
 *
 * <p>A token is accepted only if its algorithm is exactly HS256, its signature matches the
 * secret, its issuer is {@value #ISSUER} and the current time is before its expiry. The issuer
 * check keeps a gateway client token from ever being used as an admin token.
 */
public final class AdminTokenService {

    static final String ISSUER = "argus-admin";

    private final byte[] secret;
    private final Duration ttl;
    private final Clock clock;

    public AdminTokenService(byte[] secret, Duration ttl, Clock clock) {
        this.secret = secret.clone();
        this.ttl = ttl;
        this.clock = clock;
    }

    /** Creates a token for an administrator who has just signed in. */
    public IssuedToken issue(String username) {
        Instant expiresAt = clock.instant().plus(ttl);
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject(username)
                .claim("role", "admin")
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException e) {
            throw new IllegalStateException("the admin token could not be signed", e);
        }
        return new IssuedToken(jwt.serialize(), expiresAt);
    }

    /** Returns the administrator's user name if the token is valid, otherwise nothing. */
    public Optional<String> verify(String token) {
        if (token == null) {
            return Optional.empty();
        }
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())
                    || !jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiry = claims.getExpirationTime();
            boolean valid = ISSUER.equals(claims.getIssuer())
                    && claims.getSubject() != null
                    && expiry != null
                    && clock.instant().isBefore(expiry.toInstant());
            return valid ? Optional.of(claims.getSubject()) : Optional.empty();
        } catch (ParseException | JOSEException e) {
            // Not a JWT, not signed, or signed in a way we do not accept.
            return Optional.empty();
        }
    }

    /** A freshly issued token and the moment it stops working. */
    public record IssuedToken(String token, Instant expiresAt) {

        /** Never prints the token. */
        @Override
        public String toString() {
            return "IssuedToken[token=<hidden>, expiresAt=" + expiresAt + "]";
        }
    }
}
