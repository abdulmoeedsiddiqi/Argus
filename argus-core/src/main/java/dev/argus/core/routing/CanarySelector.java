package dev.argus.core.routing;

import dev.argus.core.model.CanarySplit;
import dev.argus.core.model.UpstreamInstance;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntUnaryOperator;

/**
 * Canary routing (FR-04): decides, per request, whether the request goes to the canary version
 * or to the stable version of a service, and keeps only the instances of that version. The
 * route's load-balancing strategy then picks one of them.
 *
 * <p>Stateless, so it is thread-safe.
 */
public final class CanarySelector {

    private static final int PERCENT_SCALE = 100;

    /** Returns a random int in [0, bound). */
    private final IntUnaryOperator randomBelow;

    public CanarySelector() {
        this(bound -> ThreadLocalRandom.current().nextInt(bound));
    }

    /** For tests: a seeded or fixed random source makes the choice repeatable. */
    CanarySelector(IntUnaryOperator randomBelow) {
        this.randomBelow = Objects.requireNonNull(randomBelow, "randomBelow");
    }

    /**
     * Returns the instances this request may use. Without a split, all of them. With a split,
     * a number 0..99 is drawn: below the canary percentage the canary instances are returned,
     * otherwise the stable ones. If the chosen version has no healthy instance, the other
     * version is used, so a broken canary never takes the route down.
     */
    public List<UpstreamInstance> filterByVersion(List<UpstreamInstance> instances, CanarySplit split) {
        if (split == null) {
            return instances;
        }
        List<UpstreamInstance> canary = instances.stream()
                .filter(instance -> split.canaryVersion().equals(instance.version()))
                .toList();
        List<UpstreamInstance> stable = instances.stream()
                .filter(instance -> !split.canaryVersion().equals(instance.version()))
                .toList();
        boolean goCanary = randomBelow.applyAsInt(PERCENT_SCALE) < split.canaryPercent();
        List<UpstreamInstance> chosen = goCanary ? canary : stable;
        List<UpstreamInstance> other = goCanary ? stable : canary;
        return chosen.isEmpty() ? other : chosen;
    }
}
