package com.huawei.ascend.bus.forwarding.spi;

import java.util.Objects;

/**
 * Receiver-side dedup / idempotency / audit record for the C3 inbox substrate
 * (Stage 8).
 *
 * <p>Mirrors the inbox record schema of {@code ICD-Agent-Bus-Forwarding-Runtime}
 * field-for-field: the receiver writes a record on first arrival
 * ({@link ForwardingInboxPort#receive}) keyed by the dedup key
 * {@code (tenantId, messageId, consumerServiceId)}, and mutates it through the
 * inbox state machine. {@code idempotencyKey} is an audit-only envelope field
 * (MI8-004); the dedup key is the triple below, not {@code idempotencyKey}.
 *
 * <p>Forbidden-payload invariant (HD4): same as the outbox record — no payload
 * body, no token stream, no Task execution state, no physical endpoint. The
 * compact constructor enforces tenant isolation: {@code tenantId} and
 * {@code consumerServiceId} must be non-blank (Rule R-C.c).
 *
 * <p>Authority: {@code ICD-Agent-Bus-Forwarding-Runtime} (inbox record fields);
 * {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §4.2};
 * {@code architecture/docs/L2/agent-bus/forwarding-persistence.md §3}.
 */
// scope: forwarding substrate — dedup / audit inbox record; never a payload body
public record ForwardingInboxRecord(
        String tenantId,
        ForwardingMessageId messageId,
        String consumerServiceId,
        ForwardingStatus.Inbox status,
        long receivedAtMillisEpoch,
        long consumedAtMillisEpoch,
        ForwardingFailureCode failureCode
) {
    public ForwardingInboxRecord {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(messageId, "messageId is required");
        Objects.requireNonNull(consumerServiceId, "consumerServiceId is required");
        if (consumerServiceId.isBlank()) {
            throw new IllegalArgumentException("consumerServiceId must not be blank");
        }
        Objects.requireNonNull(status, "status is required");
        // RECEIVED holds no failure code; rejected / duplicate outcomes carry one
        if (status == ForwardingStatus.Inbox.RECEIVED && failureCode != null) {
            throw new IllegalArgumentException(
                    "RECEIVED inbox record must not carry a failureCode");
        }
        if (status == ForwardingStatus.Inbox.CONSUMED && failureCode != null) {
            throw new IllegalArgumentException(
                    "CONSUMED inbox record must not carry a failureCode");
        }
    }

    /** Whether this inbox record is in a terminal state. */
    public boolean isTerminal() {
        return status == ForwardingStatus.Inbox.DUPLICATE_SUPPRESSED
                || status == ForwardingStatus.Inbox.CONSUMED
                || status == ForwardingStatus.Inbox.REJECTED;
    }
}
