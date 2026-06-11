/**
 * agent-bus knowledge-retrieval capability surface — owned by the Bus &amp;
 * State Hub plane.
 *
 * <p><strong>Ownership boundary (Authority: ADR-0051).</strong> The platform
 * never owns business knowledge content — it owns the <em>retrieval seam</em>
 * agents reach through. {@link com.huawei.ascend.bus.knowledge.KnowledgeSource}
 * is that seam: content behind it (documents, ontologies, vector indexes) is
 * C-side-owned. Tenant isolation is structural — every query carries the
 * tenant id and sources must scope retrieval to it.
 *
 * <p>The in-memory reference implementation exists so the seam is exercised
 * end-to-end; vector stores and the Graphiti adapter plug in through the same
 * SPI. Capability surface authority: ADR-0163.
 */
package com.huawei.ascend.bus.knowledge;
