package dev.argus.admin.web;

import dev.argus.admin.api.RouteDto;
import dev.argus.admin.security.AdminAuthenticationFilter;
import dev.argus.admin.service.RouteService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** REST endpoints for routes (FR-07, UC-04). */
@RestController
@RequestMapping("/admin/routes")
public class RouteController {

    private final RouteService routes;

    public RouteController(RouteService routes) {
        this.routes = routes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RouteDto create(
            @Valid @RequestBody RouteDto body,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        return routes.create(body, actor);
    }

    @GetMapping
    public List<RouteDto> list() {
        return routes.list();
    }

    @GetMapping("/{id}")
    public RouteDto get(@PathVariable String id) {
        return routes.get(id);
    }

    @PutMapping("/{id}")
    public RouteDto update(
            @PathVariable String id,
            @Valid @RequestBody RouteDto body,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        PathIds.requireSame(id, body.id());
        return routes.update(id, body, actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String id,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        routes.delete(id, actor);
    }
}
