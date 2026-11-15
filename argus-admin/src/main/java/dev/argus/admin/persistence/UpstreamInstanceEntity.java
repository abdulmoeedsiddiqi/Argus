package dev.argus.admin.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of the {@code upstream_instances} table: one backend copy of a service. */
@Entity
@Table(name = "upstream_instances")
public class UpstreamInstanceEntity {

    @Id
    private String id;

    @Column(name = "service_id", nullable = false)
    private String serviceId;

    @Column(name = "base_uri", nullable = false)
    private String baseUri;

    @Column(nullable = false)
    private int weight;

    @Column(nullable = false)
    private String version;

    /** Required by JPA. */
    protected UpstreamInstanceEntity() { }

    public UpstreamInstanceEntity(String id) {
        this.id = id;
    }

    public void update(String newServiceId, String newBaseUri, int newWeight, String newVersion) {
        this.serviceId = newServiceId;
        this.baseUri = newBaseUri;
        this.weight = newWeight;
        this.version = newVersion;
    }

    public String getId() {
        return id;
    }

    public String getServiceId() {
        return serviceId;
    }

    public String getBaseUri() {
        return baseUri;
    }

    public int getWeight() {
        return weight;
    }

    public String getVersion() {
        return version;
    }
}
