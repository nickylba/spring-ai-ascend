package com.huawei.ascend.service.taskflow.queue;

import java.time.Instant;
import java.util.Objects;

public record QueueEnvelope(
        QueueId queueId,
        String tenantId,
        String sessionId,
        String correlationId,
        String idempotencyKey,
        Object payload,
        Instant createdAt) {

    public QueueEnvelope {
        Objects.requireNonNull(queueId, "queueId");
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(sessionId, "sessionId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
