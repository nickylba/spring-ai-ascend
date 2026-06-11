package com.huawei.ascend.runtime.llm.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.huawei.ascend.runtime.boot.TraceParentFilter;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallContext;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallListener;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallResult;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmTokenUsage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

/**
 * OpenAI-compatible egress endpoint: authenticates the minted gateway token,
 * resolves the request's {@code model} alias, swaps in the upstream credential and
 * forwards the JSON body transparently — byte-identical except for the model-name
 * swap and, on streams, {@code stream_options.include_usage} injection so the final
 * chunk carries token usage. Upstream status and body pass through; upstream 5xx
 * and connect/IO failures surface as 502 because they are upstream faults, while
 * 4xx (auth, rate limit, validation) must reach the caller untranslated for its
 * retry semantics to work.
 *
 * <p>No inbound header is forwarded and no upstream header is copied back except
 * {@code Content-Type}, which strips hop-by-hop headers by construction. Telemetry
 * leaves exclusively through the ordered {@link LlmCallListener} chain; listener
 * failures are logged and never fail the call.
 */
@RestController
public final class ChatCompletionsController {

    private static final Logger log = LoggerFactory.getLogger(ChatCompletionsController.class);

    private static final String OUTCOME_SUCCESS = "success";
    private static final String OUTCOME_UPSTREAM_ERROR = "upstream_error";
    private static final String OUTCOME_TRANSPORT_ERROR = "transport_error";

    /**
     * Deliberately not the application's ObjectMapper: gateway JSON handling must
     * stay dialect-neutral and unaffected by application Jackson customisation.
     */
    private final ObjectMapper mapper = new ObjectMapper();
    private final UsageExtractor usageExtractor = new UsageExtractor();

    private final MintedTokenAuthenticator authenticator;
    private final ModelAliasRegistry registry;
    private final UpstreamModelClient upstream;
    private final LlmGatewayMetrics metrics;
    private final List<LlmCallListener> listeners;

    public ChatCompletionsController(MintedTokenAuthenticator authenticator,
            ModelAliasRegistry registry, UpstreamModelClient upstream,
            LlmGatewayMetrics metrics, List<LlmCallListener> listeners) {
        this.authenticator = authenticator;
        this.registry = registry;
        this.upstream = upstream;
        this.metrics = metrics;
        this.listeners = List.copyOf(listeners);
    }

    /**
     * Always declared as {@code ResponseEntity<StreamingResponseBody>}: Spring MVC
     * routes streaming bodies by the declared generic, so a wildcard would send SSE
     * relays through the message converters instead of the async streaming handler.
     */
    @PostMapping(path = "/v1/chat/completions")
    public ResponseEntity<StreamingResponseBody> chatCompletions(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody byte[] body) {
        var principal = authenticator.authenticate(authorization);
        if (principal.isEmpty()) {
            // Reject before any upstream contact: an unauthenticated caller must
            // never consume provider quota or learn upstream behaviour.
            return error(401, "missing or unknown gateway token", "authentication_error");
        }
        ObjectNode request = parseRequest(body);
        if (request == null) {
            return error(400, "request body is not a JSON object", "invalid_request_error");
        }
        String alias = textField(request, "model");
        if (alias == null) {
            return error(400, "missing required field: model", "invalid_request_error");
        }
        var route = registry.resolve(alias).orElse(null);
        if (route == null) {
            return error(404, "model '" + alias + "' is not a configured model alias",
                    "invalid_request_error");
        }
        boolean streaming = request.path("stream").asBoolean(false);
        byte[] forwarded = forwardedBody(body, request, route, streaming);
        LlmCallContext context = new LlmCallContext(
                principal.get().getTenantId(), principal.get().getAgentId(),
                alias, route.provider(), currentTraceId());
        notifyBefore(context);
        var upstreamRequest = new UpstreamModelClient.UpstreamRequest(
                route.chatCompletionsUrl(), route.apiKey(), forwarded);
        return streaming
                ? forwardStreaming(context, route, upstreamRequest)
                : forwardBuffered(context, route, upstreamRequest);
    }

