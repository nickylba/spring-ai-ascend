package com.huawei.ascend.service.taskflow.control.spi;

import com.huawei.ascend.service.taskflow.control.WaitingReason;

import java.util.Objects;

public record MarkWaitingCommand(
        String tenantId,
        String taskId,
        long expectedRevision,
        WaitingReason waitingReason,
        Object detail,
        String idempotencyKey) implements MarkTaskCommand {

    public MarkWaitingCommand {
        Objects.requireNonNull(waitingReason, "waitingReason");
    }
}
