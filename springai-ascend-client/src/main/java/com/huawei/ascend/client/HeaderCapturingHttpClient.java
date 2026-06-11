package com.huawei.ascend.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.a2aproject.sdk.client.http.A2AHttpClient;
import org.a2aproject.sdk.client.http.A2AHttpResponse;
import org.a2aproject.sdk.client.http.ServerSentEvent;
import org.a2aproject.sdk.client.http.ServerSentEventParser;

/**
 * {@link A2AHttpClient} over {@code java.net.http} that captures the
 * platform's {@code traceresponse} header. This is the only reason the stock
 * {@code JdkA2AHttpClient} is not used: the OSS {@link A2AHttpResponse}
 * surface exposes status and body but no response headers, and the
 * {@code traceresponse} correlation contract requires them.
 *
 * <p>Streaming preserves the stock client's error semantics, which the
 * facade's post-terminal-cancellation rule depends on: cancelling the future
 * returned by {@code postAsyncSSE} (the A2A transport does this right after a
 * terminal event) surfaces a {@link CancellationException} to the error
 * consumer and aborts the underlying exchange; at most one error is ever
 * delivered per stream.
 */
final class HeaderCapturingHttpClient implements A2AHttpClient {

    private static final String TRACERESPONSE_HEADER = "traceresponse";

    private final java.net.http.HttpClient http;
    private final Duration timeout;
    private final Consumer<String> traceresponseSink;

    HeaderCapturingHttpClient(java.net.http.HttpClient http, Duration timeout,
            Consumer<String> traceresponseSink) {
        this.http = http;
        this.timeout = timeout;
        this.traceresponseSink = traceresponseSink;
    }

    @Override
    public GetBuilder createGet() {
        return new Get();
    }

    @Override
    public PostBuilder createPost() {
        return new Post();
    }

    @Override
    public DeleteBuilder createDelete() {
        return new Delete();
    }

    private void capture(HttpHeaders headers) {
        headers.firstValue(TRACERESPONSE_HEADER).ifPresent(traceresponseSink);
    }

