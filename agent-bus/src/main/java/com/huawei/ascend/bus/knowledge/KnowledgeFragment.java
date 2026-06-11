package com.huawei.ascend.bus.knowledge;

import java.util.Map;
import java.util.Objects;

/**
 * One retrieved piece of C-side-owned knowledge content.
 *
 * <p>{@code score} is a source-relative relevance value (higher is more
 * relevant); {@code provenance} carries whatever the source knows about where
 * the content came from, so consumers can cite rather than trust blindly.
 */
public record KnowledgeFragment(
        String sourceId,
        String content,
        double score,
        Map<String, Object> provenance
) {
    public KnowledgeFragment {
        Objects.requireNonNull(sourceId, "sourceId is required");
        if (sourceId.isBlank()) {
            throw new IllegalArgumentException("sourceId must not be blank");
        }
        Objects.requireNonNull(content, "content is required");
        if (!Double.isFinite(score)) {
            throw new IllegalArgumentException("score must be finite");
        }
        provenance = provenance == null ? Map.of() : Map.copyOf(provenance);
    }
}
