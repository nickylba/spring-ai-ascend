package com.huawei.ascend.runtime.engine.agentscope;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class AgentScopeMessageAdapter {

    public AgentScopeInvocation toInvocation(AgentExecutionContext context) {
        Objects.requireNonNull(context, "context");
        RuntimeIdentity scope = Objects.requireNonNull(context.getScope(), "scope");
        return new AgentScopeInvocation(
                scope.tenantId(), scope.userId(), scope.sessionId(),
                scope.taskId(), scope.agentId(),
                context.getInputType(), context.getMessages(), context.getVariables(),
                invocationMetadata(scope.tenantId(), scope.userId(), scope.sessionId(),
                        scope.taskId(), scope.agentId()));
    }

    /**
     * Single-source projection of the five identity fields into a metadata map.
     * userId is guaranteed non-null/non-blank by RuntimeIdentity; taskId is
     * optional and omitted when null.
     */
    static Map<String, Object> invocationMetadata(
            String tenantId, String userId, String sessionId, String taskId, String agentId) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("tenantId", tenantId);
        map.put("userId", userId);
        map.put("sessionId", sessionId);
        if (taskId != null) {
            map.put("taskId", taskId);
        }
        map.put("agentId", agentId);
        return map;
    }
}
