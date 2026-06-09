package com.huawei.ascend.middleware.skill.spi;

import java.util.Map;
import java.util.Objects;

public record ToolCallPayload(
        String executionUuid,
        String toolName,
        Map<String, Object> arguments
) {
    public ToolCallPayload {
        Objects.requireNonNull(executionUuid, "executionUuid must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
