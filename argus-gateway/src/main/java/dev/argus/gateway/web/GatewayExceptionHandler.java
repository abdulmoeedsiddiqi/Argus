package dev.argus.gateway.web;

import dev.argus.core.exception.AccessBlockedException;
import dev.argus.core.exception.AuthenticationFailedException;
import dev.argus.core.exception.CircuitOpenException;
import dev.argus.core.exception.ClientRejectionException;
import dev.argus.core.exception.NoHealthyUpstreamException;
import dev.argus.core.exception.QuotaExceededException;
import dev.argus.core.exception.RateLimitExceededException;
import dev.argus.core.exception.RouteNotFoundException;
import dev.argus.core.exception.UpstreamException;
import dev.argus.core.exception.UpstreamTimeoutException;
import dev.argus.core.pipeline.RejectionReason;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The one place where gateway exceptions become HTTP responses (SE423 report, Section 5.9).
 * The exception type decides the handling: failures caused by the caller are not errors and
 * are not logged as such; failures of a backend are logged as warnings; anything else is an
 * internal error whose details stay in the log and never reach the client.
 */
@RestControllerAdvice
public class GatewayExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GatewayExceptionHandler.class);

    /** Exception type to machine-readable reason code. */
    private static final Map<Class<? extends RuntimeException>, RejectionReason> REASONS = Map.of(
            AuthenticationFailedException.class, RejectionReason.AUTHENTICATION_FAILED,
            AccessBlockedException.class, RejectionReason.ACCESS_BLOCKED,
            RateLimitExceededException.class, RejectionReason.RATE_LIMITED,
            QuotaExceededException.class, RejectionReason.QUOTA_EXCEEDED,
            RouteNotFoundException.class, RejectionReason.ROUTE_NOT_FOUND,
            NoHealthyUpstreamException.class, RejectionReason.NO_HEALTHY_UPSTREAM,
            CircuitOpenException.class, RejectionReason.CIRCUIT_OPEN,
            UpstreamTimeoutException.class, RejectionReason.UPSTREAM_TIMEOUT);

    /** Caused by the caller (401, 403, 404, 429). */
    @ExceptionHandler(ClientRejectionException.class)
    ResponseEntity<RejectionBody> onClientRejection(ClientRejectionException rejection) {
        RejectionReason reason =
                REASONS.getOrDefault(rejection.getClass(), RejectionReason.INVALID_REQUEST);
        return body(rejection.httpStatus(), reason.name(), rejection.getMessage());
    }

    /** Caused by a backend (503, 504). */
    @ExceptionHandler(UpstreamException.class)
    ResponseEntity<RejectionBody> onUpstreamFailure(UpstreamException failure) {
        LOG.warn("upstream failure: {}", failure.getMessage());
        RejectionReason reason =
                REASONS.getOrDefault(failure.getClass(), RejectionReason.NO_HEALTHY_UPSTREAM);
        return body(failure.httpStatus(), reason.name(), failure.getMessage());
    }

    /** Anything unexpected: logged in full, answered without internal details. */
    @ExceptionHandler(RuntimeException.class)
    ResponseEntity<RejectionBody> onUnexpectedError(RuntimeException error) {
        LOG.error("unexpected error while handling a request", error);
        return body(500, "INTERNAL_ERROR", "the gateway could not handle the request");
    }

    private static ResponseEntity<RejectionBody> body(int status, String reason, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new RejectionBody(reason, message));
    }
}
