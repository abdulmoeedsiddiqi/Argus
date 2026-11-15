package dev.argus.admin.api;

/** The one rule for entity ids chosen by the administrator, for example "free" or "orders-a". */
public final class Ids {

    /** 1 to 64 characters: lower-case letters, digits, dot, dash and underscore. */
    public static final String PATTERN = "^[a-z0-9][a-z0-9._-]{0,63}$";

    public static final String MESSAGE =
            "must be 1 to 64 characters: lower-case letters, digits, '.', '-' or '_'";

    private Ids() { }
}
