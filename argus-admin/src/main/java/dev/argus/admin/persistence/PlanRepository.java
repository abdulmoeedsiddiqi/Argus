package dev.argus.admin.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for plans; Spring Data writes the implementation. */
public interface PlanRepository extends JpaRepository<PlanEntity, String> { }
