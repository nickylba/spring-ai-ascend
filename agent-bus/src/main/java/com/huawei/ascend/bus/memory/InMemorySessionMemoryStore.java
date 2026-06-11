package com.huawei.ascend.bus.memory;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Reference {@link SessionMemoryStore}: bounded per-(tenant, session) deques.
 *
 * <p>Tenant isolation is structural — the map key includes the tenant id, so
 * there is no code path that can read another tenant's window. Each window is
 * capped ({@value #DEFAULT_MAX_ENTRIES_PER_SESSION} entries by default);
 * oldest entries are evicted first, because the working-memory contract is a
 * recency window, not an archive.
 *
 * <p>Per-deque mutation and reads run inside {@link ConcurrentHashMap}
 * compute blocks, which serialises access per session without a global lock.
 */
public final class InMemorySessionMemoryStore implements SessionMemoryStore {

    public static final int DEFAULT_MAX_ENTRIES_PER_SESSION = 200;

    private final int maxEntriesPerSession;
    private final ConcurrentHashMap<SessionKey, ArrayDeque<MemoryEntry>> windows = new ConcurrentHashMap<>();

    public InMemorySessionMemoryStore() {
        this(DEFAULT_MAX_ENTRIES_PER_SESSION);
    }

    public InMemorySessionMemoryStore(int maxEntriesPerSession) {
        if (maxEntriesPerSession <= 0) {
            throw new IllegalArgumentException("maxEntriesPerSession must be positive");
        }
        this.maxEntriesPerSession = maxEntriesPerSession;
    }

    @Override
    public void append(String tenantId, String sessionId, MemoryEntry entry) {
        Objects.requireNonNull(entry, "entry is required");
        windows.compute(SessionKey.of(tenantId, sessionId), (key, window) -> {
            ArrayDeque<MemoryEntry> deque = window == null ? new ArrayDeque<>() : window;
            deque.addLast(entry);
            while (deque.size() > maxEntriesPerSession) {
                deque.removeFirst();
            }
            return deque;
        });
    }

    @Override
    public List<MemoryEntry> window(String tenantId, String sessionId, int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be positive");
        }
        List<MemoryEntry> newestFirst = new ArrayList<>();
        windows.computeIfPresent(SessionKey.of(tenantId, sessionId), (key, deque) -> {
            Iterator<MemoryEntry> it = deque.descendingIterator();
            while (it.hasNext() && newestFirst.size() < maxEntries) {
                newestFirst.add(it.next());
            }
            return deque;
        });
        return List.copyOf(newestFirst);
    }

    @Override
    public void clear(String tenantId, String sessionId) {
        windows.remove(SessionKey.of(tenantId, sessionId));
    }

    /** Composite key — including the tenant makes cross-tenant reads structurally impossible. */
    private record SessionKey(String tenantId, String sessionId) {

        static SessionKey of(String tenantId, String sessionId) {
            Objects.requireNonNull(tenantId, "tenantId is required");
            if (tenantId.isBlank()) {
                throw new IllegalArgumentException("tenantId must not be blank");
            }
            Objects.requireNonNull(sessionId, "sessionId is required");
            if (sessionId.isBlank()) {
                throw new IllegalArgumentException("sessionId must not be blank");
            }
            return new SessionKey(tenantId, sessionId);
        }
    }
}
