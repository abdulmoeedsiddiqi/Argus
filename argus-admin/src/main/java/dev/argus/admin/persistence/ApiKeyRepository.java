package dev.argus.admin.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for API keys; Spring Data derives the queries from the method names. */
public interface ApiKeyRepository extends JpaRepository<ApiKeyEntity, String> {

    List<ApiKeyEntity> findByConsumerIdOrderByCreatedAtAsc(String consumerId);

    boolean existsByConsumerId(String consumerId);

    boolean existsByConsumerIdAndRevokedFalse(String consumerId);
}
