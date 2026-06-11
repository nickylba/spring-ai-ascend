package com.huawei.ascend.runtime.llm.gateway;

import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallContext;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallListener;
import com.huawei.ascend.runtime.llm.gateway.spi.LlmCallResult;
import com.huawei.ascend.runtime.llm.gateway.spi.SpendLog;
import java.time.Clock;
import java.time.LocalDate;

/**
 * Reference listener appending one spend-ledger record per successful upstream
 * call. Failed calls produce no completion to bill, so they are not recorded.
 * This is recording only — budget enforcement (pre-call checks, rejections)
 * remains a design-stage contract and deliberately does not exist here.
 */
public final class SpendRecordListener implements LlmCallListener {

    private final SpendLog spendLog;
    private final ModelAliasRegistry registry;
    private final Clock clock;

    public SpendRecordListener(SpendLog spendLog, ModelAliasRegistry registry, Clock clock) {
        this.spendLog = spendLog;
        this.registry = registry;
        this.clock = clock;
    }

    @Override
    public void afterLlmInvocation(LlmCallContext context, LlmCallResult result) {
        if (!result.success()) {
            return;
        }
        Double costUsd = registry.costUsd(context.modelAlias(), result.usage());
        spendLog.append(new SpendLog.SpendRecord(
                context.tenantId(),
                context.agentId(),
                context.modelAlias(),
                LocalDate.now(clock),
                result.usage().inputTokens(),
                result.usage().outputTokens(),
                costUsd == null ? 0.0 : costUsd));
    }
}
