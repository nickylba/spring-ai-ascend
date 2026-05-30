---
level: L0
view: scenarios
status: advisory
governance_infra: true
walked: 2026-05-30
walker: governance/progressive-learning-curve-remediation Wave W29
authority_refs: [ADR-0020, ADR-0138, ADR-0154, ADR-0156, ADR-0157, ADR-0159, ADR-0160, ADR-0161]
verdict_source: "Adjudicated verdict — L0/L1 leak critique TRUE (see layer-purity-scan.md §0); this walk proves the remediated chain is mechanically traversable"
companion: admission-criteria.md
subject_feature: FEAT-RUN-LIFECYCLE-CONTROL
subject_function_point: FP-CREATE-RUN
subject_frame: EF-ACCESS-ADMISSION
---

# Acceptance-Bar Walk — `FP-CREATE-RUN` (Frame `EF-ACCESS-ADMISSION`)

> **What this is.** A single hop-by-hop traversal of the implementation-admission
> chain ([`admission-criteria.md`](admission-criteria.md)) for **one shipped
> feature**, proving the chain is **mechanically traversable**: that a reader (or
> an AI agent) landing on the structural workspace can navigate
> `workspace.dsl -> EngineeringFrame -> Frame Card -> generated facts ->
> FunctionPoint -> Contract -> gate` and find **every** hop resolved by a live
> authority surface, with every fact ID resolving in the generated facts. The
> subject is `FP-CREATE-RUN` (`POST /v1/runs`), anchored by the highest-fan-out
> shipped frame `EF-ACCESS-ADMISSION` (9 anchors), demanded by Feature
> `FEAT-RUN-LIFECYCLE-CONTROL` and Requirement `REQ-001`.
>
> **What this is NOT.** An authority surface, an ADR, or a gate. It invents no
> IDs and no relationships; every ID, edge, property, and fact ref below is
> copied verbatim from the live tree at walk time. Where this walk and a DSL
> element disagree, the DSL wins; where the DSL and a generated fact disagree,
> the fact wins (ADR-0154 cascade). The walk records one **honest residual** (a
> tolerated, dated DECISION-axis baseline row) rather than papering over it. This
> file changes no shared authority surface (§5).

---

## §0 — The subject, and why it was chosen

`FP-CREATE-RUN` is the canonical proof subject because it is the densest fully
wired node on the value/structure/evidence axes at once:

- it is `saa.status: shipped` with all three hard-evidence ref kinds populated
  (`saa.code_entrypoint_refs` + `saa.test_refs` + `saa.contract_op_refs`);
- its frame `EF-ACCESS-ADMISSION` is the highest-fan-out shipped frame (anchors
  9 FunctionPoints) and carries a complete Frame Card with `saa.cardPath` +
  `saa.primaryPackage`;
- its Feature `FEAT-RUN-LIFECYCLE-CONTROL` and Requirement `REQ-001` close the
  full VALUE axis `ProductClaim -> Requirement -> Feature -> FunctionPoint`.

The walk therefore exercises every link the gate checks, end to end.

---

## §1 — The walk (hop by hop)

Each hop names the **edge/property traversed**, the **authority surface** it
lives in, and the **landed node**. Every ID is copied from the live tree.

### Hop 0 — Land on the structural workspace

- **Surface.** `architecture/workspace.dsl`.
- **Node.** Container `agentService` (`= container "agent-service" … "SAA Module"`,
  `architecture/workspace.dsl:65`, `saa.owner = agent-service`).
- **Note.** `workspace.dsl` `!include`s the feature fragments
  (`features/function-points.dsl`, `features/features.dsl`,
  `features/verification.dsl` at lines 122-125). The frame `anchors`/`contains`
  edges live in `features/engineering-frames.dsl`, which the gate helpers merge
  with the included fragments before reading the map (Rule 149 / G-32 / E199
  states all three DSL files are merged before the map is read). So the structure
  axis is fully resolvable from the workspace + its fragments.

### Hop 1 — Module → EngineeringFrame (STRUCTURE, `contains`)

- **Edge.** `genModule_agent_service -> efAccessAdmission`, `saa.rel = contains`
  (`architecture/features/engineering-frames.dsl:271`).
