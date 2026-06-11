package com.huawei.ascend.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.protobuf.util.JsonFormat;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils;
import org.a2aproject.sdk.grpc.utils.ProtoUtils;
import org.a2aproject.sdk.spec.AgentCapabilities;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.AgentInterface;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.spec.TransportProtocol;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Wire-level contract of the facade against a stub A2A server: the auth and
 * trace headers must actually be SENT (not just configured), the server's
 * {@code traceresponse} must surface on the result, and send/stream must
 * extract the user-visible text. The stub serializes its responses with the
 * same proto-JSON helpers the real server side uses, so the JSON dialect
 * matches the platform's A2A surface by construction.
 */
class AscendA2aClientStubServerTest {

    private static final String TRACERESPONSE =
            "00-0123456789abcdef0123456789abcdef-89abcdef01234567-01";
    private static final Pattern TRACEPARENT_PATTERN =
            Pattern.compile("00-[0-9a-f]{32}-[0-9a-f]{16}-0[01]");
    private static final Pattern REQUEST_ID_PATTERN =
            Pattern.compile("\"id\"\\s*:\\s*\"([^\"]+)\"");

    private HttpServer server;
    private String baseUrl;
    /** Last-seen request headers per path, captured off the wire. */
    private final Map<String, Map<String, String>> recordedHeaders = new ConcurrentHashMap<>();

