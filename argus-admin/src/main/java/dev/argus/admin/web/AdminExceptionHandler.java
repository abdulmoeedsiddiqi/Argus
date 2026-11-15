package dev.argus.admin.web;

import dev.argus.admin.api.ErrorBody;
import dev.argus.admin.service.ResourceConflictException;
import dev.argus.admin.service.ResourceNotFoundException;
import dev.argus.core.exception.InvalidConfigurationException;
import java.util.Map;
import java.util.TreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * The one place where Admin API exceptions become HTTP answers (SE423 report, Section 5.9).
 * Every answer is the same small JSON shape ({@link ErrorBody}); details that belong in the
 * log, such as stack traces, never reach the client.
 */
@RestControllerAdvice
public class AdminExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(AdminExceptionHandler.class);

    /** Bean Validation found bad fields: 400 with one message per field. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> onInvalidFields(MethodArgumentNotValidException invalid) {
        Map<String, String> fields = new TreeMap<>();
        for (FieldError error : invalid.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        return answer(400, new ErrorBody(
                "INVALID_CONFIGURATION", "the request has invalid fields: " + fields.keySet(), fields));
    }

    /** The body is not JSON, or a value has the wrong type (for example text for a number). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> onUnreadableBody(HttpMessageNotReadableException unreadable) {
        return answer(400, ErrorBody.of("INVALID_CONFIGURATION",
                "the request body is missing, is not valid JSON, or has a value of the wrong type"));
    }

    /** A rule that needs the stored data, for example "the plan must exist". */
    @ExceptionHandler(InvalidConfigurationException.class)
    ResponseEntity<ErrorBody> onInvalidConfiguration(InvalidConfigurationException invalid) {
        return answer(400, ErrorBody.of("INVALID_CONFIGURATION", invalid.getMessage()));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<ErrorBody> onNotFound(ResourceNotFoundException notFound) {
        return answer(404, ErrorBody.of("NOT_FOUND", notFound.getMessage()));
    }

    @ExceptionHandler(ResourceConflictException.class)
    ResponseEntity<ErrorBody> onConflict(ResourceConflictException conflict) {
        return answer(409, ErrorBody.of("CONFLICT", conflict.getMessage()));
    }

    /** Safety net: the database refused the change (unique, foreign-key or CHECK constraint). */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> onConstraintViolation(DataIntegrityViolationException violation) {
        LOG.warn("the database rejected a change: {}", violation.getMostSpecificCause().getMessage());
        return answer(409, ErrorBody.of("CONFLICT", "the change conflicts with the stored data"));
    }

    /** Anything else: Spring's own HTTP errors keep their status; the rest is a plain 500. */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> onOtherError(Exception error) {
        if (error instanceof ErrorResponse http) {
            int status = http.getStatusCode().value();
            return answer(status, ErrorBody.of(
                    status == 404 ? "NOT_FOUND" : "INVALID_REQUEST", "the request cannot be handled"));
        }
        LOG.error("unexpected error while handling an Admin API request", error);
        return answer(500, ErrorBody.of("INTERNAL_ERROR", "the Admin API could not handle the request"));
    }

    private static ResponseEntity<ErrorBody> answer(int status, ErrorBody body) {
        return ResponseEntity.status(status).body(body);
    }
}
