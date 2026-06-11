package com.huawei.ascend.client.kotlin

import com.google.protobuf.util.JsonFormat
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.OutputStream
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.a2aproject.sdk.grpc.utils.JSONRPCUtils
import org.a2aproject.sdk.grpc.utils.ProtoUtils
import org.a2aproject.sdk.spec.AgentCapabilities
import org.a2aproject.sdk.spec.AgentCard
import org.a2aproject.sdk.spec.AgentInterface
import org.a2aproject.sdk.spec.Message
import org.a2aproject.sdk.spec.TextPart
import org.a2aproject.sdk.spec.TransportProtocol

/**
 * Minimal local stub A2A server for the Kotlin idiom layer: serves the agent
 * card and a ping/pong JSON-RPC endpoint (send + SSE stream), records request
 * headers per path, and can hang a stream after the non-terminal ack so
 * cancellation tests have a genuinely blocked call to interrupt. The
 * springai-ascend-client stub is test-scoped in that module and not
 * consumable here, hence this local port; responses are serialized with the
 * same proto-JSON helpers the real server side uses.
 */
internal class StubA2aServer : AutoCloseable {

    private val server = HttpServer.create(InetSocketAddress("localhost", 0), 0)
    private val recordedHeaders = ConcurrentHashMap<String, Map<String, String>>()
    private val streamRequested = CountDownLatch(1)
    private val released = CountDownLatch(1)

    /** When set, SSE streams send the non-terminal ack and then hang until [close]. */
    @Volatile
    var hangStreams = false

    val baseUrl: String = "http://localhost:${server.address.port}"

    init {
        server.createContext("/.well-known/agent-card.json", ::serveAgentCard)
        server.createContext("/a2a", ::serveJsonRpc)
        server.start()
    }

    /** Recorded request headers for [path], names lowercased. */
    fun recordedHeaders(path: String): Map<String, String> = recordedHeaders[path] ?: emptyMap()

    /** Blocks until a hanging stream has delivered its ack and reached the hang point. */
    fun awaitHangingStream() {
        check(streamRequested.await(10, TimeUnit.SECONDS)) { "no streaming request arrived" }
    }

    override fun close() {
        released.countDown()
        server.stop(0)
    }

    private fun serveAgentCard(exchange: HttpExchange) {
        record(exchange)
        val card = AgentCard.builder()
            .name("stub-agent")
            .description("local stub for the Kotlin idiom layer tests")
            .url("$baseUrl/a2a")
            .version("1.0")
            .capabilities(AgentCapabilities.builder().streaming(true).build())
            .defaultInputModes(listOf("text"))
            .defaultOutputModes(listOf("text"))
            .skills(listOf())
            .supportedInterfaces(
                listOf(AgentInterface(TransportProtocol.JSONRPC.asString(), "$baseUrl/a2a")))
            .preferredTransport(TransportProtocol.JSONRPC.asString())
            .build()
        respondJson(exchange, JsonFormat.printer().print(ProtoUtils.ToProto.agentCard(card)))
    }

    private fun serveJsonRpc(exchange: HttpExchange) {
        record(exchange)
        val body = exchange.requestBody.readAllBytes().toString(StandardCharsets.UTF_8)
        val requestId = requestId(body)
        if (body.contains("\"SendStreamingMessage\"")) {
            serveSse(exchange, requestId)
        } else {
            respondJson(exchange, JSONRPCUtils.toJsonRPCResultResponse(
                requestId, ProtoUtils.ToProto.taskOrMessage(completedPong())))
        }
    }

    private fun serveSse(exchange: HttpExchange, requestId: String) {
        exchange.responseHeaders.set("Content-Type", "text/event-stream")
        exchange.sendResponseHeaders(200, 0)
        exchange.responseBody.use { out ->
            writeSseEvent(out, JSONRPCUtils.toJsonRPCResultResponse(
                requestId, ProtoUtils.ToProto.taskOrMessageStream(acceptedAck())))
            if (hangStreams) {
                streamRequested.countDown()
                released.await()
                return
            }
            writeSseEvent(out, JSONRPCUtils.toJsonRPCResultResponse(
                requestId, ProtoUtils.ToProto.taskOrMessageStream(completedPong())))
        }
    }

    /** Header names recorded lowercase: HTTP headers are case-insensitive on the wire. */
    private fun record(exchange: HttpExchange) {
        val headers = ConcurrentHashMap<String, String>()
        exchange.requestHeaders.forEach { (name, values) ->
            values.firstOrNull()?.let { headers[name.lowercase(Locale.ROOT)] = it }
        }
        recordedHeaders[exchange.requestURI.path] = headers
    }

    private companion object {

        val REQUEST_ID_PATTERN = Regex("\"id\"\\s*:\\s*\"([^\"]+)\"")

        fun requestId(body: String): String =
            checkNotNull(REQUEST_ID_PATTERN.find(body)?.groupValues?.get(1)) {
                "JSON-RPC request without string id: $body"
            }

        fun acceptedAck(): Message = Message.builder()
            .role(Message.Role.ROLE_AGENT)
            .messageId("ack-1")
            .metadata(mapOf("accepted" to true))
            .parts(listOf(TextPart("execution enqueued")))
            .build()

        fun completedPong(): Message = Message.builder()
            .role(Message.Role.ROLE_AGENT)
            .messageId("reply-1")
            .metadata(mapOf("runStatus" to "completed"))
            .parts(listOf(TextPart("pong")))
            .build()

        /**
         * Multi-line JSON is emitted as one SSE event with one `data:` line
         * per JSON line; conforming parsers re-join them with newlines.
         */
        fun writeSseEvent(out: OutputStream, json: String) {
            val event = json.lineSequence().joinToString("\n", postfix = "\n\n") { "data: $it" }
            out.write(event.toByteArray(StandardCharsets.UTF_8))
            out.flush()
        }

        fun respondJson(exchange: HttpExchange, json: String) {
            val payload = json.toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.set("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, payload.size.toLong())
            exchange.responseBody.use { it.write(payload) }
        }
    }
}
