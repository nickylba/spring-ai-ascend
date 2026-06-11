package com.huawei.ascend.bus.knowledge;

import java.util.List;

/**
 * SPI for the knowledge-retrieval seam (Authority: ADR-0051): the platform
 * retrieves through this interface; the content behind it is C-side-owned.
 *
 * <p>Implementations MUST scope retrieval to {@code query.tenantId()} — a
 * fragment belonging to another tenant must never be returned.
 */
public interface KnowledgeSource {

    /**
     * Retrieve up to {@code query.topK()} fragments relevant to the query,
     * most relevant first. No matches yields an empty list, never null.
     */
    List<KnowledgeFragment> retrieve(KnowledgeQuery query);
}
