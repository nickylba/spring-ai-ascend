package com.huawei.ascend.bus.forwarding.spi;

import java.util.Objects;

/**
 * Outcome of a single delivery attempt, returned by {@link ForwardingDeliveryPort}
 * and consumed by {@code ForwardingDispatcherWorker} to drive the outbox state
 * machine (Stage 8 plan §3 slice 5).
 *
 * <p>One of four outcomes, each mapped to a state-machine event:
 * <ul>
 *   <li>{@link Outcome#ACKED} → mark ACKED (terminal-success).</li>
 *   <li>{@link Outcome#RETRY_SCHEDULED} → schedule a retry (retryable failure;
 *       carries the failure code and the next attempt instant).</li>
 *   <li>{@link Outcome#DLQ} → move to DLQ (non-retryable failure or retries
 *       exhausted; carries the failure code).</li>
 *   <li>{@link Outcome#EXPIRED} → mark EXPIRED (envelope deadline exceeded).</li>
 * </ul>
 * The compact constructor pins the failure-code / next-attempt invariants per
 * outcome so the worker can switch on {@link #outcome()} without re-validating.
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §4.1/§7};
 * {@code architecture/docs/L2/agent-bus/forwarding-persistence.md §5}.
 */
public record ForwardingDeliveryResult(Outcome outcome, ForwardingFailureCode failureCode,
                                       long nextAttemptAtMillisEpoch) {

    public ForwardingDeliveryResult {
        Objects.requireNonNull(outcome, "outcome is required");
        switch (outcome) {
            case ACKED -> {
                if (failureCode != null) {
                    throw new IllegalArgumentException("ACKED result must not carry a failureCode");
                }
            }
            case RETRY_SCHEDULED -> {
                if (failureCode == null) {
                    throw new IllegalArgumentException(
                            "RETRY_SCHEDULED result requires a retryable failureCode");
                }
                if (!failureCode.retryable()) {
                    throw new IllegalArgumentException(
                            "RETRY_SCHEDULED result requires a retryable failureCode; "
                            + failureCode + " is not retryable (MI9-004)");
                }
                if (nextAttemptAtMillisEpoch <= 0) {
                    throw new IllegalArgumentException(
                            "RETRY_SCHEDULED result requires a positive nextAttemptAtMillisEpoch");
                }
            }
            case DLQ -> {
                if (failureCode == null) {
                    throw new IllegalArgumentException("DLQ result requires a failureCode");
                }
                if (failureCode.dedup()) {
                    throw new IllegalArgumentException(
                            "DLQ result must not carry a dedup failureCode (DUPLICATE_SUPPRESSED "
                            + "is a dedup outcome, not a delivery failure; MI9-004)");
                }
            }
            case EXPIRED -> {
                if (failureCode != null) {
                    throw new IllegalArgumentException("EXPIRED result must not carry a failureCode");
                }
            }
        }
    }

    /** Delivery outcome — maps 1:1 to an outbox state-machine terminal / retry event. */
    public enum Outcome {
        ACKED, RETRY_SCHEDULED, DLQ, EXPIRED
    }

    /** Successful synchronous ack (ICD Delivery Model). */
    public static ForwardingDeliveryResult acked() {
        return new ForwardingDeliveryResult(Outcome.ACKED, null, 0L);
    }

    /**
     * Retryable failure; retried at {@code nextAttemptAtMillisEpoch}. The code
     * MUST be {@link ForwardingFailureCode#retryable() retryable} (MI9-004).
     */
    public static ForwardingDeliveryResult retry(ForwardingFailureCode failureCode,
                                                 long nextAttemptAtMillisEpoch) {
        return new ForwardingDeliveryResult(Outcome.RETRY_SCHEDULED, failureCode, nextAttemptAtMillisEpoch);
    }

    /**
     * Non-retryable failure / retries exhausted → DLQ. Accepts a
     * {@link ForwardingFailureCode#nonRetryable() non-retryable} code or a
     * retryable code whose retries have been exhausted; rejects the dedup
     * outcome (MI9-004).
     */
    public static ForwardingDeliveryResult dlq(ForwardingFailureCode failureCode) {
        return new ForwardingDeliveryResult(Outcome.DLQ, failureCode, 0L);
    }

    /** Envelope deadline exceeded → EXPIRED. */
    public static ForwardingDeliveryResult expired() {
        return new ForwardingDeliveryResult(Outcome.EXPIRED, null, 0L);
    }
}
