package com.huawei.ascend.runtime.llm.gateway.spi;

/**
 * Outcome of one LLM invocation through the gateway.
 *
 * @param success        true when the upstream answered with a 2xx status and, for
 *                       streams, the body was relayed to the end
 * @param upstreamStatus HTTP status the upstream answered with, or {@code 0} when
 *                       no upstream response was received (connect/IO failure)
 * @param latencyMillis  wall time from forwarding the request until the upstream
 *                       response (last streamed byte for streams) or the failure
 * @param usage          token usage reported by the upstream
 */
public record LlmCallResult(
        boolean success,
        int upstreamStatus,
        long latencyMillis,
        LlmTokenUsage usage) {
}
