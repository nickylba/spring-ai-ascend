package com.huawei.ascend.runtime.engine.sandbox;

import com.huawei.ascend.middleware.skill.impl.SkillSandboxAutoConfiguration;
import com.huawei.ascend.middleware.skill.spi.SkillSandbox;
import com.huawei.ascend.middleware.skill.spi.ToolCallPayload;
import com.huawei.ascend.middleware.skill.spi.ToolExecutionInterruptException;
import com.huawei.ascend.runtime.engine.agentscope.sandbox.ShadowTool;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = SkillSandboxAutoConfiguration.class)
class RetailWealthAdvisorSandboxE2eTest {
    private static final Logger log = LoggerFactory.getLogger(RetailWealthAdvisorSandboxE2eTest.class);

    @Autowired
    private SkillSandbox skillSandbox;

    @Test
    void testAgentScopeHijackAndSandboxExecution() throws Exception {
        log.info("=== Starting RetailWealthAdvisor Sandbox E2E Test ===");
        
        // 1. Verify Spring Boot injected the middleware
        assertThat(skillSandbox).isNotNull();

        // 2. Mock AgentScope hitting the ShadowTool (Hijack Point)
        ShadowTool shadowTool = new ShadowTool();
        ToolCallPayload capturedPayload = null;
        
        try {
            // AgentScope internally calls this via reflection/tool-binding
            shadowTool.executeShadow(Map.of("principal", 100000.0, "rate", 0.025, "months", 6.0));
        } catch (ToolExecutionInterruptException e) {
            capturedPayload = e.getPayload();
        }

        // 3. Verify Intercept
        assertThat(capturedPayload).isNotNull();
        assertThat(capturedPayload.toolName()).isEqualTo("calc-yield-sandbox-tool");

        // 4. Runtime layer routes payload to SkillSandbox (cross-module boundary)
        log.info("Runtime passing payload to Sandbox. UUID: {}", capturedPayload.executionUuid());
        Object result = skillSandbox.submit(capturedPayload).get();

        // 5. Resume (Simulated)
        log.info("[BOUNDARY-RESUME] Sandbox execution complete. Result: {}. Resuming engine context for UUID: {}.", 
            result, capturedPayload.executionUuid());

        assertThat(result).isEqualTo(101250.0);
    }
}
