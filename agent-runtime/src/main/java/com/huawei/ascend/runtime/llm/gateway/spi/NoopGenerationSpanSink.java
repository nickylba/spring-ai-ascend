package com.huawei.ascend.runtime.llm.gateway.spi;

/**
 * Default sink for deployments without a span pipeline: GENERATION records are
 * dropped. Replaced by registering any other {@link GenerationSpanSink} bean.
 */
public final class NoopGenerationSpanSink implements GenerationSpanSink {

    @Override
    public void emit(GenerationSpan span) {
        // Intentionally empty: no span infrastructure exists to receive it.
    }
}
