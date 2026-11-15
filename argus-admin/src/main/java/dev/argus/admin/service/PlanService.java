package dev.argus.admin.service;

import dev.argus.admin.api.PlanDto;
import dev.argus.admin.events.ConfigChangeRelay;
import dev.argus.admin.persistence.ConsumerRepository;
import dev.argus.admin.persistence.PlanEntity;
import dev.argus.admin.persistence.PlanRepository;
import dev.argus.contracts.ConfigChange.EntityType;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Plans: the rules behind create, read, update and delete. One transaction per change. */
@Service
public class PlanService {

    static final String ENTITY = "PLAN";

    private final PlanRepository plans;
    private final ConsumerRepository consumers;
    private final AuditRecorder audit;
    private final ConfigChangeRelay relay;

    public PlanService(
            PlanRepository plans, ConsumerRepository consumers, AuditRecorder audit,
            ConfigChangeRelay relay) {
        this.plans = plans;
        this.consumers = consumers;
        this.audit = audit;
        this.relay = relay;
    }

    @Transactional
    public PlanDto create(PlanDto request, String actor) {
        if (plans.existsById(request.id())) {
            throw new ResourceConflictException("plan " + request.id() + " already exists");
        }
        PlanEntity plan = new PlanEntity(request.id());
        apply(request, plan);
        plans.save(plan);
        recordAndPublish(actor, "CREATE", plan);
        return toDto(plan);
    }

    @Transactional
    public PlanDto update(String id, PlanDto request, String actor) {
        PlanEntity plan = find(id);
        apply(request, plan);
        recordAndPublish(actor, "UPDATE", plan);
        return toDto(plan);
    }

    @Transactional
    public void delete(String id, String actor) {
        PlanEntity plan = find(id);
        if (consumers.existsByPlanId(id)) {
            throw new ResourceConflictException("plan " + id + " is still used by consumers");
        }
        plans.delete(plan);
        long version = audit.record(actor, "DELETE", ENTITY, id);
        relay.afterCommit(EntityType.PLAN, id, version, null);
    }

    @Transactional(readOnly = true)
    public PlanDto get(String id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public List<PlanDto> list() {
        return plans.findAll().stream().map(PlanService::toDto).toList();
    }

    /** Writes the audit record and schedules the change for the gateways. */
    private void recordAndPublish(String actor, String action, PlanEntity plan) {
        long version = audit.record(actor, action, ENTITY, plan.getId());
        relay.afterCommit(EntityType.PLAN, plan.getId(), version, ConfigPayloads.plan(plan));
    }

    private PlanEntity find(String id) {
        return plans.findById(id).orElseThrow(() -> new ResourceNotFoundException("plan", id));
    }

    private static void apply(PlanDto request, PlanEntity plan) {
        plan.update(request.name(), request.rateCapacity(), request.ratePeriodMs(),
                request.monthlyQuota());
    }

    static PlanDto toDto(PlanEntity plan) {
        return new PlanDto(plan.getId(), plan.getName(), plan.getRateCapacity(),
                plan.getRatePeriodMs(), plan.getMonthlyQuota());
    }
}
