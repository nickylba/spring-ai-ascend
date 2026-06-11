package com.huawei.ascend.bus.knowledge;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Named per-tenant registration of {@link KnowledgeSource}s.
 *
 * <p>Registration is keyed (tenant, name) so each tenant composes its own
 * source set — one tenant's sources are structurally invisible to another's
 * fan-out. Names are unique per tenant; re-registering a taken name is
 * rejected loudly rather than silently replacing a live source.
 */
public final class KnowledgeRegistry {

    private final ConcurrentHashMap<String, ConcurrentHashMap<String, KnowledgeSource>> sourcesByTenant =
            new ConcurrentHashMap<>();

    /**
     * Register a source under {@code sourceName} for the tenant.
     *
     * @throws IllegalStateException if the name is already registered for this tenant
     */
    public void register(String tenantId, String sourceName, KnowledgeSource source) {
        Objects.requireNonNull(source, "source is required");
        KnowledgeSource existing = sourcesByTenant
                .computeIfAbsent(requireKeyPart(tenantId, "tenantId"), t -> new ConcurrentHashMap<>())
                .putIfAbsent(requireKeyPart(sourceName, "sourceName"), source);
        if (existing != null) {
            throw new IllegalStateException(
                    "knowledge source '" + sourceName + "' is already registered for tenant '" + tenantId + "'");
        }
    }

    /** Remove the named source for the tenant; returns whether anything was removed. */
    public boolean unregister(String tenantId, String sourceName) {
        Map<String, KnowledgeSource> tenantSources =
                sourcesByTenant.get(requireKeyPart(tenantId, "tenantId"));
        return tenantSources != null
                && tenantSources.remove(requireKeyPart(sourceName, "sourceName")) != null;
    }

    /**
     * Snapshot of the tenant's sources, ordered by name so fan-out (and
     * therefore tie-breaking in score merges) is deterministic.
     */
    public Map<String, KnowledgeSource> sources(String tenantId) {
        Map<String, KnowledgeSource> tenantSources =
                sourcesByTenant.get(requireKeyPart(tenantId, "tenantId"));
        if (tenantSources == null) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new TreeMap<>(tenantSources));
    }

    private static String requireKeyPart(String value, String fieldName) {
        Objects.requireNonNull(value, fieldName + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
