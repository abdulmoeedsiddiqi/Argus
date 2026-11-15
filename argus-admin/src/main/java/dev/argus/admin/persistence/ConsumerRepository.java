package dev.argus.admin.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for consumers; Spring Data derives the query from the method name. */
public interface ConsumerRepository extends JpaRepository<ConsumerEntity, String> {

    boolean existsByPlanId(String planId);
}