    @BeforeEach
    void startStubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        baseUrl = "http://localhost:" + server.getAddress().getPort();
        server.createContext("/.well-known/agent-card.json", this::serveAgentCard);
        server.createContext("/a2a", this::serveJsonRpc);
        server.start();
    }

    @AfterEach
    void stopStubServer() {
        server.stop(0);
    }

    @Test
    void sendTextSendsAuthAndTraceHeadersAndSurfacesTraceresponseAndText() throws Exception {
        try (AscendA2aClient client = newClient()) {
            A2aResponse response = client.sendText(
                    SendSpec.of("stub-agent", "session-1", "user-1", "ping"));

            assertThat(response.text()).isEqualTo("pong");
            assertThat(response.events()).hasSize(1);
            assertThat(response.trace().traceresponse()).isEqualTo(TRACERESPONSE);

            Map<String, String> wire = recordedHeaders.get("/a2a");
            assertThat(wire.get("authorization")).isEqualTo("Bearer token-123");
            assertThat(wire.get("x-tenant-id")).isEqualTo("tenant-1");
            assertThat(wire.get("traceparent")).matches(TRACEPARENT_PATTERN);
            // The surfaced traceparent is the one that actually crossed the wire.
            assertThat(response.trace().traceparent()).isEqualTo(wire.get("traceparent"));
        }
    }

    @Test
    void streamTextCompletesOnTerminalEventAndExcludesAcceptedAck() throws Exception {
        try (AscendA2aClient client = newClient()) {
            A2aResponse response = client.streamText(
                    SendSpec.of("stub-agent", "session-1", "user-1", "ping"));

            assertThat(response.events()).hasSize(2);
            assertThat(response.events())
                    .anySatisfy(event -> assertThat(A2aEvents.isTerminal(event)).isTrue());
            assertThat(response.text()).isEqualTo("pong");
            assertThat(response.trace().traceresponse()).isEqualTo(TRACERESPONSE);

            Map<String, String> wire = recordedHeaders.get("/a2a");
            assertThat(wire.get("authorization")).isEqualTo("Bearer token-123");
            assertThat(wire.get("x-tenant-id")).isEqualTo("tenant-1");
            assertThat(response.trace().traceparent()).isEqualTo(wire.get("traceparent"));
        }
    }

    @Test
    void agentCardIsFetchedWithAuthAndTraceHeaders() throws Exception {
        try (AscendA2aClient client = newClient()) {
            AgentCard card = client.agentCard();

            assertThat(card.name()).isEqualTo("stub-agent");
            assertThat(card.capabilities().streaming()).isTrue();

            Map<String, String> wire = recordedHeaders.get("/.well-known/agent-card.json");
            assertThat(wire.get("authorization")).isEqualTo("Bearer token-123");
            assertThat(wire.get("x-tenant-id")).isEqualTo("tenant-1");
            assertThat(wire.get("traceparent")).matches(TRACEPARENT_PATTERN);
        }
    }

    private AscendA2aClient newClient() {
        return AscendA2aClient.builder()
                .baseUrl(baseUrl)
                .timeout(Duration.ofSeconds(10))
                .auth(ClientAuth.jwtBearer(() -> "token-123", "tenant-1"))
                .build();
    }

    private void serveAgentCard(HttpExchange exchange) throws IOException {
        record(exchange);
        AgentCard card = AgentCard.builder()
                .name("stub-agent")
                .description("stub A2A server for wire-contract tests")
                .url(baseUrl + "/a2a")
                .version("1.0")
                .capabilities(AgentCapabilities.builder().streaming(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of())
                .supportedInterfaces(List.of(
                        new AgentInterface(TransportProtocol.JSONRPC.asString(), baseUrl + "/a2a")))
                .preferredTransport(TransportProtocol.JSONRPC.asString())
                .build();
        String json = JsonFormat.printer().print(ProtoUtils.ToProto.agentCard(card));
        respondJson(exchange, json);
    }

    private void serveJsonRpc(HttpExchange exchange) throws IOException {
        record(exchange);
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String requestId = requestId(body);
        if (body.contains("\"SendStreamingMessage\"")) {
            serveSse(exchange, requestId);
        } else {
            respondJson(exchange, JSONRPCUtils.toJsonRPCResultResponse(
                    requestId, ProtoUtils.ToProto.taskOrMessage(completedPong())));
        }
    }

    private void serveSse(HttpExchange exchange, String requestId) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("traceresponse", TRACERESPONSE);
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream out = exchange.getResponseBody()) {
            writeSseEvent(out, JSONRPCUtils.toJsonRPCResultResponse(
                    requestId, ProtoUtils.ToProto.taskOrMessageStream(acceptedAck())));
            writeSseEvent(out, JSONRPCUtils.toJsonRPCResultResponse(
                    requestId, ProtoUtils.ToProto.taskOrMessageStream(completedPong())));
        }
    }

    /**
     * Multi-line JSON is emitted as one SSE event with one {@code data:} line
     * per JSON line; conforming parsers re-join them with newlines.
     */
    private static void writeSseEvent(OutputStream out, String json) throws IOException {
        StringBuilder event = new StringBuilder();
        for (String line : json.split("\\R")) {
            event.append("data: ").append(line).append('\n');
        }
        event.append('\n');
        out.write(event.toString().getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static Message acceptedAck() {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("ack-1")
                .metadata(Map.of("accepted", Boolean.TRUE))
                .parts(List.of(new TextPart("execution enqueued")))
                .build();
    }

    private static Message completedPong() {
        return Message.builder()
                .role(Message.Role.ROLE_AGENT)
                .messageId("reply-1")
                .metadata(Map.of("runStatus", "completed"))
                .parts(List.of(new TextPart("pong")))
                .build();
    }

    private static String requestId(String body) {
        Matcher matcher = REQUEST_ID_PATTERN.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("JSON-RPC request without string id: " + body);
        }
        return matcher.group(1);
    }

    /** Header names recorded lowercase: HTTP headers are case-insensitive on the wire. */
    private void record(HttpExchange exchange) {
        Map<String, String> headers = new ConcurrentHashMap<>();
        exchange.getRequestHeaders().forEach((name, values) -> {
            if (!values.isEmpty()) {
                headers.put(name.toLowerCase(java.util.Locale.ROOT), values.get(0));
            }
        });
        recordedHeaders.put(exchange.getRequestURI().getPath(), headers);
    }

    private static void respondJson(HttpExchange exchange, String json) throws IOException {
        byte[] payload = json.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("traceresponse", TRACERESPONSE);
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(payload);
        }
    }
}
