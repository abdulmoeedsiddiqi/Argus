package dev.argus.admin.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards every {@code /admin/**} request except the sign-in itself: the request must carry
 * {@code Authorization: Bearer <admin token>}. On success the administrator's user name is
 * stored on the request as {@link #ACTOR}; the audit log later records it as "who".
 */
@Component
public class AdminAuthenticationFilter extends OncePerRequestFilter {

    /** Request attribute holding the user name of the signed-in administrator. */
    public static final String ACTOR = "argus.admin.actor";

    private static final String BEARER = "Bearer ";

    private final AdminTokenService tokens;
    private final ObjectMapper json;

    public AdminAuthenticationFilter(AdminTokenService tokens, ObjectMapper json) {
        this.tokens = tokens;
        this.json = json;
    }

    /** Health checks, the API documentation and the sign-in are open. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/admin") || path.equals("/admin/login");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Optional<String> actor = tokens.verify(bearerToken(request));
        if (actor.isEmpty()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            json.writeValue(response.getOutputStream(), Map.of(
                    "reason", "AUTHENTICATION_FAILED",
                    "message", "a valid admin token is required: sign in at /admin/login"));
            return;
        }
        request.setAttribute(ACTOR, actor.get());
        chain.doFilter(request, response);
    }

    private static String bearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        return header != null && header.startsWith(BEARER) ? header.substring(BEARER.length()) : null;
    }
}
