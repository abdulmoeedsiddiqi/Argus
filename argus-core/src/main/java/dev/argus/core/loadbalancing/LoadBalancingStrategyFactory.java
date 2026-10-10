package dev.argus.core.loadbalancing;

/** Factory Method: turns a configured policy into a strategy, hiding concrete classes from callers. */
public interface LoadBalancingStrategyFactory {

    LoadBalancingStrategy create(LoadBalancingPolicy policy);
}
