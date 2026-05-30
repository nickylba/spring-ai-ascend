package com.huawei.ascend.service.taskflow.control.spi;

import java.util.Map;
import java.util.Objects;

public record TaskRuntimeDispatchRequest(
        TaskView task,
        Object input,
        String idempotencyKey,
        Map<String, Object> metadata) {

    public TaskRuntimeDispatchRequest {
        Objects.requireNonNull(task, "task");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
