package com.huawei.ascend.service.taskflow.control.spi;

public sealed interface MarkTaskCommand permits MarkRunningCommand, MarkWaitingCommand,
        MarkSucceededCommand, MarkFailedCommand, MarkCancelledCommand {

    String tenantId();

    String taskId();

    long expectedRevision();

    String idempotencyKey();
}
