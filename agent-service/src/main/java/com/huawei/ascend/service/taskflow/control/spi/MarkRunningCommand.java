package com.huawei.ascend.service.taskflow.control.spi;

public record MarkRunningCommand(
        String tenantId,
        String taskId,
        long expectedRevision,
        String idempotencyKey) implements MarkTaskCommand {
}
