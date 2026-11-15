package dev.argus.admin.service;

import dev.argus.admin.persistence.ApiKeyEntity;
import dev.argus.admin.persistence.PlanEntity;
import dev.argus.admin.persistence.RouteEntity;
import dev.argus.admin.persistence.UpstreamInstanceEntity;
import dev.argus.contracts.ApiKeyConfig;
import dev.argus.contracts.PlanConfig;
import dev.argus.contracts.RouteConfig;
import dev.argus.contracts.UpstreamConfig;
import java.util.Arrays;
import java.util.List;

/**
 * Turns stored entities into the payload records of argus-contracts, the shape in which the
 * gateways receive them. Kept in one place so that the "wire format" is easy to find.
 */
final class ConfigPayloads {

    private ConfigPayloads() { }

    static PlanConfig plan(PlanEntity plan) {
        return new PlanConfig(plan.getId(), plan.getName(), plan.getRateCapacity(),
                plan.getRatePeriodMs(), plan.getMonthlyQuota());
    }

    static RouteConfig route(RouteEntity route) {
        List<String> methods = route.getMethods().isBlank()
                ? List.of()
                : Arrays.asList(route.getMethods().split(","));
        return new RouteConfig(
                route.getId(), route.getPathPrefix(), methods, route.getServiceId(),
                route.getLbPolicy(), route.getTimeoutMs(), route.getRetryAttempts(),
                route.getCanaryVersion(), route.getCanaryPercent(), route.getVersion());
    }

    static UpstreamConfig upstream(UpstreamInstanceEntity upstream) {
        return new UpstreamConfig(upstream.getId(), upstream.getServiceId(), upstream.getBaseUri(),
                upstream.getWeight(), upstream.getVersion());
    }

    /** The hash and metadata of a key, never the key. The plan comes from the key's consumer. */
    static ApiKeyConfig apiKey(ApiKeyEntity key, String planId) {
        return new ApiKeyConfig(
                key.getId(), key.getKeyHash(), key.getKeyPrefix(), key.getConsumerId(), planId,
                key.isRevoked(), key.getExpiresAt());
    }
}
