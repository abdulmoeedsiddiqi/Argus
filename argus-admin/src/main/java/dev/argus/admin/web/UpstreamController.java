package dev.argus.admin.web;

import dev.argus.admin.api.UpstreamDto;
import dev.argus.admin.security.AdminAuthenticationFilter;
import dev.argus.admin.service.UpstreamService;
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

/** REST endpoints for upstream instances, the backend copies behind a route (FR-07). */
@RestController
@RequestMapping("/admin/upstreams")
public class UpstreamController {

    private final UpstreamService upstreams;

    public UpstreamController(UpstreamService upstreams) {
        this.upstreams = upstreams;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UpstreamDto create(
            @Valid @RequestBody UpstreamDto body,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        return upstreams.create(body, actor);
    }

    @GetMapping
    public List<UpstreamDto> list() {
        return upstreams.list();
    }

    @GetMapping("/{id}")
    public UpstreamDto get(@PathVariable String id) {
        return upstreams.get(id);
    }

    @PutMapping("/{id}")
    public UpstreamDto update(
            @PathVariable String id,
            @Valid @RequestBody UpstreamDto body,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        PathIds.requireSame(id, body.id());
        return upstreams.update(id, body, actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String id,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        upstreams.delete(id, actor);
    }
}
