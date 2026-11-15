package dev.argus.admin.web;

import dev.argus.admin.api.ApiKeyDto;
import dev.argus.admin.api.ConsumerDto;
import dev.argus.admin.api.IssueKeyRequest;
import dev.argus.admin.api.IssuedKeyDto;
import dev.argus.admin.security.AdminAuthenticationFilter;
import dev.argus.admin.service.ApiKeyService;
import dev.argus.admin.service.ConsumerService;
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

/** REST endpoints for consumers and for issuing their API keys (FR-07). */
@RestController
@RequestMapping("/admin/consumers")
public class ConsumerController {

    private final ConsumerService consumers;
    private final ApiKeyService keys;

    public ConsumerController(ConsumerService consumers, ApiKeyService keys) {
        this.consumers = consumers;
        this.keys = keys;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsumerDto create(
            @Valid @RequestBody ConsumerDto body,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        return consumers.create(body, actor);
    }

    @GetMapping
    public List<ConsumerDto> list() {
        return consumers.list();
    }

    @GetMapping("/{id}")
    public ConsumerDto get(@PathVariable String id) {
        return consumers.get(id);
    }

    @PutMapping("/{id}")
    public ConsumerDto update(
            @PathVariable String id,
            @Valid @RequestBody ConsumerDto body,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        PathIds.requireSame(id, body.id());
        return consumers.update(id, body, actor);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable String id,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        consumers.delete(id, actor);
    }

    /** Issues a new key. The response is the only place where the full key is ever shown. */
    @PostMapping("/{id}/keys")
    @ResponseStatus(HttpStatus.CREATED)
    public IssuedKeyDto issueKey(
            @PathVariable String id,
            @Valid @RequestBody(required = false) IssueKeyRequest body,
            @RequestAttribute(AdminAuthenticationFilter.ACTOR) String actor) {
        return keys.issue(id, body == null ? null : body.expiresAt(), actor);
    }

    @GetMapping("/{id}/keys")
    public List<ApiKeyDto> keysOf(@PathVariable String id) {
        return keys.listOfConsumer(id);
    }
}
