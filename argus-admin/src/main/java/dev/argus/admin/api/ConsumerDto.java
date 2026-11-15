package dev.argus.admin.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * A consumer (client organisation) as the Admin API receives and returns it.
 *
 * @param createdAt set by the server; a value sent by the client is ignored
 */
public record ConsumerDto(
        @NotBlank @Pattern(regexp = Ids.PATTERN, message = Ids.MESSAGE) String id,
        @NotBlank @Size(max = 100) String name,
        @NotBlank String planId,
        Instant createdAt) { }
