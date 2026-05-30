package com.huawei.ascend.service.taskflow.control;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Task(
        String tenantId,
        String sessionId,
        String taskId,
        String agentId,
        TaskState state,
        long revision,
        WaitingReason waitingReason,
        TaskFailureCode failureCode,
        Object detail,
        Instant createdAt,
        Instant updatedAt) {

    public Task {
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(sessionId, "sessionId");
        requireNonBlank(taskId, "taskId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        if (revision < 1L) {
            throw new IllegalArgumentException("revision must be positive");
        }
    }

    public static Task created(String tenantId, String sessionId, String agentId, Instant now) {
        return new Task(tenantId, sessionId, UUID.randomUUID().toString(), agentId, TaskState.CREATED, 1L,
                null, null, null, now, now);
    }

    public Task transitionTo(TaskState nextState, WaitingReason nextWaitingReason,
                             TaskFailureCode nextFailureCode, Object nextDetail, Instant now) {
        return new Task(tenantId, sessionId, taskId, agentId, nextState, revision + 1L,
                nextWaitingReason, nextFailureCode, nextDetail, createdAt, now);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