- **Node.** Frame element `efAccessAdmission` / `saa.id = EF-ACCESS-ADMISSION`
  (`architecture/features/features.dsl:356-379`; re-tagged agent-service frame
  per ADR-0157). Carries `saa.owner = agent-service`, `saa.status = shipped`,
  `saa.sourceAdr = ADR-0138|ADR-0155`,
  `saa.cardPath = architecture/docs/L1/frames/EF-ACCESS-ADMISSION.md`,
  `saa.primaryPackage = com.huawei.ascend.service.platform.web`.
- **Resolved?** YES. Exactly one Module `contains` the frame; the frame is
  `shipped` and declares its card + primary package.

### Hop 2 — Frame → Frame Card (`saa.cardPath`)

- **Property.** `saa.cardPath = architecture/docs/L1/frames/EF-ACCESS-ADMISSION.md`
  (`architecture/features/features.dsl:366`).
- **Node.** `architecture/docs/L1/frames/EF-ACCESS-ADMISSION.md` (exists on disk).
  Frontmatter identity block copies the DSL byte-for-byte: `frame_id:
  EF-ACCESS-ADMISSION`, `dsl_element: efAccessAdmission`, `owner_module:
  agent-service`, `status: shipped`, `primary_package:
  com.huawei.ascend.service.platform.web`, `source_adr: ADR-0138|ADR-0155`.
- **Resolved?** YES. The card exists at `saa.cardPath` and its identity matches
  the DSL element. The card is the **lowest interpretation tier** (ADR-0161
  Card-over-DSL): it invents nothing and cites every fact.
- **Gate.** Rule 146 / G-29 / E196 fails a card whose identity disagrees with the
  DSL or whose fact citations do not resolve. (See Hop 4 for the fact-resolution
  check.)

### Hop 3 — Frame → FunctionPoint (STRUCTURE, `anchors`)

- **Edge.** `efAccessAdmission -> fpCreateRun`, `saa.rel = anchors`
  (`architecture/features/engineering-frames.dsl:276`). The frame anchors 8 more
  (`fpGetRunStatus`, `fpIdempotencyClaim`, `fpTenantCrossCheck`,
  `fpPostureBootGuard`, `fpA2aMessageSend`, `fpA2aTasksCancel`,
  `fpA2aTasksResubscribe`, `fpMqInbound`) — 9 anchors total.
- **Node.** FunctionPoint element `fpCreateRun` / `saa.id = FP-CREATE-RUN`
  (`architecture/features/function-points.dsl:21-38`). Carries
  `saa.status = shipped`, `saa.owner = agent-service`,
  `saa.sourceAdr = ADR-0020`, `saa.requirement = REQ-001`, `saa.channel = http`,
  `saa.trigger = HTTP POST /v1/runs`.
- **Resolved?** YES — and **exactly one** frame anchors `fpCreateRun` (no
  `STRUCTURE-MULTI-ANCHOR`). Owning-module `implements` edge present:
  `agentService -> fpCreateRun`, `saa.rel = implements`
  (`architecture/features/function-points.dsl:268`), so no `STRUCTURE-NO-MODULE`.
- **Ownership invariant.** The `anchors` source is an EngineeringFrame
  (`efAccessAdmission`), not a Feature/Claim/Requirement — so no
  `OWNERSHIP-NONFRAME-ANCHOR` (Rule 147 / G-30 / E197).

### Hop 4 — FunctionPoint → Contract + Facts + Tests (EVIDENCE)

The FunctionPoint's three hard-evidence refs each resolve in the authoritative
generated facts (verified present at walk time):

| Ref kind | Value on `fpCreateRun` | Resolves in | Confirmed |
|---|---|---|---|
| `saa.code_entrypoint_refs` | `agent-service/…/web/runs/RunController.java#create` | fact `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller` (`code-symbols.json`) | YES |
| `saa.contract_op_refs` | `contract-op/createrun` | fact `contract-op/createrun` (`contract-surfaces.json:27`) | YES |
| `saa.test_refs` | `…web.runs.RunHttpContractIT` \| `…runtime.runs.RunStateMachineTest` | fact `test/com-huawei-ascend-service-platform-web-runs-runhttpcontractit` (`tests.json`) | YES |

