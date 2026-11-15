package dev.argus.admin.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** One row of the {@code consumers} table: a client organisation and the plan it is on. */
@Entity
@Table(name = "consumers")
public class ConsumerEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    /** Foreign key to plans.id, enforced by the database. */
    @Column(name = "plan_id", nullable = false)
    private String planId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected ConsumerEntity() { }

    public ConsumerEntity(String id, Instant createdAt) {
        this.id = id;
        this.createdAt = createdAt;
    }

    public void update(String newName, String newPlanId) {
        this.name = newName;
        this.planId = newPlanId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getPlanId() {
        return planId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
