package dev.argus.core.exception;

/** No healthy instance is available (503). */
public class NoHealthyUpstreamException extends UpstreamException {

    private static final long serialVersionUID = 1L;

    public NoHealthyUpstreamException(String message, Throwable cause) {
        super(503, message, cause);
    }
}
