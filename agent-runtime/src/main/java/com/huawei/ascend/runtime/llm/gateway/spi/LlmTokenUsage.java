package com.huawei.ascend.runtime.llm.gateway.spi;

/**
 * Token usage of one LLM invocation as reported by the upstream provider.
 *
 * <p>When the upstream response carries no usage object (some providers omit it,
 * notably on streams without {@code stream_options.include_usage} support), the
 * gateway reports zero tokens with {@code estimated} set — it never invents token
 * counts, so downstream cost math can distinguish "measured zero" from "unknown".
 *
 * @param inputTokens  prompt-side tokens reported by the upstream
 * @param outputTokens completion-side tokens reported by the upstream
 * @param estimated    true when the upstream reported no usage at all
 */
public record LlmTokenUsage(long inputTokens, long outputTokens, boolean estimated) {

    /** The "upstream reported nothing" value: zero tokens, flagged estimated. */
    public static LlmTokenUsage estimatedAbsent() {
        return new LlmTokenUsage(0, 0, true);
    }
}
