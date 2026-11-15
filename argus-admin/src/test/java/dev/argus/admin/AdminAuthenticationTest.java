package dev.argus.admin;

import static org.assertj.core.api.Assertions.assertThat;

import dev.argus.admin.security.AdminTokenService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The Admin API is protected with an admin JWT obtained from POST /admin/login. */
class AdminAuthenticationTest extends AdminIntegrationTest {

    @Test
    @DisplayName("signing in with the right username and password returns a bearer token")
    void signInReturnsAToken() throws Exception {
        AdminClient.Response login = AdminClient.anonymous(port).post(
                "/admin/login", Map.of("username", USERNAME, "password", PASSWORD));

        assertThat(login.status()).isEqualTo(200);
        assertThat(login.body().get("token").asText()).isNotBlank();
        assertThat(login.body().get("expiresAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("a wrong password or an unknown user is refused with 401 and gets no token")
    void wrongCredentialsAreRefused() throws Exception {
        AdminClient anonymous = AdminClient.anonymous(port);

        AdminClient.Response wrongPassword = anonymous.post(
                "/admin/login", Map.of("username", USERNAME, "password", "wrong"));
        AdminClient.Response unknownUser = anonymous.post(
                "/admin/login", Map.of("username", "nobody", "password", PASSWORD));

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(wrongPassword.text()).doesNotContain("token");
        assertThat(unknownUser.status()).isEqualTo(401);
    }

    @Test
    @DisplayName("a call with a valid admin token is accepted")
    void validTokenIsAccepted() throws Exception {
        assertThat(admin.get("/admin/plans").status()).isEqualTo(200);
    }

    @Test
    @DisplayName("a call without a token is refused with 401 and a reason code")
    void missingTokenIsRefused() throws Exception {
        AdminClient.Response response = AdminClient.anonymous(port).get("/admin/plans");

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.body().get("reason").asText()).isEqualTo("AUTHENTICATION_FAILED");
    }

    @Test
    @DisplayName("a malformed token, a forged token and an expired token are refused with 401")
    void badTokensAreRefused() throws Exception {
        byte[] realSecret = JWT_SECRET.getBytes(StandardCharsets.UTF_8);
        byte[] otherSecret = "another-secret-0123456789-abcdefghijklmn".getBytes(StandardCharsets.UTF_8);
        Clock now = Clock.systemUTC();
        Clock twoHoursAgo = Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC);
        String forged = new AdminTokenService(otherSecret, Duration.ofHours(1), now)
                .issue(USERNAME).token();
        String expired = new AdminTokenService(realSecret, Duration.ofHours(1), twoHoursAgo)
                .issue(USERNAME).token();

        assertThat(AdminClient.withToken(port, "not-a-token").get("/admin/plans").status())
                .isEqualTo(401);
        assertThat(AdminClient.withToken(port, forged).get("/admin/plans").status()).isEqualTo(401);
        assertThat(AdminClient.withToken(port, expired).get("/admin/plans").status()).isEqualTo(401);
    }

    @Test
    @DisplayName("writes are protected too: POST without a token changes nothing")
    void writesNeedAToken() throws Exception {
        String planId = id("plan");

        AdminClient.Response response = AdminClient.anonymous(port).post("/admin/plans", Map.of(
                "id", planId, "name", "Free", "rateCapacity", 60,
                "ratePeriodMs", 60000, "monthlyQuota", 10000));

        assertThat(response.status()).isEqualTo(401);
        assertThat(admin.get("/admin/plans/" + planId).status()).isEqualTo(404);
    }

    @Test
    @DisplayName("the health endpoint stays open, so Docker can check the service without a token")
    void healthIsOpen() throws Exception {
        assertThat(AdminClient.anonymous(port).get("/actuator/health").status()).isEqualTo(200);
    }
}
