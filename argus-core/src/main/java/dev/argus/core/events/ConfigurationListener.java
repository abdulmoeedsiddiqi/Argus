package dev.argus.core.events;

import dev.argus.contracts.ConfigChange;

/** Observer: notified for every configuration change read from the config topic. */
@FunctionalInterface
public interface ConfigurationListener {

    void onChange(ConfigChange change);
}
