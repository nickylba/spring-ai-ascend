/**
 * Wire-level LLM egress gateway: an OpenAI-compatible {@code POST /v1/chat/completions}
 * endpoint that authenticates a platform-minted per-agent token, resolves the requested
 * model alias to a configured upstream, forwards the request transparently (non-streaming
 * and SSE), extracts token usage, and feeds the observer listener chain that emits
 * GENERATION records and spend entries.
 *
 * <p>Disabled by default; activated by {@code agent-runtime.llm.gateway.enabled=true}.
 * Adoption is by ModelSpec indirection: agent model config points {@code baseUrl} at this
 * endpoint and carries the minted token as its {@code apiKey}, so OpenAI-compatible
 * framework clients route through the gateway with zero framework changes.
 */
package com.huawei.ascend.runtime.llm.gateway;