- **Frame-Card cross-check.** The `EF-ACCESS-ADMISSION` card `fact_refs`
  independently cite the same IDs —
  `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller`,
  `contract-op/createrun`,
  `test/com-huawei-ascend-service-platform-web-runs-runhttpcontractit` — all of
  which resolve. So the card carries no unresolved citation (Rule 146 / G-29 /
  E196 clean for these refs).
- **Contract surface.** `contract-op/createrun` is the OpenAPI operation for
  `POST /v1/runs`; the runtime detail the verdict ruled out of L0/L1 (status
  matrix, `X-Tenant-Id` header behaviour, idempotency) lives at the contract +
  the `L2/run-http-contract/` sink, not in the Frame Card or the L1 module prose
  (no-loss-audit §1.2, §2). So no `EVIDENCE-NO-CONTRACT` / `EVIDENCE-NO-FACT` /
  `EVIDENCE-NO-TEST`.

### Hop 5 — FunctionPoint → Verification (`verifies`)

- **Edge.** `testRunControllerCreateIT -> fpCreateRun`, `saa.rel = verifies`
  (`architecture/features/verification.dsl:99`). The test element
  `testRunControllerCreateIT` (`saa.id = TEST-RUNCONTROLLER-CREATE-IT`) names
  `saa.sourceFile =
  agent-service/src/test/java/com/huawei/ascend/service/platform/web/runs/RunHttpContractIT.java`.
- **Resolved?** YES. A `verifies` edge links a real test to the FunctionPoint,
  and that test resolves as a generated fact (Hop 4). Gate ref defaults to the
  always-on architecture-sync gate (`saa.gate_refs` not emptied), so no
  `EVIDENCE-NO-GATE`.

### Hop 6 — VALUE axis (Requirement + Feature back to ProductClaim)

The value axis closes the loop `ProductClaim -> Requirement -> Feature ->
FunctionPoint`:

- **Requirement.** `REQ-001` in `product/requirements.yaml:67-95` —
  `source_claim: [PC-001, PC-003]`, `feature: FEAT-RUN-LIFECYCLE-CONTROL`,
  `function_points: [FP-CREATE-RUN, FP-CANCEL-RUN, FP-GET-RUN-STATUS,
  FP-RUN-STATE-TRANSITION]`, `source_adr: ADR-0020`, `status: accepted`, with
  fact-backed `acceptance_criteria` (cites `RunHttpContractIT` +
  `RunStateMachineTest`).
- **Feature.** `featRunLifecycleControl` / `saa.id = FEAT-RUN-LIFECYCLE-CONTROL`
  (`architecture/features/features.dsl:23-…`), `saa.productClaim = PC-001|PC-003`,
  `saa.requirement = REQ-001`. The Feature `requires` the FunctionPoint:
  `featRunLifecycleControl -> fpCreateRun`
  (`architecture/features/features.dsl:265`).
- **Resolved?** YES. ≥1 Feature `requires` `fpCreateRun` (no `VALUE-NO-FEATURE`),
  the Feature names `REQ-001`, and `REQ-001` names `PC-001`/`PC-003` which resolve
  in `product/claims.yaml`.
- **Derived traverse (no ownership).** `featRunLifecycleControl ->
  efAccessAdmission`, `saa.rel = traverses`
  (`architecture/features/engineering-frames.dsl:376`) is **derivable**: the
  Feature `requires fpCreateRun` and the Frame `anchors fpCreateRun` — they share
  a FunctionPoint, so the traverse is legal (Rule 149 / G-32 / E199, no
  `NON-DERIVED-TRAVERSE`). The Feature only `traverses` the Frame; it never
  `contains`/`anchors`/`owns` it (no `FEATURE-OWNS-FRAME`).

### Hop 7 — DECISION axis (FunctionPoint → source ADR)

- **Property.** `saa.sourceAdr = ADR-0020` (on both `fpCreateRun` and
  `featRunLifecycleControl` / `REQ-001`).
