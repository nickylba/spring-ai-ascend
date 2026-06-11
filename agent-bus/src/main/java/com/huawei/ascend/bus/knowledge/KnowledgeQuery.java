package com.huawei.ascend.bus.knowledge;

import java.util.Map;
import java.util.Objects;

/**
 * A tenant-scoped retrieval request against the knowledge seam.
 *
 * <p>The tenant id travels inside the query (not ambient state) so every
 * {@link KnowledgeSource} sees exactly one tenant per call and can scope
 * retrieval structurally.
 */
public record KnowledgeQuery(
        String tenantId,
        String query,
        int topK,
        Map<String, Object> filters    // optional source-interpreted constraints (collection, doc type, ...)
) {
    public KnowledgeQuery {
        Objects.requireNonNull(tenantId, "tenantId is required");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(query, "query is required");
        if (query.isBlank()) {
            throw new IllegalArgumentException("query must not be blank");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be positive");
        }
        filters = filters == null ? Map.of() : Map.copyOf(filters);
    }
}
