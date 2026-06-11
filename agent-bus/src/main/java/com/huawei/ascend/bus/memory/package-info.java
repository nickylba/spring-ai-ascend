/**
 * agent-bus session-memory capability surface — owned by the Bus &amp; State
 * Hub plane.
 *
 * <p><strong>Ownership boundary (Authority: ADR-0051).</strong> The platform
 * owns <em>S-side working memory only</em>: conversation windows and
 * trajectory-adjacent session state, scoped per (tenant, session). Business
 * facts discovered during execution are <em>emitted</em> to the C-side via
 * {@link com.huawei.ascend.bus.memory.BusinessFactPublisher} — they are never
 * persisted platform-side; the C-side decides whether to accept, transform,
 * store, or discard each {@link com.huawei.ascend.bus.memory.BusinessFactEvent}.
 *
 * <p>Each SPI ships with an in-memory reference implementation in this
 * package; durable backends (Graphiti, mem0, custom stores) plug in through
 * the SPIs. Capability surface authority: ADR-0163.
 */
package com.huawei.ascend.bus.memory;
