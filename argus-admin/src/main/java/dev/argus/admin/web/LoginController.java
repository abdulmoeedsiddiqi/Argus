package dev.argus.admin.web;

import dev.argus.admin.api.ErrorBody;
import dev.argus.admin.api.LoginRequest;
import dev.argus.admin.config.AdminProperties;
import dev.argus.admin.security.AdminTokenService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin sign-in: user name and password in, a bearer token out. There is one administrator,
 * configured through the environment. The answer to a failed sign-in is the same whether the
 * user name or the password was wrong, so it reveals nothing.
 */
@RestController
public class LoginController {

    private final AdminProperties properties;
    private final AdminTokenService tokens;

    public LoginController(AdminProperties properties, AdminTokenService tokens) {
        this.properties = properties;
        this.tokens = tokens;
    }

    @PostMapping("/admin/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        // Both comparisons always run, in constant time, so timing does not give hints.
        boolean userMatches = sameText(properties.username(), request.username());
        boolean passwordMatches = sameText(properties.password(), request.password());
        if (!(userMatches && passwordMatches)) {
            return ResponseEntity.status(401)
                    .body(ErrorBody.of("AUTHENTICATION_FAILED", "wrong user name or password"));
        }
        AdminTokenService.IssuedToken issued = tokens.issue(request.username());
        return ResponseEntity.ok(new LoginResponse(issued.token(), "Bearer", issued.expiresAt()));
    }

    private static boolean sameText(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
    }

    /** Answer of a successful sign-in. */
    public record LoginResponse(String token, String tokenType, Instant expiresAt) { }
}
