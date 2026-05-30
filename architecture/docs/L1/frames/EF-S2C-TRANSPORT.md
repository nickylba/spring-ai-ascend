---
level: L1
view: development
status: shipped
authority: "ADR-0161 (Frame Card shape + Card-over-DSL); ADR-0157 (EngineeringFrame Ontology)"

# --- Identity block: COPIED from the DSL frame element (do not invent) ---
frame_id: EF-S2C-TRANSPORT
dsl_element: efS2cTransport
owner_module: agent-bus
primary_package: com.huawei.ascend.bus.spi.s2c
source_adr: ADR-0157

# --- fact_refs: every generated fact_id this card cites. Each resolves in
#     architecture/facts/generated/*.json (the gate cross-checks these). ---
fact_refs:
  - code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbacktransport
  - code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackenvelope
  - code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse
  - code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse-outcome
  - code-symbol/com-huawei-ascend-bus-spi-s2c-reflectionenveloperouter
  - test/com-huawei-ascend-bus-spi-s2c-s2ccallbackenvelopelibrarytest
  - test/com-huawei-ascend-service-runtime-s2c-s2ccallbackroundtripit
  - test/com-huawei-ascend-service-runtime-s2c-s2cfailuretransitionsruntofailedit
  - test/com-huawei-ascend-service-runtime-s2c-s2ctransporttimeouttrippedbyorchestratortest
  - test/com-huawei-ascend-service-runtime-s2c-s2ccallbackenvelopevalidationtest
  - test/com-huawei-ascend-service-runtime-s2c-s2ccallbackrespectsrule38test
---

# `EF-S2C-TRANSPORT` — S2C Transport Frame

> Anchors the server-to-client callback transport SPI: the neutral boundary by which a
> suspended Run hands a capability invocation to the client and resumes on the client's
> response. Rationale lives in `source_adr` (ADR-0157) and ADR-0088 / ADR-0074.

## 1. Identity

> COPIED from the DSL frame element. These fields MUST match the DSL byte-for-byte;
> the gate (Rule G-29) fails a card that disagrees.

| Field | Value | Source |
|---|---|---|
| Frame ID (`saa.id`) | `EF-S2C-TRANSPORT` | DSL element |
| DSL element | `efS2cTransport` | `architecture/features/engineering-frames.dsl` |
| Owner module (`saa.owner`) | `agent-bus` | DSL element |
| Status (`saa.status`) | `shipped` | DSL element |
| Primary package (`saa.primaryPackage`) | `com.huawei.ascend.bus.spi.s2c` | DSL element |
| Source ADR (`saa.sourceAdr`) | `ADR-0157` | DSL element |
| Card path (`saa.cardPath`) | `architecture/docs/L1/frames/EF-S2C-TRANSPORT.md` | DSL element ↔ this file |

## 2. Capability Boundary

> AUTHORED prose. Package names are CITED (they must exist); the lists below are the
> human-readable boundary, not a second registry.

**Can do** — the responsibilities that live inside this frame:

- Declare the server-to-client callback transport SPI — the `S2cCallbackTransport`
  interface (`code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbacktransport`) that a
  platform binds to dispatch a capability invocation from a suspended Run to its client.
- Define the request/response value envelopes carried across that boundary —
  `S2cCallbackEnvelope` (`code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackenvelope`)
  and `S2cCallbackResponse`
  (`code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse`) with its closed
  outcome enumeration `S2cCallbackResponse.Outcome`
  (`code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse-outcome`).
- Own the construction-time integrity of the envelope identity / correlation fields
  (callback id, server run id, capability ref, trace id, idempotency key) as the SPI
  package's invariant surface.

**Cannot do** — explicitly out of scope (handled by another frame or an L2 detail):

- Drive the suspend/resume state machine. Catching the client-callback `SuspendSignal`,
  transitioning the Run to `SUSPENDED`/`FAILED`, firing the error hook, and resuming on
  the response is orchestration behaviour owned by `EF-TASK-CONTROL` /
  `EF-ORCHESTRATION-SPI` (agent-service / bus.spi.engine), not by this SPI.
- Provide a concrete transport. The in-process reference realization
  (`InMemoryS2cCallbackTransport`) lives on agent-service under
  `com.huawei.ascend.service.runtime.s2c`, outside this frame's package; this frame owns
  only the SPI contract.
- Carry the over-the-wire delivery mechanics, timeout/deadline scheduling, persistence,
  or busy-wait behaviour — that runtime detail is delegated to the frame's L2 sink and the
  contract surface, not restated here.

