package dev.argus.admin.web;

import dev.argus.core.exception.InvalidConfigurationException;

/** Shared check for updates: the id in the URL and the id in the body must be the same. */
final class PathIds {

    private PathIds() { }

    static void requireSame(String pathId, String bodyId) {
        if (!pathId.equals(bodyId)) {
            throw new InvalidConfigurationException(
                    "the id in the body (" + bodyId + ") must match the id in the path (" + pathId
                            + "); an id cannot be changed");
        }
    }
}
