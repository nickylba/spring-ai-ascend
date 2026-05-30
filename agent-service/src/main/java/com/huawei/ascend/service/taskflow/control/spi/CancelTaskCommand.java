package com.huawei.ascend.service.taskflow.control.spi;

import java.util.Objects;

public record CancelTaskCommand(
        String tenantId,
        String sessionId,
        String taskId,
        String reason,
        String idempotencyKey) {

    public CancelTaskCommand {
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(sessionId, "sessionId");
        requireNonBlank(taskId, "taskId");
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
