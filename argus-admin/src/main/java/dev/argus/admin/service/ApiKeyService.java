package dev.argus.admin.service;

import dev.argus.admin.api.ApiKeyDto;
import dev.argus.admin.api.IssuedKeyDto;
import dev.argus.admin.persistence.ApiKeyEntity;
import dev.argus.admin.persistence.ApiKeyRepository;
import dev.argus.admin.persistence.ConsumerRepository;
import dev.argus.core.auth.ApiKeyHasher;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API key lifecycle: issue, rotate, revoke (module M2 on the admin side).
 *
 * <p>The full key exists only inside {@link #issue}: it is generated, hashed with SHA-256,
 * returned once to the caller, and then forgotten. Only the hash and a prefix are stored.
 */
@Service
public class ApiKeyService {

    static final String ENTITY = "API_KEY";

    private final ApiKeyRepository keys;
    private final ConsumerRepository consumers;
    private final ApiKeyGenerator generator;
    private final ApiKeyHasher hasher;
    private final AuditRecorder audit;
    private final Clock clock;

    public ApiKeyService(
            ApiKeyRepository keys, ConsumerRepository consumers, ApiKeyGenerator generator,
            ApiKeyHasher hasher, AuditRecorder audit, Clock clock) {
        this.keys = keys;
        this.consumers = consumers;
        this.generator = generator;
        this.hasher = hasher;
        this.audit = audit;
        this.clock = clock;
    }

    /** Creates a key for a consumer and returns it in full, this one time only. */
    @Transactional
    public IssuedKeyDto issue(String consumerId, Instant expiresAt, String actor) {
        if (!consumers.existsById(consumerId)) {
            throw new ResourceNotFoundException("consumer", consumerId);
        }
        String key = generator.newKey();
        ApiKeyEntity entity = keys.save(new ApiKeyEntity(
                generator.newKeyId(), consumerId, hasher.hash(key), generator.prefixOf(key),
                expiresAt, clock.instant()));
        audit.record(actor, "ISSUE", ENTITY, entity.getId());
        return new IssuedKeyDto(
                entity.getId(), key, entity.getConsumerId(), entity.getKeyPrefix(),
                entity.isRevoked(), entity.getExpiresAt(), entity.getCreatedAt());
    }

    /** Marks a key as revoked. Revoking an already revoked key changes nothing. */
    @Transactional
    public ApiKeyDto revoke(String keyId, String actor) {
        ApiKeyEntity key = find(keyId);
        if (!key.isRevoked()) {
            key.revoke();
            audit.record(actor, "REVOKE", ENTITY, keyId);
        }
        return toDto(key);
    }

    /**
     * Replaces a key: issues a new one for the same consumer (same expiry) and revokes the
     * old one, both in one transaction, so there is never a moment with no key or two keys.
     */
    @Transactional
    public IssuedKeyDto rotate(String keyId, String actor) {
        ApiKeyEntity old = find(keyId);
        if (old.isRevoked()) {
            throw new ResourceConflictException(
                    "key " + keyId + " is revoked and cannot be rotated; issue a new key instead");
        }
        IssuedKeyDto replacement = issue(old.getConsumerId(), old.getExpiresAt(), actor);
        old.revoke();
        audit.record(actor, "ROTATE", ENTITY, keyId);
        return replacement;
    }

    @Transactional(readOnly = true)
    public ApiKeyDto get(String keyId) {
        return toDto(find(keyId));
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDto> list() {
        return keys.findAll().stream().map(ApiKeyService::toDto).toList();
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDto> listOfConsumer(String consumerId) {
        if (!consumers.existsById(consumerId)) {
            throw new ResourceNotFoundException("consumer", consumerId);
        }
        return keys.findByConsumerIdOrderByCreatedAtAsc(consumerId).stream()
                .map(ApiKeyService::toDto).toList();
    }

    private ApiKeyEntity find(String keyId) {
        return keys.findById(keyId).orElseThrow(() -> new ResourceNotFoundException("key", keyId));
    }

    static ApiKeyDto toDto(ApiKeyEntity key) {
        return new ApiKeyDto(
                key.getId(), key.getConsumerId(), key.getKeyPrefix(), key.isRevoked(),
                key.getExpiresAt(), key.getCreatedAt());
    }
}
