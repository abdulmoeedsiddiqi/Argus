package dev.argus.gateway.web;

import dev.argus.core.model.HttpMethod;
import dev.argus.core.model.IncomingRequest;
import dev.argus.core.model.UpstreamResponse;
import dev.argus.core.pipeline.FilterChain;
import dev.argus.core.pipeline.FilterResult;
import dev.argus.core.pipeline.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP entry point of a gateway instance. It only translates: servlet request to an immutable
 * {@link IncomingRequest}, then {@link FilterResult} back to an HTTP response. Every decision
 * about the request is made by the filter chain in argus-core.
 */
@RestController
public class GatewayEntryPoint {

    /** Headers that describe the upstream connection, not ours; the server sets its own. */
    private static final Set<String> NOT_RETURNED = Set.of(
            "connection", "content-length", "keep-alive", "transfer-encoding",
            "te", "trailer", "upgrade", "proxy-authenticate");

    static final String UPSTREAM_HEADER = "X-Argus-Upstream";

    private final FilterChain chain;

    public GatewayEntryPoint(FilterChain chain) {
        this.chain = chain;
    }

    @RequestMapping("/**")
    public ResponseEntity<?> handle(
            HttpServletRequest servletRequest, @RequestBody(required = false) byte[] body) {
        RequestContext context = RequestContext.of(toIncomingRequest(servletRequest, body));
        return switch (chain.proceed(context)) {
            case FilterResult.Forwarded forwarded -> toResponse(forwarded.response());
            case FilterResult.Rejected rejected -> toResponse(rejected);
        };
    }

    private static IncomingRequest toIncomingRequest(HttpServletRequest request, byte[] body) {
        String query = request.getQueryString();
        String path = query == null ? request.getRequestURI() : request.getRequestURI() + "?" + query;
        Map<String, List<String>> headers = new HashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name.toLowerCase(Locale.ROOT),
                    List.copyOf(Collections.list(request.getHeaders(name))));
        }
        return new IncomingRequest(
                UUID.randomUUID().toString(),
                HttpMethod.valueOf(request.getMethod()),
                path,
                headers,
                body,
                request.getRemoteAddr(),
                Instant.now());
    }

    private static ResponseEntity<byte[]> toResponse(UpstreamResponse upstream) {
        HttpHeaders headers = new HttpHeaders();
        upstream.headers().forEach((name, values) -> {
            if (!NOT_RETURNED.contains(name.toLowerCase(Locale.ROOT))) {
                headers.addAll(name, values);
            }
        });
        if (upstream.upstreamInstanceId() != null) {
            // Shows which backend copy answered, so load balancing can be observed from outside.
            headers.set(UPSTREAM_HEADER, upstream.upstreamInstanceId());
        }
        return ResponseEntity.status(upstream.status()).headers(headers).body(upstream.body());
    }

    private static ResponseEntity<RejectionBody> toResponse(FilterResult.Rejected rejected) {
        return ResponseEntity.status(rejected.httpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RejectionBody(rejected.reason().name(), rejected.message()));
    }
}
