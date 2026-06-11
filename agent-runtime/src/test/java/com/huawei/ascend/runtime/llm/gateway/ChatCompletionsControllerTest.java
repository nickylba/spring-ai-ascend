package com.huawei.ascend.runtime.llm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallContext;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallListener;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallResult;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

class ChatCompletionsControllerTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ScriptedUpstream upstream = new ScriptedUpstream();
    private final RecordingListener listener = new RecordingListener();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private ChatCompletionsController controller(LlmCallListener... extraListeners) {
        LlmGatewayProperties properties = new LlmGatewayProperties();
        LlmGatewayProperties.Upstream route = new LlmGatewayProperties.Upstream();
        route.setBaseUrl("http://upstream.test/v1");
        route.setApiKey("sk-upstream");
        route.setProvider("openai");
        route.setUpstreamModel("gpt-4o-mini");
        properties.getAliases().put("finance-chat", route);
        LlmGatewayProperties.Upstream passThrough = new LlmGatewayProperties.Upstream();
        passThrough.setBaseUrl("http://upstream.test/v1");
        passThrough.setApiKey("sk-upstream");
        properties.getAliases().put("plain-model", passThrough);
        LlmGatewayProperties.MintedToken token = new LlmGatewayProperties.MintedToken();
        token.setTenantId("tenant-a");
        token.setAgentId("agent-1");
        properties.getTokens().put("minted-secret", token);

        List<LlmCallListener> listeners = new ArrayList<>();
        listeners.add(listener);
        listeners.addAll(List.of(extraListeners));
        return new ChatCompletionsController(
                new MintedTokenAuthenticator(properties),
                new ModelAliasRegistry(properties),
                upstream,
                new LlmGatewayMetrics(meterRegistry),
                listeners);
    }

    @Test
    void missingTokenIs401WithoutAnyUpstreamContact() {
        ResponseEntity<?> response = controller().chatCompletions(null, request("finance-chat"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(errorType(response)).isEqualTo("authentication_error");
        assertThat(upstream.calls.get()).isZero();
        assertThat(listener.afterResults).isEmpty();
    }

    @Test
    void unknownTokenIs401WithoutAnyUpstreamContact() {
        ResponseEntity<?> response =
                controller().chatCompletions("Bearer forged", request("finance-chat"));

        assertThat(response.getStatusCode().value()).isEqualTo(401);
        assertThat(upstream.calls.get()).isZero();
    }

    @Test
    void unknownAliasIs404OpenAiErrorShape() {
        ResponseEntity<?> response =
                controller().chatCompletions("Bearer minted-secret", request("no-such-model"));

        assertThat(response.getStatusCode().value()).isEqualTo(404);
        assertThat(errorType(response)).isEqualTo("invalid_request_error");
        assertThat(upstream.calls.get()).isZero();
    }

    @Test
    void missingModelFieldIs400() {
        ResponseEntity<?> response = controller().chatCompletions("Bearer minted-secret",
                "{\"messages\":[]}".getBytes(StandardCharsets.UTF_8));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(upstream.calls.get()).isZero();
    }

    @Test
    void forwardsWithModelSwapUpstreamKeyAndPassesResponseThrough() throws Exception {
        upstream.response = new UpstreamModelClient.UpstreamResponse(200, "application/json",
                "{\"id\":\"chatcmpl-1\",\"usage\":{\"prompt_tokens\":3,\"completion_tokens\":5}}"
                        .getBytes(StandardCharsets.UTF_8));

        ResponseEntity<?> response =
                controller().chatCompletions("Bearer minted-secret", request("finance-chat"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(bodyBytes(response)).isEqualTo(upstream.response.body());
        assertThat(upstream.lastRequest.url()).isEqualTo("http://upstream.test/v1/chat/completions");
        assertThat(upstream.lastRequest.apiKey()).isEqualTo("sk-upstream");
        JsonNode forwarded = JSON.readTree(upstream.lastRequest.body());
        assertThat(forwarded.get("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(forwarded.has("stream_options")).isFalse();

        assertThat(listener.beforeContexts).hasSize(1);
        LlmCallContext context = listener.beforeContexts.get(0);
        assertThat(context.tenantId()).isEqualTo("tenant-a");
        assertThat(context.agentId()).isEqualTo("agent-1");
        assertThat(context.modelAlias()).isEqualTo("finance-chat");
        assertThat(context.provider()).isEqualTo("openai");
        assertThat(context.requestId()).isNotBlank();
        LlmCallResult result = listener.afterResults.get(0);
        assertThat(result.success()).isTrue();
        assertThat(result.upstreamStatus()).isEqualTo(200);
        assertThat(result.usage().inputTokens()).isEqualTo(3);
        assertThat(result.usage().outputTokens()).isEqualTo(5);
        assertThat(result.usage().estimated()).isFalse();

        assertThat(meterRegistry.counter(LlmGatewayMetrics.REQUESTS_TOTAL,
                "model_alias", "finance-chat", "provider", "openai", "outcome", "success")
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter(LlmGatewayMetrics.TOKENS_TOTAL,
                "model_alias", "finance-chat", "provider", "openai", "direction", "input")
                .count()).isEqualTo(3.0);
        assertThat(meterRegistry.counter(LlmGatewayMetrics.TOKENS_TOTAL,
                "model_alias", "finance-chat", "provider", "openai", "direction", "output")
                .count()).isEqualTo(5.0);
        assertThat(meterRegistry.timer(LlmGatewayMetrics.UPSTREAM_LATENCY_SECONDS,
                "model_alias", "finance-chat", "provider", "openai").count()).isEqualTo(1);
    }

    @Test
    void unmappedAliasForwardsBodyBytesUntouched() {
        upstream.response = new UpstreamModelClient.UpstreamResponse(200, "application/json",
                "{}".getBytes(StandardCharsets.UTF_8));
        byte[] body = request("plain-model");

        controller().chatCompletions("Bearer minted-secret", body);

        assertThat(upstream.lastRequest.body()).isEqualTo(body);
    }

    @Test
    void upstream4xxPassesThroughBodyAndStatus() {
        byte[] upstreamError = "{\"error\":{\"message\":\"rate limited\",\"type\":\"requests\"}}"
                .getBytes(StandardCharsets.UTF_8);
        upstream.response =
                new UpstreamModelClient.UpstreamResponse(429, "application/json", upstreamError);

        ResponseEntity<?> response =
                controller().chatCompletions("Bearer minted-secret", request("finance-chat"));

        assertThat(response.getStatusCode().value()).isEqualTo(429);
        assertThat(bodyBytes(response)).isEqualTo(upstreamError);
        assertThat(listener.afterResults.get(0).success()).isFalse();
        assertThat(listener.afterResults.get(0).upstreamStatus()).isEqualTo(429);
    }

    @Test
    void upstream500MapsTo502UpstreamErrorShape() {
        upstream.response = new UpstreamModelClient.UpstreamResponse(500, "application/json",
                "{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8));

        ResponseEntity<?> response =
                controller().chatCompletions("Bearer minted-secret", request("finance-chat"));

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(errorType(response)).isEqualTo("upstream_error");
        assertThat(listener.afterResults.get(0).upstreamStatus()).isEqualTo(500);
        assertThat(meterRegistry.counter(LlmGatewayMetrics.REQUESTS_TOTAL,
                "model_alias", "finance-chat", "provider", "openai", "outcome", "upstream_error")
                .count()).isEqualTo(1.0);
    }

    @Test
    void upstreamIoFailureIs502AndStillNotifiesListeners() {
        upstream.failWith = new UpstreamModelClient.UpstreamIoException("connection refused", null);

        ResponseEntity<?> response =
                controller().chatCompletions("Bearer minted-secret", request("finance-chat"));

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(errorType(response)).isEqualTo("upstream_error");
        LlmCallResult result = listener.afterResults.get(0);
        assertThat(result.success()).isFalse();
        assertThat(result.upstreamStatus()).isZero();
        assertThat(result.usage().estimated()).isTrue();
        assertThat(meterRegistry.counter(LlmGatewayMetrics.REQUESTS_TOTAL,
                "model_alias", "finance-chat", "provider", "openai", "outcome", "transport_error")
                .count()).isEqualTo(1.0);
    }

    @Test
    void throwingListenerNeverFailsTheCall() {
        upstream.response = new UpstreamModelClient.UpstreamResponse(200, "application/json",
                "{}".getBytes(StandardCharsets.UTF_8));
        LlmCallListener bomb = new LlmCallListener() {
            @Override
            public void beforeLlmInvocation(LlmCallContext context) {
                throw new IllegalStateException("before boom");
            }

            @Override
            public void afterLlmInvocation(LlmCallContext context, LlmCallResult result) {
                throw new IllegalStateException("after boom");
            }
        };

        ResponseEntity<?> response =
                controller(bomb).chatCompletions("Bearer minted-secret", request("finance-chat"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(listener.afterResults).hasSize(1);
    }

    @Test
    void streamingRelaysSseBytesVerbatimAndInjectsIncludeUsage() throws Exception {
        String sse = """
                data: {"choices":[{"delta":{"content":"He"}}],"usage":null}

                data: {"choices":[{"delta":{"content":"llo"}}],"usage":null}

                data: {"choices":[],"usage":{"prompt_tokens":4,"completion_tokens":2}}

                data: [DONE]

                """;
        upstream.streamResponse = new UpstreamModelClient.UpstreamStreamResponse(200,
                "text/event-stream",
                new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<?> response = controller().chatCompletions("Bearer minted-secret",
                streamingRequest("finance-chat"));

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getContentType())
                .isEqualTo(MediaType.parseMediaType("text/event-stream"));
        JsonNode forwarded = JSON.readTree(upstream.lastRequest.body());
        assertThat(forwarded.get("model").asText()).isEqualTo("gpt-4o-mini");
        assertThat(forwarded.get("stream_options").get("include_usage").asBoolean()).isTrue();

        ByteArrayOutputStream relayed = new ByteArrayOutputStream();
        ((StreamingResponseBody) response.getBody()).writeTo(relayed);
        assertThat(relayed.toString(StandardCharsets.UTF_8)).isEqualTo(sse);

        LlmCallResult result = listener.afterResults.get(0);
        assertThat(result.success()).isTrue();
        assertThat(result.usage().inputTokens()).isEqualTo(4);
        assertThat(result.usage().outputTokens()).isEqualTo(2);
        assertThat(result.usage().estimated()).isFalse();
    }

    @Test
    void streamingRequestWithExistingStreamOptionsOnlyAddsIncludeUsage() throws Exception {
        upstream.streamResponse = new UpstreamModelClient.UpstreamStreamResponse(200,
                "text/event-stream", new ByteArrayInputStream(new byte[0]));
        byte[] body = ("{\"model\":\"finance-chat\",\"stream\":true,"
                + "\"stream_options\":{\"chunk_size\":1}}").getBytes(StandardCharsets.UTF_8);

        controller().chatCompletions("Bearer minted-secret", body);

        JsonNode forwarded = JSON.readTree(upstream.lastRequest.body());
        assertThat(forwarded.get("stream_options").get("chunk_size").asInt()).isEqualTo(1);
        assertThat(forwarded.get("stream_options").get("include_usage").asBoolean()).isTrue();
    }

    @Test
    void streamingUpstream500MapsTo502BeforeAnyBytesAreCommitted() {
        upstream.streamResponse = new UpstreamModelClient.UpstreamStreamResponse(500,
                "application/json",
                new ByteArrayInputStream("{\"error\":\"boom\"}".getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<?> response = controller().chatCompletions("Bearer minted-secret",
                streamingRequest("finance-chat"));

        assertThat(response.getStatusCode().value()).isEqualTo(502);
        assertThat(errorType(response)).isEqualTo("upstream_error");
        assertThat(listener.afterResults.get(0).upstreamStatus()).isEqualTo(500);
    }

    @Test
    void streamWithoutUsageChunkReportsEstimatedZeroTokens() throws Exception {
        String sse = "data: {\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}\n\ndata: [DONE]\n\n";
        upstream.streamResponse = new UpstreamModelClient.UpstreamStreamResponse(200,
                "text/event-stream",
                new ByteArrayInputStream(sse.getBytes(StandardCharsets.UTF_8)));

        ResponseEntity<?> response = controller().chatCompletions("Bearer minted-secret",
                streamingRequest("finance-chat"));
        ((StreamingResponseBody) response.getBody()).writeTo(new ByteArrayOutputStream());

        assertThat(listener.afterResults.get(0).usage().estimated()).isTrue();
        assertThat(meterRegistry.find(LlmGatewayMetrics.TOKENS_TOTAL).counters()).isEmpty();
    }

    private static byte[] request(String model) {
        return ("{\"model\":\"" + model + "\",\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    private static byte[] streamingRequest(String model) {
        return ("{\"model\":\"" + model + "\",\"stream\":true,"
                + "\"messages\":[{\"role\":\"user\",\"content\":\"hi\"}]}")
                .getBytes(StandardCharsets.UTF_8);
    }

    /** Every response body is a StreamingResponseBody; drain it to bytes for assertions. */
    private static byte[] bodyBytes(ResponseEntity<?> response) {
        ByteArrayOutputStream drained = new ByteArrayOutputStream();
        try {
            ((StreamingResponseBody) response.getBody()).writeTo(drained);
        } catch (IOException e) {
            throw new AssertionError("response body failed to write", e);
        }
        return drained.toByteArray();
    }

    private static String errorType(ResponseEntity<?> response) {
        try {
            return JSON.readTree(bodyBytes(response)).path("error").path("type").asText();
        } catch (IOException e) {
            throw new AssertionError("error body is not JSON", e);
        }
    }

    /** Scripted port double: records the forwarded request, plays back a canned response. */
    private static final class ScriptedUpstream implements UpstreamModelClient {
        final AtomicInteger calls = new AtomicInteger();
        UpstreamRequest lastRequest;
        UpstreamResponse response;
        UpstreamStreamResponse streamResponse;
        UpstreamIoException failWith;

        @Override
        public UpstreamResponse exchange(UpstreamRequest request) {
            calls.incrementAndGet();
            lastRequest = request;
            if (failWith != null) {
                throw failWith;
            }
            return response;
        }

        @Override
        public UpstreamStreamResponse openStream(UpstreamRequest request) {
            calls.incrementAndGet();
            lastRequest = request;
            if (failWith != null) {
                throw failWith;
            }
            return streamResponse;
        }
    }

    private static final class RecordingListener implements LlmCallListener {
        final List<LlmCallContext> beforeContexts = new ArrayList<>();
        final List<LlmCallResult> afterResults = new ArrayList<>();

        @Override
        public void beforeLlmInvocation(LlmCallContext context) {
            beforeContexts.add(context);
        }

        @Override
        public void afterLlmInvocation(LlmCallContext context, LlmCallResult result) {
            afterResults.add(result);
        }
    }
}
