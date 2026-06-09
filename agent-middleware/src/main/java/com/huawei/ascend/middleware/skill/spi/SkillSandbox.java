package com.huawei.ascend.middleware.skill.spi;

import java.util.concurrent.CompletableFuture;

public interface SkillSandbox {
    void registerExecutor(String toolName, SandboxExecutor executor);
    CompletableFuture<Object> submit(ToolCallPayload payload);
}
