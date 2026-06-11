package com.huawei.ascend.client;

import com.huawei.ascend.client.telemetry.ClientCallSpan;
import com.huawei.ascend.client.telemetry.ClientTelemetry;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.http.A2ACardResolver;
import org.a2aproject.sdk.client.transport.jsonrpc.JSONRPCTransport;
import org.a2aproject.sdk.client.transport.spi.interceptors.ClientCallContext;
import org.a2aproject.sdk.jsonrpc.common.json.JsonProcessingException;
import org.a2aproject.sdk.jsonrpc.common.json.JsonUtil;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskRequest;
import org.a2aproject.sdk.jsonrpc.common.wrappers.CancelTaskResponse;
import org.a2aproject.sdk.spec.AgentCard;
import org.a2aproject.sdk.spec.CancelTaskParams;
import org.a2aproject.sdk.spec.EventKind;
import org.a2aproject.sdk.spec.Message;
import org.a2aproject.sdk.spec.MessageSendParams;
import org.a2aproject.sdk.spec.StreamingEventKind;
import org.a2aproject.sdk.spec.Task;
import org.a2aproject.sdk.spec.TextPart;
import org.a2aproject.sdk.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A2A client for spring-ai-ascend ingress endpoints, built on the OSS
 * a2a-java-sdk JSON-RPC client. The facade exists for the semantics a raw
 * protocol client leaves to every caller:
 *
 * <ul>
 *   <li>blocking send/stream that completes on the platform's turn-ending
 *       events — run-terminal, or input/auth-required when the agent pauses
 *       for the caller — and classifies the SDK's post-turn-end SSE
 *       cancellation as normal completion (see {@link A2aEvents});</li>
 *   <li>per-call W3C {@code traceparent} origination and {@code traceresponse}
 *       correlation surfaced on every {@link A2aResponse}; with a
 *       {@link ClientTelemetry} configured, the outbound header is derived
 *       from that call's business span so wire trace and local span share
 *       one trace-id;</li>
 *   <li>JWT bearer / {@code X-Tenant-Id} headers on every request, matching
 *       the platform's tenant cross-check ingress (ADR-0040).</li>
 * </ul>
 *
 * <p>Instances are thread-safe: each call builds its own transport over one
 * shared JDK HTTP client, and the agent card is fetched once and cached.
 */
