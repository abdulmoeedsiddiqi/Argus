package dev.argus.core.exception;

/** API key blocked by an enforcement decision (403). */
public class AccessBlockedException extends ClientRejectionException {

    private static final long serialVersionUID = 1L;

    public AccessBlockedException(String message) {
        super(403, message);
    }
}