    private HttpRequest request(String url, Map<String, String> headers, String acceptDefault,
            Consumer<HttpRequest.Builder> method) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url)).timeout(timeout);
        Map<String, String> all = new LinkedHashMap<>(headers);
        all.putIfAbsent(ACCEPT, acceptDefault);
        all.forEach(builder::header);
        method.accept(builder);
        return builder.build();
    }

    private A2AHttpResponse send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        capture(response.headers());
        return new CapturedResponse(response.statusCode(), response.body());
    }

    private CompletableFuture<Void> asyncSse(HttpRequest request,
            Consumer<ServerSentEvent> eventConsumer, Consumer<Throwable> errorConsumer,
            Runnable completeRunnable) {
        Consumer<Throwable> deliverOnce = deliverOnceTo(errorConsumer);
        ServerSentEventParser parser = new ServerSentEventParser(eventConsumer, deliverOnce);
        CompletableFuture<HttpResponse<Void>> exchange = http.sendAsync(request,
                responseInfo -> sseBody(responseInfo, parser, deliverOnce, completeRunnable));
        return subscriptionFor(exchange, deliverOnce);
    }

    /** At most one error is ever delivered per stream, matching the stock client. */
    private static Consumer<Throwable> deliverOnceTo(Consumer<Throwable> errorConsumer) {
        AtomicBoolean errorDelivered = new AtomicBoolean();
        return error -> {
            if (errorDelivered.compareAndSet(false, true)) {
                errorConsumer.accept(error);
            }
        };
    }

    /** Header capture plus HTTP-status gate before the body reaches the SSE parser. */
    private HttpResponse.BodySubscriber<Void> sseBody(HttpResponse.ResponseInfo responseInfo,
            ServerSentEventParser parser, Consumer<Throwable> deliverOnce,
            Runnable completeRunnable) {
        capture(responseInfo.headers());
        if (responseInfo.statusCode() != 200) {
            deliverOnce.accept(new IOException(
                    "A2A SSE endpoint returned HTTP " + responseInfo.statusCode()));
            return HttpResponse.BodySubscribers.discarding();
        }
        return HttpResponse.BodySubscribers.fromLineSubscriber(
                new SseLineSubscriber(parser, completeRunnable));
    }

    /** The future handed to the transport, mirroring the exchange's outcome. */
    private static CompletableFuture<Void> subscriptionFor(
            CompletableFuture<HttpResponse<Void>> exchange, Consumer<Throwable> deliverOnce) {
        CompletableFuture<Void> subscription = new CompletableFuture<>();
        exchange.whenComplete((response, error) -> {
            if (error != null) {
                deliverOnce.accept(unwrap(error));
                subscription.completeExceptionally(error);
            } else {
                subscription.complete(null);
            }
        });
        subscription.whenComplete((nothing, error) -> {
            if (subscription.isCancelled()) {
                // The transport cancels the subscription right after a terminal
                // event; surface it like the stock client so the facade's
                // post-terminal classification applies, then stop the exchange.
                deliverOnce.accept(new CancellationException("A2A SSE subscription cancelled"));
                exchange.cancel(true);
            }
        });
        return subscription;
    }

    private static Throwable unwrap(Throwable error) {
        if ((error instanceof CompletionException || error instanceof ExecutionException)
                && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private record CapturedResponse(int status, String body) implements A2AHttpResponse {
        @Override
        public boolean success() {
            return status / 100 == 2;
        }
    }

    private final class Get implements GetBuilder {

        private final Map<String, String> headers = new LinkedHashMap<>();
        private String url;

        @Override
        public GetBuilder url(String url) {
            this.url = url;
            return this;
        }

        @Override
        public GetBuilder addHeaders(Map<String, String> toAdd) {
            headers.putAll(toAdd);
            return this;
        }

        @Override
        public GetBuilder addHeader(String name, String value) {
            headers.put(name, value);
            return this;
        }

        @Override
        public A2AHttpResponse get() throws IOException, InterruptedException {
            return send(request(url, headers, APPLICATION_JSON, HttpRequest.Builder::GET));
        }

        @Override
        public CompletableFuture<Void> getAsyncSSE(Consumer<ServerSentEvent> eventConsumer,
                Consumer<Throwable> errorConsumer, Runnable completeRunnable) {
            return asyncSse(request(url, headers, EVENT_STREAM, HttpRequest.Builder::GET),
                    eventConsumer, errorConsumer, completeRunnable);
        }
    }

    private final class Post implements PostBuilder {

        private final Map<String, String> headers = new LinkedHashMap<>();
        private String url;
        private String body = "";

        @Override
        public PostBuilder url(String url) {
            this.url = url;
            return this;
        }

        @Override
        public PostBuilder addHeaders(Map<String, String> toAdd) {
            headers.putAll(toAdd);
            return this;
        }

        @Override
        public PostBuilder addHeader(String name, String value) {
            headers.put(name, value);
            return this;
        }

        @Override
        public PostBuilder body(String body) {
            this.body = body;
            return this;
        }

        @Override
        public A2AHttpResponse post() throws IOException, InterruptedException {
            return send(postRequest(APPLICATION_JSON));
        }

        @Override
        public CompletableFuture<Void> postAsyncSSE(Consumer<ServerSentEvent> eventConsumer,
                Consumer<Throwable> errorConsumer, Runnable completeRunnable) {
            return asyncSse(postRequest(EVENT_STREAM), eventConsumer, errorConsumer, completeRunnable);
        }

        private HttpRequest postRequest(String acceptDefault) {
            Map<String, String> withContentType = new LinkedHashMap<>(headers);
            withContentType.putIfAbsent(CONTENT_TYPE, APPLICATION_JSON);
            return request(url, withContentType, acceptDefault,
                    builder -> builder.POST(HttpRequest.BodyPublishers.ofString(body)));
        }
    }

    private final class Delete implements DeleteBuilder {

        private final Map<String, String> headers = new LinkedHashMap<>();
        private String url;

        @Override
        public DeleteBuilder url(String url) {
            this.url = url;
            return this;
        }

        @Override
        public DeleteBuilder addHeaders(Map<String, String> toAdd) {
            headers.putAll(toAdd);
            return this;
        }

        @Override
        public DeleteBuilder addHeader(String name, String value) {
            headers.put(name, value);
            return this;
        }

        @Override
        public A2AHttpResponse delete() throws IOException, InterruptedException {
            return send(request(url, headers, APPLICATION_JSON, HttpRequest.Builder::DELETE));
        }
    }
}
