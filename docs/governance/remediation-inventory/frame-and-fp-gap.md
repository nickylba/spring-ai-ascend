# Frame & FunctionPoint Gap Inventory (Wave W0 — Advisory)

Date: 2026-05-30 (UTC)
Status: advisory baseline (not authority)
Scope: enumerate every EngineeringFrame and FunctionPoint declared in the
Structurizr authoring DSL, and record — per node — which lack a Frame Card
and which lack an L2 detailed-design / method-and-test anchor.
Authority chain (read top-down): ADR -> `architecture/profile/*` ->
`architecture/workspace.dsl` -> `architecture/features/engineering-frames.dsl`
(+ frame *elements* re-tagged in `architecture/features/features.dsl`) ->
`architecture/facts/generated/*.json` -> Frame Card -> gate ->
`docs/governance/architecture-status.yaml`.

This file is a READABLE INTERPRETATION layer. It invents no IDs, owners,
statuses, or relationship edges; every count and every ID below is copied
from the DSL named above. Where this document and the DSL disagree, the DSL
wins; where the DSL and generated facts disagree, generated facts win.

## Source basis

- Frame element declarations + `contains` / `anchors` / `traverses` edges:
  `architecture/features/engineering-frames.dsl` (11 frame elements) and
  `architecture/features/features.dsl` (6 agent-service frame elements
  re-tagged from the ADR-0138 Layer features; their containment/anchor edges
  live back in `engineering-frames.dsl`).
- FunctionPoint element declarations + `implements` edges:
  `architecture/features/function-points.dsl` (14 elements) and the four A2A /
  MQ elements appended later in the same file (18 total).
- Collective frame narrative: `architecture/docs/L1/engineering-frames.md`.
- Profile shape for `SAA EngineeringFrame`:
  `architecture/profile/required-properties.yaml:60` (currently requires only
  `saa.owner` + `saa.sourceAdr`).

## Count reconciliation (vs. the W0 brief's "16 frames + ~30 FunctionPoints")

The W0 planning brief cited "16 EngineeringFrames + ~30 FunctionPoints" as an
approximate starting figure. The authoritative DSL ground truth as of this
inventory is:

| Node kind | Authoritative DSL count | Brief's approximate figure |
|---|---|---|
| EngineeringFrame | **17** | 16 |
| FunctionPoint | **18** | ~30 |

The frame count is 17, not 16: 11 elements in `engineering-frames.dsl` plus 6
re-tagged agent-service elements in `features.dsl`. This matches the structural
map table in `architecture/docs/L1/engineering-frames.md` exactly
(agent-service 6 + agent-bus 5 + agent-execution-engine 1 + agent-middleware 2
+ agent-client 1 + agent-evolve 1 + graphmemory-starter 1 = 17). The "~30" FP
figure overcounts: the curated seed inventory is 18 (one prior element,
`FP-LIST-RUNS`, was deliberately removed on 2026-05-28 because no `GET /v1/runs`
handler or `listRuns` OpenAPI operation exists; it is not re-listed here).

## Headline gaps

1. **No Frame Cards exist for any frame.** The
   `architecture/docs/L1/frames/` directory does not exist. The only
   frame-level prose is the single collective narrative
   `architecture/docs/L1/engineering-frames.md`. Gap = 17 / 17 frames lack a
   per-frame Frame Card.
2. **The binding property does not exist yet.** No DSL element declares
   `saa.cardPath` or `saa.primaryPackage`, and
   `required-properties.yaml#SAA EngineeringFrame` does not require them. So
   there is no machine-checkable DSL -> card link to gate against today.
3. **No L2 detailed-design documents exist.** `architecture/docs/L2/` ships
   only its scaffold `README.md`; zero `docs/L2/<feature-slug>/...` files have
   landed. Every runtime detail the verified verdict flagged as "leaked" into
   L0/L1 (method call chains, runtime sequences, SQL/RLS/GUC, HTTP
   status/route-verb/header behaviour, filter ordering, wire formats, method
   signatures, test-class inventories) currently has no L2 home to migrate to.
4. **Method/test/contract anchoring is sparse at the FunctionPoint layer.**
   Only 3 of 18 FunctionPoints carry all three hard-evidence refs
   (`saa.code_entrypoint_refs` + `saa.test_refs` + `saa.contract_op_refs`); 11
   shipped "internal" FunctionPoints carry none of the three; 4 design-only
   FunctionPoints carry code + contract refs but no test refs.

## EngineeringFrame inventory (17) — Frame-Card & L2 gap