    private ResponseEntity<StreamingResponseBody> forwardBuffered(LlmCallContext context,
            ModelAliasRegistry.Route route, UpstreamModelClient.UpstreamRequest request) {
        long startNanos = System.nanoTime();
        UpstreamModelClient.UpstreamResponse response;
        try {
            response = upstream.exchange(request);
        } catch (UpstreamModelClient.UpstreamIoException e) {
            return transportFailure(context, route, startNanos, e);
        }
        long latencyMillis = elapsedMillis(startNanos);
        boolean success = isSuccess(response.status());
        LlmTokenUsage usage = success
                ? usageExtractor.fromResponseBody(response.body())
                : LlmTokenUsage.estimatedAbsent();
        record(context, route, success ? OUTCOME_SUCCESS : OUTCOME_UPSTREAM_ERROR,
                latencyMillis, usage);
        notifyAfter(context, new LlmCallResult(success, response.status(), latencyMillis, usage));
        if (response.status() >= 500) {
            return error(502, "upstream returned status " + response.status(), "upstream_error");
        }
        return ResponseEntity.status(response.status())
                .contentType(parseContentType(response.contentType()))
                .body(writer(response.body()));
    }

    private ResponseEntity<StreamingResponseBody> forwardStreaming(LlmCallContext context,
            ModelAliasRegistry.Route route, UpstreamModelClient.UpstreamRequest request) {
        long startNanos = System.nanoTime();
        UpstreamModelClient.UpstreamStreamResponse response;
        try {
            response = upstream.openStream(request);
        } catch (UpstreamModelClient.UpstreamIoException e) {
            return transportFailure(context, route, startNanos, e);
        }
        if (!isSuccess(response.status())) {
            return streamingUpstreamError(context, route, startNanos, response);
        }
        MediaType contentType = parseContentType(response.contentType());
        StreamingResponseBody relay = output -> {
            UsageExtractor.SseAccumulator accumulator = usageExtractor.newSseAccumulator();
            boolean completed = false;
            try (response) {
                InputStream in = response.body();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                    // Flush per upstream chunk so tokens reach the caller as the
                    // provider produces them instead of at servlet-buffer boundaries.
                    output.flush();
                    accumulator.accept(buffer, read);
                }
                completed = true;
            } finally {
                long latencyMillis = elapsedMillis(startNanos);
                LlmTokenUsage usage = accumulator.finish();
                record(context, route, completed ? OUTCOME_SUCCESS : OUTCOME_TRANSPORT_ERROR,
                        latencyMillis, usage);
                notifyAfter(context,
                        new LlmCallResult(completed, response.status(), latencyMillis, usage));
            }
        };
        return ResponseEntity.status(response.status()).contentType(contentType).body(relay);
    }

    /** Non-2xx on a stream open: relay the (small) error payload as a buffered response. */
    private ResponseEntity<StreamingResponseBody> streamingUpstreamError(LlmCallContext context,
            ModelAliasRegistry.Route route, long startNanos,
            UpstreamModelClient.UpstreamStreamResponse response) {
        byte[] errorBody;
        try (response) {
            errorBody = response.body().readAllBytes();
        } catch (IOException e) {
            errorBody = new byte[0];
        }
        long latencyMillis = elapsedMillis(startNanos);
        LlmTokenUsage usage = LlmTokenUsage.estimatedAbsent();
        record(context, route, OUTCOME_UPSTREAM_ERROR, latencyMillis, usage);
        notifyAfter(context, new LlmCallResult(false, response.status(), latencyMillis, usage));
        if (response.status() >= 500) {
            return error(502, "upstream returned status " + response.status(), "upstream_error");
        }
        return ResponseEntity.status(response.status())
                .contentType(parseContentType(response.contentType()))
                .body(writer(errorBody));
    }

    private ResponseEntity<StreamingResponseBody> transportFailure(LlmCallContext context,
            ModelAliasRegistry.Route route, long startNanos,
            UpstreamModelClient.UpstreamIoException failure) {
        log.warn("LLM upstream unreachable for alias '{}': {}",
                context.modelAlias(), failure.getMessage());
        long latencyMillis = elapsedMillis(startNanos);
        metrics.recordRequest(context.modelAlias(), route.provider(), OUTCOME_TRANSPORT_ERROR);
        notifyAfter(context,
                new LlmCallResult(false, 0, latencyMillis, LlmTokenUsage.estimatedAbsent()));
        return error(502, "upstream unreachable: " + failure.getMessage(), "upstream_error");
    }

    private void record(LlmCallContext context, ModelAliasRegistry.Route route,
            String outcome, long latencyMillis, LlmTokenUsage usage) {
        metrics.recordRequest(context.modelAlias(), route.provider(), outcome);
        metrics.recordUpstreamLatency(context.modelAlias(), route.provider(),
                Duration.ofMillis(latencyMillis));
        if (!usage.estimated()) {
            // Estimated usage is zero-by-construction; counting it would understate
            // real token consumption as confidently-measured zeros.
            metrics.recordTokens(context.modelAlias(), route.provider(),
                    usage.inputTokens(), usage.outputTokens());
        }
    }

    private void notifyBefore(LlmCallContext context) {
        for (LlmCallListener listener : listeners) {
            try {
                listener.beforeLlmInvocation(context);
            } catch (RuntimeException e) {
                log.warn("LlmCallListener {} failed in beforeLlmInvocation",
                        listener.getClass().getName(), e);
            }
        }
    }

    private void notifyAfter(LlmCallContext context, LlmCallResult result) {
        for (LlmCallListener listener : listeners) {
            try {
                listener.afterLlmInvocation(context, result);
            } catch (RuntimeException e) {
                log.warn("LlmCallListener {} failed in afterLlmInvocation",
                        listener.getClass().getName(), e);
            }
        }
    }

    private ObjectNode parseRequest(byte[] body) {
        try {
            JsonNode root = mapper.readTree(body);
            return root instanceof ObjectNode object ? object : null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * The exact bytes to forward: the caller's body untouched unless the alias maps
     * to a different upstream model name or a stream needs
     * {@code stream_options.include_usage} — the two mutations the gateway owns.
     */
    private byte[] forwardedBody(byte[] original, ObjectNode request,
            ModelAliasRegistry.Route route, boolean streaming) {
        boolean mutated = false;
        String upstreamModel = route.upstreamModel();
        if (upstreamModel != null && !upstreamModel.equals(request.path("model").asText())) {
            request.put("model", upstreamModel);
            mutated = true;
        }
        if (streaming) {
            JsonNode streamOptions = request.get("stream_options");
            if (streamOptions == null || !streamOptions.isObject()) {
                request.set("stream_options", mapper.createObjectNode().put("include_usage", true));
                mutated = true;
            } else if (!streamOptions.has("include_usage")) {
                ((ObjectNode) streamOptions).put("include_usage", true);
                mutated = true;
            }
        }
        try {
            return mutated ? mapper.writeValueAsBytes(request) : original;
        } catch (IOException e) {
            // Cannot happen for a tree we just parsed; forward the original rather than fail.
            return original;
        }
    }

    private ResponseEntity<StreamingResponseBody> error(int status, String message, String type) {
        ObjectNode error = mapper.createObjectNode();
        error.putObject("error").put("message", message).put("type", type);
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(writer(error.toString().getBytes(StandardCharsets.UTF_8)));
    }

    private static StreamingResponseBody writer(byte[] bytes) {
        return output -> output.write(bytes);
    }

    private static String textField(ObjectNode request, String field) {
        JsonNode node = request.get(field);
        return node != null && node.isTextual() ? node.asText() : null;
    }

    private static MediaType parseContentType(String contentType) {
        if (contentType == null) {
            return MediaType.APPLICATION_JSON;
        }
        try {
            return MediaType.parseMediaType(contentType);
        } catch (RuntimeException e) {
            return MediaType.APPLICATION_JSON;
        }
    }

    private static boolean isSuccess(int status) {
        return status >= 200 && status < 300;
    }

    private static long elapsedMillis(long startNanos) {
        return Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    }

    private static String currentTraceId() {
        String traceId = MDC.get(TraceParentFilter.TRACE_ID_MDC_KEY);
        return traceId != null ? traceId : UUID.randomUUID().toString();
    }
}
