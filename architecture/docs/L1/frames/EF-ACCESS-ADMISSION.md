---
# Frame Card frontmatter. The identity block is COPIED from the frame's DSL element
# (architecture/features/features.dsl — the agent-service Layer-1 feature re-tagged as
# an EngineeringFrame per ADR-0157). Every value here MUST match that DSL element; the
# gate (Rule G-29) fails a card whose frontmatter disagrees with saa.id / saa.owner /
# saa.status / saa.primaryPackage.
level: L1
view: development
status: shipped
authority: "ADR-0161 (Frame Card shape + Card-over-DSL); ADR-0157 (EngineeringFrame Ontology); ADR-0138 (agent-service per-layer architecture)"

# --- Identity block: COPIED from the DSL frame element (do not invent) ---
frame_id: EF-ACCESS-ADMISSION
dsl_element: efAccessAdmission
owner_module: agent-service
primary_package: com.huawei.ascend.service.platform.web
source_adr: ADR-0138|ADR-0155

# --- fact_refs: every generated fact_id this card cites. Each resolves in
#     architecture/facts/generated/*.json; the gate cross-checks these. ---
fact_refs:
  - code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller
  - code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfilter
  - code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencystore
  - code-symbol/com-huawei-ascend-service-platform-posture-posturebootguard
  - code-symbol/com-huawei-ascend-service-platform-posture-requiredconfig
  - code-symbol/com-huawei-ascend-service-platform-tenant-jwttenantclaimcrosscheck
  - code-symbol/com-huawei-ascend-service-platform-tenant-tenantcontextfilter
  - code-symbol/com-huawei-ascend-service-platform-web-websecurityconfig
  - contract-op/createrun
  - contract-op/getrun
  - test/com-huawei-ascend-service-platform-web-runs-runhttpcontractit
  - test/com-huawei-ascend-service-runtime-runs-runstatemachinetest
  - test/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfilterit
  - test/com-huawei-ascend-service-platform-idempotency-idempotencystorepostgresit
  - test/com-huawei-ascend-service-platform-tenant-jwttenantclaimcrosschecktest
  - test/com-huawei-ascend-service-platform-tenant-tenantcontextfilterit
  - test/com-huawei-ascend-service-platform-posture-posturebootguardit
  - test/com-huawei-ascend-service-platform-architecture-httpedgemustnotimportmemoryspitest
  - test/com-huawei-ascend-service-platform-architecture-platformimportsonlyruntimepublicapitest
  - test/com-huawei-ascend-service-runtime-architecture-runtimemustnotdependonplatformtest
---

<!--
  ALTITUDE: this is an L1 artifact. It names the boundary, the package cluster root, the
  public API/SPI surface, and the inter-frame contract. It does NOT carry L2 runtime
  detail (private call chains, runtime sequences, SQL/RLS/GUC, HTTP status/verb/header
  behaviour, filter ordering, wire formats, exhaustive method/test dumps); that detail is
  delegated to the contract surface + the frame's L2 sink, not restated here.
  <!-- l2-detail-sink-allow: card altitude disclaimer NAMES the forbidden L2 categories to forbid them (a delegation pointer, not an inlined leak); the home is the contract surface + architecture/docs/L2/<slug>/ -->
-->

# `EF-ACCESS-ADMISSION` — Access and Admission Frame

> The durable agent-service responsibility for converging external protocol ingress into
> tenant-bound, idempotency-decided, posture-gated service requests, and for publishing the
> client-facing capability surface — without owning Run, Task, or Session state.

## 1. Identity

> COPIED from the DSL frame element. These fields MUST match the DSL byte-for-byte;
> the gate fails a card that disagrees.

| Field | Value | Source |
|---|---|---|
| Frame ID (`saa.id`) | `EF-ACCESS-ADMISSION` | DSL element |
| DSL element | `efAccessAdmission` | `architecture/features/features.dsl` (agent-service frame re-tagged per ADR-0157) |
| Owner module (`saa.owner`) | `agent-service` | DSL element |
| Status (`saa.status`) | `shipped` | DSL element |
| Primary package (`saa.primaryPackage`) | `com.huawei.ascend.service.platform.web` | DSL element |
| Source ADR (`saa.sourceAdr`) | `ADR-0138|ADR-0155` | DSL element |
| Card path (`saa.cardPath`) | `architecture/docs/L1/frames/EF-ACCESS-ADMISSION.md` | DSL element ↔ this file |

