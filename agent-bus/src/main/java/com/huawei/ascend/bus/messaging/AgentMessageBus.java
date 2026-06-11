package com.huawei.ascend.bus.messaging;

/**
 * SPI for asynchronous in-process messaging between co-hosted agents.
 * Topics are tenant-scoped: a subscriber registered for one tenant never
 * receives another tenant's messages, even on an identical topic name.
 *
 * <p>This is the in-process plane only; cross-process agent communication is
 * A2A through the service facade (see {@link com.huawei.ascend.bus.messaging
 * package docs}). Authority: ADR-0163.
 */
public interface AgentMessageBus {

    /** Publishes a message to its (tenant, topic) subscribers asynchronously. */
    void publish(AgentMessage message);

    /**
     * Registers a handler for all future messages on {@code (tenantId, topic)}.
     * Delivery per topic is ordered; a slow subscriber is bounded by its own
     * queue and cannot block other subscribers indefinitely beyond shared
     * dispatcher scheduling.
     */
    Subscription subscribe(String tenantId, String topic, AgentMessageHandler handler);
}
