package com.huawei.ascend.service.taskflow.queue;

import java.util.Objects;

public record QueueSpec(
        String queueType,
        String ownerTenantId,
        String ownerSessionId,
        String createdByLayer,
        String createdByComponent) {

    public QueueSpec {
        requireNonBlank(queueType, "queueType");
        requireNonBlank(ownerTenantId, "ownerTenantId");
        requireNonBlank(ownerSessionId, "ownerSessionId");
        requireNonBlank(createdByLayer, "createdByLayer");
        requireNonBlank(createdByComponent, "createdByComponent");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
