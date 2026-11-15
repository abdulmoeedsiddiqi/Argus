package dev.argus.admin.service;

/**
 * The request is well-formed but cannot be applied to the current data: the id already exists,
 * the entity is still in use, or the key is already revoked (HTTP 409).
 */
public class ResourceConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceConflictException(String message) {
        super(message);
    }
}
