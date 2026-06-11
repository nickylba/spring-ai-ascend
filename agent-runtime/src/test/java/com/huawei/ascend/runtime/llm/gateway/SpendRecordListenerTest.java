package com.huawei.ascend.runtime.llm.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.llm.gateway.spi.InMemorySpendLog;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallContext;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallResult;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmTokenUsage;
import com.huawei.ascend.runtime.llm.gateway.spi.SpendLog;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SpendRecordListenerTest {

    private static final Clock FIXED_UTC =
            Clock.fixed(Instant.parse("2026-06-11T22:30:00Z"), ZoneOffset.UTC);

    private final InMemorySpendLog spendLog = new InMemorySpendLog();

    private SpendRecordListener listener() {
        LlmGatewayProperties properties = new LlmGatewayProperties();
        LlmGatewayProperties.Upstream priced = new LlmGatewayProperties.Upstream();
        priced.setBaseUrl("http://upstream.test/v1");
        priced.setApiKey("sk");
        LlmGatewayProperties.Pricing pricing = new LlmGatewayProperties.Pricing();
        pricing.setInputPerMillionTokensUsd(2.0);
        pricing.setOutputPerMillionTokensUsd(4.0);
        priced.setPricing(pricing);
        properties.getAliases().put("priced-alias", priced);
        LlmGatewayProperties.Upstream unpriced = new LlmGatewayProperties.Upstream();
        unpriced.setBaseUrl("http://upstream.test/v1");
        unpriced.setApiKey("sk");
        properties.getAliases().put("unpriced-alias", unpriced);
        return new SpendRecordListener(spendLog, new ModelAliasRegistry(properties), FIXED_UTC);
    }

    @Test
    void appendsOnePricedRecordPerSuccessfulCall() {
        listener().afterLlmInvocation(
                new LlmCallContext("tenant-a", "agent-1", "priced-alias", "openai", "trace-1"),
                new LlmCallResult(true, 200, 10, new LlmTokenUsage(500_000, 250_000, false)));

        assertThat(spendLog.records()).containsExactly(new SpendLog.SpendRecord(
                "tenant-a", "agent-1", "priced-alias",
                LocalDate.of(2026, 6, 11), 500_000, 250_000, 0.5 * 2.0 + 0.25 * 4.0));
    }

    @Test
    void unpricedAliasRecordsZeroCostButKeepsTokenCounts() {
        listener().afterLlmInvocation(
                new LlmCallContext("tenant-a", "agent-1", "unpriced-alias", "openai", "trace-1"),
                new LlmCallResult(true, 200, 10, new LlmTokenUsage(7, 8, false)));

        SpendLog.SpendRecord record = spendLog.records().get(0);
        assertThat(record.costUsd()).isZero();
        assertThat(record.inputTokens()).isEqualTo(7);
        assertThat(record.outputTokens()).isEqualTo(8);
    }

    @Test
    void failedCallsAreNotBilled() {
        listener().afterLlmInvocation(
                new LlmCallContext("tenant-a", "agent-1", "priced-alias", "openai", "trace-1"),
                new LlmCallResult(false, 500, 10, LlmTokenUsage.estimatedAbsent()));

        assertThat(spendLog.records()).isEmpty();
    }
}
