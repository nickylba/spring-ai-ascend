package com.huawei.ascend.bus.memory;

import java.util.List;

/**
 * SPI for tenant+session scoped S-side working memory (Authority: ADR-0051).
 *
 * <p>Implementations hold conversation windows and trajectory-adjacent
 * session state only. Tenant isolation is structural: every operation takes
 * the tenant id, and an implementation MUST key its storage so one tenant's
 * entries are unreachable from another tenant's session — filtering after a
 * shared lookup is not acceptable.
 */
public interface SessionMemoryStore {

    /**
     * Append one entry to the (tenant, session) window. Implementations are
     * expected to bound the window and evict the oldest entries first.
     */
    void append(String tenantId, String sessionId, MemoryEntry entry);

    /**
     * Return up to {@code maxEntries} of the most recent entries for the
     * (tenant, session) window, newest first. An unknown session yields an
     * empty list, never null.
     */
    List<MemoryEntry> window(String tenantId, String sessionId, int maxEntries);

    /** Discard the (tenant, session) window entirely. Unknown sessions are a no-op. */
    void clear(String tenantId, String sessionId);
}
