package com.huawei.ascend.runtime.llm.gateway;

import com.huawei.ascend.runtime.llm.gateway.spi.GenerationSpanSink;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallContext;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallListener;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallResult;

/**
 * Reference listener emitting the GENERATION record of every LLM invocation to the
 * {@link GenerationSpanSink}: provider, requested model, token usage, latency,
 * tenant attribution, and — when the alias is priced — the call cost. Unpriced
 * calls omit the cost attribute (a fabricated zero would read as "free") and
 * increment the unpriced counter so operators see the pricing gap.
 */
public final class LlmSpanEmitterListener implements LlmCallListener {

    private final GenerationSpanSink sink;
    private final ModelAliasRegistry registry;
    private final LlmGatewayMetrics metrics;

    public LlmSpanEmitterListener(GenerationSpanSink sink, ModelAliasRegistry registry,
            LlmGatewayMetrics metrics) {
        this.sink = sink;
        this.registry = registry;
        this.metrics = metrics;
    }

    @Override
    public void afterLlmInvocation(LlmCallContext context, LlmCallResult result) {
        Double costUsd = registry.costUsd(context.modelAlias(), result.usage());
        if (costUsd == null) {
            metrics.recordUnpriced(context.modelAlias(), context.provider());
        }
        sink.emit(new GenerationSpanSink.GenerationSpan(
                context.provider(),
                context.modelAlias(),
                result.usage().inputTokens(),
                result.usage().outputTokens(),
                costUsd,
                result.latencyMillis(),
                context.tenantId()));
    }
}
