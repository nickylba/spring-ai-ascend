package com.huawei.ascend.service.taskflow.queue;

import java.time.Instant;
import java.util.Objects;

public record QueueEvent(
        QueueId queueId,
        QueueEventType type,
        QueueItemKey itemKey,
        Object value,
        Instant occurredAt) {

    public QueueEvent {
        Objects.requireNonNull(queueId, "queueId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
