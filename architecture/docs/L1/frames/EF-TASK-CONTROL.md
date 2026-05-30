---
# Frame Card frontmatter. The identity block is COPIED from the frame's DSL
# element efTaskControl in architecture/features/features.dsl (agent-service
# frames are re-tagged from the ADR-0138 Layer features there). Every value here
# MUST match the DSL; the gate fails a card whose frontmatter disagrees with the
# DSL element's saa.id / saa.owner / saa.status / saa.primaryPackage.
level: L1
view: development
status: shipped
authority: "ADR-0161 (Frame Card shape + Card-over-DSL); ADR-0157 (EngineeringFrame Ontology)"

# --- Identity block: COPIED from the DSL frame element (do not invent) ---
frame_id: EF-TASK-CONTROL
dsl_element: efTaskControl
owner_module: agent-service
primary_package: "com.huawei.ascend.service.runtime.runs"
source_adr: ADR-0138|ADR-0155

# --- fact_refs: every generated fact_id this card cites. Each resolves in
#     architecture/facts/generated/*.json. The gate cross-checks these. ---
fact_refs:
  - code-symbol/com-huawei-ascend-service-runtime-runs-run
  - code-symbol/com-huawei-ascend-service-runtime-runs-runstatemachine
  - code-symbol/com-huawei-ascend-service-runtime-runs-runstatus
  - code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller
  - code-symbol/com-huawei-ascend-bus-spi-engine-suspendsignal
  - code-symbol/com-huawei-ascend-service-runtime-resilience-spi-suspendreason
  - contract-op/cancelrun
  - test/com-huawei-ascend-service-platform-web-runs-runhttpcontractit
  - test/com-huawei-ascend-service-runtime-runs-runstatemachinetest
  - test/com-huawei-ascend-service-runtime-architecture-runtimemustnotdependonplatformtest

# --- participating_function_points: FunctionPoints this frame does NOT anchor but
#     references in prose as a neighbouring-frame boundary (the cross-reference escape
#     hatch, ADR-0161 §5). FP-RUN-STATE-TRANSITION is anchored by EF-SESSION-TASK-STATE;
#     this card names it in section 2 only to delegate the Run aggregate write across
#     that boundary, never to claim it. Declaring it here keeps section 6 anchored
#     strictly to this frame's own anchors edges. ---
participating_function_points:
  - FP-RUN-STATE-TRANSITION
---

# `EF-TASK-CONTROL` — Task Control Frame

> Anchors task-centric control over a Run: cancel re-authorization and the
> suspend / resume / child-spawn lifecycle, guarded by the Run state machine.
> Rationale lives in `source_adr` (ADR-0138 per-layer architecture; ADR-0155).

## 1. Identity

> COPIED from the DSL frame element. These fields MUST match the DSL byte-for-byte;
> the gate fails a card that disagrees.

| Field | Value | Source |
|---|---|---|
| Frame ID (`saa.id`) | `EF-TASK-CONTROL` | DSL element |
| DSL element | `efTaskControl` | `architecture/features/features.dsl` (re-tagged agent-service frame) |
| Owner module (`saa.owner`) | `agent-service` | DSL element |
| Status (`saa.status`) | `shipped` | DSL element |
| Primary package (`saa.primaryPackage`) | `com.huawei.ascend.service.runtime.runs` | DSL element |
| Source ADR (`saa.sourceAdr`) | `ADR-0138\|ADR-0155` | DSL element |
| Card path (`saa.cardPath`) | `architecture/docs/L1/frames/EF-TASK-CONTROL.md` | DSL element ↔ this file |

## 2. Capability Boundary

> AUTHORED prose. Package names are CITED (they must exist); the lists below are the
> human-readable boundary, not a second registry.

**Can do** — the responsibilities that live inside this frame:

- Own the Run state model in `com.huawei.ascend.service.runtime.runs`: the `Run`
  aggregate snapshot, its `RunStatus` lifecycle enum, and the `RunStateMachine`
  transition guard.
- Decide which `RunStatus` → `RunStatus` transitions are legal and which are
  terminal, and reject illegal transitions at the model boundary.
