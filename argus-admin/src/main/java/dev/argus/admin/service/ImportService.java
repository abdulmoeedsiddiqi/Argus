package dev.argus.admin.service;

import dev.argus.admin.api.ConsumerDto;
import dev.argus.admin.api.ImportDocument;
import dev.argus.admin.api.ImportDocument.ConsumerEntry;
import dev.argus.admin.api.ImportResult;
import dev.argus.admin.api.IssuedKeyDto;
import dev.argus.admin.api.PlanDto;
import dev.argus.admin.api.RouteDto;
import dev.argus.admin.api.UpstreamDto;
import dev.argus.admin.persistence.ApiKeyRepository;
import dev.argus.admin.persistence.ConsumerRepository;
import dev.argus.admin.persistence.PlanRepository;
import dev.argus.admin.persistence.RouteRepository;
import dev.argus.admin.persistence.UpstreamInstanceRepository;
import dev.argus.core.exception.InvalidConfigurationException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies a whole configuration document (the seed file) in one transaction.
 *
 * <p>It adds no rules of its own. Every entry goes through the same service method as the
 * matching single endpoint, so it is validated, audited and published in exactly the same
 * way. An entry that already exists is updated, which makes the import safe to repeat. If any
 * entry is rejected, the transaction rolls back: nothing is stored and nothing is published.
 */
@Service
public class ImportService {

    private final PlanService planService;
    private final UpstreamService upstreamService;
    private final RouteService routeService;
    private final ConsumerService consumerService;
    private final ApiKeyService keyService;
    private final PlanRepository plans;
    private final UpstreamInstanceRepository upstreams;
    private final RouteRepository routes;
    private final ConsumerRepository consumers;
    private final ApiKeyRepository keys;
    private final Validator validator;

    public ImportService(
            PlanService planService, UpstreamService upstreamService, RouteService routeService,
            ConsumerService consumerService, ApiKeyService keyService, PlanRepository plans,
            UpstreamInstanceRepository upstreams, RouteRepository routes,
            ConsumerRepository consumers, ApiKeyRepository keys, Validator validator) {
        this.planService = planService;
        this.upstreamService = upstreamService;
        this.routeService = routeService;
        this.consumerService = consumerService;
        this.keyService = keyService;
        this.plans = plans;
        this.upstreams = upstreams;
        this.routes = routes;
        this.consumers = consumers;
        this.keys = keys;
        this.validator = validator;
    }

    @Transactional
    public ImportResult apply(ImportDocument document, String actor) {
        validate(document);
        int created = 0;
        int updated = 0;
        List<IssuedKeyDto> issuedKeys = new ArrayList<>();

        // Plans before consumers: a consumer refers to its plan.
        for (PlanDto plan : document.plans()) {
            if (plans.existsById(plan.id())) {
                planService.update(plan.id(), plan, actor);
                updated++;
            } else {
                planService.create(plan, actor);
                created++;
            }
        }
        for (UpstreamDto upstream : document.upstreams()) {
            if (upstreams.existsById(upstream.id())) {
                upstreamService.update(upstream.id(), upstream, actor);
                updated++;
            } else {
                upstreamService.create(upstream, actor);
                created++;
            }
        }
        for (RouteDto route : document.routes()) {
            if (routes.existsById(route.id())) {
                routeService.update(route.id(), route, actor);
                updated++;
            } else {
                routeService.create(route, actor);
                created++;
            }
        }
        for (ConsumerEntry entry : document.consumers()) {
            ConsumerDto consumer = entry.toDto();
            if (consumers.existsById(consumer.id())) {
                consumerService.update(consumer.id(), consumer, actor);
                updated++;
            } else {
                consumerService.create(consumer, actor);
                created++;
            }
            if (entry.wantsKey() && !keys.existsByConsumerIdAndRevokedFalse(consumer.id())) {
                issuedKeys.add(keyService.issue(consumer.id(), null, actor));
            }
        }
        return new ImportResult(created, updated, issuedKeys);
    }

    /** Checks every entry with the same Bean Validation rules as the single endpoints. */
    private void validate(ImportDocument document) {
        List<String> problems = new ArrayList<>();
        check("plans", document.plans(), problems);
        check("upstreams", document.upstreams(), problems);
        check("routes", document.routes(), problems);
        check("consumers", document.consumers().stream().map(ConsumerEntry::toDto).toList(), problems);
        if (!problems.isEmpty()) {
            throw new InvalidConfigurationException("the document is invalid: " + problems);
        }
    }

    private <T> void check(String section, List<T> entries, List<String> problems) {
        for (int index = 0; index < entries.size(); index++) {
            Set<ConstraintViolation<T>> violations = validator.validate(entries.get(index));
            for (ConstraintViolation<T> violation : violations) {
                problems.add(section + "[" + index + "]." + violation.getPropertyPath()
                        + " " + violation.getMessage());
            }
        }
    }
}
