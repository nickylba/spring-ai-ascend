package com.huawei.ascend.service.core;

import com.huawei.ascend.service.spi.registry.RuntimeInstanceId;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sticky-session bookkeeping for route selection: maps
 * (tenant, agent, session) to the runtime instance the session is pinned to.
 * The map is capped with oldest-pin eviction so abandoned sessions can never
 * grow memory unboundedly; evicting the oldest pin merely costs that session
 * one re-pick, so a modest cap is safe.
 *
 * <p>Insertion-ordered so the eldest pin is the eviction victim; all view
 * iteration synchronizes on the map per {@link Collections#synchronizedMap}.
 */
final class SessionPins {

    record Key(String tenantId, String agentId, String sessionId) {
    }

    private final Map<Key, RuntimeInstanceId> pins;

    SessionPins(int maxPins) {
        this.pins = Collections.synchronizedMap(new LinkedHashMap<>() {
            @Override
            protected boolean removeEldestEntry(Map.Entry<Key, RuntimeInstanceId> eldest) {
                return size() > maxPins;
            }
        });
    }

    RuntimeInstanceId pinnedInstance(Key key) {
        return pins.get(key);
    }

    void pin(Key key, RuntimeInstanceId runtimeInstanceId) {
        pins.put(key, runtimeInstanceId);
    }

    /** Drops every pin held by the instance — used on deregister and lease expiry. */
    void dropAllFor(RuntimeInstanceId runtimeInstanceId) {
        synchronized (pins) {
            pins.values().removeIf(runtimeInstanceId::equals);
        }
    }
}
