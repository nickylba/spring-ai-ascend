package com.huawei.ascend.runtime.llm.gateway.spi;

/**
 * Observer of every LLM invocation the gateway forwards upstream. The two methods
 * mirror the {@code before_llm_invocation} / {@code after_llm_invocation} positions
 * of {@code docs/contracts/engine-hooks.v1.yaml} so a future hook resurrection can
 * adopt these call sites unchanged.
 *
 * <p><strong>Observer-only:</strong> listeners cannot veto, mutate or short-circuit
 * a call. The gateway invokes the ordered chain around every upstream call, catches
 * and logs every listener exception, and never lets one fail the call. The listener
 * chain is the sole emission path for GENERATION spans and spend records — the
 * forwarder itself emits neither.
 */
public interface LlmCallListener {

    /** Called after identity and route are resolved, before the upstream is contacted. */
    default void beforeLlmInvocation(LlmCallContext context) {
    }

    /** Called once per call after the upstream responded, failed, or the stream ended. */
    default void afterLlmInvocation(LlmCallContext context, LlmCallResult result) {
    }
}
