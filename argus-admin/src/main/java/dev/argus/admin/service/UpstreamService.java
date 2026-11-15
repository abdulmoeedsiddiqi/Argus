package dev.argus.admin.service;

import dev.argus.admin.api.UpstreamDto;
import dev.argus.admin.events.ConfigChangeRelay;
import dev.argus.admin.persistence.UpstreamInstanceEntity;
import dev.argus.admin.persistence.UpstreamInstanceRepository;
import dev.argus.contracts.ConfigChange.EntityType;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Upstream instances (backend copies): create, read, update and delete. */
@Service
public class UpstreamService {

    static final String ENTITY = "UPSTREAM";

    private final UpstreamInstanceRepository upstreams;
    private final AuditRecorder audit;
    private final ConfigChangeRelay relay;

    public UpstreamService(
            UpstreamInstanceRepository upstreams, AuditRecorder audit, ConfigChangeRelay relay) {
        this.upstreams = upstreams;
        this.audit = audit;
        this.relay = relay;
    }

    @Transactional
    public UpstreamDto create(UpstreamDto request, String actor) {
        if (upstreams.existsById(request.id())) {
            throw new ResourceConflictException("upstream " + request.id() + " already exists");
        }
        UpstreamInstanceEntity upstream = new UpstreamInstanceEntity(request.id());
        apply(request, upstream);
        upstreams.save(upstream);
        recordAndPublish(actor, "CREATE", upstream);
        return toDto(upstream);
    }

    @Transactional
    public UpstreamDto update(String id, UpstreamDto request, String actor) {
        UpstreamInstanceEntity upstream = find(id);
        apply(request, upstream);
        recordAndPublish(actor, "UPDATE", upstream);
        return toDto(upstream);
    }

    @Transactional
    public void delete(String id, String actor) {
        upstreams.delete(find(id));
        long version = audit.record(actor, "DELETE", ENTITY, id);
        relay.afterCommit(EntityType.UPSTREAM, id, version, null);
    }

    @Transactional(readOnly = true)
    public UpstreamDto get(String id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public List<UpstreamDto> list() {
        return upstreams.findAll().stream().map(UpstreamService::toDto).toList();
    }

    /** Writes the audit record and schedules the change for the gateways. */
    private void recordAndPublish(String actor, String action, UpstreamInstanceEntity upstream) {
        long version = audit.record(actor, action, ENTITY, upstream.getId());
        relay.afterCommit(
                EntityType.UPSTREAM, upstream.getId(), version, ConfigPayloads.upstream(upstream));
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
