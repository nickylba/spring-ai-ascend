package com.huawei.ascend.service.taskflow.queue;

import java.time.Instant;
import java.util.Objects;

public record QueueInfo(
        QueueId queueId,
        String queueType,
        String ownerTenantId,
        String ownerSessionId,
        String createdByLayer,
        String createdByComponent,
        Instant createdAt) {

    public QueueInfo {
        Objects.requireNonNull(queueId, "queueId");
        requireNonBlank(queueType, "queueType");
        requireNonBlank(ownerTenantId, "ownerTenantId");
        requireNonBlank(ownerSessionId, "ownerSessionId");
        requireNonBlank(createdByLayer, "createdByLayer");
        requireNonBlank(createdByComponent, "createdByComponent");
        Objects.requireNonNull(createdAt, "createdAt");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