Legend:
- **Card** = a per-frame `architecture/docs/L1/frames/<frame-id>.md` Frame Card.
- **L2 spec** = an `architecture/docs/L2/...` detailed-design document, or an
  equivalent frame-scoped technical-design doc carrying method/sequence/wire
  detail. The agent-service "proto-L2" column flags the six per-layer feature
  inventories at `architecture/docs/L1/agent-service/features/*.md` — these are
  L1 feature-inventory docs (front-matter `level: L1`, `status: proposed` /
  `design_only`), NOT Frame Cards and NOT L2 specs; they are the closest
  existing material but do not satisfy either gap.
- **Anchors** = count of `anchors` edges from this frame to a FunctionPoint.

### agent-bus (5 frames)

| Frame ID | DSL element | Status | Anchors | Has Card | Has L2 spec | Notes |
|---|---|---|---|---|---|---|
| EF-INGRESS-GATEWAY | efIngressGateway | shipped | 1 (FP-INGRESS-ENVELOPE) | NO | NO | No frame-scoped prose beyond the agent-bus L1 module docs. |
| EF-S2C-TRANSPORT | efS2cTransport | shipped | 1 (FP-S2C-CALLBACK) | NO | NO | S2C round-trip sequence + envelope wire detail currently has no L2 home. |
| EF-CHANNEL-ISOLATION | efChannelIsolation | design_only | 0 | NO | NO | Anchors no FP yet (allowed for design_only under Rule G-23). |
| EF-ENGINE-PORT | efEnginePort | design_only | 0 | NO | NO | Pilot-card candidate per the correction checklist (Phase 2). Anchors no FP yet. |
| EF-ORCHESTRATION-SPI | efOrchestrationSpi | design_only | 0 | NO | NO | Re-homed from agent-execution-engine to agent-bus per ADR-0158. Anchors no FP yet. |

### agent-service (6 frames; elements re-tagged in features.dsl)

| Frame ID | DSL element | Status | Anchors | Has Card | Has L2 spec | Notes |
|---|---|---|---|---|---|---|
| EF-ACCESS-ADMISSION | efAccessAdmission | shipped | 9 (FP-CREATE-RUN, FP-GET-RUN-STATUS, FP-IDEMPOTENCY-CLAIM, FP-TENANT-CROSS-CHECK, FP-POSTURE-BOOT-GUARD, FP-A2A-MESSAGE-SEND, FP-A2A-TASKS-CANCEL, FP-A2A-TASKS-RESUBSCRIBE, FP-MQ-INBOUND) | NO | proto-L2 only (features/access-layer.md, L1 status=proposed) | Highest-fan-out frame; pilot-card candidate per checklist Phase 2. |
| EF-SESSION-TASK-STATE | efSessionTaskState | shipped | 1 (FP-RUN-STATE-TRANSITION) | NO | proto-L2 only (features/session-task-manager.md, L1 status=proposed) | Owns Run/Session/Task aggregate state. |
| EF-TASK-CONTROL | efTaskControl | shipped | 3 (FP-CANCEL-RUN, FP-SUSPEND-RESUME, FP-CHILD-RUN-SPAWN) | NO | proto-L2 only (features/task-centric-control.md, L1 status=proposed) | Cancel re-auth + suspend/resume control loop. |
| EF-ENGINE-DISPATCH | efEngineDispatch | design_only | 0 | NO | proto-L2 only (features/engine-dispatch-execution.md, L1 status=proposed) | Service-side engine adapter dispatch; anchors no FP yet. Distinct from agent-execution-engine's EF-ENGINE-REGISTRY. |
| EF-INTERNAL-EVENT-QUEUE | efInternalEventQueue | design_only | 0 | NO | proto-L2 only (features/internal-event-queue.md, L1 status=design_only) | No `service.queue/` code home exists yet; anchors no FP. |
| EF-TRANSLATION-INTERCEPT | efTranslationIntercept | design_only | 0 | NO | proto-L2 only (features/translation-tool-intercept.md, L1 status=proposed) | Model/tool translation hooks; anchors no FP yet. |

### agent-execution-engine (1 frame)

| Frame ID | DSL element | Status | Anchors | Has Card | Has L2 spec | Notes |
|---|---|---|---|---|---|---|
| EF-ENGINE-REGISTRY | efEngineRegistry | shipped | 1 (FP-ENGINE-DISPATCH) | NO | NO | Engine contract surface (EngineRegistry strict matching). |

### agent-middleware (2 frames)

