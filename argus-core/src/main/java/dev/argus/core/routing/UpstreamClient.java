package dev.argus.core.routing;

import dev.argus.core.exception.UpstreamTimeoutException;
import dev.argus.core.pipeline.IncomingRequest;
import dev.argus.core.pipeline.UpstreamResponse;
import java.time.Duration;

/**
 * Port: sends a request to one upstream instance. The gateway module provides
 * JdkHttpUpstreamClient (java.net.http on virtual threads); tests use a fake.
 */
public interface UpstreamClient {

    UpstreamResponse send(IncomingRequest request, UpstreamInstance target, Duration timeout)
            throws UpstreamTimeoutException;
}
