---
level: L1
view: [logical, development]
module: springai-ascend-client
status: active
freeze_id: null
authority: "ADR-0162 (client SDK un-deferred — first slice)"
---

# springai-ascend-client — L1 (Index)

The Audience B client SDK: a Java-only, Spring-free A2A client facade for
external developers integrating against the platform's A2A ingress
(agent-runtime directly, or the agent-service facade edge). The OSS
a2a-java-sdk remains the protocol client (`JSONRPCTransport`,
`A2ACardResolver`); this module adds only platform semantics on top.

## Logical surface (package `com.huawei.ascend.client`)

- `AscendA2aClient` — builder-configured entry point (`baseUrl` required;
  timeout default 30s; optional `ClientAuth`, `TracePropagation`,
  `ClientTelemetry`). Methods: `agentCard()`, blocking `sendText(SendSpec)`,
  blocking-aggregate `streamText(SendSpec[, listener])`.
- `A2aEvents` — the client-side truth for terminal-event detection (final A2A
  task states + `Message` metadata `runStatus`) and user-visible text
  extraction, promoted from the e2e example's `SampleA2aClient`; the
  post-terminal-cancellation rule (a `CancellationException` after a terminal
  event is normal SSE unsubscription) lives here.
- `ClientAuth` — JWT bearer + optional `X-Tenant-Id` cross-check headers,
  matching `A2aTenantAuthFilter` / `ServiceTenantAuthFilter` (ADR-0040).
- `TracePropagation` / `TraceCorrelation` — outbound W3C version-00
  `traceparent` derived from the active client span when telemetry is enabled
  (JDK-only random mint as the no-telemetry fallback) and the server's
  `traceresponse` surfaced on every `A2aResponse`.
- `HeaderCapturingHttpClient` (internal) — `A2AHttpClient` over
  `java.net.http`; exists solely because the OSS response surface hides
  response headers and the `traceresponse` contract requires them.

## Telemetry surface (package `com.huawei.ascend.client.telemetry`)

- `ClientTelemetry` — the seam the facade emits business CLIENT spans
  through. Three bindings: `noop()` (default), `OtelClientTelemetry` over a
  consumer-supplied `OpenTelemetry` (never shut down by the SDK), and
  `otlpHttp(endpoint, posture, headers)` — an SDK-owned `OpenTelemetrySdk`
  with `BatchSpanProcessor` + OTLP/HTTP exporter
  (`service.name=springai-ascend-client`), flushed and closed with the
  client (`OtlpClientTelemetry`).
- `Posture` — client-side mirror of the platform posture: parent-based head
  sampling at dev=1.0 / research=0.10 / prod=0.01, and structural PII
  redaction in `PROD` — message-text span attributes are never set, rather
  than set and filtered.
- `ClientCallSpan` — per-call span handle; the active span supplies the
  outbound `traceparent` and records the `traceresponse` correlation as a
  span attribute.

## Constraints

- Main scope is Spring-free and depends on no reactor sibling
  (`ClientPackageBoundaryTest`); the SDK must embed in customer applications
  without platform server modules.
- The OpenTelemetry dependencies are `<optional>` and confined to the
  telemetry package: only `ClientTelemetry.otlpHttp` /
  `OtelClientTelemetry` require them on the consumer classpath; the rest of
  the facade stays JDK-only. This fulfils the observability ledger contract
  (`architecture-status.yaml#client_sdk_observability_contract`, ADR-0162).
- The Kotlin idiom layer ships as the sibling module
  `springai-ascend-client-kotlin` (see its L1 index).