**Owned state** — the data/state this frame is the structural home for:

- The S2C callback SPI surface and its value types — the package
  `com.huawei.ascend.bus.spi.s2c`. The frame holds no mutable runtime state of its own;
  the envelopes are immutable records and the transport is a stateless dispatch interface.

**External dependencies** — frames / modules this frame is allowed to depend on:

- `java.*` only. As a pure SPI package the frame depends on the JDK standard library and
  its own same-package siblings; this is asserted by an ArchUnit enforcer (see section 7).

**Forbidden dependencies** — dependencies the boundary must never take (held by an
ArchUnit enforcer; cited in section 7):

- Any non-`java.*`, non-sibling package — including the agent-service reference
  implementation under `com.huawei.ascend.service.runtime.s2c`, the orchestration kernel,
  and any other module. The SPI must not depend downward on its own callers or on a
  concrete transport.
- `java.lang.Thread#sleep` (busy-wait). The S2C wait MUST route through the
  suspend/checkpoint path, never a sleep in the SPI or its reference impl.

**Included / excluded packages** (this frame is a single-package SPI root, not a cluster):

- Included: `com.huawei.ascend.bus.spi.s2c`
- Excluded: `com.huawei.ascend.service.runtime.s2c` (the reference transport implementation,
  owned by agent-service, not this frame)

## 3. Type Inventory

> GENERATED — do not hand-edit between the markers. Rendered from
> `architecture/facts/generated/code-symbols.json`, filtered to the frame's in-boundary
> package(s). Every row cites its `code-symbol/<kebab-fqn>` fact ID. The Card generator owns
> this region and overwrites it on every re-render.

<!-- BEGIN GENERATED: type-inventory -->
| Type | Kind | Fact ID |
|---|---|---|
| `com.huawei.ascend.bus.spi.s2c.ReflectionEnvelopeRouter` | interface | `code-symbol/com-huawei-ascend-bus-spi-s2c-reflectionenveloperouter` |
| `com.huawei.ascend.bus.spi.s2c.S2cCallbackEnvelope` | record | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackenvelope` |
| `com.huawei.ascend.bus.spi.s2c.S2cCallbackResponse` | record | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse` |
| `com.huawei.ascend.bus.spi.s2c.S2cCallbackResponse$Outcome` | enum | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse-outcome` |
| `com.huawei.ascend.bus.spi.s2c.S2cCallbackTransport` | interface | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbacktransport` |
<!-- END GENERATED: type-inventory -->

## 4. Internal Collaboration

> GENERATED — do not hand-edit between the markers. Rendered from `code-symbols.json`:
> the structural relationships (implements / extends / references) among the in-boundary
> types listed in section 3. This is the *structural* collaboration only — runtime call
> sequences belong in the frame's L2 sink, not here.

<!-- BEGIN GENERATED: internal-collaboration -->
| From | Relationship | To |
|---|---|---|
| `S2cCallbackResponse` | references | `S2cCallbackResponse$Outcome` |
| `S2cCallbackTransport` | references | `S2cCallbackEnvelope` |
<!-- END GENERATED: internal-collaboration -->

## 5. Contracts

> AUTHORED prose. The communication contracts this frame exposes or consumes. Cite each
> contract operation by its `contract-op/<id>` fact ID and each SPI by its package identity.
> Wire-field and over-the-wire mechanics are L2 — link down, do not inline.

**Exposed SPI / public surface (boundary identity):**

- `com.huawei.ascend.bus.spi.s2c` — the public SPI package that *is* the boundary. Its
  identity interface is `S2cCallbackTransport`
  (`code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbacktransport`); the value types it
  exchanges are `S2cCallbackEnvelope`
  (`code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackenvelope`) and
  `S2cCallbackResponse` (`code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse`).

**Schema contract (AsyncAPI-style / SPI envelope, not an OpenAPI operation):**

The S2C callback travels on an internal channel; it has no HTTP route and therefore no
`contract-op/<id>` operation. Its envelope/response shape is pinned by the schema contract
[`docs/contracts/s2c-callback.v1.yaml`](../../../../docs/contracts/s2c-callback.v1.yaml)
(Authority: ADR-0074). The request field set, the closed `outcome_values` `{ok, error,
timeout}`, and the W3C trace-ID character class are contract material, asserted by the
enforcers in section 7 — they are not restated here.

**Consumed contracts** (operations this frame calls on another frame):

