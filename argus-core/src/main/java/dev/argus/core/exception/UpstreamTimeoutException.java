package dev.argus.core.exception;

/** Upstream did not respond within the route timeout (504). */
public class UpstreamTimeoutException extends UpstreamException {

    private static final long serialVersionUID = 1L;

    public UpstreamTimeoutException(String message, Throwable cause) {
        super(504, message, cause);
    }
}
