package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingDeliveryResult;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxClaimPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxPort;
import com.huawei.ascend.bus.forwarding.spi.ForwardingOutboxRecord;

import java.util.List;
import java.util.Objects;

/**
 * Minimal dispatcher worker that drives claimed outbox records to a terminal
 * state through the abstract delivery port (Stage 8 plan §3 slice 5).
 *
 * <p>This is the claim / deliver / ack / retry half of the forwarding lifecycle,
 * kept separate from {@code ForwardingDispatcher} (the accept / enqueue gateway
 * role) per MI8-003. A single synchronous {@link #runOnce} tick:
 * <ol>
 *   <li>claims due records via {@link ForwardingOutboxClaimPort#claimDue} (each
 *       already atomically transitioned to DISPATCHING and leased to the worker);</li>
 *   <li>delivers each via {@link ForwardingDeliveryPort#deliver}, consuming only
 *       the opaque {@code routeHandle} (never a physical endpoint);</li>
 *   <li>maps the {@link ForwardingDeliveryResult} to the matching outbox state
 *       transition — ACKED / RETRY_SCHEDULED / DLQ / EXPIRED.</li>
 * </ol>
 *
 * <p>The worker holds no threads, no scheduler, no registry, no transport. Real
 * polling cadence, threading, backpressure and a concrete delivery binding are
 * deferred to a later stage; Stage 8 ships this skeleton so the ACK / RETRY /
 * DLQ / EXPIRED paths can be exercised with a fake delivery port. The worker
 * never writes Task execution state.
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §3/§4.1};
 * {@code architecture/docs/L2/agent-bus/forwarding-persistence.md §5}.
 */
public final class ForwardingDispatcherWorker {

    private final ForwardingOutboxClaimPort claimPort;
    private final ForwardingOutboxPort outboxPort;
    private final ForwardingDeliveryPort deliveryPort;

    public ForwardingDispatcherWorker(ForwardingOutboxClaimPort claimPort,
                                      ForwardingOutboxPort outboxPort,
                                      ForwardingDeliveryPort deliveryPort) {
        this.claimPort = Objects.requireNonNull(claimPort, "claimPort is required");
        this.outboxPort = Objects.requireNonNull(outboxPort, "outboxPort is required");
        this.deliveryPort = Objects.requireNonNull(deliveryPort, "deliveryPort is required");
    }

    /**
     * Run one dispatch tick for a single tenant.
     *
     * @param tenantId             tenant scope of the tick (Rule R-C.c)
     * @param nowMillisEpoch       the tick instant
     * @param limit                max records to claim this tick ({@code > 0})
     * @param leaseOwner           identity of this worker instance
     * @param leaseUntilMillisEpoch instant until which claimed leases are exclusive
     * @return a summary of how many records were claimed and how each resolved
     */
    public DispatchTickResult runOnce(String tenantId, long nowMillisEpoch, int limit,
                                      String leaseOwner, long leaseUntilMillisEpoch) {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(leaseOwner, "leaseOwner is required");
        if (leaseOwner.isBlank()) {
            throw new IllegalArgumentException("leaseOwner must not be blank");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be > 0");
        }

        List<ForwardingOutboxRecord> claimed =
                claimPort.claimDue(tenantId, nowMillisEpoch, limit, leaseOwner, leaseUntilMillisEpoch);

        int acked = 0;
        int retried = 0;
        int dlqd = 0;
        int expired = 0;
        for (ForwardingOutboxRecord record : claimed) {
            ForwardingDeliveryResult result = deliveryPort.deliver(record, nowMillisEpoch);
            switch (result.outcome()) {
                case ACKED -> {
                    outboxPort.markAcked(record.messageId(), tenantId);
                    acked++;
                }
                case RETRY_SCHEDULED -> {
                    outboxPort.scheduleRetry(record.messageId(), tenantId,
                            result.failureCode(), result.nextAttemptAtMillisEpoch());
                    retried++;
                }
                case DLQ -> {
                    outboxPort.moveToDlq(record.messageId(), tenantId, result.failureCode());
                    dlqd++;
                }
                case EXPIRED -> {
                    outboxPort.markExpired(record.messageId(), tenantId);
                    expired++;
                }
            }
        }
        return new DispatchTickResult(claimed.size(), acked, retried, dlqd, expired);
    }

    /** Immutable summary of one dispatch tick. */
    public record DispatchTickResult(int claimed, int acked, int retried, int dlqd, int expired) {
        public DispatchTickResult {
            if (claimed < 0 || acked < 0 || retried < 0 || dlqd < 0 || expired < 0) {
                throw new IllegalArgumentException("tick counts must be non-negative");
            }
        }
    }
}
