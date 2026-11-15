package dev.argus.admin.service;

import dev.argus.admin.api.UpstreamDto;
import dev.argus.admin.persistence.UpstreamInstanceEntity;
import dev.argus.admin.persistence.UpstreamInstanceRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Upstream instances (backend copies): create, read, update and delete. */
@Service
public class UpstreamService {

    static final String ENTITY = "UPSTREAM";

    private final UpstreamInstanceRepository upstreams;
    private final AuditRecorder audit;

    public UpstreamService(UpstreamInstanceRepository upstreams, AuditRecorder audit) {
        this.upstreams = upstreams;
        this.audit = audit;
    }

    @Transactional
    public UpstreamDto create(UpstreamDto request, String actor) {
        if (upstreams.existsById(request.id())) {
            throw new ResourceConflictException("upstream " + request.id() + " already exists");
        }
        UpstreamInstanceEntity upstream = new UpstreamInstanceEntity(request.id());
        apply(request, upstream);
        upstreams.save(upstream);
        audit.record(actor, "CREATE", ENTITY, upstream.getId());
        return toDto(upstream);
    }

    @Transactional
    public UpstreamDto update(String id, UpstreamDto request, String actor) {
        UpstreamInstanceEntity upstream = find(id);
        apply(request, upstream);
        audit.record(actor, "UPDATE", ENTITY, id);
        return toDto(upstream);
    }

    @Transactional
    public void delete(String id, String actor) {
        upstreams.delete(find(id));
        audit.record(actor, "DELETE", ENTITY, id);
    }

    @Transactional(readOnly = true)
    public UpstreamDto get(String id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public List<UpstreamDto> list() {
        return upstreams.findAll().stream().map(UpstreamService::toDto).toList();
    }

    private UpstreamInstanceEntity find(String id) {
        return upstreams.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("upstream", id));
    }

    private static void apply(UpstreamDto request, UpstreamInstanceEntity upstream) {
        upstream.update(request.serviceId(), request.baseUri(), request.weight(), request.version());
    }

    static UpstreamDto toDto(UpstreamInstanceEntity upstream) {
        return new UpstreamDto(
                upstream.getId(), upstream.getServiceId(), upstream.getBaseUri(),
                upstream.getWeight(), upstream.getVersion());
    }
}
