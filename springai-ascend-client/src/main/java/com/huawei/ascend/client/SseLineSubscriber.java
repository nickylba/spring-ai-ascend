package com.huawei.ascend.client;

import java.util.concurrent.Flow;
import org.a2aproject.sdk.client.http.ServerSentEventParser;

/** Feeds response body lines into the SSE parser; completion flushes a trailing event. */
final class SseLineSubscriber implements Flow.Subscriber<String> {

    private final ServerSentEventParser parser;
    private final Runnable completeRunnable;

    SseLineSubscriber(ServerSentEventParser parser, Runnable completeRunnable) {
        this.parser = parser;
        this.completeRunnable = completeRunnable;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(String line) {
        parser.processLine(line);
    }

    @Override
    public void onError(Throwable throwable) {
        // Surfaced once through the exchange future in HeaderCapturingHttpClient;
        // reporting here as well would double-deliver the same failure.
    }

    @Override
    public void onComplete() {
        parser.flush();
        completeRunnable.run();
    }
}
