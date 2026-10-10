package dev.argus.core.pipeline;

import java.util.Objects;

/** Outcome of running the pipeline: either an upstream response or a rejection. */
public sealed interface FilterResult permits FilterResult.Forwarded, FilterResult.Rejected {

    record Forwarded(UpstreamResponse response) implements FilterResult {
        public Forwarded {
            Objects.requireNonNull(response, "response");
        }
    }

    record Rejected(int httpStatus, RejectionReason reason, String message) implements FilterResult {
        public Rejected {
            Objects.requireNonNull(reason, "reason");
            if (httpStatus < 400 || httpStatus > 599) {
                throw new IllegalArgumentException("rejection must be a 4xx/5xx status: " + httpStatus);
            }
        }
    }
}
