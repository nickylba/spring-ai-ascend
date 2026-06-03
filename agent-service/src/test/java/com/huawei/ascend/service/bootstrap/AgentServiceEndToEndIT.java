package com.huawei.ascend.service.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.service.access.config.AccessLayerConfiguration;
import com.huawei.ascend.service.access.protocol.a2a.egress.A2aOutput;
import com.huawei.ascend.service.access.protocol.a2a.egress.A2aOutputHandle;
import com.huawei.ascend.service.access.protocol.a2a.egress.A2aOutputRegistry;
import com.huawei.ascend.service.access.protocol.a2a.jsonrpc.A2aJsonRpcHandler;
import com.huawei.ascend.service.engine.config.EngineAutoConfiguration;
import com.huawei.ascend.service.engine.handler.AgentExecutionContext;
import com.huawei.ascend.service.engine.spi.AgentExecutionResult;
import com.huawei.ascend.service.engine.spi.AgentHandler;
import com.huawei.ascend.service.engine.spi.AgentResultAdapter;
import com.huawei.ascend.service.queue.config.QueueAutoConfiguration;
import com.huawei.ascend.service.session.api.SessionManager;
import com.huawei.ascend.service.session.config.SessionManageConfiguration;
import com.huawei.ascend.service.taskcontrol.config.TaskControlAutoConfiguration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end test from the access layer's perspective: an A2A request enters,
 * flows access -> task-centric-control -> engine -> fake agent, and the reply
 * comes back out through the access layer's A2A output channel.
 */
@SpringBootTest(classes = AgentServiceEndToEndIT.TestRuntime.class)
class AgentServiceEndToEndIT {

    private static final String TENANT = "tenant-e2e";
    private static final String AGENT = "echo-agent";
    private static final String FAILING_AGENT = "boom-agent";

    @Autowired
    private A2aJsonRpcHandler a2aJsonRpcHandler;

    @Autowired
    private A2aOutputRegistry outputRegistry;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void a2aRequestRunsThroughTheStackAndRepliesBack() {
        JsonNode accepted = send("session-1", "hello world");

        assertThat(accepted.path("metadata").path("accepted").asBoolean()).isTrue();
        assertThat(accepted.path("taskId").asText()).isNotBlank();
        assertThat(accepted.path("metadata").path("tenantId").asText()).isEqualTo(TENANT);

        A2aOutputHandle handle = new A2aOutputHandle(TENANT, "session-1");
        List<A2aOutput> outputs = awaitOutputs(handle);

        assertThat(outputs).isNotEmpty();
        assertThat(outputs).anyMatch(o -> "Message".equals(o.kind()));
        assertThat(outputs.get(outputs.size() - 1).terminal()).isTrue();
        assertThat(outputs).anyMatch(o -> String.valueOf(o.body()).contains("hello world"));
        assertThat(sessionManager.get(TENANT, "session-1")).hasValueSatisfying(session ->
                assertThat(session.currentUserInput()).anyMatch(message -> "hello world".equals(message.text())));
    }

    @Test
    void aThrowingAgentStillRepliesWithATerminalError() {
        JsonNode accepted = send(FAILING_AGENT, "session-err", "trigger failure");
        assertThat(accepted.path("metadata").path("accepted").asBoolean()).isTrue();

        A2aOutputHandle handle = new A2aOutputHandle(TENANT, "session-err");
        List<A2aOutput> outputs = awaitOutputs(handle);

        assertThat(outputs).isNotEmpty();
        assertThat(outputs.get(outputs.size() - 1).terminal()).isTrue();
        assertThat(outputs).anyMatch(o -> "error".equals(o.kind()));
    }

    @Test
    void a2aRequestCanReadIdentityFromParamsMetadata() {
        JsonNode accepted = sendWithParamsMetadataOnly("session-params", "params metadata");

        assertThat(accepted.path("metadata").path("accepted").asBoolean()).isTrue();
        assertThat(accepted.path("metadata").path("tenantId").asText()).isEqualTo(TENANT);

        A2aOutputHandle handle = new A2aOutputHandle(TENANT, "session-params");
        List<A2aOutput> outputs = awaitOutputs(handle);

        assertThat(outputs).isNotEmpty();
        assertThat(outputs.get(outputs.size() - 1).terminal()).isTrue();
        assertThat(outputs).anyMatch(o -> String.valueOf(o.body()).contains("params metadata"));
    }

