package com.huawei.ascend.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.Map;
import org.junit.jupiter.api.Test;

class SendSpecTest {

    @Test
    void generatesMessageIdAndEmptyMetadataByDefault() {
        SendSpec spec = SendSpec.of("agent-1", "session-1", "user-1", "ping");

        assertThat(spec.messageId()).isNotBlank();
        assertThat(spec.metadata()).isEmpty();
        // Two sends are two distinct A2A messages.
        assertThat(SendSpec.of("agent-1", "session-1", "user-1", "ping").messageId())
                .isNotEqualTo(spec.messageId());
    }

    @Test
    void reservedRoutingKeysCannotBeOverriddenThroughExtraMetadata() {
        SendSpec spec = new SendSpec("agent-1", "session-1", "user-1", "ping", "message-1",
                Map.of("agentId", "spoofed", "channel", "mobile"));

        // The runtime routes/attributes from these keys; caller extras must not
        // redirect a message to another agent or user.
        assertThat(spec.messageMetadata())
                .containsEntry("agentId", "agent-1")
                .containsEntry("sessionId", "session-1")
                .containsEntry("userId", "user-1")
                .containsEntry("channel", "mobile");
    }

    @Test
    void rejectsBlankRequiredFields() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SendSpec.of(" ", "session-1", "user-1", "ping"))
                .withMessageContaining("agentId");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> SendSpec.of("agent-1", "session-1", "user-1", null))
                .withMessageContaining("text");
    }
}
