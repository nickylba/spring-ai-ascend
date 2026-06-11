package com.huawei.ascend.client.kotlin

import com.huawei.ascend.client.AscendA2aClient
import com.huawei.ascend.client.ClientAuth
import com.huawei.ascend.client.TracePropagation
import com.huawei.ascend.client.telemetry.ClientTelemetry
import java.time.Duration

/**
 * Builds an [AscendA2aClient] Kotlin-style:
 *
 * ```
 * val client = ascendA2aClient {
 *     baseUrl = "http://localhost:8080"
 *     auth = ClientAuth.jwtBearer({ token }, "tenant-a")
 * }
 * ```
 *
 * Unset properties keep the Java builder's defaults (30s timeout, sampled
 * trace propagation, no-op telemetry, no auth headers); `baseUrl` is required
 * and validated by the Java builder's `build()`.
 */
fun ascendA2aClient(configure: AscendA2aClientSpec.() -> Unit): AscendA2aClient =
    AscendA2aClientSpec().apply(configure).build()

/** Mutable mirror of [AscendA2aClient.Builder]; null means "keep the Java default". */
class AscendA2aClientSpec internal constructor() {

    /** Server base URL, e.g. `http://localhost:8080`. Required. */
    var baseUrl: String? = null

    /** Per-call completion timeout (connect + request + stream-to-terminal). */
    var timeout: Duration? = null

    /** Without it no Authorization/X-Tenant-Id headers are sent. */
    var auth: ClientAuth? = null

    /** Used only for calls whose telemetry originates no trace context. */
    var tracePropagation: TracePropagation? = null

    /** Closed with the client, exactly like on the Java builder. */
    var telemetry: ClientTelemetry? = null

    internal fun build(): AscendA2aClient {
        val builder = AscendA2aClient.builder()
        baseUrl?.let(builder::baseUrl)
        timeout?.let(builder::timeout)
        auth?.let(builder::auth)
        tracePropagation?.let(builder::tracePropagation)
        telemetry?.let(builder::telemetry)
        return builder.build()
    }
}
