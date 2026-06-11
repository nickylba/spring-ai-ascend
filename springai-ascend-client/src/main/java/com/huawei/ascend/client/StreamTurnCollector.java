package com.huawei.ascend.client;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.a2aproject.sdk.spec.StreamingEventKind;

/**
 * Collects one streaming exchange's events until a turn-ending event or a
 * classified failure (see {@link A2aEvents}): the latch choreography behind
 * {@link AscendA2aClient#streamText}. Event and error callbacks arrive on the
 * transport's threads while the caller blocks in {@link #awaitTurnEnd}, hence
 * the synchronized list and atomics.
 */
final class StreamTurnCollector {

    private final List<StreamingEventKind> events = Collections.synchronizedList(new ArrayList<>());
    private final CountDownLatch completed = new CountDownLatch(1);
    private final AtomicReference<Throwable> failure = new AtomicReference<>();
    private final AtomicBoolean sawTurnEnd = new AtomicBoolean(false);
    private final Consumer<StreamingEventKind> listener;

    StreamTurnCollector(Consumer<StreamingEventKind> listener) {
        this.listener = listener;
    }

    void onEvent(StreamingEventKind event) {
        events.add(event);
        listener.accept(event);
        if (A2aEvents.isTurnEnding(event)) {
            sawTurnEnd.set(true);
            completed.countDown();
        }
    }

    void onError(Throwable error) {
        if (A2aEvents.isFailureError(error, sawTurnEnd.get())) {
            failure.set(error);
        }
        completed.countDown();
    }

    /** Blocks until a turn-ending event or stream error arrives. */
    void awaitTurnEnd(Duration timeout) throws InterruptedException {
        if (!completed.await(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("A2A stream did not complete before timeout");
        }
    }

    /**
     * The collected events, or the classified failure. Called after the
     * transport is closed so a post-turn-end cancellation error has been
     * delivered (and classified as normal) before the verdict.
     */
    List<StreamingEventKind> eventsOrThrow() {
        if (failure.get() != null) {
            throw new IllegalStateException("A2A stream failed", failure.get());
        }
        synchronized (events) {
            return List.copyOf(events);
        }
    }
}
