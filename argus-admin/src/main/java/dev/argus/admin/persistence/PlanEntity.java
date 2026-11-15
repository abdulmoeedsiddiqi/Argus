package dev.argus.admin.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One row of the {@code plans} table: the limits that a plan gives to its consumers. */
@Entity
@Table(name = "plans")
public class PlanEntity {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(name = "rate_capacity", nullable = false)
    private int rateCapacity;

    @Column(name = "rate_period_ms", nullable = false)
    private long ratePeriodMs;

    @Column(name = "monthly_quota", nullable = false)
    private long monthlyQuota;

    /** Required by JPA. */
    protected PlanEntity() { }

    public PlanEntity(String id) {
        this.id = id;
    }

    public void update(String newName, int newRateCapacity, long newRatePeriodMs, long newMonthlyQuota) {
        this.name = newName;
        this.rateCapacity = newRateCapacity;
        this.ratePeriodMs = newRatePeriodMs;
        this.monthlyQuota = newMonthlyQuota;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getRateCapacity() {
        return rateCapacity;
    }

    public long getRatePeriodMs() {
        return ratePeriodMs;
    }

    public long getMonthlyQuota() {
        return monthlyQuota;
    }
}
