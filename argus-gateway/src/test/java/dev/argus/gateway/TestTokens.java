package dev.argus.gateway;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/** Test helper: mints signed JWTs the way a trusted issuer would. Test values only. */
public final class TestTokens {

    /** 64 bytes, so it is long enough for HS256 and for the HS384 negative test. */
    public static final String SECRET =
            "test-only-secret-0123456789-test-only-secret-0123456789-abcdefgh";
    public static final String ISSUER = "argus-test-issuer";

    private TestTokens() { }

    /** A token as the gateway expects it: HS256, with issuer, subject, plan and expiry. */
    public static String valid(String subject, String plan, Instant expiresAt) {
        return sign(JWSAlgorithm.HS256, SECRET, claims(ISSUER, subject, plan, expiresAt));
    }

    public static JWTClaimsSet claims(String issuer, String subject, String plan, Instant expiresAt) {
        JWTClaimsSet.Builder builder = new JWTClaimsSet.Builder();
        if (issuer != null) {
            builder.issuer(issuer);
        }
        if (subject != null) {
            builder.subject(subject);
        }
        if (plan != null) {
            builder.claim("plan", plan);
        }
        if (expiresAt != null) {
            builder.expirationTime(Date.from(expiresAt));
        }
        return builder.build();
    }

    public static String sign(JWSAlgorithm algorithm, String secret, JWTClaimsSet claims) {
        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(algorithm), claims);
            jwt.sign(new MACSigner(secret.getBytes(StandardCharsets.UTF_8)));
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("could not sign the test token", e);
        }
    }
}
