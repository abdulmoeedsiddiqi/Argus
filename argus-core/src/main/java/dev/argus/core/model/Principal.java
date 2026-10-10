package dev.argus.core.model;

import java.util.Objects;

/** The authenticated caller, as established by an Authenticator. */
public record Principal(String consumerId, String apiKeyId, String planId) {

    public Principal {
        Objects.requireNonNull(consumerId, "consumerId");
        Objects.requireNonNull(planId, "planId");
    }
}
