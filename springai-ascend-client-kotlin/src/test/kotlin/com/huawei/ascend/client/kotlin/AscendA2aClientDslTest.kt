package com.huawei.ascend.client.kotlin

import com.huawei.ascend.client.ClientAuth
import com.huawei.ascend.client.TracePropagation
import com.huawei.ascend.client.telemetry.ClientTelemetry
import java.time.Duration
import kotlinx.coroutines.test.runTest
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class AscendA2aClientDslTest {

    /** Every DSL property reaches the Java builder, observed off the wire. */
    @Test
    fun dslPropertiesMapOntoTheJavaBuilder() = runTest {
        StubA2aServer().use { stub ->
            ascendA2aClient {
                baseUrl = stub.baseUrl
                timeout = Duration.ofSeconds(10)
                auth = ClientAuth.jwtBearer({ "jwt-token" }, "tenant-a")
                tracePropagation = TracePropagation.notSampled()
                telemetry = ClientTelemetry.noop()
            }.use { client ->
                val response = client.sendTextSuspending(
                    sendSpec(agentId = "a", sessionId = "s", userId = "u", text = "ping"))

                assertThat(response.text()).isEqualTo("pong")
                val headers = stub.recordedHeaders("/a2a")
                assertThat(headers["authorization"]).isEqualTo("Bearer jwt-token")
                assertThat(headers["x-tenant-id"]).isEqualTo("tenant-a")
                // notSampled() mints trace-flags 00 instead of the default 01.
                assertThat(headers["traceparent"]).startsWith("00-").endsWith("-00")
            }
        }
    }

    @Test
    fun missingBaseUrlFailsAtBuild() {
        assertThatThrownBy { ascendA2aClient { } }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("baseUrl")
    }
}