- Translate task-centric control intents — cancel, suspend, resume, child-run
  spawn — into Run lifecycle transitions, by applying a status change through the
  state-machine guard.

**Cannot do** — explicitly out of scope (handled by another frame or an L2 detail):

- Terminate the HTTP wire surface for the cancel route (route, verb, status codes,
  request parsing). That entry sits in the `platform` web layer
  (`RunController.cancel`, see section 6) and the over-the-wire behaviour is L2
  material under `architecture/docs/L2/run-http-contract/`, not restated here.
- Persist or atomically compare-and-swap Run rows. The persistence port
  `com.huawei.ascend.service.runtime.runs.spi.RunRepository` (with its
  `updateIfNotTerminal` aggregate-write) is the structural home of the Run
  aggregate's *state transition*, anchored by `EF-SESSION-TASK-STATE`
  (`FP-RUN-STATE-TRANSITION`); this frame uses that port but does not own it.
- Define the suspend taxonomy. The sealed `SuspendReason` variants live in
  `com.huawei.ascend.service.runtime.resilience.spi` and the cross-module
  `SuspendSignal` carrier lives in `com.huawei.ascend.bus.spi.engine`; this frame
  reacts to them but does not declare them.

**Owned state** — the data/state this frame is the structural home for:

- The Run lifecycle model in `com.huawei.ascend.service.runtime.runs`: the `Run`
  record (immutable snapshot with `with*` copy methods), the `RunStatus` enum, and
  the `RunStateMachine` legal-transition / terminal table.

**External dependencies** — frames / modules this frame is allowed to depend on:

- `com.huawei.ascend.service.runtime.runs.spi.RunRepository` (anchored by
  `EF-SESSION-TASK-STATE`) — the persistence port through which a guarded status
  change is committed.
- `com.huawei.ascend.bus.spi.engine` (`EF-ORCHESTRATION-SPI`) — `SuspendSignal` and
  `RunMode`, the neutral execution-model carriers a Run snapshot references.

**Forbidden dependencies** — dependencies the boundary must never take (held by an
ArchUnit enforcer; cite it in section 7):

- `com.huawei.ascend.service.platform..` — the `runtime` Run model must never import
  the `platform` web/edge layer; that inversion is asserted by
  `RuntimeMustNotDependOnPlatformTest` (section 7). The cancel HTTP entry depends on
  this model, never the reverse.

**Included / excluded packages** (this frame is a single-root package, not a cluster):

- Included: `com.huawei.ascend.service.runtime.runs` (the `saa.primaryPackage`).
- Excluded: `com.huawei.ascend.service.runtime.runs.spi` — the `RunRepository`
  persistence port; it backs the Run *aggregate state*, anchored by
  `EF-SESSION-TASK-STATE`, not this frame.

## 3. Type Inventory

> GENERATED — do not hand-edit between the markers. Rendered from
> `architecture/facts/generated/code-symbols.json`, filtered to the frame's in-boundary
> package (`com.huawei.ascend.service.runtime.runs`, exact). Every row cites its
> `code-symbol/<kebab-fqn>` fact ID. The Card generator owns this region and overwrites
> it on every re-render.

<!-- BEGIN GENERATED: type-inventory -->
| Type | Kind | Fact ID |
|---|---|---|
| `com.huawei.ascend.service.runtime.runs.Run` | record | `code-symbol/com-huawei-ascend-service-runtime-runs-run` |
| `com.huawei.ascend.service.runtime.runs.RunStateMachine` | class | `code-symbol/com-huawei-ascend-service-runtime-runs-runstatemachine` |
| `com.huawei.ascend.service.runtime.runs.RunStatus` | enum | `code-symbol/com-huawei-ascend-service-runtime-runs-runstatus` |
<!-- END GENERATED: type-inventory -->

## 4. Internal Collaboration

> GENERATED — do not hand-edit between the markers. Rendered from `code-symbols.json`:
> the structural relationships (implements / extends / references) among the in-boundary
> types listed in section 3. This is the *structural* collaboration only — runtime call
> sequences belong in the frame's L2 sink, not here.

