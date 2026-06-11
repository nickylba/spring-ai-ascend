package com.huawei.ascend.bus.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable message exchanged between co-hosted agents over the in-process
 * {@link AgentMessageBus}. The payload map is defensively copied so a message
 * already published can never be mutated by its producer.
 *
 * @param messageId     unique id of this message; use {@link #of} to auto-generate
 * @param tenantId      owning tenant — topics are tenant-scoped, never global
 * @param topic         logical topic name within the tenant
 * @param fromAgentId   id of the publishing agent
 * @param correlationId optional id linking a reply to its request, nullable
 * @param traceparent   optional W3C traceparent for trace continuity, nullable
 * @param payload       message body; copied, null treated as empty
 * @param occurredAt    production timestamp
 */
public record AgentMessage(
        String messageId,
        String tenantId,
        String topic,
        String fromAgentId,
        String correlationId,
        String traceparent,
        Map<String, Object> payload,
        Instant occurredAt) {

    public AgentMessage {
        messageId = requireNonBlank(messageId, "messageId");
        tenantId = requireNonBlank(tenantId, "tenantId");
        topic = requireNonBlank(topic, "topic");
        fromAgentId = requireNonBlank(fromAgentId, "fromAgentId");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        Objects.requireNonNull(occurredAt, "occurredAt is required");
    }

    /** Creates a message with an auto-generated UUID id and {@code occurredAt = now}. */
    public static AgentMessage of(String tenantId, String topic, String fromAgentId, Map<String, Object> payload) {
        return new AgentMessage(UUID.randomUUID().toString(), tenantId, topic, fromAgentId,
                null, null, payload, Instant.now());
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
