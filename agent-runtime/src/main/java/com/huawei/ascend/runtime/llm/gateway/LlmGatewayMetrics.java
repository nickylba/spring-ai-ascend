package com.huawei.ascend.runtime.llm.gateway;

import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;

/**
 * Aggregate gateway meters. Tags are bounded vocabularies only (model alias,
 * provider, outcome, direction) — never {@code tenant_id}, whose cardinality is
 * unbounded; per-tenant cost questions are answered by the spend log and the
 * GENERATION record, not Prometheus. Like the trace filter, the gateway runs
 * unmetered when no {@link MeterRegistry} is on the classpath.
 */
public final class LlmGatewayMetrics {

    static final String REQUESTS_TOTAL = "springai_ascend_llm_requests_total";
    static final String TOKENS_TOTAL = "springai_ascend_llm_tokens_total";
    static final String UPSTREAM_LATENCY_SECONDS = "springai_ascend_llm_upstream_latency_seconds";
    static final String COST_UNPRICED_TOTAL = "springai_ascend_llm_cost_unpriced_total";

    /** Null when no MeterRegistry is available; every recorder then no-ops. */
    private final MeterRegistry registry;

    public LlmGatewayMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void recordRequest(String modelAlias, String provider, String outcome) {
        if (registry == null) {
            return;
        }
        registry.counter(REQUESTS_TOTAL,
                "model_alias", modelAlias, "provider", provider, "outcome", outcome).increment();
    }

    void recordTokens(String modelAlias, String provider, long inputTokens, long outputTokens) {
        if (registry == null) {
            return;
        }
        registry.counter(TOKENS_TOTAL,
                "model_alias", modelAlias, "provider", provider, "direction", "input")
                .increment(inputTokens);
        registry.counter(TOKENS_TOTAL,
                "model_alias", modelAlias, "provider", provider, "direction", "output")
                .increment(outputTokens);
    }

    void recordUpstreamLatency(String modelAlias, String provider, Duration latency) {
        if (registry == null) {
            return;
        }
        registry.timer(UPSTREAM_LATENCY_SECONDS,
                "model_alias", modelAlias, "provider", provider).record(latency);
    }

    void recordUnpriced(String modelAlias, String provider) {
        if (registry == null) {
            return;
        }
        registry.counter(COST_UNPRICED_TOTAL,
                "model_alias", modelAlias, "provider", provider).increment();
    }
}
