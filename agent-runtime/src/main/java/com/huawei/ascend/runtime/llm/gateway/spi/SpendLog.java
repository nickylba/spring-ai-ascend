package com.huawei.ascend.runtime.llm.gateway.spi;

import java.time.LocalDate;

/**
 * Per-tenant LLM spend ledger. One record is appended per successful upstream
 * call; daily roll-ups aggregate over {@code (tenantId, agentId, modelAlias, day)}.
 */
public interface SpendLog {

    void append(SpendRecord record);

    /**
     * One billable LLM call.
     *
     * @param tenantId     tenant the call was attributed to
     * @param agentId      agent the call was attributed to
     * @param modelAlias   platform model alias that served the call
     * @param day          UTC calendar day of the call (the roll-up key)
     * @param inputTokens  prompt tokens reported by the upstream
     * @param outputTokens completion tokens reported by the upstream
     * @param costUsd      priced cost of the call; {@code 0.0} when the alias has
     *                     no pricing configured (the unpriced counter tracks those)
     */
    record SpendRecord(
            String tenantId,
            String agentId,
            String modelAlias,
            LocalDate day,
            long inputTokens,
            long outputTokens,
            double costUsd) {
    }
}
