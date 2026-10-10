package dev.argus.core.exception;

/** No route matches the request (404). */
public class RouteNotFoundException extends ClientRejectionException {

    private static final long serialVersionUID = 1L;

    public RouteNotFoundException(String message) {
        super(404, message);
    }
}
