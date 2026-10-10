package dev.argus.core.exception;

/** The upstream service could not produce a usable response. */
public abstract class UpstreamException extends ArgusException {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;

    protected UpstreamException(int httpStatus, String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