This frame is Layer 1 of the agent-service per-layer architecture (ADR-0138). It is a
**package cluster** rooted at the declared primary package
`com.huawei.ascend.service.platform.web` and extending across the sibling
`com.huawei.ascend.service.platform.*` packages that hold the admission machinery (see
section 2). The collective structural narrative is in
[`../engineering-frames.md`](../engineering-frames.md); the deep-dive feature inventory
(AS-L1-F01..F08, AS-L1-F48..F51) is in
[`../agent-service/features/access-layer.md`](../agent-service/features/access-layer.md).

## 2. Capability Boundary

> AUTHORED prose. Package names are CITED (they must exist); the lists below are the
> human-readable boundary, not a second registry.

**Can do** — the responsibilities that live inside this frame:

- Converge external protocol ingress (HTTP today; gRPC / A2A / MQ designed) into a single
  tenant-bound service request at the edge of agent-service.
- Bind identity and tenancy: JWT decode and the `JWT.tenant` claim cross-check
  (`code-symbol/com-huawei-ascend-service-platform-tenant-jwttenantclaimcrosscheck`), tenant
  context propagation (`code-symbol/com-huawei-ascend-service-platform-tenant-tenantcontextfilter`),
  and the web security wiring (`code-symbol/com-huawei-ascend-service-platform-web-websecurityconfig`).
- Make the idempotency decision before any state write, via the header filter
  (`code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfilter`) over the
  claim/replay store SPI (`code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencystore`).
- Fail closed at startup when required configuration is absent, via the posture boot guard
  (`code-symbol/com-huawei-ascend-service-platform-posture-posturebootguard`) reading the
  `@RequiredConfig` marker (`code-symbol/com-huawei-ascend-service-platform-posture-requiredconfig`).
- Expose the client-facing Run admission and polling HTTP surface
  (`code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller`) and publish the
  client capability / OpenAPI surface.

**Cannot do** — explicitly out of scope (owned by another frame or an L2 detail):

- Own Run aggregate state or its state-machine transitions — that is the agent-service
  task-control / Run-repository frames; this frame only admits and reads.
- Own Task control state, Session context state, engine dispatch, or model/tool translation
  — those are the downstream agent-service frames (`EF-TASK-CONTROL`, `EF-ENGINE-DISPATCH`,
  and peers).
- Reach persistence, the memory SPI, or runtime-internal packages from the request thread
  (held by the enforcers in section 7).
- Carry the over-the-wire mechanics (HTTP status mapping, route verbs, header semantics,
  filter ordering, SQL): those are L2 / contract-surface concerns, delegated downward, not
  restated in this card.

**Owned state** — the data/state this frame is the structural home for:

- The idempotency claim/replay surface as a boundary SPI
  (`code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencystore`); the frame owns
  the admission decision, not the durable storage implementation behind the SPI.
- The per-request tenant context binding established at ingress
  (`code-symbol/com-huawei-ascend-service-platform-tenant-tenantcontextfilter`).
- No Run / Task / Session aggregate — this frame holds none of those.

**External dependencies** — frames / modules this frame is allowed to depend on:

- `agent-service` runtime public API (`com.huawei.ascend.service.runtime.runs.*`) — the Run
  repository SPI it admits and reads against.
- `agent-bus` engine SPI (`com.huawei.ascend.bus.spi.engine.*`) — the neutral engine-facing
  contract it hands admitted work to.
- `com.huawei.ascend.service.runtime.posture.*` — the posture gate it consults at startup.

**Forbidden dependencies** — dependencies the boundary must never take (held by ArchUnit
enforcers; cited in section 7):

