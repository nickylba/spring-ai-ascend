/**
 * agent-bus async inter-agent messaging — the IN-PROCESS plane only.
 *
 * <p>{@link com.huawei.ascend.bus.messaging.AgentMessageBus} carries
 * asynchronous messages between agents co-hosted on one runtime JVM, with
 * tenant-scoped topics, bounded per-subscriber queues, and contained handler
 * failures. It is NOT a cross-process transport: agent-to-agent communication
 * across process boundaries remains A2A through the service facade
 * (A2A-NO-REWRITE). Durable broker-backed transports plug in behind the same
 * SPI later.
 *
 * <p>Authority: ADR-0163.
 */
package com.huawei.ascend.bus.messaging;
