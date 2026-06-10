package com.huawei.ascend.agentsdk.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.agentsdk.spec.tool.McpExecutionHandle;
import com.huawei.ascend.agentsdk.spec.tool.McpServerSpec;
import com.huawei.ascend.agentsdk.support.ToolExecutionException;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.McpSyncClient;
import io.modelcontextprotocol.client.transport.HttpClientSseClientTransport;
import io.modelcontextprotocol.client.transport.ServerParameters;
import io.modelcontextprotocol.client.transport.StdioClientTransport;
import io.modelcontextprotocol.json.McpJsonDefaults;
import io.modelcontextprotocol.spec.McpClientTransport;
import io.modelcontextprotocol.spec.McpSchema;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Executes an {@code mcp:} tool against the server declared for it under
 * {@code mcpServers}. One client per server name is connected lazily on first
 * use and reused across invocations; {@link #close()} tears them all down.
 * Text results reach the agent as plain strings; structured results as
 * Jackson-decoded maps/lists.
 */
public final class McpToolExecutor implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /** The slice of {@link McpSyncClient} the executor calls; the test seam. */
    interface McpConnection extends AutoCloseable {
        McpSchema.CallToolResult callTool(McpSchema.CallToolRequest request);

        @Override
        void close();
    }

    private final Map<String, McpServerSpec> servers;
    private final Function<McpServerSpec, McpConnection> connectionFactory;
    private final ConcurrentHashMap<String, McpConnection> connections = new ConcurrentHashMap<>();

    public McpToolExecutor(Map<String, McpServerSpec> servers) {
        this(servers, McpToolExecutor::connect);
    }

    McpToolExecutor(Map<String, McpServerSpec> servers, Function<McpServerSpec, McpConnection> connectionFactory) {
        this.servers = servers == null ? Map.of() : Map.copyOf(servers);
        this.connectionFactory = connectionFactory;
    }

    public boolean hasServer(String server) {
        return servers.containsKey(server);
    }

    public Object execute(McpExecutionHandle handle, Map<String, Object> inputs) {
        McpServerSpec spec = servers.get(handle.server());
        if (spec == null) {
            throw new ToolExecutionException("MCP tool '" + handle.tool() + "' references unknown server '"
                    + handle.server() + "'; declare it under mcpServers");
        }
        McpSchema.CallToolResult result;
        try {
            McpConnection connection = connections.computeIfAbsent(handle.server(),
                    name -> connectionFactory.apply(spec));
            result = connection.callTool(new McpSchema.CallToolRequest(
                    handle.tool(), inputs == null ? Map.of() : inputs));
        } catch (ToolExecutionException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new ToolExecutionException("MCP tool '" + handle.tool() + "' on server '"
                    + handle.server() + "' failed: " + e.getMessage(), e);
        }
        if (Boolean.TRUE.equals(result.isError())) {
            throw new ToolExecutionException("MCP tool '" + handle.tool() + "' on server '"
                    + handle.server() + "' answered an error: " + text(result));
        }
        try {
            return convert(result);
        } catch (IllegalArgumentException e) {
            throw new ToolExecutionException("MCP tool '" + handle.tool() + "' on server '"
                    + handle.server() + "' answered a result the SDK cannot decode: " + e.getMessage(), e);
        }
    }

    @Override
    public void close() {
        connections.forEach((name, connection) -> {
            try {
                connection.close();
            } catch (RuntimeException e) {
                // Best-effort shutdown: one stuck server must not keep the others open.
            }
        });
        connections.clear();
    }

    private static Object convert(McpSchema.CallToolResult result) {
        if (result.structuredContent() != null) {
            return MAPPER.convertValue(result.structuredContent(), Object.class);
        }
        List<McpSchema.Content> content = result.content() == null ? List.of() : result.content();
        if (content.isEmpty()) {
            return "";
        }
        if (content.size() == 1) {
            return convert(content.get(0));
        }
        List<Object> values = new ArrayList<>(content.size());
        for (McpSchema.Content item : content) {
            values.add(convert(item));
        }
        return values;
    }

    private static Object convert(McpSchema.Content content) {
        if (content instanceof McpSchema.TextContent text) {
            return text.text();
        }
        // Image/audio/resource payloads: hand the agent the record fields as a map.
        return MAPPER.convertValue(content, Object.class);
    }

    private static String text(McpSchema.CallToolResult result) {
        if (result.content() == null) {
            return "";
        }
        StringJoiner joiner = new StringJoiner("\n");
        for (McpSchema.Content item : result.content()) {
            if (item instanceof McpSchema.TextContent text && text.text() != null) {
                joiner.add(text.text());
            }
        }
        return joiner.toString();
    }

    private static McpConnection connect(McpServerSpec spec) {
        McpSyncClient client = McpClient.sync(transport(spec))
                .clientInfo(new McpSchema.Implementation("ascend-agent-sdk", "0.1.0"))
                .requestTimeout(REQUEST_TIMEOUT)
                .build();
        try {
            client.initialize();
        } catch (RuntimeException e) {
            client.close();
            throw new ToolExecutionException("MCP server '" + spec.name()
                    + "' failed to initialize: " + e.getMessage(), e);
        }
        return new SyncClientConnection(client);
    }

    private static McpClientTransport transport(McpServerSpec spec) {
        if (spec.stdio()) {
            return new StdioClientTransport(
                    ServerParameters.builder(spec.command())
                            .args(spec.args())
                            .env(spec.env())
                            .build(),
                    McpJsonDefaults.getMapper());
        }
        return HttpClientSseClientTransport.builder(spec.url())
                .customizeRequest(request -> spec.headers().forEach(request::header))
                .build();
    }

    private record SyncClientConnection(McpSyncClient client) implements McpConnection {
        @Override
        public McpSchema.CallToolResult callTool(McpSchema.CallToolRequest request) {
            return client.callTool(request);
        }

        @Override
        public void close() {
            client.close();
        }
    }
}
