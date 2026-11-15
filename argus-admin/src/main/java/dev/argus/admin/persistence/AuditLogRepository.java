package dev.argus.admin.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for the audit log. Rows are only ever added, never changed or removed. */
public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {

    /** The 200 most recent entries, newest first. */
    List<AuditLogEntity> findTop200ByOrderByIdDesc();
}
