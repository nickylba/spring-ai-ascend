package com.huawei.ascend.service.taskflow.control.spi;

import java.util.Objects;

public record TaskRuntimeDispatchResult(
        TaskRuntimeDispatchStatus status,
        Object detail) {

    public TaskRuntimeDispatchResult {
        Objects.requireNonNull(status, "status");
    }

    public static TaskRuntimeDispatchResult accepted() {
        return new TaskRuntimeDispatchResult(TaskRuntimeDispatchStatus.ACCEPTED, null);
    }

    public static TaskRuntimeDispatchResult rejected(Object detail) {
        return new TaskRuntimeDispatchResult(TaskRuntimeDispatchStatus.REJECTED, detail);
    }

    public boolean acceptedStatus() {
        return status() == TaskRuntimeDispatchStatus.ACCEPTED;
    }
}
