package dev.argus.admin.web;

import dev.argus.admin.service.ConfigRepublisher;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Maintenance endpoint: send the whole current configuration to the gateways again. */
@RestController
@RequestMapping("/admin/config")
public class ConfigController {

    private final ConfigRepublisher republisher;

    public ConfigController(ConfigRepublisher republisher) {
        this.republisher = republisher;
    }

    /** Changes no data, so it writes no audit record. */
    @PostMapping("/republish")
    public Map<String, Integer> republish() {
        return Map.of("published", republisher.republishAll());
    }
}