| Frame ID | DSL element | Status | Anchors | Has Card | Has L2 spec | Notes |
|---|---|---|---|---|---|---|
| EF-HOOK-SURFACE | efHookSurface | shipped | 1 (FP-HOOK-DISPATCH) | NO | NO | Runtime middleware hook dispatch. |
| EF-CAPABILITY-SPI | efCapabilitySpi | design_only | 0 | NO | NO | Cross-cutting capability SPI families; anchors no FP yet. |

### agent-client (1 frame, skeleton)

| Frame ID | DSL element | Status | Anchors | Has Card | Has L2 spec | Notes |
|---|---|---|---|---|---|---|
| EF-CLIENT-INGRESS-ADAPTER | efClientIngressAdapter | design_only | 0 | NO | NO | Edge SDK skeleton (W3+); anchors no FP yet. |

### agent-evolve (1 frame, skeleton)

| Frame ID | DSL element | Status | Anchors | Has Card | Has L2 spec | Notes |
|---|---|---|---|---|---|---|
| EF-EVOLUTION-EXPORT | efEvolutionExport | design_only | 0 | NO | NO | RunEvent/trajectory export skeleton; anchors no FP yet. |

### spring-ai-ascend-graphmemory-starter (1 frame)

| Frame ID | DSL element | Status | Anchors | Has Card | Has L2 spec | Notes |
|---|---|---|---|---|---|---|
| EF-GRAPHMEMORY-AUTOCONFIG | efGraphmemoryAutoconfig | design_only | 1 (FP-GRAPH-MEMORY-STORE) | NO | NO | Starter auto-config wiring; design_only but anchors one FP. |

### Frame-gap summary

- **Frame Cards present: 0 / 17.** Every frame lacks a Frame Card.
- **`saa.cardPath` declared: 0 / 17.** Every frame lacks the binding property.
- **`saa.primaryPackage` declared: 0 / 17.** No shipped frame declares its
  package anchor (7 frames are shipped: EF-INGRESS-GATEWAY, EF-S2C-TRANSPORT,
  EF-ACCESS-ADMISSION, EF-SESSION-TASK-STATE, EF-TASK-CONTROL,
  EF-ENGINE-REGISTRY, EF-HOOK-SURFACE).
- **L2 spec present: 0 / 17.** No frame has an `architecture/docs/L2/...`
  detailed design.
- **Proto-L2 material present: 6 / 17** (the agent-service per-layer feature
  inventories), but these are L1 docs and satisfy neither the Frame-Card nor
  the L2-spec requirement.
- **Frames anchoring zero FunctionPoints: 9** — EF-CHANNEL-ISOLATION,
  EF-ENGINE-PORT, EF-ORCHESTRATION-SPI, EF-ENGINE-DISPATCH,
  EF-INTERNAL-EVENT-QUEUE, EF-TRANSLATION-INTERCEPT, EF-CAPABILITY-SPI,
  EF-CLIENT-INGRESS-ADAPTER, EF-EVOLUTION-EXPORT. All nine are `design_only`,
  so Rule G-23 (shipped frame MUST anchor >=1 FunctionPoint) is satisfied
  today; promotion of any of them to `shipped` requires anchoring a FP first.

## FunctionPoint inventory (18) — method/test/contract anchor gap

Hard-evidence columns reflect the presence of the `saa.code_entrypoint_refs`,
`saa.test_refs`, and `saa.contract_op_refs` (or `saa.contract_refs`) properties
on each element. "Primary frame" is the frame that holds the `anchors` edge to
the FunctionPoint.

| FP ID | Status | Channel | Primary frame | code_ref | test_ref | contract_ref |
|---|---|---|---|---|---|---|
| FP-CREATE-RUN | shipped | http | EF-ACCESS-ADMISSION | YES | YES | YES |
| FP-CANCEL-RUN | shipped | http | EF-TASK-CONTROL | YES | YES | YES |
| FP-GET-RUN-STATUS | shipped | http | EF-ACCESS-ADMISSION | YES | YES | YES |
| FP-INGRESS-ENVELOPE | shipped | internal | EF-INGRESS-GATEWAY | no | no | no |
| FP-S2C-CALLBACK | shipped | internal | EF-S2C-TRANSPORT | no | no | no |
| FP-RUN-STATE-TRANSITION | shipped | internal | EF-SESSION-TASK-STATE | no | no | no |
| FP-SUSPEND-RESUME | shipped | internal | EF-TASK-CONTROL | no | no | no |
| FP-CHILD-RUN-SPAWN | shipped | internal | EF-TASK-CONTROL | no | no | no |
| FP-IDEMPOTENCY-CLAIM | shipped | internal | EF-ACCESS-ADMISSION | no | no | no |
| FP-TENANT-CROSS-CHECK | shipped | internal | EF-ACCESS-ADMISSION | no | no | no |
| FP-POSTURE-BOOT-GUARD | shipped | internal | EF-ACCESS-ADMISSION | no | no | no |
| FP-GRAPH-MEMORY-STORE | shipped | internal | EF-GRAPHMEMORY-AUTOCONFIG | no | no | no |
| FP-ENGINE-DISPATCH | shipped | internal | EF-ENGINE-REGISTRY | no | no | no |
| FP-HOOK-DISPATCH | shipped | internal | EF-HOOK-SURFACE | no | no | no |
| FP-A2A-MESSAGE-SEND | design_only | http | EF-ACCESS-ADMISSION | YES | no | YES |
| FP-A2A-TASKS-CANCEL | design_only | http | EF-ACCESS-ADMISSION | YES | no | YES |
| FP-A2A-TASKS-RESUBSCRIBE | design_only | http | EF-ACCESS-ADMISSION | YES | no | YES |
| FP-MQ-INBOUND | design_only | spi | EF-ACCESS-ADMISSION | YES | no | YES |

