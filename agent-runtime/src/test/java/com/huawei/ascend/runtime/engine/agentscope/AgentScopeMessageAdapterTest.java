package com.huawei.ascend.runtime.engine.agentscope;

import static org.assertj.core.api.Assertions.assertThat;

import com.huawei.ascend.runtime.common.RuntimeIdentity;
import com.huawei.ascend.runtime.common.RuntimeMessage;
import com.huawei.ascend.runtime.engine.AgentExecutionContext;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentScopeMessageAdapterTest {

    private final AgentScopeMessageAdapter adapter = new AgentScopeMessageAdapter();

    // --- A5: userId contract ---

    @Test
    void userIdPresentWhenNonNull() {
        // RuntimeIdentity enforces non-null/non-blank userId — use the guaranteed non-null value.
        Map<String, Object> meta = AgentScopeMessageAdapter.invocationMetadata(
                "t1", "alice", "s1", "task1", "agent1");

        assertThat(meta).containsEntry("userId", "alice");
    }

    @Test
    void taskIdOmittedWhenNull() {
        Map<String, Object> meta = AgentScopeMessageAdapter.invocationMetadata(
                "t1", "alice", "s1", null, "agent1");

        assertThat(meta).doesNotContainKey("taskId");
    }

    @Test
    void taskIdPresentWhenNonNull() {
        Map<String, Object> meta = AgentScopeMessageAdapter.invocationMetadata(
                "t1", "alice", "s1", "task42", "agent1");

        assertThat(meta).containsEntry("taskId", "task42");
    }

    // --- A6: single-source metadata assembly ---

    @Test
    void invocationMetadataContainsAllFiveFields() {
        Map<String, Object> meta = AgentScopeMessageAdapter.invocationMetadata(
                "tenantA", "userB", "sessC", "taskD", "agentE");

        assertThat(meta)
                .containsEntry("tenantId", "tenantA")
                .containsEntry("userId", "userB")
                .containsEntry("sessionId", "sessC")
                .containsEntry("taskId", "taskD")
                .containsEntry("agentId", "agentE")
                .hasSize(5);
    }

    @Test
    void toInvocationMetadataMatchesInvocationMetadataHelper() {
        RuntimeIdentity scope = new RuntimeIdentity("t1", "u1", "s1", "task1", "a1");
        AgentExecutionContext ctx = new AgentExecutionContext(
                scope, "USER_MESSAGE", List.of(RuntimeMessage.user("hi")), Map.of());

        AgentScopeInvocation inv = adapter.toInvocation(ctx);

        Map<String, Object> expected = AgentScopeMessageAdapter.invocationMetadata(
                "t1", "u1", "s1", "task1", "a1");
        assertThat(inv.metadata()).isEqualTo(expected);
    }
}
