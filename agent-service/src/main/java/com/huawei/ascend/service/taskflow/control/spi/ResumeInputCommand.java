package com.huawei.ascend.service.taskflow.control.spi;

import java.util.Map;
import java.util.Objects;

public record ResumeInputCommand(
        String tenantId,
        String sessionId,
        String taskId,
        Object input,
        String idempotencyKey,
        Map<String, Object> metadata) {

    public ResumeInputCommand {
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(sessionId, "sessionId");
        Objects.requireNonNull(input, "input");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