- **Fact resolution.** `ADR-0020` resolves in
  `architecture/facts/generated/adrs.json` (present).
- **Normalized view.** `docs/adr/normalized/ADR-0020.yaml` is **ABSENT** (the
  normalized-view corpus currently spans `ADR-0068` upward — 90 views, lowest is
  `ADR-0068`; `ADR-0020` predates it). This is the single residual on the walk —
  see §2.

---

## §2 — The one honest residual (tolerated, dated, non-blocking)

The DECISION axis for `FP-CREATE-RUN` is the only hop that does not fully
resolve: `saa.sourceAdr = ADR-0020` resolves as a generated fact (`adrs.json`)
but has **no normalized view** at `docs/adr/normalized/ADR-0020.yaml`. Under Rule
147 / G-30 / E197 this is a `DECISION-NO-NORMALIZED-VIEW` finding.

It does **not** block, and the chain is still admissible, because the finding is
an explicit, **dated baseline row** already registered in the gate's allow-list:

- **Baseline row.** `docs/governance/feature-readiness-baseline.yaml`,
  `id: FRB-create-run-no-normalized-view`, `fp_id: FP-CREATE-RUN`,
  `axis: decision`, `code: DECISION-NO-NORMALIZED-VIEW`,
  `sunset_date: 2026-07-31`. Its note records: *"The FunctionPoint discharges its
  full evidence axis (contract+fact+test refs all wired); only the decision-axis
  normalized view is missing. Discharge by authoring the ADR-0020 normalized view
  in a citeable state."*
- **Gate behaviour.** While in scope the finding is reported `BASELINED` and never
  blocks; an **expired** row would block; `full-blocking` (the terminal rung)
  ignores the baseline entirely. The OWNERSHIP invariant is **not** baselined
  here (it is fully satisfied — see Hop 3).

So the walk's verdict is: **every structure / value / evidence link resolves
cleanly; the single DECISION-axis gap is a known, dated, tolerated residual whose
discharge (author `docs/adr/normalized/ADR-0020.yaml`) is named.** Recording it
honestly — rather than hiding it — is the point of a no-loss acceptance walk.

---

## §3 — Per-axis verdict table

| Axis | Link walked | Authority surface | Resolved? | Finding |
|---|---|---|---|---|
| STRUCTURE | `genModule_agent_service --contains--> efAccessAdmission` | `engineering-frames.dsl:271` | YES | — |
| STRUCTURE | `efAccessAdmission --anchors--> fpCreateRun` (exactly one frame) | `engineering-frames.dsl:276` | YES | no `STRUCTURE-NO/MULTI-ANCHOR` |
| STRUCTURE | `agentService --implements--> fpCreateRun` | `function-points.dsl:268` | YES | no `STRUCTURE-NO-MODULE` |
| STRUCTURE | Frame → Frame Card via `saa.cardPath` (identity matches) | `features.dsl:366` + card frontmatter | YES | Rule 146/G-29 clean |
| VALUE | `featRunLifecycleControl --requires--> fpCreateRun` | `features.dsl:265` | YES | no `VALUE-NO-FEATURE` |
| VALUE | Feature `saa.requirement REQ-001`; `REQ-001 source_claim PC-001/PC-003` | `requirements.yaml:67`, `claims.yaml` | YES | — |
| VALUE | `featRunLifecycleControl --traverses--> efAccessAdmission` (derived, shared FP) | `engineering-frames.dsl:376` | YES | no `FEATURE-OWNS-FRAME` / `NON-DERIVED-TRAVERSE` |
| EVIDENCE | `saa.code_entrypoint_refs` → `code-symbol/…-runcontroller` | `code-symbols.json` | YES | no `EVIDENCE-NO/UNRESOLVED-FACT` |
| EVIDENCE | `saa.contract_op_refs` → `contract-op/createrun` | `contract-surfaces.json:27` | YES | no `EVIDENCE-NO-CONTRACT` |
| EVIDENCE | `saa.test_refs` → `test/…-runhttpcontractit` | `tests.json` | YES | no `EVIDENCE-NO-TEST` |
| EVIDENCE | `testRunControllerCreateIT --verifies--> fpCreateRun` + default gate ref | `verification.dsl:99` | YES | no `EVIDENCE-NO-GATE` |
| DECISION | `saa.sourceAdr ADR-0020` in `adrs.json` | `adrs.json` | YES (fact) | — |
| DECISION | normalized view `docs/adr/normalized/ADR-0020.yaml` | (absent) | **NO** | `DECISION-NO-NORMALIZED-VIEW` — **BASELINED**, sunset 2026-07-31 |

