package dev.argus.admin.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for upstream instances; Spring Data writes the implementation. */
public interface UpstreamInstanceRepository extends JpaRepository<UpstreamInstanceEntity, String> { }
