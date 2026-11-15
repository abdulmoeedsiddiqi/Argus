package dev.argus.admin.api;

import jakarta.validation.constraints.NotBlank;

/** Body of POST /admin/login. */
public record LoginRequest(@NotBlank String username, @NotBlank String password) {

    /** Never prints the password. */
    @Override
    public String toString() {
        return "LoginRequest[username=" + username + ", password=<hidden>]";
    }
}
