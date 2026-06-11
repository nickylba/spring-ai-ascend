package com.huawei.ascend.runtime.llm.gateway.spi;

/**
 * Identity and routing facts of one LLM invocation through the gateway, resolved
 * server-side from the minted gateway token and the model-alias registry — never
 * from caller-supplied headers.
 *
 * @param tenantId   tenant the minted token was provisioned for
 * @param agentId    agent the minted token was provisioned for
 * @param modelAlias platform model alias named by the request {@code model} field
 * @param provider   upstream provider label of the resolved alias
 * @param requestId  trace id correlating the call with the request logs
 */
public record LlmCallContext(
        String tenantId,
        String agentId,
        String modelAlias,
        String provider,
        String requestId) {
}