public final class AscendA2aClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(AscendA2aClient.class);

    private final String baseUrl;
    private final Duration timeout;
    private final ClientAuth auth;
    private final TracePropagation trace;
    private final ClientTelemetry telemetry;
    private final String serverAddress;
    private final HttpClient http;
    private final AtomicReference<AgentCard> cachedCard = new AtomicReference<>();

    private AscendA2aClient(Builder builder) {
        this.baseUrl = builder.baseUrl;
        this.timeout = builder.timeout;
        this.auth = builder.auth;
        this.trace = builder.trace;
        this.telemetry = builder.telemetry;
        this.serverAddress = hostOf(builder.baseUrl);
        this.http = HttpClient.newBuilder().connectTimeout(builder.timeout).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * The server's agent card from {@code /.well-known/agent-card.json},
     * fetched with this client's auth/trace headers and cached for the
     * lifetime of the client (the card also routes every subsequent call).
     */
    public AgentCard agentCard() {
        AgentCard cached = cachedCard.get();
        if (cached != null) {
            return cached;
        }
        AgentCard fetched = new A2ACardResolver(
                newHttpClient(traceresponse -> { }), baseUrl, null, null,
                callHeaders(trace.newTraceparent()))
                .getAgentCard();
        cachedCard.compareAndSet(null, fetched);
        return cachedCard.get();
    }

    /**
     * Blocking non-streaming send (JSON-RPC {@code SendMessage}); the answer
     * arrives as one terminal {@code Message} or {@code Task}.
     */
    public A2aResponse sendText(SendSpec spec) {
        Objects.requireNonNull(spec, "spec");
        AgentCard card = agentCard();
        CallTrace call = startCall("send", spec);
        JSONRPCTransport transport = newTransport(card, call.traceresponse());
        try {
            EventKind result = transport.sendMessage(
                    messageSendParams(spec), callContext(call.traceparent()));
            List<StreamingEventKind> events = result instanceof StreamingEventKind streaming
                    ? List.of(streaming) : List.of();
            return complete(call, events);
        } catch (RuntimeException e) {
            fail(call, e);
            throw e;
        } finally {
            transport.close();
        }
    }

    /**
     * Blocking JSON-RPC {@code tasks/cancel} for a task this client created
     * — e.g. one suspended on {@link A2aResponse#awaitingInput()} the caller
     * decides not to answer. Returns the server's task snapshot after the
     * cancellation; the runtime also marks the task's non-terminal runs
     * cancelled.
     */
    public Task cancelTask(String taskId) {
        Objects.requireNonNull(taskId, "taskId");
        // Deliberately NOT the OSS transport's cancelTask: the server wraps the
        // result in the proto-JSON oneof shape ({"result":{"task":{...}}}),
        // which the OSS client-side parser rejects expecting a bare Task. Both
        // ends of THIS path use the SDK's JsonUtil, so the round trip is
        // symmetric by construction.
        String traceparent = trace.newTraceparent();
        try {
            String requestJson = JsonUtil.toJson(new CancelTaskRequest(
                    "cancel-" + UUID.randomUUID(), new CancelTaskParams(taskId)));
            String body = postTasksCancel(requestJson, traceparent);
            CancelTaskResponse parsed = JsonUtil.fromJson(body, CancelTaskResponse.class);
            if (parsed.getResult() == null) {
                throw new IllegalStateException("tasks/cancel returned no task: " + body);
            }
            return parsed.getResult();
        } catch (JsonProcessingException | IOException e) {
            throw new IllegalStateException("tasks/cancel failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("tasks/cancel interrupted", e);
        }
    }

    /** One blocking JSON-RPC POST to {@code /a2a} with this client's auth/trace headers. */
    private String postTasksCancel(String requestJson, String traceparent)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(baseUrl + "/a2a"))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestJson));
        callHeaders(traceparent).forEach(request::header);
        HttpResponse<String> response = http.send(
                request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("tasks/cancel failed with HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** Streaming send aggregated into one blocking result; see {@link #streamText(SendSpec, Consumer)}. */
    public A2aResponse streamText(SendSpec spec) throws InterruptedException {
        return streamText(spec, event -> { });
    }

    /**
     * Streaming send (JSON-RPC {@code SendStreamingMessage} over SSE):
     * {@code listener} observes every event as it arrives; the call blocks
     * until a turn-ending event (or classified failure), then returns the
     * aggregate. A turn ends on a run-terminal event OR an input-required /
     * auth-required status — the runtime keeps a suspended run's stream open,
     * so the agent's prompt must return to the caller instead of timing out;
     * {@link A2aResponse#awaitingInput()} tells the two apart. Failure
     * classification: any transport error before a turn-ending event —
     * including a premature SSE cancellation — fails the call; the normal
     * post-turn-end cancellation does not.
     *
     * @throws IllegalStateException when the stream fails or does not reach a
     *                               turn-ending event within the configured timeout
     */
    public A2aResponse streamText(SendSpec spec, Consumer<StreamingEventKind> listener)
            throws InterruptedException {
        Objects.requireNonNull(spec, "spec");
        Objects.requireNonNull(listener, "listener");
        AgentCard card = agentCard();
        CallTrace call = startCall("stream", spec);
        try {
            List<StreamingEventKind> events = streamEvents(card, spec, listener, call);
            return complete(call, events);
        } catch (RuntimeException | InterruptedException e) {
            fail(call, e);
            throw e;
        }
    }

    /** The streaming exchange itself: collect events until turn-end/failure/timeout. */
    private List<StreamingEventKind> streamEvents(AgentCard card, SendSpec spec,
            Consumer<StreamingEventKind> listener, CallTrace call) throws InterruptedException {
        StreamTurnCollector collector = new StreamTurnCollector(listener);
        JSONRPCTransport transport = newTransport(card, call.traceresponse());
        try {
            transport.sendMessageStreaming(messageSendParams(spec),
                    collector::onEvent, collector::onError, callContext(call.traceparent()));
            collector.awaitTurnEnd(timeout);
        } finally {
            transport.close();
        }
        return collector.eventsOrThrow();
    }

    @Override
    public void close() {
        // Immediate shutdown, not the graceful close(): a graceful close waits
        // for in-flight exchanges, and a stream that just failed its completion
        // timeout may still hold an open SSE connection — close() must never
        // hang on it. All successful calls complete before returning, so there
        // is nothing graceful shutdown would protect.
        http.shutdownNow();
        // No-op unless the telemetry owns its pipeline (ClientTelemetry.otlpHttp):
        // a consumer-supplied OpenTelemetry SDK is never shut down here.
        telemetry.close();
    }

    /** Per-call telemetry context: business span, wire traceparent, traceresponse capture. */
    private record CallTrace(ClientCallSpan span, String traceparent,
            AtomicReference<String> traceresponse) {
    }

    private CallTrace startCall(String operation, SendSpec spec) {
        ClientCallSpan span = telemetry.startCall(operation, spec, tenantId(), serverAddress);
        return new CallTrace(span, outboundTraceparent(span), new AtomicReference<>());
    }

    /**
     * The header that crosses the wire: the active span's trace context when
     * telemetry originates one, otherwise this client's own per-call mint.
     */
    private String outboundTraceparent(ClientCallSpan span) {
        String fromSpan = span.traceparent();
        return fromSpan != null ? fromSpan : trace.newTraceparent();
    }

    private A2aResponse complete(CallTrace call, List<StreamingEventKind> events) {
        String traceresponse = call.traceresponse().get();
        A2aResponse result = response(events, call.traceparent(), traceresponse);
        call.span().traceresponse(traceresponse);
        call.span().succeed(events.stream().anyMatch(A2aEvents::isTerminal), result.text());
        return result;
    }

    private static void fail(CallTrace call, Throwable error) {
        call.span().traceresponse(call.traceresponse().get());
        call.span().fail(error);
    }

    private String tenantId() {
        return auth == null ? null : auth.tenantId();
    }

    /** Null (attribute omitted) rather than failing on an unparsable base URL. */
    private static String hostOf(String baseUrl) {
        try {
            return URI.create(baseUrl).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private A2aResponse response(List<StreamingEventKind> events, String traceparent,
            String traceresponse) {
        if (log.isDebugEnabled()) {
            log.debug("A2A call completed: events={} traceparent={} traceresponse={}",
                    events.size(), traceparent, traceresponse);
        }
        return new A2aResponse(A2aEvents.textFrom(events), events,
                new TraceCorrelation(traceparent, traceresponse));
    }

    private JSONRPCTransport newTransport(AgentCard card, AtomicReference<String> traceresponse) {
        return new JSONRPCTransport(newHttpClient(traceresponse::set), card,
                Utils.getFavoriteInterface(card), List.of());
    }

    private HeaderCapturingHttpClient newHttpClient(Consumer<String> traceresponseSink) {
        return new HeaderCapturingHttpClient(http, timeout, traceresponseSink);
    }

    private ClientCallContext callContext(String traceparent) {
        return new ClientCallContext(Map.of(), callHeaders(traceparent));
    }

    private Map<String, String> callHeaders(String traceparent) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (auth != null) {
            headers.putAll(auth.headers());
        }
        headers.put("traceparent", traceparent);
        return headers;
    }

    private static MessageSendParams messageSendParams(SendSpec spec) {
        Message message = Message.builder()
                .role(Message.Role.ROLE_USER)
                .messageId(spec.messageId())
                .contextId(spec.sessionId())
                .metadata(spec.messageMetadata())
                .parts(List.of(new TextPart(spec.text())))
                .build();
        return MessageSendParams.builder()
                .message(message)
                .build();
    }

    /** Builder; {@code baseUrl} is the only required field. */
    public static final class Builder {

        private String baseUrl;
        private Duration timeout = Duration.ofSeconds(30);
        private ClientAuth auth;
        private TracePropagation trace = TracePropagation.sampled();
        private ClientTelemetry telemetry = ClientTelemetry.noop();

        private Builder() {
        }

        /** Server base URL, e.g. {@code http://localhost:8080}. Required. */
        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        /** Per-call completion timeout (connect + request + stream-to-terminal). Default 30s. */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout, "timeout");
            return this;
        }

        /** Optional; without it no Authorization/X-Tenant-Id headers are sent. */
        public Builder auth(ClientAuth auth) {
            this.auth = auth;
            return this;
        }

        /**
         * Optional; defaults to {@link TracePropagation#sampled()}. Used only
         * for calls whose telemetry originates no trace context.
         */
        public Builder tracePropagation(TracePropagation trace) {
            this.trace = Objects.requireNonNull(trace, "trace");
            return this;
        }

        /**
         * Optional; defaults to {@link ClientTelemetry#noop()} (no spans,
         * unchanged behavior). The telemetry is closed with the client —
         * harmless for span-only telemetry, and exactly what an owned
         * {@link ClientTelemetry#otlpHttp} pipeline needs.
         */
        public Builder telemetry(ClientTelemetry telemetry) {
            this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
            return this;
        }

        public AscendA2aClient build() {
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalArgumentException("baseUrl must not be null or blank");
            }
            return new AscendA2aClient(this);
        }
    }
}
