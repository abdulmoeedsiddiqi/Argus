package dev.argus.contracts;

/** Payload of a {@link ConfigChange} of type PLAN: the limits that a plan gives its consumers. */
public record PlanConfig(
        String id, String name, int rateCapacity, long ratePeriodMs, long monthlyQuota) { }
