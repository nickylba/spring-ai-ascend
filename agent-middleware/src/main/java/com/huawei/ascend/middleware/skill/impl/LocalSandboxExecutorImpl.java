package com.huawei.ascend.middleware.skill.impl;

import com.huawei.ascend.middleware.skill.spi.SandboxExecutor;
import com.huawei.ascend.middleware.skill.spi.ToolCallPayload;
import com.huawei.ascend.middleware.skill.spi.ToolSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;

public class LocalSandboxExecutorImpl implements SandboxExecutor {
    private static final Logger log = LoggerFactory.getLogger(LocalSandboxExecutorImpl.class);
    private final ExecutorService workerPool;
    private final ToolSchema schema;
    private final Function<Map<String, Object>, Object> physicalLogic;

    public LocalSandboxExecutorImpl(ToolSchema schema, Function<Map<String, Object>, Object> physicalLogic) {
        this.schema = schema;
        this.physicalLogic = physicalLogic;
        this.workerPool = Executors.newFixedThreadPool(
            Runtime.getRuntime().availableProcessors(),
            r -> new Thread(r, "sandbox-worker-" + schema.name() + "-" + System.nanoTime())
        );
    }

    @Override
    public ToolSchema getSchema() {
        return schema;
    }

    @Override
    public CompletableFuture<Object> execute(ToolCallPayload payload) {
        return CompletableFuture.supplyAsync(() -> {
            log.info("[BOUNDARY-SANDBOX] [{}] Executing isolated tool: {}, UUID: {}", 
                Thread.currentThread().getName(), payload.toolName(), payload.executionUuid());
            try {
                return physicalLogic.apply(payload.arguments());
            } catch (Exception e) {
                log.error("[TELEMETRY-ERROR] Sandbox execution failed for UUID: {}", payload.executionUuid(), e);
                throw new RuntimeException("Sandbox isolation failure", e);
            }
        }, workerPool);
    }
}
