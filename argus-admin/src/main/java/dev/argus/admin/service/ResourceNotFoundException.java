package dev.argus.admin.service;

/** The entity named in the request does not exist (HTTP 404). */
public class ResourceNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String kind, String id) {
        super(kind + " " + id + " does not exist");
    }
}
