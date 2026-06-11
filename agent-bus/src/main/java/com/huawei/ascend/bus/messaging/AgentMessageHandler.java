package com.huawei.ascend.bus.messaging;

/**
 * Callback invoked for each message delivered to a subscription. Exceptions
 * thrown here are contained by the bus implementation — they never disturb
 * other subscribers or later messages on the topic.
 */
@FunctionalInterface
public interface AgentMessageHandler {

    void onMessage(AgentMessage message);
}
