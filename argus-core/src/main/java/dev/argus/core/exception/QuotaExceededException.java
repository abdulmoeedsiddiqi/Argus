package dev.argus.core.exception;

/** Monthly plan quota exhausted (429). */
public class QuotaExceededException extends ClientRejectionException {

    private static final long serialVersionUID = 1L;

    public QuotaExceededException(String message) {
        super(429, message);
    }
}
