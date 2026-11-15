package dev.argus.admin.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * A plan as the Admin API receives and returns it. The annotations are the input rules
 * (Bean Validation); the database CHECK constraints repeat the numeric ones.
 */
public record PlanDto(
        @NotBlank @Pattern(regexp = Ids.PATTERN, message = Ids.MESSAGE) String id,
        @NotBlank @Size(max = 100) String name,
        @NotNull @Min(1) Integer rateCapacity,
        @NotNull @Min(1) Long ratePeriodMs,
        @NotNull @Min(1) Long monthlyQuota) { }
