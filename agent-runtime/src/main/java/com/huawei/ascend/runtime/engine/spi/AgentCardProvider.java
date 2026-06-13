package com.huawei.ascend.runtime.engine.spi;

/**
 * Provides the protocol-neutral agent card descriptor for one runtime-hosted agent.
 *
 * <p>This is separated from {@link AgentRuntimeHandler}: executing an agent
 * and describing its public metadata are two different capabilities. If a bean
 * of this type is registered, the A2A bridge uses it (via
 * {@code engine.a2a.A2aAgentCardMapper}) to produce the wire card; otherwise
 * the auto-configuration derives a default card from {@code AgentCardProperties}.
 *
 * <p>A concrete handler may implement both interfaces when that is the simplest
 * shape. This interface lives in the neutral SPI package (zero {@code org.a2aproject}
 * imports) so adapters and handlers can implement it without depending on the
 * protocol bridge.
 */
public interface AgentCardProvider {

    /** Returns the neutral descriptor for the agent card exposed by this runtime instance. */
    AgentCardDescriptor describe();
}