<!-- BEGIN GENERATED: internal-collaboration -->
| From | Relationship | To |
|---|---|---|
| `com.huawei.ascend.service.runtime.runs.Run` | references | `com.huawei.ascend.service.runtime.runs.RunStatus` |
| `com.huawei.ascend.service.runtime.runs.RunStateMachine` | references | `com.huawei.ascend.service.runtime.runs.RunStatus` |
<!-- END GENERATED: internal-collaboration -->

## 5. Contracts

> AUTHORED prose. The communication contracts this frame exposes or consumes. Each
> contract operation is cited by its `contract-op/<id>` fact ID; SPI surfaces by package
> identity. Wire-field and over-the-wire mechanics are L2 — link down, do not inline.

**Exposed SPI / public surface (boundary identity):**

- `com.huawei.ascend.service.runtime.runs` — the public package that *is* this frame's
  boundary. Its public surface is the Run model: `RunStatus`
  (`code-symbol/com-huawei-ascend-service-runtime-runs-runstatus`), the `RunStateMachine`
  transition guard (`code-symbol/com-huawei-ascend-service-runtime-runs-runstatemachine`),
  and the `Run` aggregate snapshot (`code-symbol/com-huawei-ascend-service-runtime-runs-run`).

**Contract operations (OpenAPI / AsyncAPI):**

| Operation (logical) | Fact ID | Contract source |
|---|---|---|
| Cancel Run | `contract-op/cancelrun` | `docs/contracts/openapi-v1.yaml` |

> The cancel operation's concrete route, verb, status-code envelope, and header behaviour
> are the contract surface's to define (`docs/contracts/openapi-v1.yaml`, op `cancelrun`)
> with the runtime mechanics at [`../../L2/run-http-contract/`](../../L2/run-http-contract/);
> this card names the operation identity only, and delegates its transport shape downward.

**Consumed contracts** (operations this frame calls on another frame):

- None over a published contract surface. The suspend / resume / child-spawn paths are
  internal orchestration collaborations (no OpenAPI / AsyncAPI operation), realized
  through `SuspendSignal`
  (`code-symbol/com-huawei-ascend-bus-spi-engine-suspendsignal`) and the
  `SuspendReason` taxonomy
  (`code-symbol/com-huawei-ascend-service-runtime-resilience-spi-suspendreason`).

## 6. FunctionPoint Mapping

> AUTHORED prose over the frame's DSL `anchors` edges. Lists ONLY the FunctionPoints the
> DSL anchors to `efTaskControl` in `engineering-frames.dsl`
> (`fpCancelRun`, `fpSuspendResume`, `fpChildRunSpawn`). Every anchor is CITED by
> generated fact ID. Where an internal FunctionPoint declares no contract op or test in
> the DSL, none is invented here.

### `FP-CANCEL-RUN` — re-validate tenant, then transition the Run toward cancellation

| Anchor | Fact ID |
|---|---|
| Entry class (web edge) | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller` |
| Entry method | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller` :: `cancel(Ljava/lang/String;)Lorg/springframework/http/ResponseEntity;` |
| In-frame transition guard | `code-symbol/com-huawei-ascend-service-runtime-runs-runstatemachine` :: `validate(Lcom/huawei/ascend/service/runtime/runs/RunStatus;Lcom/huawei/ascend/service/runtime/runs/RunStatus;)V` |
| Contract op | `contract-op/cancelrun` |
| Test (HTTP contract) | `test/com-huawei-ascend-service-platform-web-runs-runhttpcontractit` |
| Test (state machine) | `test/com-huawei-ascend-service-runtime-runs-runstatemachinetest` |

> The entry method lives in the `platform` web layer (`EF-ACCESS-ADMISSION` territory);
> EF-TASK-CONTROL anchors the *control* behaviour — the guarded Run transition the cancel
> intent drives. The transition itself is the `RunStateMachine` guard above.

### `FP-SUSPEND-RESUME` — suspend a Run and later resume it under the transition guard

