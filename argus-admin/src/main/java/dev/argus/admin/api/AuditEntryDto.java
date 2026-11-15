package dev.argus.admin.api;

import java.time.Instant;

/** One audit record: who (actor) did what (action) to which entity, and when. */
public record AuditEntryDto(
        long id,
        String actor,
        String action,
        String entityType,
        String entityId,
        Instant occurredAt) { }
