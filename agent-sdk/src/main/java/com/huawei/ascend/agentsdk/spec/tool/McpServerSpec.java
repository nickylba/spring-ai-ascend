package com.huawei.ascend.agentsdk.spec.tool;

import java.util.List;
import java.util.Map;

/**
 * Connection settings for one named MCP server that {@code mcp:} tool refs run
 * against. Exactly one of {@link #command()} (stdio child process) or
 * {@link #url()} (HTTP/SSE endpoint) is set; the YAML parser enforces the
 * exclusivity so an entry can never be ambiguous at connect time.
 */
public record McpServerSpec(
        String name,
        String command,
        List<String> args,
        Map<String, String> env,
        String url,
        Map<String, String> headers) {

    public McpServerSpec {
        args = args == null ? List.of() : List.copyOf(args);
        env = env == null ? Map.of() : Map.copyOf(env);
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }

    public boolean stdio() {
        return command != null && !command.isBlank();
    }
}