**Walk verdict.** 12 / 13 links resolve cleanly; the 1 remaining (DECISION
normalized view) is a known, dated, tolerated baseline residual. The chain
`workspace.dsl -> Frame -> Frame Card -> facts -> FunctionPoint -> Contract ->
gate` is **mechanically traversable end-to-end** for `FP-CREATE-RUN`.

---

## §4 — Reproduce the walk (commands)

Run on Linux/WSL per Rule G-7 from the repo root. These are read-only
verifications of the hops above; they assert nothing and edit nothing.

```bash
# Hop 1-3: Module->Frame->FunctionPoint structure edges
grep -n "genModule_agent_service -> efAccessAdmission" architecture/features/engineering-frames.dsl
grep -n "efAccessAdmission -> fpCreateRun"            architecture/features/engineering-frames.dsl
grep -n "agentService -> fpCreateRun"                 architecture/features/function-points.dsl

# Hop 2: Frame -> Frame Card binding + the card exists with matching identity
grep -n "saa.cardPath"                                architecture/features/features.dsl | grep EF-ACCESS-ADMISSION
test -f architecture/docs/L1/frames/EF-ACCESS-ADMISSION.md && echo "card present"

# Hop 4: the three evidence fact IDs resolve in the generated facts
grep -c '"code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller"' architecture/facts/generated/code-symbols.json
grep -c '"contract-op/createrun"'                                                 architecture/facts/generated/contract-surfaces.json
grep -c '"test/com-huawei-ascend-service-platform-web-runs-runhttpcontractit"'    architecture/facts/generated/tests.json

# Hop 5: the verifies edge
grep -n "testRunControllerCreateIT -> fpCreateRun"    architecture/features/verification.dsl

# Hop 6: value axis (Feature requires FP; Requirement + claim)
grep -n "featRunLifecycleControl -> fpCreateRun"      architecture/features/features.dsl
grep -n "REQ-001"                                     product/requirements.yaml

# Hop 7 + §2: DECISION axis — ADR fact present, normalized view absent, baseline row present
grep -c "ADR-0020"                                    architecture/facts/generated/adrs.json
test -f docs/adr/normalized/ADR-0020.yaml || echo "normalized ADR-0020 absent (expected — baselined)"
grep -n "FRB-create-run-no-normalized-view"           docs/governance/feature-readiness-baseline.yaml

# Whole-chain assertion (authoritative): run the gate's FunctionPoint-readiness check on WSL
#   gate/check_architecture_sync.sh   (Rule 147 / G-30 / E197 evaluates the four-axis bar)
```

---

## §5 — Reconcile-step ownership note

This advisory file does NOT touch any shared authority surface
(`docs/governance/architecture-status.yaml`, `README.md`, `gate/README`,
`docs/governance/enforcers.yaml`, `docs/governance/recurring-defect-families.yaml`,
`architecture/profile/*`, `architecture/features/engineering-frames.dsl`, or any
`architecture/facts/generated/*` / `architecture/generated/*`). Every ID, edge,
property, line reference, and fact ref above is **read** from the live tree at
walk time and copied verbatim — no ID and no relationship is invented, and the
walk authors no normalized view (the ADR-0020 normalized-view gap is recorded as
a tolerated, dated baseline residual, not closed here). Where this walk and a DSL
element disagree, the DSL wins; where the DSL and a generated fact disagree, the
fact wins (ADR-0154 authority cascade). Discharging the DECISION-axis residual
(authoring `docs/adr/normalized/ADR-0020.yaml`) and any change to the surfaces
this walk points at are owned by the reconcile / ADR-normalization steps, not by
this readable interpretation layer.
