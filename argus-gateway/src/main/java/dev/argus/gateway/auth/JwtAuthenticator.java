package dev.argus.gateway.auth;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import dev.argus.core.auth.Authenticator;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.Principal;
import dev.argus.core.time.TimeSource;
import java.text.ParseException;
import java.util.Date;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Adapter: implements the core's {@link Authenticator} with the Nimbus JOSE + JWT library
 * (FR-05). It lives in the gateway module because the core may not depend on libraries.
 *
 * <p>A token from {@code Authorization: Bearer <token>} is accepted only if all of these hold:
 * <ol>
 *   <li>it is a signed JWT whose algorithm is exactly HS256 (so "alg: none" and algorithm
 *       swapping are refused);</li>
 *   <li>its signature matches the shared secret;</li>
 *   <li>its issuer is the configured one;</li>
 *   <li>its expiry time is still ahead of the clock (no grace period);</li>
 *   <li>it names a subject (the consumer) and a plan.</li>
 * </ol>
 * Any failure gives an empty result. The token is never logged or put into a message.
 *
 * <p>Thread-safe: the verifier is immutable and no other state is kept.
 */
public final class JwtAuthenticator implements Authenticator {

    private static final String HEADER = "Authorization";
    private static final String SCHEME = "bearer ";
    private static final String PLAN_CLAIM = "plan";
    private static final int MIN_SECRET_BYTES = 32;

    private final JWSVerifier verifier;
    private final String issuer;
    private final TimeSource time;

    public JwtAuthenticator(byte[] secret, String issuer, TimeSource time) {
        Objects.requireNonNull(secret, "secret");
        if (secret.length < MIN_SECRET_BYTES) {
            // HS256 needs a key of at least 256 bits. The message does not show the secret.
            throw new IllegalArgumentException(
                    "the JWT secret must be at least " + MIN_SECRET_BYTES + " bytes long");
        }
        this.issuer = Objects.requireNonNull(issuer, "issuer");
        this.time = Objects.requireNonNull(time, "time");
        try {
            this.verifier = new MACVerifier(secret);
        } catch (JOSEException invalidKey) {
            throw new IllegalArgumentException("the JWT secret cannot be used for HS256");
        }
    }

    @Override
    public boolean supports(IncomingRequest request) {
        return request.header(HEADER)
                .map(value -> value.toLowerCase(Locale.ROOT).startsWith(SCHEME))
                .orElse(false);
    }

    @Override
    public Optional<Principal> authenticate(IncomingRequest request) {
        return request.header(HEADER)
                .filter(value -> value.length() > SCHEME.length())
                .map(value -> value.substring(SCHEME.length()).trim())
                .flatMap(this::verifiedClaims)
                .filter(this::isFromExpectedIssuer)
                .filter(this::isNotExpired)
                .flatMap(JwtAuthenticator::toPrincipal);
    }

    /** Parses the token and checks algorithm and signature. Empty if anything is wrong. */
    private Optional<JWTClaimsSet> verifiedClaims(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!JWSAlgorithm.HS256.equals(jwt.getHeader().getAlgorithm())) {
                return Optional.empty();
            }
            return jwt.verify(verifier) ? Optional.of(jwt.getJWTClaimsSet()) : Optional.empty();
        } catch (ParseException | JOSEException notAValidToken) {
            return Optional.empty();
        }
    }

    private boolean isFromExpectedIssuer(JWTClaimsSet claims) {
        return issuer.equals(claims.getIssuer());
    }

    /** A token without an expiry time is refused: it would be valid for ever. */
    private boolean isNotExpired(JWTClaimsSet claims) {
        Date expiresAt = claims.getExpirationTime();
        return expiresAt != null && time.now().isBefore(expiresAt.toInstant());
    }

    private static Optional<Principal> toPrincipal(JWTClaimsSet claims) {
        String subject = claims.getSubject();
        Object plan = claims.getClaim(PLAN_CLAIM);
        if (subject == null || subject.isBlank() || !(plan instanceof String planId) || planId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new Principal(subject, null, planId));
    }
}
