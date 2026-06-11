package com.huawei.ascend.runtime.llm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.llm.gateway.spi.GenerationSpanSink;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallContext;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallResult;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmTokenUsage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class LlmSpanEmitterListenerTest {

    private final List<GenerationSpanSink.GenerationSpan> spans = new ArrayList<>();
    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    private LlmSpanEmitterListener listener() {
        LlmGatewayProperties properties = new LlmGatewayProperties();
        LlmGatewayProperties.Upstream priced = new LlmGatewayProperties.Upstream();
        priced.setBaseUrl("http://upstream.test/v1");
        priced.setApiKey("sk");
        priced.setProvider("openai");
        LlmGatewayProperties.Pricing pricing = new LlmGatewayProperties.Pricing();
        pricing.setInputPerMillionTokensUsd(1.0);
        pricing.setOutputPerMillionTokensUsd(2.0);
        priced.setPricing(pricing);
        properties.getAliases().put("priced-alias", priced);

        LlmGatewayProperties.Upstream unpriced = new LlmGatewayProperties.Upstream();
        unpriced.setBaseUrl("http://upstream.test/v1");
        unpriced.setApiKey("sk");
        unpriced.setProvider("openai");
        properties.getAliases().put("unpriced-alias", unpriced);

        return new LlmSpanEmitterListener(spans::add, new ModelAliasRegistry(properties),
                new LlmGatewayMetrics(meterRegistry));
    }

    @Test
    void emitsAllSixGenerationAttributesPlusTenantId() {
        listener().afterLlmInvocation(
                new LlmCallContext("tenant-a", "agent-1", "priced-alias", "openai", "trace-1"),
                new LlmCallResult(true, 200, 321, new LlmTokenUsage(1_000_000, 500_000, false)));

        assertThat(spans).hasSize(1);
        GenerationSpanSink.GenerationSpan span = spans.get(0);
        assertThat(span.genAiSystem()).isEqualTo("openai");
        assertThat(span.genAiRequestModel()).isEqualTo("priced-alias");
        assertThat(span.inputTokens()).isEqualTo(1_000_000);
        assertThat(span.outputTokens()).isEqualTo(500_000);
        assertThat(span.costUsd()).isEqualTo(1.0 + 0.5 * 2.0);
        assertThat(span.latencyMs()).isEqualTo(321);
        assertThat(span.tenantId()).isEqualTo("tenant-a");
        assertThat(meterRegistry.find(LlmGatewayMetrics.COST_UNPRICED_TOTAL).counters()).isEmpty();
    }

    @Test
    void unpricedAliasOmitsCostAndIncrementsUnpricedCounter() {
        listener().afterLlmInvocation(
                new LlmCallContext("tenant-a", "agent-1", "unpriced-alias", "openai", "trace-1"),
                new LlmCallResult(true, 200, 10, new LlmTokenUsage(5, 5, false)));

        assertThat(spans.get(0).costUsd()).isNull();
        assertThat(meterRegistry.counter(LlmGatewayMetrics.COST_UNPRICED_TOTAL,
                "model_alias", "unpriced-alias", "provider", "openai").count()).isEqualTo(1.0);
    }

    @Test
    void estimatedUsageOmitsCostEvenWhenAliasIsPriced() {
        listener().afterLlmInvocation(
                new LlmCallContext("tenant-a", "agent-1", "priced-alias", "openai", "trace-1"),
                new LlmCallResult(true, 200, 10, LlmTokenUsage.estimatedAbsent()));

        assertThat(spans.get(0).costUsd()).isNull();
    }
}
