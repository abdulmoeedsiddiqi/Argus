package dev.argus.admin.web;

import dev.argus.admin.api.ApiKeyDto;
import dev.argus.admin.api.IssuedKeyDto;
import dev.argus.admin.security.AdminAuthenticationFilter;
import dev.argus.admin.service.ApiKeyService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for existing API keys: look up, rotate, revoke (FR-07). Keys are issued under
 * their consumer ({@code POST /admin/consumers/{id}/keys}) and are never deleted, only revoked.
 */
@RestController
@RequestMapping("/admin/keys")
public class ApiKeyController {

    private final ApiKeyService keys;

    public ApiKeyController(ApiKeyService keys) {
        this.keys = keys;
    }

    @GetMapping
    public List<ApiKeyDto> list() {
        return keys.list();
    }

    @GetMapping("/{id}")
    public ApiKeyDto get(@PathVariable String id) {
        return keys.get(id);
    }

    @PostMapping("/{id}/revoke")
    public ApiKeyDto revoke(
            @PathVariable String id,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        return keys.revoke(id, actor);
    }

    /** Returns the replacement key in full, this one time only; the old key is revoked. */
    @PostMapping("/{id}/rotate")
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedKeyDto rotate(
            @PathVariable String id,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        return keys.rotate(id, actor);
    }
}
