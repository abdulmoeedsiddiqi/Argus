package dev.argus.admin.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of the {@code routes} table. */
@Entity
@Table(name = "routes")
public class RouteEntity {

    @Id
    private String id;

    @Column(name = "path_prefix", nullable = false)
    private String pathPrefix;

    /** Comma-separated method names, for example "GET,POST"; empty means every method. */
    @Column(nullable = false)
    private String methods;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(name = "lb_policy", nullable = false)
    private String lbPolicy;

    @Column(name = "timeout_ms", nullable = false)
    private int timeoutMs;

    @Column(name = "retry_attempts", nullable = false)
    private int retryAttempts;

    @Column(name = "canary_version")
    private String canaryVersion;

    @Column(name = "canary_percent")
    private Integer canaryPercent;

    /** Starts at 1 and is raised by one on every update. */
    @Column(nullable = false)
    private long version;

    /** Required by JPA. */
    protected RouteEntity() { }

    public RouteEntity(String id) {
        this.id = id;
        this.version = 0;
    }

    /** Sets every editable field and raises the version. */
    public void update(
            String newPathPrefix, String newMethods, String newServiceId, String newLbPolicy,
            int newTimeoutMs, int newRetryAttempts, String newCanaryVersion, Integer newCanaryPercent) {
        this.pathPrefix = newPathPrefix;
        this.methods = newMethods;
        this.serviceId = newServiceId;
        this.lbPolicy = newLbPolicy;
        this.timeoutMs = newTimeoutMs;
        this.retryAttempts = newRetryAttempts;
        this.canaryVersion = newCanaryVersion;
        this.canaryPercent = newCanaryPercent;
        this.version++;
    }

    public String getId() {
        return id;
    }

    public String getPathPrefix() {
        return pathPrefix;
    }

    public String getMethods() {
        return methods;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getLbPolicy() {
        return lbPolicy;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    public String getCanaryVersion() {
        return canaryVersion;
    }

    public Integer getCanaryPercent() {
        return canaryPercent;
    }

    public long getVersion() {
        return version;
    }
}
