package dev.argus.admin.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One row of the {@code api_keys} table. There is no field for the key itself: only its
 * SHA-256 hash and a short display prefix are ever stored (QR-11).
 */
@Entity
@Table(name = "api_keys")
public class ApiKeyEntity {

    @Id
    private String id;

    @Column(name = "consumer_id", nullable = false, updatable = false)
    private String consumerId;

    /** SHA-256 of the key as 64 hex characters (column type CHAR(64)). */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "key_hash", nullable = false, updatable = false, length = 64)
    private String keyHash;

    @Column(name = "key_prefix", nullable = false, updatable = false)
    private String keyPrefix;

    @Column(nullable = false)
    private boolean revoked;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA. */
    protected ApiKeyEntity() { }

    public ApiKeyEntity(
            String id, String consumerId, String keyHash, String keyPrefix,
            Instant expiresAt, Instant createdAt) {
        this.id = id;
        this.consumerId = consumerId;
        this.keyHash = keyHash;
        this.keyPrefix = keyPrefix;
        this.expiresAt = expiresAt;
        this.createdAt = createdAt;
    }

    public void revoke() {
        this.revoked = true;
    }

    public String getId() {
        return id;
    }

    public String getConsumerId() {
        return consumerId;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public boolean isRevoked() {
        return revoked;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
