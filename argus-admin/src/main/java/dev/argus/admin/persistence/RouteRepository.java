package dev.argus.admin.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/** Database access for routes; Spring Data derives the query from the method name. */
public interface RouteRepository extends JpaRepository<RouteEntity, String> {

    /** True if another route (not the one with this id) already uses the path prefix. */
    boolean existsByPathPrefixAndIdNot(String pathPrefix, String id);
}
