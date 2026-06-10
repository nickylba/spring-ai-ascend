package com.huawei.ascend.agentsdk.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.agentsdk.spec.tool.McpExecutionHandle;
import com.huawei.ascend.agentsdk.spec.tool.McpServerSpec;
import com.huawei.ascend.agentsdk.spec.tool.ToolDescriptor;
import com.huawei.ascend.agentsdk.spec.tool.WrappableTool;
import com.huawei.ascend.agentsdk.support.ValidationException;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpenJiuwenToolMapperTest {

    @Test
    void unknownMcpServerFailsAtAgentBuildNotFirstInvocation() {
        OpenJiuwenToolMapper mapper = new OpenJiuwenToolMapper();
        WrappableTool tool = new WrappableTool(
                descriptor("lookup"), new McpExecutionHandle("ghost", "lookup"));

        assertThatThrownBy(() -> mapper.toTool(tool))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ghost")
                .hasMessageContaining("mcpServers");
    }

    @Test
    void declaredMcpServerToolRoutesInvocationsThroughTheMcpExecutor() throws Exception {
        McpServerSpec server = new McpServerSpec(
                "inventory", "inventory-server", List.of(), Map.of(), null, Map.of());
        McpToolExecutor mcpExecutor = new McpToolExecutor(
                Map.of("inventory", server),
                spec -> new McpToolExecutor.McpConnection() {
                    @Override
                    public McpSchema.CallToolResult callTool(McpSchema.CallToolRequest request) {
                        return new McpSchema.CallToolResult(
                                List.of(new McpSchema.TextContent("stock for " + request.arguments().get("sku"))),
                                false, null, null);
                    }

                    @Override
                    public void close() {
                    }
                });
        OpenJiuwenToolMapper mapper = new OpenJiuwenToolMapper(new HttpToolExecutor(), mcpExecutor);
        WrappableTool wrappable = new WrappableTool(
                descriptor("lookup"), new McpExecutionHandle("inventory", "lookup"));

        Tool tool = mapper.toTool(wrappable);

        assertThat(tool).isInstanceOf(LocalFunction.class);
        Object result = ((LocalFunction) tool).getFunc().apply(Map.of("sku", "s-1"));
        assertThat(result).isEqualTo("stock for s-1");
    }

    private static ToolDescriptor descriptor(String name) {
        return new ToolDescriptor(name, "lookup inventory", Map.of("type", "object"), Map.of());
    }
}
