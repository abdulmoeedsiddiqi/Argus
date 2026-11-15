package dev.argus.admin.service;

import dev.argus.admin.api.PlanDto;
import dev.argus.admin.persistence.ConsumerRepository;
import dev.argus.admin.persistence.PlanEntity;
import dev.argus.admin.persistence.PlanRepository;
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

    public PlanService(PlanRepository plans, ConsumerRepository consumers, AuditRecorder audit) {
        this.plans = plans;
        this.consumers = consumers;
        this.audit = audit;
    }

    @Transactional
    public PlanDto create(PlanDto request, String actor) {
        if (plans.existsById(request.id())) {
            throw new ResourceConflictException("plan " + request.id() + " already exists");
        }
        PlanEntity plan = new PlanEntity(request.id());
        apply(request, plan);
        plans.save(plan);
        audit.record(actor, "CREATE", ENTITY, plan.getId());
        return toDto(plan);
    }

    @Transactional
    public PlanDto update(String id, PlanDto request, String actor) {
        PlanEntity plan = find(id);
        apply(request, plan);
        audit.record(actor, "UPDATE", ENTITY, id);
        return toDto(plan);
    }

    @Transactional
    public void delete(String id, String actor) {
        PlanEntity plan = find(id);
        if (consumers.existsByPlanId(id)) {
            throw new ResourceConflictException("plan " + id + " is still used by consumers");
        }
        plans.delete(plan);
        audit.record(actor, "DELETE", ENTITY, id);
    }

    @Transactional(readOnly = true)
    public PlanDto get(String id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public List<PlanDto> list() {
        return plans.findAll().stream().map(PlanService::toDto).toList();
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
