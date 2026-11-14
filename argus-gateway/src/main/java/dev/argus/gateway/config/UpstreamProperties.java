package dev.argus.gateway.config;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Walking-skeleton configuration: the single upstream every request is forwarded to
 * ({@code argus.upstream.url}, environment variable {@code ARGUS_UPSTREAM_URL}).
 * Replaced by routes from the configuration topic when module M5 is built.
 */
@ConfigurationProperties("argus.upstream")
public record UpstreamProperties(URI url, @DefaultValue("5s") Duration timeout) {

    public UpstreamProperties {
        Objects.requireNonNull(url, "argus.upstream.url must be set");
        Objects.requireNonNull(timeout, "argus.upstream.timeout");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("argus.upstream.timeout must be positive");
        }
    }
}
