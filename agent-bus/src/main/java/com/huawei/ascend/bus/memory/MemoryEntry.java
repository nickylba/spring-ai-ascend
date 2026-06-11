package com.huawei.ascend.bus.memory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One unit of S-side working memory: a single conversation turn or
 * trajectory-adjacent note inside a (tenant, session) window.
 *
 * <p>S-side only (Authority: ADR-0051) — entries hold execution trajectory,
 * never C-side business facts (those flow through {@link BusinessFactEvent}).
 */
public record MemoryEntry(
        String role,                     // who produced the entry, e.g. "user", "assistant", "tool"
        String text,
        Instant timestamp,
        Map<String, Object> attributes   // optional structured extras (tool name, step id, ...)
) {
    public MemoryEntry {
        Objects.requireNonNull(role, "role is required");
        if (role.isBlank()) {
            throw new IllegalArgumentException("role must not be blank");
        }
        Objects.requireNonNull(text, "text is required");
        Objects.requireNonNull(timestamp, "timestamp is required");
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
