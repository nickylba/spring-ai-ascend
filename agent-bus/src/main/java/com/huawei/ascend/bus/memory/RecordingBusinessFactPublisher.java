package com.huawei.ascend.bus.memory;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Objects;

/**
 * Reference {@link BusinessFactPublisher}: records emitted facts in a bounded
 * in-memory log so tests and co-hosted consumers can observe the emission
 * path.
 *
 * <p>This is deliberately NOT persistence — the platform never stores
 * business facts (ADR-0051 ownership boundary). The log is bounded
 * ({@value #DEFAULT_MAX_RECORDED_EVENTS} by default, oldest dropped) and
 * {@link #drain()} hands the recorded facts over exactly once, mirroring the
 * "emitted, then gone from the platform" contract. A real deployment replaces
 * this with a C-side bridge.
 */
public final class RecordingBusinessFactPublisher implements BusinessFactPublisher {

    public static final int DEFAULT_MAX_RECORDED_EVENTS = 1000;

    private final int maxRecordedEvents;
    private final ArrayDeque<BusinessFactEvent> recorded = new ArrayDeque<>();

    public RecordingBusinessFactPublisher() {
        this(DEFAULT_MAX_RECORDED_EVENTS);
    }

    public RecordingBusinessFactPublisher(int maxRecordedEvents) {
        if (maxRecordedEvents <= 0) {
            throw new IllegalArgumentException("maxRecordedEvents must be positive");
        }
        this.maxRecordedEvents = maxRecordedEvents;
    }

    @Override
    public synchronized void publish(BusinessFactEvent event) {
        Objects.requireNonNull(event, "event is required");
        recorded.addLast(event);
        while (recorded.size() > maxRecordedEvents) {
            recorded.removeFirst();
        }
    }

    /**
     * Snapshot of all recorded facts in emission order, clearing the log —
     * each fact is observable exactly once, so consumers cannot treat this
     * publisher as a queryable fact store.
     */
    public synchronized List<BusinessFactEvent> drain() {
        List<BusinessFactEvent> snapshot = List.copyOf(recorded);
        recorded.clear();
        return snapshot;
    }
}
