package com.huawei.ascend.service.taskflow.control.spi;

public record MarkCancelledCommand(
        String tenantId,
        String taskId,
        long expectedRevision,
        String reason,
        String idempotencyKey) implements MarkTaskCommand {
}
