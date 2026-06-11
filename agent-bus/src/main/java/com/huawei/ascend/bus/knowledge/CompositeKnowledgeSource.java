package com.huawei.ascend.bus.knowledge;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Fans one query across every source the tenant has registered and merges the
 * results by score, descending.
 *
 * <p>The merge sort is stable and the registry hands back sources in
 * name-sorted order, so equal-score fragments keep a deterministic order
 * across runs. {@code topK} is applied AFTER the merge — applying it per
 * source first could starve a strong source behind a weak one's quota.
 */
public final class CompositeKnowledgeSource implements KnowledgeSource {

    private static final Comparator<KnowledgeFragment> BY_SCORE_DESC =
            Comparator.comparingDouble(KnowledgeFragment::score).reversed();

    private final KnowledgeRegistry registry;

    public CompositeKnowledgeSource(KnowledgeRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry is required");
    }

    @Override
    public List<KnowledgeFragment> retrieve(KnowledgeQuery query) {
        Objects.requireNonNull(query, "query is required");
        List<KnowledgeFragment> merged = new ArrayList<>();
        for (KnowledgeSource source : registry.sources(query.tenantId()).values()) {
            merged.addAll(source.retrieve(query));
        }
        merged.sort(BY_SCORE_DESC);
        List<KnowledgeFragment> bounded =
                merged.size() > query.topK() ? merged.subList(0, query.topK()) : merged;
        return List.copyOf(bounded);
    }
}
