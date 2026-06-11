package com.huawei.ascend.bus.messaging;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentMessageTest {

    @Test
    void factory_generates_message_id_and_timestamp() {
        var message = AgentMessage.of("t1", "orders", "agent-a", Map.of("k", "v"));
        assertThat(message.messageId()).isNotBlank();
        assertThat(message.occurredAt()).isNotNull();
        assertThat(message.correlationId()).isNull();
        assertThat(message.traceparent()).isNull();
        var other = AgentMessage.of("t1", "orders", "agent-a", Map.of());
        assertThat(other.messageId()).isNotEqualTo(message.messageId());
    }

    @Test
    void payload_is_copied_so_later_producer_mutation_is_invisible() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("k", "v1");
        var message = AgentMessage.of("t1", "orders", "agent-a", payload);
        payload.put("k", "v2");
        assertThat(message.payload()).containsEntry("k", "v1");
        assertThatThrownBy(() -> message.payload().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void null_payload_becomes_empty_map() {
        var message = AgentMessage.of("t1", "orders", "agent-a", null);
        assertThat(message.payload()).isEmpty();
    }

    @Test
    void tenant_topic_and_from_agent_are_required_non_blank() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AgentMessage.of(" ", "orders", "agent-a", Map.of()))
                .withMessageContaining("tenantId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> AgentMessage.of("t1", "", "agent-a", Map.of()))
                .withMessageContaining("topic");
        assertThatNullPointerException()
                .isThrownBy(() -> AgentMessage.of("t1", "orders", null, Map.of()))
                .withMessageContaining("fromAgentId");
    }

    @Test
    void canonical_constructor_requires_message_id_and_occurred_at() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new AgentMessage("", "t1", "orders", "agent-a", null, null, Map.of(), Instant.now()))
                .withMessageContaining("messageId");
        assertThatNullPointerException()
                .isThrownBy(() -> new AgentMessage("m1", "t1", "orders", "agent-a", null, null, Map.of(), null))
                .withMessageContaining("occurredAt");
    }
}
