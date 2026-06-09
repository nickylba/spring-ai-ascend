package com.huawei.ascend.runtime.engine.sandbox;

import io.agentscope.core.tool.SchemaOnlyTool;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolCallParam;
import io.agentscope.core.tool.ToolSuspendException;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.Map;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RetailWealthAdvisorSandboxNativeE2eTest {
    private static final Logger log = LoggerFactory.getLogger(RetailWealthAdvisorSandboxNativeE2eTest.class);

    @Test
    void testNativeSchemaOnlyToolSuspension() {
        log.info("==================================================================================");
        log.info("[PHASE 1 - SETUP] 正在构建原生的 SchemaOnlyTool 实例...");
        log.info("架构意图：系统从 YAML 读取配置，不提供真实的物理代码，仅注册工具的 JSON Schema。");
        
        ToolSchema schema = ToolSchema.builder()
            .name("calc-yield-sandbox-tool")
            .description("计算零售财富顾问的预期收益（隔离环境）")
            .parameters(Map.of(
                "type", "object",
                "properties", Map.of(
                    "principal", Map.of("type", "number", "description", "本金"),
                    "rate", Map.of("type", "number", "description", "年化利率"),
                    "months", Map.of("type", "number", "description", "月数")
                ),
                "required", List.of("principal", "rate", "months")
            ))
            .build();

        SchemaOnlyTool schemaOnlyTool = new SchemaOnlyTool(schema);
        
        log.info("[PHASE 2 - RENDER] 验证对大模型的暴露格式 (Gene-Tool 绑定)...");
        log.info("工具名称: {}", schemaOnlyTool.getName());
        log.info("工具描述: {}", schemaOnlyTool.getDescription());
        log.info("工具参数: {}", schemaOnlyTool.getInputSchema());
        assertThat(schemaOnlyTool.getName()).isEqualTo("calc-yield-sandbox-tool");

        log.info("[PHASE 3 - INVOKE] 模拟大模型 (LLM) 命中该工具...");
        Map<String, Object> llmGeneratedArgs = Map.of("principal", 100000.0, "rate", 0.025, "months", 6.0);
        log.info("大模型输出的参数解析结果: {}", llmGeneratedArgs);
        
        ToolCallParam param = new ToolCallParam(null, llmGeneratedArgs, null);
        
        log.info("[PHASE 4 - YIELD] AgentScope 尝试执行 callAsync(). 期待其原生抛出挂起中断...");
        Mono<ToolResultBlock> resultMono = schemaOnlyTool.callAsync(param);
        
        StepVerifier.create(resultMono)
            .expectErrorSatisfies(throwable -> {
                assertThat(throwable).isInstanceOf(ToolSuspendException.class);
                log.info("[PHASE 5 - SUSPEND] 拦截成功！捕获到了原生的 ToolSuspendException。");
                log.info("架构状态机流转：当前 LLM 推理流已被安全切断。");
                log.info("下一步系统总线将携带参数 {} 去调度真实的 agent-middleware 沙箱进行计算，随后将结果 Resume 唤醒引擎。", llmGeneratedArgs);
            })
            .verify();
            
        log.info("==================================================================================");
        log.info("验证完成！原生挂起机制 (Native Yield Mechanism) 完全生效，实现了零侵入的物理隔离。");
    }
}
