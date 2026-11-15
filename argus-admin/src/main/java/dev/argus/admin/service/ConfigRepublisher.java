package dev.argus.admin.service;

import dev.argus.admin.events.ConfigChangeRelay;
import dev.argus.admin.events.ConfigNotPropagatedException;
import dev.argus.admin.persistence.ApiKeyEntity;
import dev.argus.admin.persistence.ApiKeyRepository;
import dev.argus.admin.persistence.ConsumerEntity;
import dev.argus.admin.persistence.ConsumerRepository;
import dev.argus.admin.persistence.PlanEntity;
import dev.argus.admin.persistence.PlanRepository;
import dev.argus.admin.persistence.RouteEntity;
import dev.argus.admin.persistence.RouteRepository;
import dev.argus.admin.persistence.UpstreamInstanceEntity;
import dev.argus.admin.persistence.UpstreamInstanceRepository;
import dev.argus.contracts.ConfigChange.EntityType;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the current state of every stored entity again.
 *
 * <p>This is the repair for the one gap of "publish after commit": if Kafka was unreachable
 * right after a commit, the database is ahead of the topic. Because the topic is compacted and
 * the gateways simply apply the latest record per key, sending everything again is harmless
 * and brings them back in line. It runs when the Admin API starts and on request
 * ({@code POST /admin/config/republish}).
 */
@Service
public class ConfigRepublisher implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigRepublisher.class);

    private final PlanRepository plans;
    private final ConsumerRepository consumers;
    private final ApiKeyRepository keys;
    private final RouteRepository routes;
    private final UpstreamInstanceRepository upstreams;
    private final ConfigChangeRelay relay;

    public ConfigRepublisher(
            PlanRepository plans, ConsumerRepository consumers, ApiKeyRepository keys,
            RouteRepository routes, UpstreamInstanceRepository upstreams, ConfigChangeRelay relay) {
        this.plans = plans;
        this.consumers = consumers;
        this.keys = keys;
        this.routes = routes;
        this.upstreams = upstreams;
        this.relay = relay;
    }

    /** At start-up: bring the topic in line with the database. */
    @Override
    public void run(ApplicationArguments args) {
        try {
            LOG.info("republished {} configuration entities at start-up", republishAll());
        } catch (ConfigNotPropagatedException e) {
            LOG.warn("configuration could not be republished at start-up: {}", e.getMessage());
        }
    }

    /** Sends every plan, upstream instance, route and key; returns how many were sent. */
    @Transactional(readOnly = true)
    public int republishAll() {
        int published = 0;
        for (PlanEntity plan : plans.findAll()) {
            relay.afterCommit(EntityType.PLAN, plan.getId(), 0, ConfigPayloads.plan(plan));
            published++;
        }
        for (UpstreamInstanceEntity upstream : upstreams.findAll()) {
            relay.afterCommit(
                    EntityType.UPSTREAM, upstream.getId(), 0, ConfigPayloads.upstream(upstream));
            published++;
        }
        for (RouteEntity route : routes.findAll()) {
            relay.afterCommit(EntityType.ROUTE, route.getId(), 0, ConfigPayloads.route(route));
            published++;
        }
        Map<String, String> planOfConsumer = consumers.findAll().stream()
                .collect(Collectors.toMap(ConsumerEntity::getId, ConsumerEntity::getPlanId));
        for (ApiKeyEntity key : keys.findAll()) {
            relay.afterCommit(EntityType.API_KEY, key.getId(), 0,
                    ConfigPayloads.apiKey(key, planOfConsumer.get(key.getConsumerId())));
            published++;
        }
        return published;
    }
}
