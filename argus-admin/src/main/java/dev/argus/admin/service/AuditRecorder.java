package dev.argus.admin.service;

import dev.argus.admin.api.AuditEntryDto;
import dev.argus.admin.persistence.AuditLogEntity;
import dev.argus.admin.persistence.AuditLogRepository;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the audit trail (QR-13). Every service calls {@link #record} inside the same database
 * transaction as the change itself, so a change and its audit record are saved together or
 * not at all.
 */
@Service
public class AuditRecorder {

    private final AuditLogRepository auditLog;
    private final Clock clock;

    public AuditRecorder(AuditLogRepository auditLog, Clock clock) {
        this.auditLog = auditLog;
        this.clock = clock;
    }

    /**
     * Adds one record and returns its number. The numbers only ever grow, so they also serve
     * as the version of the change.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public long record(String actor, String action, String entityType, String entityId) {
        AuditLogEntity saved = auditLog.save(
                new AuditLogEntity(actor, action, entityType, entityId, clock.instant()));
        return saved.getId();
    }

    @Transactional(readOnly = true)
    public List<AuditEntryDto> latest() {
        return auditLog.findTop200ByOrderByIdDesc().stream()
                .map(entry -> new AuditEntryDto(
                        entry.getId(), entry.getActor(), entry.getAction(),
                        entry.getEntityType(), entry.getEntityId(), entry.getOccurredAt()))
                .toList();
    }
}
