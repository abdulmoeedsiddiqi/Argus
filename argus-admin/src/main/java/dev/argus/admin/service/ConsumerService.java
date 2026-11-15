package dev.argus.admin.service;

import dev.argus.admin.api.ConsumerDto;
import dev.argus.admin.persistence.ApiKeyRepository;
import dev.argus.admin.persistence.ConsumerEntity;
import dev.argus.admin.persistence.ConsumerRepository;
import dev.argus.admin.persistence.PlanRepository;
import dev.argus.core.exception.InvalidConfigurationException;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consumers: the rules behind create, read, update and delete. One transaction per change. */
@Service
public class ConsumerService {

    static final String ENTITY = "CONSUMER";

    private final ConsumerRepository consumers;
    private final PlanRepository plans;
    private final ApiKeyRepository keys;
    private final AuditRecorder audit;
    private final Clock clock;

    public ConsumerService(
            ConsumerRepository consumers, PlanRepository plans, ApiKeyRepository keys,
            AuditRecorder audit, Clock clock) {
        this.consumers = consumers;
        this.plans = plans;
        this.keys = keys;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public ConsumerDto create(ConsumerDto request, String actor) {
        if (consumers.existsById(request.id())) {
            throw new ResourceConflictException("consumer " + request.id() + " already exists");
        }
        requirePlan(request.planId());
        ConsumerEntity consumer = new ConsumerEntity(request.id(), clock.instant());
        consumer.update(request.name(), request.planId());
        consumers.save(consumer);
        audit.record(actor, "CREATE", ENTITY, consumer.getId());
        return toDto(consumer);
    }

    @Transactional
    public ConsumerDto update(String id, ConsumerDto request, String actor) {
        ConsumerEntity consumer = find(id);
        requirePlan(request.planId());
        consumer.update(request.name(), request.planId());
        audit.record(actor, "UPDATE", ENTITY, id);
        return toDto(consumer);
    }

    @Transactional
    public void delete(String id, String actor) {
        ConsumerEntity consumer = find(id);
        if (keys.existsByConsumerId(id)) {
            // Keys are kept for the record even after they are revoked.
            throw new ResourceConflictException(
                    "consumer " + id + " has API keys; revoke them instead of deleting the consumer");
        }
        consumers.delete(consumer);
        audit.record(actor, "DELETE", ENTITY, id);
    }

    @Transactional(readOnly = true)
    public ConsumerDto get(String id) {
        return toDto(find(id));
    }

    @Transactional(readOnly = true)
    public List<ConsumerDto> list() {
        return consumers.findAll().stream().map(ConsumerService::toDto).toList();
    }

    private ConsumerEntity find(String id) {
        return consumers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("consumer", id));
    }

    private void requirePlan(String planId) {
        if (!plans.existsById(planId)) {
            throw new InvalidConfigurationException("plan " + planId + " does not exist");
        }
    }

    static ConsumerDto toDto(ConsumerEntity consumer) {
        return new ConsumerDto(
                consumer.getId(), consumer.getName(), consumer.getPlanId(), consumer.getCreatedAt());
    }
}
