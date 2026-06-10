package com.huawei.ascend.agentsdk.adapter;

import com.huawei.ascend.agentsdk.spec.tool.ExecutionHandle;
import com.huawei.ascend.agentsdk.spec.tool.HttpExecutionHandle;
import com.huawei.ascend.agentsdk.spec.tool.JavaExecutionHandle;
import com.huawei.ascend.agentsdk.spec.tool.McpExecutionHandle;
import com.huawei.ascend.agentsdk.spec.tool.McpServerSpec;
import com.huawei.ascend.agentsdk.spec.tool.NativeTool;
import com.huawei.ascend.agentsdk.spec.tool.ResolvedTool;
import com.huawei.ascend.agentsdk.spec.tool.ToolDescriptor;
import com.huawei.ascend.agentsdk.spec.tool.WrappableTool;
import com.huawei.ascend.agentsdk.support.ValidationException;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedHashMap;
import java.util.Map;

public final class OpenJiuwenToolMapper {

    private final HttpToolExecutor httpExecutor;
    private final McpToolExecutor mcpExecutor;

    public OpenJiuwenToolMapper() {
        this(Map.of());
    }

    public OpenJiuwenToolMapper(Map<String, McpServerSpec> mcpServers) {
        this(new HttpToolExecutor(), new McpToolExecutor(mcpServers));
    }

    OpenJiuwenToolMapper(HttpToolExecutor httpExecutor, McpToolExecutor mcpExecutor) {
        this.httpExecutor = httpExecutor;
        this.mcpExecutor = mcpExecutor;
    }

    public Tool toTool(ResolvedTool resolvedTool) {
        if (resolvedTool instanceof NativeTool nativeTool) {
            if (nativeTool.tool() instanceof Tool tool) {
                return tool;
            }
            throw new ValidationException("Native OpenJiuwen tool expected, got: "
                    + (nativeTool.tool() == null ? "null" : nativeTool.tool().getClass().getName()));
        }
        WrappableTool wrappable = (WrappableTool) resolvedTool;
        if (wrappable.executionHandle() instanceof McpExecutionHandle mcp && !mcpExecutor.hasServer(mcp.server())) {
            // Failing at agent build beats failing on the first invocation —
            // the model would otherwise discover the misconfiguration mid-run.
            throw new ValidationException("Tool '" + wrappable.descriptor().name()
                    + "' references unknown MCP server '" + mcp.server() + "'; declare it under mcpServers");
        }
        ToolCard card = card(wrappable.descriptor());
        return new LocalFunction(card, inputs -> invoke(wrappable.executionHandle(), inputs));
    }

    private ToolCard card(ToolDescriptor descriptor) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("outputSchema", descriptor.outputSchema());
        return ToolCard.builder()
                .id(descriptor.name())
                .name(descriptor.name())
                .description(descriptor.description())
                .inputParams(descriptor.inputSchema())
                .properties(properties)
                .build();
    }

    private Object invoke(ExecutionHandle handle, Map<String, Object> inputs) {
        if (handle instanceof HttpExecutionHandle http) {
            return httpExecutor.execute(http, inputs);
        }
        if (handle instanceof JavaExecutionHandle java) {
            return invokeJava(java, inputs);
        }
        if (handle instanceof McpExecutionHandle mcp) {
            return mcpExecutor.execute(mcp, inputs);
        }
        throw new ValidationException("Unsupported execution handle: " + handle);
    }

    private Object invokeJava(JavaExecutionHandle handle, Map<String, Object> inputs) {
        try {
            Class<?> type = Class.forName(handle.className());
            Method method = type.getMethod(handle.methodName(), Map.class);
            if (!Modifier.isStatic(method.getModifiers())) {
                throw new ValidationException("Java tool method must be static: "
                        + handle.className() + "#" + handle.methodName());
            }
            return method.invoke(null, inputs);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException error) {
            throw new ValidationException("Failed to invoke Java tool: "
                    + handle.className() + "#" + handle.methodName(), error);
        } catch (InvocationTargetException error) {
            Throwable cause = error.getCause() == null ? error : error.getCause();
            throw new ValidationException("Java tool failed: "
                    + handle.className() + "#" + handle.methodName(), cause);
        }
    }
}

