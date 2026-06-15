package com.huawei.ascend.bus.forwarding.spi;

/**
 * Abstract delivery port consumed by {@code ForwardingDispatcherWorker} to push a
 * claimed outbox record toward the receiver (Stage 8 plan §3 slice 5).
 *
 * <p>The port consumes only the {@link ForwardingOutboxRecord} — in particular the
 * opaque {@link ForwardingRouteHandle} — and returns a {@link ForwardingDeliveryResult}.
 * It MUST NOT unwrap {@code routeHandle} to a physical endpoint and MUST NOT write
 * Task execution state. A real HTTP / gRPC / broker transport binding is deferred
 * to a later stage; Stage 8 ships this interface plus an in-memory fake delivery
 * port so the worker can be exercised end-to-end (ACK / RETRY / DLQ / EXPIRED)
 * without any network dependency.
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §3};
 * {@code architecture/docs/L2/agent-bus/forwarding-persistence.md §5}.
 */
public interface ForwardingDeliveryPort {

    /**
     * Attempt delivery of a claimed (already DISPATCHING) outbox record and
     * report the outcome the worker should apply to the state machine.
     *
     * @param record         the claimed record (carries routeHandle / payloadRef)
     * @param nowMillisEpoch the delivery instant
     */
    ForwardingDeliveryResult deliver(ForwardingOutboxRecord record, long nowMillisEpoch);
}