    private List<A2aOutput> awaitOutputs(A2aOutputHandle handle) {
        long deadline = System.nanoTime() + java.time.Duration.ofSeconds(5).toNanos();
        List<A2aOutput> outputs = outputRegistry.list(handle);
        while (System.nanoTime() < deadline
                && (outputs.isEmpty() || !outputs.get(outputs.size() - 1).terminal())) {
            try {
                Thread.sleep(20L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                break;
            }
            outputs = outputRegistry.list(handle);
        }
        return outputs;
    }

    private JsonNode send(String sessionId, String text) {
        return send(AGENT, sessionId, text);
    }

    private JsonNode send(String agentId, String sessionId, String text) {
        String response = a2aJsonRpcHandler.handleToJson(sendMessageBody(agentId, sessionId, text));
        return result(response);
    }

    private JsonNode sendWithParamsMetadataOnly(String sessionId, String text) {
        String response = a2aJsonRpcHandler.handleToJson(sendMessageBodyWithParamsMetadataOnly(sessionId, text));
        return result(response);
    }

    private JsonNode result(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);
            assertThat(root.path("error").isMissingNode() || root.path("error").isNull())
                    .as(response)
                    .isTrue();
            JsonNode result = root.path("result");
            assertThat(result.isMissingNode()).as(response).isFalse();
            return result;
        } catch (java.io.IOException ex) {
            throw new AssertionError("Invalid JSON-RPC response: " + response, ex);
        }
    }

    private static String sendMessageBody(String agentId, String sessionId, String text) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "%s",
                  "method": "SendMessage",
                  "params": {
                    "tenant": "%s",
                    "message": {
                      "role": "ROLE_USER",
                      "messageId": "%s",
                      "contextId": "%s",
                      "parts": [
                        {
                          "kind": "text",
                          "text": "%s"
                        }
                      ],
                      "metadata": {
                        "tenantId": "%s",
                        "userId": "user-1",
                        "agentId": "%s",
                        "sessionId": "%s",
                        "idempotencyKey": "%s",
                        "correlationId": "corr-1"
                      }
                    }
                  }
                }
                """.formatted(
                UUID.randomUUID(),
                TENANT,
                UUID.randomUUID(),
                sessionId,
                text,
                TENANT,
                agentId,
                sessionId,
                UUID.randomUUID());
    }

    private static String sendMessageBodyWithParamsMetadataOnly(String sessionId, String text) {
        return """
                {
                  "jsonrpc": "2.0",
                  "id": "%s",
                  "method": "SendMessage",
                  "params": {
                    "tenant": "%s",
                    "metadata": {
                      "tenantId": "%s",
                      "userId": "user-1",
                      "agentId": "%s",
                      "sessionId": "%s",
                      "idempotencyKey": "%s",
                      "correlationId": "corr-params"
                    },
                    "message": {
                      "role": "ROLE_USER",
                      "messageId": "%s",
                      "contextId": "%s",
                      "parts": [
                        {
                          "kind": "text",
                          "text": "%s"
                        }
                      ]
                    }
                  }
                }
                """.formatted(
                UUID.randomUUID(),
                TENANT,
                TENANT,
                AGENT,
                sessionId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                sessionId,
                text);
    }

    @SpringBootConfiguration
    @Import({
            QueueAutoConfiguration.class,
            TaskControlAutoConfiguration.class,
            AgentServiceBootstrapConfiguration.class,
            AccessLayerConfiguration.class,
            SessionManageConfiguration.class,
            EngineAutoConfiguration.class
    })
    static class TestRuntime {

        @Bean
        AgentHandler echoAgentHandler() {
            return new EchoAgentHandler();
        }

        @Bean
        AgentHandler boomAgentHandler() {
            return new ThrowingAgentHandler();
        }
    }

    static final class EchoAgentHandler implements AgentHandler {

        @Override
        public String agentId() {
            return AGENT;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            String userText = context.getInput() == null || context.getInput().messages().isEmpty()
                    ? "" : context.getInput().messages().get(0).text();
            return Stream.of(
                    java.util.Map.of("result_type", "output", "output", "echo: " + userText),
                    java.util.Map.of("result_type", "answer", "output", "echo: " + userText));
        }

        @Override
        public AgentResultAdapter resultAdapter() {
            return AgentServiceEndToEndIT::adaptRawResults;
        }
    }

    static final class ThrowingAgentHandler implements AgentHandler {

        @Override
        public String agentId() {
            return FAILING_AGENT;
        }

        @Override
        public boolean isHealthy() {
            return true;
        }

        @Override
        public Stream<?> execute(AgentExecutionContext context) {
            throw new IllegalStateException("boom");
        }

        @Override
        public AgentResultAdapter resultAdapter() {
            return AgentServiceEndToEndIT::adaptRawResults;
        }
    }

    @SuppressWarnings("unchecked")
    private static Stream<AgentExecutionResult> adaptRawResults(Stream<?> rawResults) {
        return rawResults.map(rawResult -> {
            java.util.Map<String, Object> result = (java.util.Map<String, Object>) rawResult;
            String output = String.valueOf(result.get("output"));
            if ("answer".equals(result.get("result_type"))) {
                return AgentExecutionResult.completed(output);
            }
            return AgentExecutionResult.output(output);
        });
    }
}
