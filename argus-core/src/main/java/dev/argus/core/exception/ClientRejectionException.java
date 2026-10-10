package dev.argus.core.exception;

/**
 * The request was refused because of the caller (bad credentials, limits, unknown route).
 * Carries the HTTP status so one handler can map every rejection consistently.
 */
public abstract class ClientRejectionException extends ArgusException {

    private static final long serialVersionUID = 1L;

    private final int httpStatus;

    protected ClientRejectionException(int httpStatus, String message) {
        super(message);
        this.httpStatus = httpStatus;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
