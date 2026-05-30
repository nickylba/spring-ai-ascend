package com.huawei.ascend.service.taskflow.control.spi;

public record MarkSucceededCommand(
        String tenantId,
        String taskId,
        long expectedRevision,
        Object result,
        String idempotencyKey) implements MarkTaskCommand {
}