- `com.huawei.ascend.service.runtime.memory.spi..` — the HTTP edge must not embed the memory
  SPI (enforcer E4).
- `com.huawei.ascend.service.runtime.orchestration.inmemory..`,
  `com.huawei.ascend.service.runtime.resilience..`, and other runtime-internal packages — the
  platform layer may import only the runtime *public* API (enforcer E34).

**Included / excluded packages** (this frame is a package *cluster*, not a single root):

- Included: `com.huawei.ascend.service.platform.web` (declared primary), and the admission
  siblings `com.huawei.ascend.service.platform.idempotency`,
  `com.huawei.ascend.service.platform.tenant`,
  `com.huawei.ascend.service.platform.posture`, and
  `com.huawei.ascend.service.platform.auth`.
- Excluded: `com.huawei.ascend.service.runtime..` (the runtime / cognitive-kernel frames) and
  `com.huawei.ascend.service.task..` (the Task-control frame).

## 3. Type Inventory

> GENERATED — do not hand-edit between the markers. Rendered from
> `architecture/facts/generated/code-symbols.json`, filtered to the frame's declared primary
> package `com.huawei.ascend.service.platform.web`. Every row cites its
> `code-symbol/<kebab-fqn>` fact ID. The Card generator owns this region and overwrites it on
> every re-render. (Admission-sibling types under `...platform.{idempotency,tenant,posture,auth}`
> are cited inline in sections 2/5/6 by their own fact IDs; they render here when the generator
> is configured with the full package-cluster filter.)

