package com.huawei.ascend.agentsdk.spec.yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.huawei.ascend.agentsdk.spec.AgentSpec;
import com.huawei.ascend.agentsdk.support.ValidationException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AgentYamlLoaderTest {

    @Test
    void loadsYamlWithToolsAndSkillsResolvedRelativeToYamlFile() throws Exception {
        Path tempDir = testDirectory("loads");
        Path skill = Files.createDirectories(tempDir.resolve("skills").resolve("orders"));
        Files.writeString(skill.resolve("SKILL.md"), "# Order Skill\n");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: order-agent
                description: Order agent
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  provider: openai-compatible
                  name: deepseek-chat
                  baseUrl: http://localhost
                  apiKey: secret
                prompt:
                  system: hello
                skills:
                  sources:
                    - ./skills/orders
                tools:
                  - name: queryOrder
                    description: Query an order
                    inputSchema:
                      type: object
                    ref:
                      type: file
                      class: example.OrderTools
                      method: query
                """);

        AgentSpec spec = new AgentYamlLoader().load(yaml);

        assertThat(spec.name()).isEqualTo("order-agent");
        assertThat(spec.displayName()).isEqualTo("order-agent");
        assertThat(spec.skillSpecs()).hasSize(1);
        assertThat(spec.skillSpecs().get(0).path()).isEqualTo(skill.toAbsolutePath().normalize());
        assertThat(spec.toolSpecs()).hasSize(1);
        assertThat(spec.toolSpecs().get(0).ref().scheme()).isEqualTo("file");
        assertThat(spec.toolSpecs().get(0).ref().attributes()).doesNotContainKey("path");
        assertThat(spec.toolSpecs().get(0).name()).isEqualTo("queryOrder");
    }

    @Test
    void stringShorthandRefsEmitTheKeysTheBuiltInResolversRead() throws Exception {
        Path tempDir = testDirectory("shorthand");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: shorthand-agent
                description: Shorthand agent
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  name: deepseek-chat
                  baseUrl: http://localhost
                  apiKey: secret
                tools:
                  - name: javaTool
                    description: java
                    ref: "file:example.OrderTools#query"
                  - name: httpTool
                    description: http
                    ref: "http:https://api.example.com/orders"
                  - name: mcpTool
                    description: mcp
                    ref: "mcp:inventory/lookup"
                """);

        AgentSpec spec = new AgentYamlLoader().load(yaml);

        assertThat(spec.toolSpecs().get(0).ref().attributes())
                .containsEntry("class", "example.OrderTools")
                .containsEntry("method", "query");
        assertThat(spec.toolSpecs().get(1).ref().attributes())
                .containsEntry("url", "https://api.example.com/orders");
        assertThat(spec.toolSpecs().get(2).ref().attributes())
                .containsEntry("server", "inventory")
                .containsEntry("tool", "lookup");
    }

    @Test
    void mcpServersParseIntoSpecsKeyedByName() throws Exception {
        Path tempDir = testDirectory("mcp-servers");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: mcp-agent
                description: MCP agent
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  name: deepseek-chat
                  baseUrl: http://localhost
                  apiKey: secret
                mcpServers:
                  inventory:
                    command: npx
                    args: ["-y", "@example/inventory-mcp"]
                    env:
                      API_KEY: secret
                  market:
                    url: https://mcp.example.com
                    headers:
                      Authorization: Bearer token
                tools:
                  - name: lookup
                    description: Inventory lookup
                    ref: "mcp:inventory/lookup"
                """);

        AgentSpec spec = new AgentYamlLoader().load(yaml);

        assertThat(spec.mcpServers()).containsOnlyKeys("inventory", "market");
        assertThat(spec.mcpServers().get("inventory").stdio()).isTrue();
        assertThat(spec.mcpServers().get("inventory").command()).isEqualTo("npx");
        assertThat(spec.mcpServers().get("inventory").args())
                .containsExactly("-y", "@example/inventory-mcp");
        assertThat(spec.mcpServers().get("inventory").env()).containsEntry("API_KEY", "secret");
        assertThat(spec.mcpServers().get("market").stdio()).isFalse();
        assertThat(spec.mcpServers().get("market").url()).isEqualTo("https://mcp.example.com");
        assertThat(spec.mcpServers().get("market").headers())
                .containsEntry("Authorization", "Bearer token");
    }

    @Test
    void mcpServerWithBothCommandAndUrlIsRejectedByName() throws Exception {
        assertThatThrownBy(() -> loadWithMcpServer("mcp-both", """
                  ambiguous:
                    command: npx
                    url: https://mcp.example.com
                """))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ambiguous")
                .hasMessageContaining("exactly one of command");
    }

    @Test
    void mcpServerWithNeitherCommandNorUrlIsRejectedByName() throws Exception {
        assertThatThrownBy(() -> loadWithMcpServer("mcp-neither", """
                  empty:
                    env:
                      API_KEY: secret
                """))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("empty")
                .hasMessageContaining("exactly one of command");
    }

    @Test
    void mcpServerWithKeysOfTheOtherTransportIsRejectedByName() throws Exception {
        assertThatThrownBy(() -> loadWithMcpServer("mcp-cross-stdio", """
                  local:
                    command: npx
                    headers:
                      Authorization: Bearer token
                """))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("local")
                .hasMessageContaining("headers");
        assertThatThrownBy(() -> loadWithMcpServer("mcp-cross-http", """
                  remote:
                    url: https://mcp.example.com
                    env:
                      API_KEY: secret
                """))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("remote")
                .hasMessageContaining("env");
    }

    private static AgentSpec loadWithMcpServer(String directory, String serverYaml) throws Exception {
        Path tempDir = testDirectory(directory);
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: mcp-agent
                description: MCP agent
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  name: deepseek-chat
                  baseUrl: http://localhost
                  apiKey: secret
                mcpServers:
                """ + serverYaml);
        return new AgentYamlLoader().load(yaml);
    }

    @Test
    void malformedStringShorthandFailsAtLoadWithSyntaxHint() throws Exception {
        Path tempDir = testDirectory("shorthand-bad");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: bad-agent
                description: Bad agent
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  name: deepseek-chat
                  baseUrl: http://localhost
                  apiKey: secret
                tools:
                  - name: javaTool
                    description: java
                    ref: "file:./tools/order.java"
                """);

        assertThatThrownBy(() -> new AgentYamlLoader().load(yaml))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("file:com.example.Class#method");
    }

    @Test
    void duplicateToolNamesAreRejected() throws Exception {
        Path tempDir = testDirectory("dup-tools");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: dup-agent
                description: Dup agent
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  name: deepseek-chat
                  baseUrl: http://localhost
                  apiKey: secret
                tools:
                  - name: lookup
                    description: first
                    ref: "http:https://api.example.com/a"
                  - name: lookup
                    description: second
                    ref: "http:https://api.example.com/b"
                """);

        assertThatThrownBy(() -> new AgentYamlLoader().load(yaml))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("Duplicate tool name: lookup");
    }

    @Test
    void missingFrameworkSectionIsNamedInTheError() throws Exception {
        Path tempDir = testDirectory("no-framework");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: no-framework-agent
                description: No framework
                model:
                  name: deepseek-chat
                  baseUrl: http://localhost
                  apiKey: secret
                """);

        assertThatThrownBy(() -> new AgentYamlLoader().load(yaml))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("framework");
    }

    @Test
    void nonBooleanSslVerifyIsRejectedNotSilentlyFalse() throws Exception {
        Path tempDir = testDirectory("bad-bool");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: bad-bool-agent
                description: Bad bool
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  name: deepseek-chat
                  baseUrl: http://localhost
                  apiKey: secret
                  sslVerify: enabled
                """);

        assertThatThrownBy(() -> new AgentYamlLoader().load(yaml))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("sslVerify");
    }

    @Test
    void rejectsMissingEnvironmentVariable() throws Exception {
        Path tempDir = testDirectory("rejects-missing-env");
        Path yaml = tempDir.resolve("agent.yaml");
        Files.writeString(yaml, """
                schema: ascend-agent/v1
                name: broken-agent
                description: Broken agent
                framework:
                  type: openjiuwen
                  agent: react
                model:
                  provider: openai-compatible
                  name: deepseek-chat
                  baseUrl: ${ASCEND_AGENT_SDK_TEST_MISSING}
                  apiKey: secret
                """);

        assertThatThrownBy(() -> new AgentYamlLoader().load(yaml))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ASCEND_AGENT_SDK_TEST_MISSING");
    }

    private static Path testDirectory(String name) throws Exception {
        return Files.createDirectories(Path.of("target", "agent-yaml-loader-test", name,
                UUID.randomUUID().toString()));
    }
}

