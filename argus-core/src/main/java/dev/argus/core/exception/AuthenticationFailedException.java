package dev.argus.core.exception;

/** Credential missing, invalid, expired or revoked (401). */
public class AuthenticationFailedException extends ClientRejectionException {

    private static final long serialVersionUID = 1L;

    public AuthenticationFailedException(String message) {
        super(401, message);
    }
}
