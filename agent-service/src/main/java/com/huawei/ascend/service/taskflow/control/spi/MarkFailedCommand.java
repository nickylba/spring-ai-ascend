package com.huawei.ascend.service.taskflow.control.spi;

import com.huawei.ascend.service.taskflow.control.TaskFailureCode;

import java.util.Objects;

public record MarkFailedCommand(
        String tenantId,
        String taskId,
        long expectedRevision,
        TaskFailureCode failureCode,
        Object detail,
        String idempotencyKey) implements MarkTaskCommand {

    public MarkFailedCommand {
        Objects.requireNonNull(failureCode, "failureCode");
    }
}
