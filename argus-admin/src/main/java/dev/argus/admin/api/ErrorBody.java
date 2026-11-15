package dev.argus.admin.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

/**
 * JSON body of every Admin API error: a machine-readable reason code, a short message and,
 * for validation errors, the message per rejected field. Never a stack trace.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(String reason, String message, Map<String, String> fields) {

    public static ErrorBody of(String reason, String message) {
        return new ErrorBody(reason, message, null);
    }
}
