package com.huawei.ascend.runtime.engine.sandbox;

import com.huawei.ascend.middleware.skill.spi.ToolCallPayload;
import com.huawei.ascend.middleware.skill.spi.ToolExecutionInterruptException;
import com.huawei.ascend.middleware.skill.impl.LocalSandboxExecutorImpl;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

class SkillSandboxMockTest {
    private static final Logger log = LoggerFactory.getLogger(SkillSandboxMockTest.class);

    @Test
    void testRetailWealthAdvisorYieldAndResumeStateTransition() throws Exception {
        // [Library-mode Validation & Sandbox-Isolation] setup
        LocalSandboxExecutorImpl sandbox = new LocalSandboxExecutorImpl();
        String executionUuid = UUID.randomUUID().toString();
        
        log.info("=== Starting Shadow-Execution-Validation for UUID: {} ===", executionUuid);

        // 1. Simulate AgentScope executing a Shadow Tool
        ToolCallPayload capturedPayload = null;
        try {
            // LLM decides to call the tool, hits our ShadowTool wrapper
            simulateAgentScopeShadowToolCall(executionUuid);
        } catch (ToolExecutionInterruptException e) {
            capturedPayload = e.getPayload();
            log.info("[BOUNDARY-YIELD] Agent yield requested. Tool: {}, UUID: {}. Payload captured.", 
                capturedPayload.toolName(), capturedPayload.executionUuid());
        }

        // Verify the interrupt was successfully caught and payload extracted
        assertThat(capturedPayload).isNotNull();
        assertThat(capturedPayload.toolName()).isEqualTo("calc-yield-sandbox-tool");

        // 2. Dispatcher takes over, delegates to Sandbox (Cross-Boundary Execution)
        CompletableFuture<Object> future = sandbox.execute(capturedPayload);
        
        // 3. Wait for Sandbox to complete (Single-process Future resolution)
        Object result = future.get();
        
        // 4. Resume Phase Log
        log.info("[BOUNDARY-RESUME] Sandbox execution complete. Result: {}. Resuming engine context for UUID: {}.", 
            result, capturedPayload.executionUuid());

        // Verify accurate calculation without polluting main thread
        assertThat(result).isEqualTo(101250.0);
    }

    private void simulateAgentScopeShadowToolCall(String uuid) {
        // This simulates the parameters extracted by LLM in AgentScope
        Map<String, Object> args = Map.of(
            "principal", 100000.0,
            "rate", 0.025,
            "months", 6.0
        );
        
        // Telemetry safeguard (防空值策略)
        if (args.isEmpty()) {
            log.warn("[TELEMETRY-WARN] Tool arguments empty for UUID: {}", uuid);
        }
        
        ToolCallPayload payload = new ToolCallPayload(uuid, "calc-yield-sandbox-tool", args);
        
        // Throwing interrupt to break out of the native framework loop
        throw new ToolExecutionInterruptException(payload);
    }
}
