package com.huawei.ascend.service.taskflow.control;

import java.time.Instant;
import java.util.Objects;

public record TaskEvent(
        String tenantId,
        String sessionId,
        String taskId,
        TaskEventType type,
        TaskState state,
        Object detail,
        Instant occurredAt) {

    public TaskEvent {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(taskId, "taskId");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(occurredAt, "occurredAt");
    }
}
