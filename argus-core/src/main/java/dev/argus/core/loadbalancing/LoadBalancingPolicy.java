package dev.argus.core.loadbalancing;

/** Configurable algorithm names; mapped to strategies by LoadBalancingStrategyFactory. */
public enum LoadBalancingPolicy { ROUND_ROBIN, WEIGHTED, LEAST_CONNECTIONS }