### FunctionPoint-gap summary

- **Fully anchored (code + test + contract): 3 / 18** — FP-CREATE-RUN,
  FP-CANCEL-RUN, FP-GET-RUN-STATUS (the three shipped HTTP Run-lifecycle verbs).
- **Shipped but lacking ALL three hard-evidence refs: 11 / 18** — every
  shipped `internal`-channel FunctionPoint (FP-INGRESS-ENVELOPE,
  FP-S2C-CALLBACK, FP-RUN-STATE-TRANSITION, FP-SUSPEND-RESUME,
  FP-CHILD-RUN-SPAWN, FP-IDEMPOTENCY-CLAIM, FP-TENANT-CROSS-CHECK,
  FP-POSTURE-BOOT-GUARD, FP-GRAPH-MEMORY-STORE, FP-ENGINE-DISPATCH,
  FP-HOOK-DISPATCH). These name a behaviour and an owning frame but provide no
  method/test/contract anchor for a worker to navigate to. This is the largest
  FunctionPoint-layer remediation surface.
- **Design-only with code + contract but no test ref: 4 / 18** — the A2A and MQ
  ingress FunctionPoints (FP-A2A-MESSAGE-SEND, FP-A2A-TASKS-CANCEL,
  FP-A2A-TASKS-RESUBSCRIBE, FP-MQ-INBOUND). Each cites a code entrypoint and a
  contract operation but no test (consistent with `design_only`).
- **Contract-op refs that do not resolve to a generated `contract-op/<id>`
  fact ID:** the 4 design-only FPs reference contract operations in the
  `<file>.yaml#operation=<NAME>` form (e.g.
  `access-intent.v1.yaml#operation=SUBMIT`), not the canonical
  `contract-op/<kebab-op-id>` fact-ID form used by the 3 shipped HTTP FPs
  (`contract-op/createrun`). This ref-format inconsistency is a candidate
  follow-up for the contract-surface reconciliation step (not owned here).

## What this means for the remediation plan (advisory, non-binding)

1. The Frame-Card layer is greenfield: there is no `frames/` directory, no
   `_template.md`, no `README.md`, and no DSL `saa.cardPath` property to bind
   against. The pilot (per the correction checklist Phase 2) should target a
   high-fan-out shipped frame — EF-ACCESS-ADMISSION (9 anchors) — and/or the
   design-only boundary frame EF-ENGINE-PORT.
2. The L2 layer is also greenfield: `docs/L2/` is empty. The "leaked" runtime
   detail identified in the verified verdict (method chains, sequences,
   SQL/RLS/GUC, HTTP status/verb/header behaviour, filter ordering, wire
   formats, method signatures, test-class inventories) has no destination
   today; standing up at least one L2 slug is a prerequisite for the migration.
3. The FunctionPoint-anchor gap (11 shipped FPs with no method/test/contract
   ref) is independent of the Frame-Card work and is the densest single
   evidence gap. Closing it draws on `architecture/facts/generated/*.json`
   (`code-symbols.json`, `tests.json`, `contract-surfaces.json`) for fact IDs.

> Reconcile-step ownership note: this advisory file does NOT touch any shared
> authority surface (`architecture-status.yaml`, README, `gate/README`,
> `enforcers.yaml`, `recurring-defect-families.yaml`, `profile/*`,
> `engineering-frames.dsl`, or any `architecture/facts/generated/*` /
> `architecture/generated/*`). All counts here are observations to be ratified
> by the reconcile step against the live gate and generated facts.
