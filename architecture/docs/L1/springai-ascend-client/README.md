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
  timeout default 30s; optional `ClientAuth`, `TracePropagation`). Methods:
  `agentCard()`, blocking `sendText(SendSpec)`, blocking-aggregate
  `streamText(SendSpec[, listener])`.
- `A2aEvents` — the client-side truth for terminal-event detection (final A2A
  task states + `Message` metadata `runStatus`) and user-visible text
  extraction, promoted from the e2e example's `SampleA2aClient`; the
  post-terminal-cancellation rule (a `CancellationException` after a terminal
  event is normal SSE unsubscription) lives here.
- `ClientAuth` — JWT bearer + optional `X-Tenant-Id` cross-check headers,
  matching `A2aTenantAuthFilter` / `ServiceTenantAuthFilter` (ADR-0040).
- `TracePropagation` / `TraceCorrelation` — per-call W3C version-00
  `traceparent` origination (JDK-only, no OTel dependency in this slice) and
  the server's `traceresponse` surfaced on every `A2aResponse`.
- `HeaderCapturingHttpClient` (internal) — `A2AHttpClient` over
  `java.net.http`; exists solely because the OSS response surface hides
  response headers and the `traceresponse` contract requires them.

## Constraints

- Main scope is Spring-free and depends on no reactor sibling
  (`ClientPackageBoundaryTest`); the SDK must embed in customer applications
  without platform server modules.
- OTLP batching and posture-aware PII redaction are the second slice per
  ADR-0162; the observability ledger contract is NOT fulfilled by this module
  yet.