- None. The SPI is a pure boundary declaration; it invokes no operation on another frame.
  The suspend/resume orchestration that *uses* this transport is owned by `EF-TASK-CONTROL`
  / `EF-ORCHESTRATION-SPI`.

## 6. FunctionPoint Mapping

> AUTHORED prose over the frame's DSL `anchors` edges. Lists ONLY the FunctionPoint the DSL
> anchors to this frame (`efS2cTransport -> fpS2cCallback` in `engineering-frames.dsl`).
> Each anchor is CITED by generated fact ID.

### `FP-S2C-CALLBACK` — server-to-client callback envelope via the transport SPI; the Run suspends, the client receives the capability invocation, the response resumes the Run

| Anchor | Fact ID |
|---|---|
| Entry class | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbacktransport` |
| Entry method | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbacktransport#dispatch(Lcom/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelope;)Ljava/util/concurrent/CompletionStage;` |
| Request envelope | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackenvelope` |
| Response envelope | `code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbackresponse` |
| Contract (schema) | [`docs/contracts/s2c-callback.v1.yaml`](../../../../docs/contracts/s2c-callback.v1.yaml) (Authority ADR-0074; internal channel, no `contract-op`) |
| Test (round trip) | `test/com-huawei-ascend-service-runtime-s2c-s2ccallbackroundtripit` |
| Test (failure transitions) | `test/com-huawei-ascend-service-runtime-s2c-s2cfailuretransitionsruntofailedit` |

> FunctionPoint identity (`saa.id` `FP-S2C-CALLBACK`, `saa.status` `shipped`,
> `saa.requirement` `REQ-003`) is declared in
> [`../../../features/function-points.dsl`](../../../features/function-points.dsl); this
> card reads, never re-declares, that element.

## 7. Verification

> AUTHORED prose. The constraints, ArchUnit enforcers, and gate rules that hold this
> boundary. Each enforcer / rule is cited as a structural identifier (not version metadata).

**Constraints / enforcers holding the boundary:**

- `SpiPurityGeneralizedArchTest#s2c_spi_imports_only_java_and_same_package_siblings`
  (enforcer `E93`) — asserts every class under `com.huawei.ascend.bus.spi.s2c` depends only
  on `java.*` + same-SPI-package siblings (the forbidden-dependency invariant of section 2).
- `S2cCallbackRespectsRule38Test#s2c_package_must_not_call_thread_sleep` (enforcer `E83`) —
  asserts no class in the S2C SPI or its reference impl calls `Thread.sleep(...)`; the S2C
  wait routes through suspend/checkpoint, never a busy-wait.
- `s2c_callback_yaml_present_and_wellformed` (enforcer `E81`) — asserts the schema contract
  `docs/contracts/s2c-callback.v1.yaml` exists and is well-formed (request fields,
  `outcome_values` closed at `{ok, error, timeout}`).
- Rule `G-23` (enforcer `E188`) — Shipped-Frame Anchor Integrity: this `shipped` frame
  carries the required `anchors` edge to `FP-S2C-CALLBACK` in `engineering-frames.dsl`.
- Rule `G-29` (enforcer `E196`) — Frame-Card / DSL Parity: validates this card's copied
  identity fields and every fact citation above against the DSL and the generated facts.

**Tests anchoring the behaviour.** The S2C transport behaviour (happy-path callback round
trip and parent resume, failure-to-`FAILED` transitions, orchestrator-tripped timeout,
envelope validation, in-package envelope invariants, and the no-busy-wait guard) is proven
by the test facts in this card's frontmatter `fact_refs:` block, which the gate resolves
against `architecture/facts/generated/tests.json`. The per-test asserted behaviour is the
behaviour catalogue and lives with those test facts and this frame's FunctionPoint
`saa.test_refs[]` in `function-points.dsl`, not as an inventory in this L1 card.

## Cross-references

- Frames directory + Card-over-DSL rules: [`README.md`](README.md).
- This frame's DSL element (authority): [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl).
- The anchored FunctionPoint element: [`../../../features/function-points.dsl`](../../../features/function-points.dsl).
- Generated facts cited above (authority over this prose): [`../../../facts/generated/`](../../../facts/generated/).
- Collective structural map: [`../engineering-frames.md`](../engineering-frames.md).
- Schema contract: [`../../../../docs/contracts/s2c-callback.v1.yaml`](../../../../docs/contracts/s2c-callback.v1.yaml) (Authority ADR-0074).
