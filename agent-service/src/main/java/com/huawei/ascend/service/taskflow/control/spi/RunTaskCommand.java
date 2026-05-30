package com.huawei.ascend.service.taskflow.control.spi;

import java.util.Map;
import java.util.Objects;

public record RunTaskCommand(
        String tenantId,
        String sessionId,
        String taskId,
        String agentId,
        Object input,
        String idempotencyKey,
        Map<String, Object> metadata) {

    public RunTaskCommand {
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
