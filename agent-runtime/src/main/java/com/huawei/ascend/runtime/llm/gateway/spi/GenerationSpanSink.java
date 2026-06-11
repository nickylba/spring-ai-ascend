package com.huawei.ascend.runtime.llm.gateway.spi;

/**
 * Receiver of GENERATION records for LLM invocations. This is the L1.x stand-in
 * for a real span exporter: the platform has no OpenTelemetry span infrastructure
 * yet, so the gateway emits the GENERATION attribute set to this minimal sink
 * until the OTel exporter wave replaces the default with a real span pipeline.
 */
public interface GenerationSpanSink {

    void emit(GenerationSpan span);

    /**
     * The GENERATION attribute set of one LLM invocation: the six observability
     * attributes plus the mandatory tenant attribution. Per-tenant cost rides this
     * record (and the spend log) — never Prometheus labels, whose cardinality rules
     * forbid a tenant tag.
     *
     * @param genAiSystem        upstream provider label ({@code gen_ai.system})
     * @param genAiRequestModel  model alias the caller requested ({@code gen_ai.request.model})
     * @param inputTokens        prompt tokens ({@code gen_ai.usage.input_tokens})
     * @param outputTokens       completion tokens ({@code gen_ai.usage.output_tokens})
     * @param costUsd            call cost ({@code langfuse.cost_usd}); null when the
     *                           alias has no pricing — the attribute is omitted, not zeroed
     * @param latencyMs          upstream latency ({@code langfuse.latency_ms})
     * @param tenantId           tenant attribution ({@code tenant.id})
     */
    record GenerationSpan(
            String genAiSystem,
            String genAiRequestModel,
            long inputTokens,
            long outputTokens,
            Double costUsd,
            long latencyMs,
            String tenantId) {
    }
}
