package dev.argus.admin.web;

import dev.argus.admin.api.PlanDto;
import dev.argus.admin.security.AdminAuthenticationFilter;
import dev.argus.admin.service.PlanService;
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

/** REST endpoints for plans (FR-07). The controller only translates HTTP; rules are in the service. */
@RestController
@RequestMapping("/admin/plans")
public class PlanController {

    private final PlanService plans;

    public PlanController(PlanService plans) {
        this.plans = plans;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlanDto create(
            @Valid @RequestBody PlanDto body,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        return plans.create(body, actor);
    }

    @GetMapping
    public List<PlanDto> list() {
        return plans.list();
    }

    @GetMapping("/{id}")
    public PlanDto get(@PathVariable String id) {
        return plans.get(id);
    }

    @PutMapping("/{id}")
    public PlanDto update(
            @PathVariable String id,
            @Valid @RequestBody PlanDto body,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        PathIds.requireSame(id, body.id());
        return plans.update(id, body, actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String id,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        plans.delete(id, actor);
    }
}
