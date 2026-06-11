package com.huawei.ascend.runtime.llm.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class LlmGatewayMetricsTest {

    @Test
    void recordsAllFourMetersWithBoundedTagsOnly() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LlmGatewayMetrics metrics = new LlmGatewayMetrics(registry);

        metrics.recordRequest("alias-a", "openai", "success");
        metrics.recordTokens("alias-a", "openai", 11, 22);
        metrics.recordUpstreamLatency("alias-a", "openai", Duration.ofMillis(150));
        metrics.recordUnpriced("alias-a", "openai");

        assertThat(registry.counter(LlmGatewayMetrics.REQUESTS_TOTAL,
                "model_alias", "alias-a", "provider", "openai", "outcome", "success")
                .count()).isEqualTo(1.0);
        assertThat(registry.counter(LlmGatewayMetrics.TOKENS_TOTAL,
                "model_alias", "alias-a", "provider", "openai", "direction", "input")
                .count()).isEqualTo(11.0);
        assertThat(registry.counter(LlmGatewayMetrics.TOKENS_TOTAL,
                "model_alias", "alias-a", "provider", "openai", "direction", "output")
                .count()).isEqualTo(22.0);
        assertThat(registry.timer(LlmGatewayMetrics.UPSTREAM_LATENCY_SECONDS,
                "model_alias", "alias-a", "provider", "openai").count()).isEqualTo(1);
        assertThat(registry.counter(LlmGatewayMetrics.COST_UNPRICED_TOTAL,
                "model_alias", "alias-a", "provider", "openai").count()).isEqualTo(1.0);

        // Cardinality rule: no meter may carry a tenant_id tag.
        registry.getMeters().forEach(meter ->
                assertThat(meter.getId().getTag("tenant_id")).isNull());
    }

    @Test
    void runsUnmeteredWithoutAMeterRegistry() {
        LlmGatewayMetrics metrics = new LlmGatewayMetrics(null);

        assertThatCode(() -> {
            metrics.recordRequest("alias-a", "openai", "success");
            metrics.recordTokens("alias-a", "openai", 1, 1);
            metrics.recordUpstreamLatency("alias-a", "openai", Duration.ofMillis(1));
            metrics.recordUnpriced("alias-a", "openai");
        }).doesNotThrowAnyException();
    }
}
