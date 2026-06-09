package com.huawei.ascend.middleware.skill.impl;

import com.huawei.ascend.middleware.skill.spi.SandboxExecutor;
import com.huawei.ascend.middleware.skill.spi.SkillSandbox;
import com.huawei.ascend.middleware.skill.spi.ToolCallPayload;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class DefaultSkillSandbox implements SkillSandbox {
    private final Map<String, SandboxExecutor> executors = new ConcurrentHashMap<>();

    @Override
    public void registerExecutor(String toolName, SandboxExecutor executor) {
        executors.put(toolName, executor);
    }

    @Override
    public CompletableFuture<Object> submit(ToolCallPayload payload) {
        SandboxExecutor executor = executors.get(payload.toolName());
        if (executor == null) {
            return CompletableFuture.failedFuture(new IllegalArgumentException("No executor found for tool: " + payload.toolName()));
        }
        return executor.execute(payload);
    }
}
