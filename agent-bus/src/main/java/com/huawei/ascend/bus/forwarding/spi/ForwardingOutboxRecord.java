package com.huawei.ascend.bus.forwarding.spi;

import java.util.Objects;

/**
 * Sender-side durable-queue record for the C3 outbox substrate (Stage 8).
 *
 * <p>Mirrors the outbox record schema of {@code ICD-Agent-Bus-Forwarding-Runtime}
 * field-for-field: the gateway writes a record when it accepts an envelope
 * ({@link ForwardingOutboxPort#enqueue}), and the dispatcher worker reads /
 * claims / mutates it. The record is the single source of truth a real JDBC
 * adapter must persist in Stage 8 — it carries exactly the ICD fields plus the
 * Stage 8 additive {@link ForwardingLease} (claim / lease ownership).
 *
 * <p>{@code sourceServiceId} and {@code targetServiceId} live on the record, not
 * on {@link ForwardingEnvelope} (MI8-002): they are gateway / discovery audit
 * metadata written when the record is created, projected from the caller and
 * from the opaque {@link ForwardingRouteHandle} — never a physical endpoint.
 *
 * <p>Forbidden-payload invariant (HD4): this record NEVER carries a payload body,
 * a token stream, Task execution state, or a physical endpoint. There are no
 * such fields, by design; large payloads take the {@code payloadRef} data
 * reference path. The compact constructor additionally enforces tenant
 * isolation: {@code tenantId} must be non-blank (Rule R-C.c).
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding-Runtime} (outbox record fields);
 * {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §4.1};
 * {@code architecture/docs/L2/agent-bus/forwarding-persistence.md §3}.
 */
// scope: forwarding substrate — durable outbox record; never a payload body
public record ForwardingOutboxRecord(
        String tenantId,
        ForwardingMessageId messageId,
        String sourceServiceId,
        String targetServiceId,
        ForwardingRouteHandle routeHandle,
        String payloadRef,
        ForwardingStatus.Outbox status,
        int attemptCount,
        long nextAttemptAtMillisEpoch,
        long createdAtMillisEpoch,
        long updatedAtMillisEpoch,
        ForwardingFailureCode lastFailureCode,
        ForwardingLease lease
) {
    public ForwardingOutboxRecord {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(messageId, "messageId is required");
        Objects.requireNonNull(sourceServiceId, "sourceServiceId is required");
        if (sourceServiceId.isBlank()) {
            throw new IllegalArgumentException("sourceServiceId must not be blank");
        }
        Objects.requireNonNull(targetServiceId, "targetServiceId is required");
        if (targetServiceId.isBlank()) {
            throw new IllegalArgumentException("targetServiceId must not be blank");
        }
        Objects.requireNonNull(routeHandle, "routeHandle is required");
        Objects.requireNonNull(status, "status is required");
        if (payloadRef != null && payloadRef.isBlank()) {
            throw new IllegalArgumentException("payloadRef must be null or non-blank");
        }
        // ACKED is terminal-success: no outstanding failure code
        if (status == ForwardingStatus.Outbox.ACKED && lastFailureCode != null) {
            throw new IllegalArgumentException(
                    "ACKED outbox record must not carry a lastFailureCode");
        }
        // lease ownership is internally consistent (ForwardingLease enforces owner non-blank)
    }

    /** Whether this record currently holds an unexpired lease at the given instant. */
    public boolean isActivelyLeasedAt(long nowMillisEpoch) {
        return lease != null && !lease.isExpiredAt(nowMillisEpoch);
    }

    /** Whether this record is in a terminal state (no further dispatch). */
    public boolean isTerminal() {
        return status == ForwardingStatus.Outbox.ACKED
                || status == ForwardingStatus.Outbox.DLQ
                || status == ForwardingStatus.Outbox.EXPIRED;
    }
}
