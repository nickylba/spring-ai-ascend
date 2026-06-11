---
level: L1
view: [logical, development]
module: springai-ascend-client-kotlin
status: active
freeze_id: null
authority: "ADR-0162 (client SDK un-deferred — Kotlin idiom layer)"
---

# springai-ascend-client-kotlin — L1 (Index)

The Kotlin idiom layer for the Audience B client SDK: thin extensions over
`springai-ascend-client` so Kotlin/coroutine codebases consume the A2A facade
natively. The module carries no protocol logic — every call delegates to the
Java `AscendA2aClient`, which remains the single authority for terminal-event
semantics, trace propagation and auth headers.

## Logical surface (package `com.huawei.ascend.client.kotlin`)

- `sendTextSuspending(SendSpec)` / `streamTextSuspending(SendSpec[, listener])`
  — the blocking Java calls as suspend functions, executed on
  `Dispatchers.IO` via `runInterruptible`: cancelling the coroutine interrupts
  the client's blocking wait (the Java client rethrows the interrupt), so the
  call ends with a `CancellationException` instead of blocking until timeout.
- `ascendA2aClient { }` — builder DSL whose properties (`baseUrl`, `timeout`,
  `auth`, `tracePropagation`, `telemetry`) map one-to-one onto
  `AscendA2aClient.Builder`; unset properties keep the Java defaults.
- `sendSpec(agentId, sessionId, userId, text[, messageId, metadata])` —
  named/default-argument factory mirroring the `SendSpec` record's canonical
  constructor.
- `use { }` scoping works directly on `AscendA2aClient` (it is
  `AutoCloseable`); the layer adds no wrapper type.

## Constraints

- Depends on `springai-ascend-client` only (plus kotlin-stdlib and
  kotlinx-coroutines-core, both managed by the Spring Boot parent); no other
  reactor sibling — the layer must embed in customer applications without
  platform server modules, exactly like the Java facade.
- Spring-free main scope, like the Java facade.