| Anchor | Fact ID |
|---|---|
| In-frame state snapshot | `code-symbol/com-huawei-ascend-service-runtime-runs-run` :: `withSuspension(Ljava/lang/String;Ljava/time/Instant;)Lcom/huawei/ascend/service/runtime/runs/Run;` |
| In-frame transition guard | `code-symbol/com-huawei-ascend-service-runtime-runs-runstatemachine` :: `validate(Lcom/huawei/ascend/service/runtime/runs/RunStatus;Lcom/huawei/ascend/service/runtime/runs/RunStatus;)V` |
| Collaborating signal carrier | `code-symbol/com-huawei-ascend-bus-spi-engine-suspendsignal` |
| Collaborating reason taxonomy | `code-symbol/com-huawei-ascend-service-runtime-resilience-spi-suspendreason` |

> Internal channel — the DSL element `fpSuspendResume` declares no contract op and no
> test ref, so none is cited. The end-to-end suspend → `SUSPENDED` → resume → `RUNNING`
> runtime sequence is L2 process detail, not restated here.

### `FP-CHILD-RUN-SPAWN` — parent suspends awaiting a child Run, then resumes on the child's terminal

| Anchor | Fact ID |
|---|---|
| In-frame parent linkage | `code-symbol/com-huawei-ascend-service-runtime-runs-run` :: `parentRunId()Ljava/util/UUID;` |
| In-frame transition guard | `code-symbol/com-huawei-ascend-service-runtime-runs-runstatemachine` :: `isTerminal(Lcom/huawei/ascend/service/runtime/runs/RunStatus;)Z` |
| Collaborating signal carrier | `code-symbol/com-huawei-ascend-bus-spi-engine-suspendsignal` |

> Internal channel — the DSL element `fpChildRunSpawn` declares no contract op and no
> test ref, so none is cited. The parent-suspend / child-execute / parent-resume
> orchestration sequence is L2 process detail.

## 7. Verification

> AUTHORED prose. The constraints, ArchUnit enforcers, and gate rules that hold this
> boundary. Each enforcer / rule is cited as a structural identifier.

**Constraints + their enforcing mechanism** (the boundary invariant, and the ArchUnit /
gate enforcer that holds it — a mechanism citation, not a behaviour catalogue):

- **Runtime-above-platform layering.** No class under `com.huawei.ascend.service.runtime..`
  imports any class under `com.huawei.ascend.service.platform..`, so the Run model stays
  independent of the web edge that invokes it. Enforced by ArchUnit
  `RuntimeMustNotDependOnPlatformTest`
  (`test/com-huawei-ascend-service-runtime-architecture-runtimemustnotdependonplatformtest`).
- **Card-over-DSL parity.** This card's identity block matches the `efTaskControl` DSL
  element and every cited fact ID resolves in the generated facts. Enforced by Rule `G-29`
  (enforcer `E196`, `gate/check_architecture_sync.sh#frame_card_consistency`).

**Tests anchoring the behaviour.** The control behaviour (the transition guard the cancel /
suspend / resume / child-spawn intents drive, and the cancel operation's contract
conformance) is proven by the test facts in this card's frontmatter `fact_refs:` block,
which the gate resolves against `architecture/facts/generated/tests.json`. The per-test
asserted behaviour is the behaviour catalogue and lives with those test facts and this
frame's FunctionPoint `saa.test_refs[]` in `function-points.dsl`, not as an inventory in
this L1 card.

## Cross-references

- Frames directory + Card-over-DSL rules: [`README.md`](README.md).
- This frame's DSL element (authority): [`../../../features/features.dsl`](../../../features/features.dsl) (agent-service frame element `efTaskControl`); its `anchors` edges: [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl).
- Generated facts cited above (authority over this prose): [`../../../facts/generated/`](../../../facts/generated/).
- Collective structural map: [`../engineering-frames.md`](../engineering-frames.md).
- This frame's L2 detail sink (cancel-route runtime mechanics): [`../../L2/run-http-contract/`](../../L2/run-http-contract/).
