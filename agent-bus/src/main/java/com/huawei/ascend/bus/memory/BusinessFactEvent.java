package com.huawei.ascend.bus.memory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Structured business-fact emission from the S-side to the C-side
 * (Authority: ADR-0051).
 *
 * <p>The S-side does not claim factual authority and never stores these
 * events as business memory — the C-side decides whether to accept,
 * transform, store, or discard each one. {@code placeholdersPreserved}
 * asserts the placeholder-preservation rule: opaque C-side identity tokens
 * (e.g. {@code [USER_ID_102]}) in the payload were carried as algebraic
 * symbols and never resolved or guessed at platform-side.
 */
public record BusinessFactEvent(
        String tenantId,
        String sessionId,
        String runId,                  // nullable — facts may surface outside a Run
        String factType,               // e.g. USER_PREFERENCE_DISCOVERED, ENTITY_STATE_CHANGE
        Map<String, Object> payload,
        boolean placeholdersPreserved,
        Instant occurredAt
) {
    public BusinessFactEvent {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(sessionId, "sessionId is required");
        if (sessionId.isBlank()) {
            throw new IllegalArgumentException("sessionId must not be blank");
        }
        Objects.requireNonNull(factType, "factType is required");
        if (factType.isBlank()) {
            throw new IllegalArgumentException("factType must not be blank");
        }
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        payload = payload == null ? Map.of() : Map.copyOf(payload);
    }
}
