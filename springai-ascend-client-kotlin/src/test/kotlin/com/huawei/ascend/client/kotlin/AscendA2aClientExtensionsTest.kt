package com.huawei.ascend.client.kotlin

import java.time.Duration
import java.util.concurrent.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.a2aproject.sdk.spec.StreamingEventKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AscendA2aClientExtensionsTest {

    @Test
    fun sendTextSuspendingReturnsTheTerminalAnswer() = runTest {
        StubA2aServer().use { stub ->
            ascendA2aClient { baseUrl = stub.baseUrl }.use { client ->
                val response = client.sendTextSuspending(pingSpec())

                assertThat(response.text()).isEqualTo("pong")
                assertThat(response.events()).hasSize(1)
            }
        }
    }

    @Test
    fun streamTextSuspendingAggregatesEventsAndFeedsTheListener() = runTest {
        StubA2aServer().use { stub ->
            ascendA2aClient { baseUrl = stub.baseUrl }.use { client ->
                val seen = mutableListOf<StreamingEventKind>()

                val response = client.streamTextSuspending(pingSpec()) { seen.add(it) }

                assertThat(response.text()).isEqualTo("pong")
                assertThat(response.events()).hasSize(2)
                assertThat(seen).hasSize(2)
            }
        }
    }

    /**
     * The deadline is the 15s runTest timeout: the client's own completion
     * timeout is 2 minutes, so the test only passes when cancelling the
     * coroutine actually interrupts the blocking wait for the terminal event.
     */
    @Test
    fun cancellingTheCoroutineInterruptsTheBlockingStream() = runTest(timeout = 15.seconds) {
        StubA2aServer().use { stub ->
            stub.hangStreams = true
            ascendA2aClient {
                baseUrl = stub.baseUrl
                timeout = Duration.ofMinutes(2)
            }.use { client ->
                val outcome = CompletableDeferred<Throwable>()
                val job = launch(Dispatchers.Default) {
                    try {
                        client.streamTextSuspending(pingSpec())
                        error("stream completed despite a hanging server")
                    } catch (t: Throwable) {
                        outcome.complete(t)
                        throw t
                    }
                }
                stub.awaitHangingStream()

                job.cancel()

                assertThat(outcome.await()).isInstanceOf(CancellationException::class.java)
                job.join()
                assertThat(job.isCancelled).isTrue()
            }
        }
    }

    private fun pingSpec() = sendSpec(
        agentId = "agent-1",
        sessionId = "session-1",
        userId = "user-1",
        text = "ping",
    )
}
