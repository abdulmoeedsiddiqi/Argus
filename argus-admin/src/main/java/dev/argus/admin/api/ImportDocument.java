package dev.argus.admin.api;

import java.util.List;

/**
 * A whole configuration in one document, as written in deploy/seed/demo-config.yaml and sent
 * to POST /admin/import. Each entry has the same fields as the matching single endpoint.
 */
public record ImportDocument(
        List<PlanDto> plans,
        List<UpstreamDto> upstreams,
        List<RouteDto> routes,
        List<ConsumerEntry> consumers) {

    /** Missing sections are simply empty. */
    public ImportDocument {
        plans = plans == null ? List.of() : List.copyOf(plans);
        upstreams = upstreams == null ? List.of() : List.copyOf(upstreams);
        routes = routes == null ? List.of() : List.copyOf(routes);
        consumers = consumers == null ? List.of() : List.copyOf(consumers);
    }

    /**
     * A consumer in a seed document.
     *
     * @param issueKey if true, a key is issued for the consumer unless it already has a
     *                 working (not revoked) key, so importing twice does not pile up keys
     */
    public record ConsumerEntry(String id, String name, String planId, Boolean issueKey) {

        public ConsumerDto toDto() {
            return new ConsumerDto(id, name, planId, null);
        }

        public boolean wantsKey() {
            return Boolean.TRUE.equals(issueKey);
        }
    }
}