<!-- BEGIN GENERATED: type-inventory -->
| Type | Kind | Fact ID |
|---|---|---|
| `com.huawei.ascend.service.platform.web.ErrorEnvelope` | record | `code-symbol/com-huawei-ascend-service-platform-web-errorenvelope` |
| `com.huawei.ascend.service.platform.web.ErrorEnvelope$Error` | record | `code-symbol/com-huawei-ascend-service-platform-web-errorenvelope-error` |
| `com.huawei.ascend.service.platform.web.ErrorEnvelopeWriter` | class | `code-symbol/com-huawei-ascend-service-platform-web-errorenvelopewriter` |
| `com.huawei.ascend.service.platform.web.HealthController` | class | `code-symbol/com-huawei-ascend-service-platform-web-healthcontroller` |
| `com.huawei.ascend.service.platform.web.HealthResponse` | record | `code-symbol/com-huawei-ascend-service-platform-web-healthresponse` |
| `com.huawei.ascend.service.platform.web.WebSecurityConfig` | class | `code-symbol/com-huawei-ascend-service-platform-web-websecurityconfig` |
| `com.huawei.ascend.service.platform.web.runs.AsyncRunDispatcher` | interface | `code-symbol/com-huawei-ascend-service-platform-web-runs-asyncrundispatcher` |
| `com.huawei.ascend.service.platform.web.runs.CreateRunRequest` | record | `code-symbol/com-huawei-ascend-service-platform-web-runs-createrunrequest` |
| `com.huawei.ascend.service.platform.web.runs.NoOpAsyncRunDispatcher` | class | `code-symbol/com-huawei-ascend-service-platform-web-runs-noopasyncrundispatcher` |
| `com.huawei.ascend.service.platform.web.runs.OrchestratingAsyncRunDispatcher` | class | `code-symbol/com-huawei-ascend-service-platform-web-runs-orchestratingasyncrundispatcher` |
| `com.huawei.ascend.service.platform.web.runs.RunController` | class | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller` |
| `com.huawei.ascend.service.platform.web.runs.RunControllerAutoConfiguration` | class | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontrollerautoconfiguration` |
| `com.huawei.ascend.service.platform.web.runs.RunCursorResponse` | record | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcursorresponse` |
| `com.huawei.ascend.service.platform.web.runs.RunDispatchExecutorConfiguration` | class | `code-symbol/com-huawei-ascend-service-platform-web-runs-rundispatchexecutorconfiguration` |
| `com.huawei.ascend.service.platform.web.runs.RunDispatchProperties` | class | `code-symbol/com-huawei-ascend-service-platform-web-runs-rundispatchproperties` |
| `com.huawei.ascend.service.platform.web.runs.RunDispatchProperties$RejectionPolicy` | enum | `code-symbol/com-huawei-ascend-service-platform-web-runs-rundispatchproperties-rejectionpolicy` |
| `com.huawei.ascend.service.platform.web.runs.RunHttpExceptionMapper` | class | `code-symbol/com-huawei-ascend-service-platform-web-runs-runhttpexceptionmapper` |
| `com.huawei.ascend.service.platform.web.runs.RunResponse` | record | `code-symbol/com-huawei-ascend-service-platform-web-runs-runresponse` |
<!-- END GENERATED: type-inventory -->

## 4. Internal Collaboration

> GENERATED — do not hand-edit between the markers. Rendered from `code-symbols.json`: the
> structural relationships (implements / extends / declares-nested) among the in-boundary
> types listed in section 3. This is the *structural* collaboration only — runtime call
> sequences belong in the frame's L2 sink, not here.

<!-- BEGIN GENERATED: internal-collaboration -->
| From | Relationship | To |
|---|---|---|
| `com.huawei.ascend.service.platform.web.runs.NoOpAsyncRunDispatcher` | implements | `com.huawei.ascend.service.platform.web.runs.AsyncRunDispatcher` |
| `com.huawei.ascend.service.platform.web.runs.OrchestratingAsyncRunDispatcher` | implements | `com.huawei.ascend.service.platform.web.runs.AsyncRunDispatcher` |
| `com.huawei.ascend.service.platform.web.ErrorEnvelope` | declares-nested | `com.huawei.ascend.service.platform.web.ErrorEnvelope$Error` |
| `com.huawei.ascend.service.platform.web.runs.RunDispatchProperties` | declares-nested | `com.huawei.ascend.service.platform.web.runs.RunDispatchProperties$RejectionPolicy` |
<!-- END GENERATED: internal-collaboration -->

## 5. Contracts

> AUTHORED prose. The communication contracts this frame exposes or consumes. Contract
> operations are CITED by `contract-op/<id>`; SPIs by their package identity. Wire-field and
> over-the-wire mechanics are L2 — link down, do not inline.

**Exposed public surface (boundary identity):**

- `com.huawei.ascend.service.platform.web` — the HTTP edge package that *is* the client-facing
  boundary; its admission entry point is
  `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller`.
- `com.huawei.ascend.service.platform.idempotency` — the idempotency admission SPI surface, keyed
  on `code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencystore`.

**Contract operations (OpenAPI):**

| Operation | Fact ID | Contract source |
|---|---|---|
| Create Run (admission) | `contract-op/createrun` | `docs/contracts/openapi-v1.yaml` |
| Get Run status (polling) | `contract-op/getrun` | `docs/contracts/openapi-v1.yaml` |

The route, verb, status codes, and headers behind these operations are the contract-surface /
L2 material, not this card.

**Designed (not-yet-shipped) contract:**

- `docs/contracts/access-intent.v1.yaml` (`AccessIntent`, status `design_only`, ADR-0155) is the
  canonical ingress contract for the A2A / MQ admission FunctionPoints in section 6. It carries no
  `contract-op/*` fact yet because those operations are not implemented; the card therefore cites
  it by path, not by an operation fact ID.

**Consumed contracts** (operations this frame depends on, by package identity):

- `com.huawei.ascend.service.runtime.runs.*` (Run repository public API) — admitted work is handed
  to the runtime; this frame consumes it, it does not own it.
- `com.huawei.ascend.bus.spi.engine.*` (neutral engine SPI) — the boundary the admitted request
  crosses into the compute plane.

## 6. FunctionPoint Mapping

> AUTHORED prose over the frame's DSL `anchors` edges. Lists ONLY the nine FunctionPoints the
> DSL anchors to `efAccessAdmission` in `engineering-frames.dsl`. Each anchor is CITED by
> generated fact ID. The five `shipped` FPs carry fact-cited anchors; the four `design_only`
> A2A / MQ FPs are anchored by the DSL but have no backing code / test / contract facts yet
> (their entry classes do not exist in `code-symbols.json`), so they are listed without
> fact-cited anchors, as the DSL marks them.

### `FP-CREATE-RUN` — admit a new Run (the route/verb/status is `contract-op/createrun` material)

| Anchor | Fact ID |
|---|---|
| Entry class | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller` |
| Entry method | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller#create(Lcom/huawei/ascend/service/platform/web/runs/CreateRunRequest;Ljakarta/servlet/http/HttpServletRequest;)Lorg/springframework/http/ResponseEntity;` |
| Contract op | `contract-op/createrun` |
| Test | `test/com-huawei-ascend-service-platform-web-runs-runhttpcontractit` |
| Test | `test/com-huawei-ascend-service-runtime-runs-runstatemachinetest` |

### `FP-GET-RUN-STATUS` — tenant-scoped Run polling (the route/verb is `contract-op/getrun` material)

| Anchor | Fact ID |
|---|---|
| Entry class | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller` |
| Entry method | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller#get(Ljava/lang/String;)Lorg/springframework/http/ResponseEntity;` |
| Contract op | `contract-op/getrun` |
| Test | `test/com-huawei-ascend-service-platform-web-runs-runhttpcontractit` |

### `FP-IDEMPOTENCY-CLAIM` — admission idempotency claim + replay (mechanism FP, no HTTP route)

| Anchor | Fact ID |
|---|---|
| Mechanism class | `code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfilter` |
| Claim/replay SPI | `code-symbol/com-huawei-ascend-service-platform-idempotency-idempotencystore` |
| Test | `test/com-huawei-ascend-service-platform-idempotency-idempotencyheaderfilterit` |
| Test | `test/com-huawei-ascend-service-platform-idempotency-idempotencystorepostgresit` |

> The DSL FunctionPoint declares no `contract_op_refs` and no HTTP entry for this mechanism;
> it is an admission filter, not a route. Claim/replay storage detail (schema, durability) is
> L2 / contract-surface material.

### `FP-TENANT-CROSS-CHECK` — `JWT.tenant` claim cross-check at every tenant-scoped surface

| Anchor | Fact ID |
|---|---|
| Mechanism class | `code-symbol/com-huawei-ascend-service-platform-tenant-jwttenantclaimcrosscheck` |
| Context binding | `code-symbol/com-huawei-ascend-service-platform-tenant-tenantcontextfilter` |
| Test | `test/com-huawei-ascend-service-platform-tenant-jwttenantclaimcrosschecktest` |
| Test | `test/com-huawei-ascend-service-platform-tenant-tenantcontextfilterit` |

> Mechanism FP — the DSL declares no contract operation or HTTP entry; it is a cross-cutting
> admission check applied at the tenant-scoped surfaces.

### `FP-POSTURE-BOOT-GUARD` — fail-closed startup validation of `@RequiredConfig`

| Anchor | Fact ID |
|---|---|
| Guard class | `code-symbol/com-huawei-ascend-service-platform-posture-posturebootguard` |
| Guard method | `code-symbol/com-huawei-ascend-service-platform-posture-posturebootguard#onApplicationEvent(Lorg/springframework/boot/context/event/ApplicationReadyEvent;)V` |
| Marker annotation | `code-symbol/com-huawei-ascend-service-platform-posture-requiredconfig` |
| Test | `test/com-huawei-ascend-service-platform-posture-posturebootguardit` |

> Startup-time mechanism FP — no HTTP route or contract operation; it gates application
> readiness, not a request.

### `FP-A2A-MESSAGE-SEND` — A2A JSON-RPC `message/send` ingress

| Anchor | Status |
|---|---|
| DSL status | `design_only` (ADR-0155); anchored by `efAccessAdmission` but the entry class is not yet implemented, so there is no backing `code-symbol` / `test` / `contract-op` fact. |
| Designed contract | `docs/contracts/access-intent.v1.yaml` (`design_only`) — cited by path, not by an operation fact. |

### `FP-A2A-TASKS-CANCEL` — A2A `tasks/cancel` control ingress

| Anchor | Status |
|---|---|
| DSL status | `design_only` (ADR-0155); anchored but not yet implemented — no backing `code-symbol` / `test` / `contract-op` fact. |
| Designed contract | `docs/contracts/access-intent.v1.yaml` (`design_only`) — cited by path. |

### `FP-A2A-TASKS-RESUBSCRIBE` — A2A `tasks/resubscribe` stream ingress

| Anchor | Status |
|---|---|
| DSL status | `design_only` (ADR-0155); anchored but not yet implemented — no backing `code-symbol` / `test` / `contract-op` fact. |
| Designed contract | `docs/contracts/access-intent.v1.yaml` (`design_only`) — cited by path. |

### `FP-MQ-INBOUND` — broker to inbound consumer admission

| Anchor | Status |
|---|---|
| DSL status | `design_only` (ADR-0155); anchored but not yet implemented — no backing `code-symbol` / `test` / `contract-op` fact. |
| Designed contract | `docs/contracts/access-intent.v1.yaml` (`design_only`) — cited by path. |

## 7. Verification

> AUTHORED prose. The constraints, ArchUnit enforcers, and gate rules that hold this
> boundary. Enforcers / rules are cited as structural identifiers (not version metadata).

**Constraints / enforcers holding the boundary:**

- Enforcer E4 (`HttpEdgeMustNotImportMemorySpiTest`,
  `test/com-huawei-ascend-service-platform-architecture-httpedgemustnotimportmemoryspitest`) —
  asserts no class under `com.huawei.ascend.service.platform..` depends on
  `com.huawei.ascend.service.runtime.memory.spi..` (the HTTP edge must not embed the memory SPI).
- Enforcer E34 (`PlatformImportsOnlyRuntimePublicApiTest`,
  `test/com-huawei-ascend-service-platform-architecture-platformimportsonlyruntimepublicapitest`) —
  asserts the platform layer imports only the runtime public API
  (`service.runtime.runs.*`, `bus.spi.engine.*`, `service.runtime.posture.*`), never
  runtime-internal packages.
- Enforcer E2 (`RuntimeMustNotDependOnPlatformTest`,
  `test/com-huawei-ascend-service-runtime-architecture-runtimemustnotdependonplatformtest`) —
  asserts the reverse direction: no `service.runtime..` class imports any `service.platform..`
  class, keeping the admission edge strictly above the runtime.
- Rule G-29 (gate `check_frame_card_consistency.py`) — checks this card's frontmatter identity
  against the DSL element and resolves every fact citation above against
  `architecture/facts/generated/*.json`.

**Tests anchoring the behaviour.** The admission behaviour (Create-Run / Get-Run HTTP
contract, idempotency claim-and-replay, JWT tenant cross-check + context binding,
fail-closed posture boot guard, admitted-Run state machine) is proven by the test facts
listed in this card's frontmatter `fact_refs:` block, which the gate resolves against
`architecture/facts/generated/tests.json`. The per-test asserted behaviour — which case
each integration/unit test arms — is the behaviour catalogue and lives with those test
facts and this frame's FunctionPoint `saa.test_refs[]` in `function-points.dsl`, not as an
inventory in this L1 card.

## Cross-references

- Frames directory + Card-over-DSL rules: [`README.md`](README.md).
- This frame's DSL element (authority): [`../../../features/features.dsl`](../../../features/features.dsl) (re-tagged agent-service Layer-1 frame) and its anchor edges in [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl).
- Generated facts cited above (authority over this prose): [`../../../facts/generated/`](../../../facts/generated/).
- Collective structural map: [`../engineering-frames.md`](../engineering-frames.md).
- Deep-dive Access Layer feature inventory: [`../agent-service/features/access-layer.md`](../agent-service/features/access-layer.md).
- This frame's runtime mechanics (routes, status mapping, persistence) live in the contract surface (`docs/contracts/openapi-v1.yaml`) + the frame's L2 sink (`architecture/docs/L2/`), not in this card.
