package com.huawei.ascend.client.kotlin

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SendSpecsTest {

    @Test
    fun factoryDefaultsMatchTheRecordCanonicalConstructor() {
        val spec = sendSpec(agentId = "a", sessionId = "s", userId = "u", text = "hi")

        assertThat(spec.agentId()).isEqualTo("a")
        assertThat(spec.sessionId()).isEqualTo("s")
        assertThat(spec.userId()).isEqualTo("u")
        assertThat(spec.text()).isEqualTo("hi")
        assertThat(spec.messageId()).isNotBlank()
        assertThat(spec.metadata()).isEmpty()
    }

    @Test
    fun namedArgumentsPassThrough() {
        val spec = sendSpec(
            agentId = "a",
            sessionId = "s",
            userId = "u",
            text = "hi",
            messageId = "m-1",
            metadata = mapOf("channel" to "mobile"),
        )

        assertThat(spec.messageId()).isEqualTo("m-1")
        assertThat(spec.metadata()).containsEntry("channel", "mobile")
    }
}
