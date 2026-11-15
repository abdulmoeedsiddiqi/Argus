package dev.argus.gateway.config;

import dev.argus.gateway.events.ReadinessGate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Where the gateway's routes and keys come from, and when it may start serving. */
@Configuration
public class ConfigSourceConfiguration {

    /** Static configuration from a file is complete at start-up, so the gate is open. */
    @Bean
    ReadinessGate readinessGate() {
        return ReadinessGate.alreadyOpen();
    }
}
