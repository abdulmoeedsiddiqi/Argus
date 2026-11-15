package dev.argus.admin.web;

import dev.argus.admin.api.AuditEntryDto;
import dev.argus.admin.service.AuditRecorder;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only view of the audit trail (QR-13): the latest changes, newest first. */
@RestController
@RequestMapping("/admin/audit")
public class AuditLogController {

    private final AuditRecorder audit;

    public AuditLogController(AuditRecorder audit) {
        this.audit = audit;
    }

    @GetMapping
    public List<AuditEntryDto> latest() {
        return audit.latest();
    }
}
