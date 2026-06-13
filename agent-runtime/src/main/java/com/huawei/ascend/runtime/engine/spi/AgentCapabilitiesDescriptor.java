package com.huawei.ascend.runtime.engine.spi;

/**
 * Protocol-neutral descriptor for the capabilities advertised by an agent card.
 *
 * <p>All fields default to {@code false}; the A2A mapper sets the actual
 * wire values. This type carries zero {@code org.a2aproject} imports.
 */
public record AgentCapabilitiesDescriptor(
        boolean streaming,
        boolean pushNotifications,
        boolean extendedAgentCard) {

    /** Default capabilities: streaming on, pushNotifications on, extended off (matching the current default card). */
    public static AgentCapabilitiesDescriptor defaults() {
        return new AgentCapabilitiesDescriptor(true, true, false);
    }

    /** All flags false — useful as an explicit zero baseline. */
    public static AgentCapabilitiesDescriptor none() {
        return new AgentCapabilitiesDescriptor(false, false, false);
    }
}
