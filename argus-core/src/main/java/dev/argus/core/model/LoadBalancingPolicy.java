package dev.argus.core.model;

/** Configurable algorithm names; mapped to strategies by LoadBalancingStrategyFactory. */
public enum LoadBalancingPolicy { ROUND_ROBIN, WEIGHTED, LEAST_CONNECTIONS }
