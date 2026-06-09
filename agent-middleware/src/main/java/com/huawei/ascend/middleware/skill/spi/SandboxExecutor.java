package com.huawei.ascend.middleware.skill.spi;

import java.util.concurrent.CompletableFuture;

public interface SandboxExecutor {
    ToolSchema getSchema();
    CompletableFuture<Object> execute(ToolCallPayload payload);
}
