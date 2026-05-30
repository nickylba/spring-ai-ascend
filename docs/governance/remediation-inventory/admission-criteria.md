---
level: L0
view: scenarios
status: advisory
governance_infra: true
authored: 2026-05-30
author: governance/progressive-learning-curve-remediation Wave W29
authority_refs: [ADR-0154, ADR-0156, ADR-0157, ADR-0159, ADR-0160, ADR-0161]
verdict_source: "Adjudicated verdict — L0/L1 leak critique TRUE (see layer-purity-scan.md §0); admission chain is the remediation's mechanically-traversable target"
companion: acceptance-walk.md
---

# Implementation-Admission Chain — Definition + Per-Node Admission Criteria + Checklist

> **What this is.** The single readable statement of the large-scale
> **implementation-admission chain** — the eight ordered nodes a unit of work
> traverses from a Product Claim down to a green gate — together with, for each
> node, the **admission criterion** (what must be true to enter the next node),
> the **carrier** (the on-disk artifact that holds the node), the **binding
> property** (the DSL/frontmatter field that links the node to its neighbour),
> and the **gate** (the enforcer that mechanically holds the link). It is the
> W29 "prove the chain is admissible" companion to the W29
> [`acceptance-walk.md`](acceptance-walk.md), which then walks one shipped
> feature down the whole chain to demonstrate it is traversable in practice.
>
> **What this is NOT.** An authority surface, an ADR, a gate, or a new doc
> system. It invents no IDs and no relationships; every node, property, and
> enforcer named below is copied from the live authority surfaces (the ADRs,
> `architecture/profile/*`, the DSL fragments, the generated facts, the rule
> cards, and `docs/governance/enforcers.yaml`). Where this document and a DSL
> element disagree, the DSL wins; where the DSL and a generated fact disagree,
> the fact wins (the ADR-0154 authority cascade). This file changes no shared
> authority surface (see §6).

---

## §1 — The eight-node chain (the admission spine)

The remediation target is the product-first eight-node progressive learning
curve fixed by ADR-0159 (Progressive Learning Curve and Authority Lanes). A
unit of implementation work is **admissible** only when every node above it
already holds, in order:

```text
1. Product Definition      (ProductClaim)
2. Requirement Definition  (Requirement, ISO/IEC/IEEE 29148)
3. L0 Architecture         (Constraint / ADR, ISO/IEC/IEEE 42010 + C4 context/container)
4. EngineeringFrame        (C4 Component / arc42 building block = a Java package-cluster anchor)
5. FunctionPoint           (a concrete method / scenario — the behavioural join point)
6. Contract Surface        (OpenAPI / AsyncAPI / SPI)
7. Implementation Facts    (generated)
8. Verification & Gate
```

The chain is read along three axes that share the FunctionPoint as their pivot
(ADR-0157 dual-track ontology, ADR-0159 §13 four-axis bar):

| Axis | Path | Pivot |
|---|---|---|
| **VALUE** | `ProductClaim -> Requirement -> Feature -> FunctionPoint` | FunctionPoint |
| **STRUCTURE** | `Module -> EngineeringFrame -> FunctionPoint` | FunctionPoint |
| **EVIDENCE** | `FunctionPoint -> Contract -> GeneratedFact -> Gate` | FunctionPoint |
| **DECISION** (derived) | `FunctionPoint -> source ADR` | FunctionPoint |

The derived edge `Feature --traverses--> EngineeringFrame` is a **navigation
convenience, never ownership**: it is admissible only when the Feature and the
Frame share at least one FunctionPoint (the Feature `requires` it, the Frame
`anchors` it). A Feature may **only** traverse a Frame — never `contains`,
`anchors`, or `owns` it (ADR-0157; gate Rule 149 / G-32 / E199
`FEATURE-OWNS-FRAME`).

---

## §2 — Per-node admission criteria

Each row states what must hold for the node to be **admitted** (and therefore
for the next node to be entered). "Carrier" is the authoritative on-disk home;
"binding property" is the field that links the node downward; "gate" is the
enforcer that mechanically checks the link. All IDs/paths are copied from the
live tree — none is invented here.

### Node 1 — Product Definition (ProductClaim)

- **Admission criterion.** The demanded value is stated as a `PC-NNN`
  ProductClaim in `product/claims.yaml`, in product-owner-authored wording.
- **Carrier.** `product/PRODUCT.md` (Tier-1 authority), `product/claims.yaml`
  (`PC-001 .. PC-005`), `product/personas.yaml`, `product/journey.md`.
- **Binding property (downward).** A Requirement names this claim via
  `source_claim: [PC-NNN]`; a Feature echoes it via `saa.productClaim`.
- **Gate.** Rule 133 / E… (every `product_claim:` / `saa.productClaim` value
  MUST resolve to a `PC-NNN` in `product/claims.yaml` or carry a sentinel);
  Rule 135 (every `PC-NNN` MUST have ≥1 Feature referencing it via
  `saa.productClaim`).

### Node 2 — Requirement Definition (Requirement)

- **Admission criterion.** A `REQ-NNN` entry exists in
  `product/requirements.yaml` that (a) names its `source_claim` (≥1 `PC-NNN`),
  (b) names the realising `feature` and `function_points`, (c) cites its
  `source_adr`, and (d) carries `acceptance_criteria` whose lines map to a real
  test or contract (fact-backed, not prose). ECR-2 acceptance bar: **no
  FunctionPoint may be added from prose alone** — it must trace to a Requirement
  or an ADR-created structural need.
- **Carrier.** `product/requirements.yaml` (schema `id` / `source_claim` /
  `feature` / `function_points` / `source_adr` / `priority` / `status` /
  `rationale` / `acceptance_criteria`).
- **Binding property (downward).** The FunctionPoint and Feature both carry
  `saa.requirement = REQ-NNN`; the Requirement lists its `function_points: [FP-…]`.
- **Gate.** The reading-path / value-axis checks (Rule 135 shipped-feature bar);
  the FunctionPoint-readiness VALUE axis (`VALUE-NO-FEATURE`, Rule 147 / G-30 /
  E197) confirms ≥1 Feature `requires` the FunctionPoint the Requirement demands.

### Node 3 — L0 Architecture (Constraint / ADR)

- **Admission criterion.** The architectural decision that creates or constrains
  the structural need exists as a `docs/adr/NNNN-*.yaml` ADR, is carried by the
  generated `adrs.json` fact, and (for the DECISION axis to be fully citeable)
  has a normalized view at `docs/adr/normalized/ADR-NNNN.yaml` in an `active`
  guidance state. **L0 carries the invariant, not the runtime detail** — wire
  shapes, SQL/RLS/GUC, HTTP status/verb/header behaviour, filter ordering, and
  method signatures are **leaked** at L0/L1 and belong at the Contract surface or
  the L2 sink (adjudicated verdict; gate Rule 145 / G-27 / E194-E195 layer
  purity).
- **Carrier.** `docs/adr/*.yaml` (raw) + `docs/adr/normalized/ADR-NNNN.yaml`
  (normalized view) + `architecture/docs/L0/ARCHITECTURE.md` (constraint prose).
- **Binding property (downward).** The FunctionPoint and Feature carry
  `saa.sourceAdr = ADR-NNNN`.
- **Gate.** Rule 150 / G-33 / E200 (ADR-ID uniqueness; one normalized view per
  accepted ADR); Rule 147 / G-30 / E197 DECISION axis
  (`DECISION-NO-ADR` / `DECISION-NO-NORMALIZED-VIEW` / `DECISION-ADR-NOT-CITEABLE`);
  Rule 145 / G-27 / E194-E195 (no L2 detail leaked into L0/L1).

### Node 4 — EngineeringFrame (package-cluster anchor)

- **Admission criterion.** A `SAA EngineeringFrame` element exists (in
  `architecture/features/engineering-frames.dsl`, or — for the six agent-service
  frames re-tagged per ADR-0157 — in `architecture/features/features.dsl`) with
  `saa.id = EF-…`, is `contains`-owned by exactly one `genModule_*` Module, and
  carries `saa.owner`, `saa.sourceAdr`, `saa.cardPath`, and (when `shipped`)
  `saa.primaryPackage`. A `shipped` frame MUST `anchors` ≥1 FunctionPoint. The
  frame's readable Frame Card exists at `saa.cardPath`
  (`architecture/docs/L1/frames/<frame-id>.md`) and copies its identity block
  byte-for-byte from the DSL.
- **Carrier.** DSL frame element + Frame Card (`architecture/docs/L1/frames/`).
  The Frame Card is the **lowest interpretation tier** of the ADR-0154 cascade
  (ADR-0161 Card-over-DSL): it invents no ID/owner/status/relationship and never
  outranks a generated fact.
- **Binding property (downward).** `efX -> fpY` with `saa.rel = anchors`.
  Upward to STRUCTURE: `genModule_X -> efX` with `saa.rel = contains`.
- **Gate.** Rule 139 / G-22 (accepted-ADR frame-map coherence); Rule 140 / G-23
  (shipped-frame anchor integrity — `shipped` frame MUST `anchors` ≥1
  FunctionPoint unless allow-listed); Rule 146 / G-29 / E196 (Frame-Card / DSL
  parity — card identity MUST match the DSL element, every cited fact MUST
  resolve, no invented anchor); Rule 149 / G-32 / E199 (`FEATURE-OWNS-FRAME` /
  `FRAME-OWNS-VALUE` / `NON-MODULE-CONTAINS-FRAME`).

### Node 5 — FunctionPoint (the behavioural join point)

- **Admission criterion.** A `SAA FunctionPoint` element exists in
  `architecture/features/function-points.dsl` with `saa.id = FP-…`,
  `saa.status`, `saa.owner`, `saa.sourceAdr`, and `saa.requirement`. It is
  `anchors`-owned by **exactly one** EngineeringFrame (STRUCTURE), has an
  owning-module `implements` edge (`agentService -> fpX`, STRUCTURE), and is
  `requires`-demanded by ≥1 Feature (VALUE). A `shipped` FunctionPoint discharges
  the full per-axis acceptance bar (see §3); a `design_only` FunctionPoint
  requires nothing beyond declaration; a `mock_functional` one requires an
  `anchors` edge + an L2 detailed design under `architecture/docs/L2/<fp-slug>/`.
- **Carrier.** `architecture/features/function-points.dsl` (+ `verification.dsl`
  for the `test -> fp` `verifies` edges).
- **Binding property (downward to EVIDENCE).** `saa.code_entrypoint_refs`,
  `saa.test_refs`, `saa.contract_op_refs` (or `saa.contract_refs`), each
  resolving to a generated fact / contract op.
- **Gate.** Rule 147 / G-30 / E197 (the per-axis readiness bar + the OWNERSHIP
  invariant `OWNERSHIP-NONFRAME-ANCHOR`: only an EngineeringFrame may `anchors` a
  FunctionPoint — a ProductClaim / Requirement / Feature on an `anchors` edge is
  a structural lie and blocks in any blocking mode).

### Node 6 — Contract Surface (OpenAPI / AsyncAPI / SPI)

- **Admission criterion.** The FunctionPoint's external promise exists as a
  contract operation — `contract-op/<kebab-op-id>` resolving in
  `architecture/facts/generated/contract-surfaces.json` — and/or an SPI named as
  a boundary identity in `docs/contracts/contract-catalog.md`. The runtime detail
  the verdict ruled out of L0/L1 (status codes, verbs, headers, wire shapes)
  lives **here** (the contract `*.v1.yaml`) and at the L2 sink, not in the Frame
  Card or the L1 module prose.
- **Carrier.** `docs/contracts/*.v1.yaml` (+ `contract-catalog.md` curated index)
  → extracted into `contract-surfaces.json`.
- **Binding property (downward).** The FunctionPoint's `saa.contract_op_refs`
  cites the `contract-op/<id>`; the Frame Card's `fact_refs` cite it too.
- **Gate.** Rule 147 / G-30 / E197 EVIDENCE axis (`EVIDENCE-NO-CONTRACT` unless a
  `saa.no_contract_rationale` is given); Rule 146 / G-29 / E196 (a Frame Card
  citing a `contract-op/<id>` that does not resolve fails).

### Node 7 — Implementation Facts (generated)

- **Admission criterion.** The code that implements the FunctionPoint is carried
  by the generated fact layer: the class as `code-symbol/<kebab-fqn>` in
  `code-symbols.json`, the method as a JVM-descriptor entry in that class fact's
  `public_methods[]`, the test as `test/<kebab-fqn>` in `tests.json`. Facts are
  **deterministically extracted and never hand-edited**; they are the top of the
  authority cascade (facts > DSL > Card/prose).
- **Carrier.** `architecture/facts/generated/{code-symbols,tests,contract-surfaces,module-build,runtime-config,adrs}.json`.
- **Binding property (upward).** The FunctionPoint's `saa.code_entrypoint_refs` /
  `saa.fact_refs` / `saa.test_refs` and the Frame Card `fact_refs` all resolve to
  these fact IDs.
- **Gate.** Rule 147 / G-30 / E197 EVIDENCE axis (`EVIDENCE-NO-FACT` /
  `EVIDENCE-FACT-UNRESOLVED` / `EVIDENCE-NO-TEST`); Rule 146 / G-29 / E196 (every
  Frame-Card fact citation MUST resolve in the generated facts).

### Node 8 — Verification & Gate

- **Admission criterion.** The FunctionPoint is verified by a test (`test -> fp`
  `verifies` edge in `verification.dsl`) and is held by the always-on
  architecture-sync gate (default `saa.gate_refs`, unless explicitly empty). The
  gate is run on Linux/WSL (Rule G-7) and is green.
- **Carrier.** `architecture/features/verification.dsl` + the gate
  (`gate/check_architecture_sync.sh` and its `gate/lib/*.py` helpers) +
  `docs/governance/architecture-status.yaml` (the gate's status mirror).
- **Binding property.** The `verifies` edge + the `saa.gate_refs` default.
- **Gate.** Rule 147 / G-30 / E197 EVIDENCE axis (`EVIDENCE-NO-GATE`); the whole
  enforcer set in `docs/governance/enforcers.yaml` keyed by `constraint_ref`.

---

## §3 — The shipped acceptance bar (the four-axis exit criterion)

A FunctionPoint at `saa.status: shipped` is **fully admitted** only when all
four axes are discharged. This is the exact bar gate Rule 147 / G-30 / E197
evaluates per FunctionPoint (`docs/governance/feature-readiness-policy.yaml` is
the schema); the codes in parentheses are the findings the helper emits when an
obligation is not discharged.

| Axis | Obligation | Finding code(s) when unmet |
|---|---|---|
| **STRUCTURE** | anchored by **exactly one** EngineeringFrame | `STRUCTURE-NO-ANCHOR`, `STRUCTURE-MULTI-ANCHOR` |
| **STRUCTURE** | an owning-module `implements` edge | `STRUCTURE-NO-MODULE` |
| **VALUE** | ≥1 Feature `requires` it | `VALUE-NO-FEATURE` |
| **EVIDENCE** | a contract ref **or** a `saa.no_contract_rationale` | `EVIDENCE-NO-CONTRACT` |
| **EVIDENCE** | a generated-fact ref that resolves in `facts/generated/*.json` | `EVIDENCE-NO-FACT`, `EVIDENCE-FACT-UNRESOLVED` |
| **EVIDENCE** | a `saa.test_refs` ref **or** a `saa.test_exception` | `EVIDENCE-NO-TEST` |
| **EVIDENCE** | a gate ref (defaults to the architecture-sync gate) | `EVIDENCE-NO-GATE` |
| **DECISION** | `saa.sourceAdr` resolves to a normalized ADR view in `active`/`partial` guidance | `DECISION-NO-ADR`, `DECISION-NO-NORMALIZED-VIEW`, `DECISION-ADR-NOT-CITEABLE` |
| **OWNERSHIP** (invariant) | only an EngineeringFrame may `anchors` the FunctionPoint | `OWNERSHIP-NONFRAME-ANCHOR` |

Two relaxations apply by design:

- A **known historical** finding may be frozen, with a `sunset_date`, in the
  dated baseline allow-list `docs/governance/feature-readiness-baseline.yaml`;
  while in scope it is reported `BASELINED` and does not block. An **expired** row
  blocks; the **OWNERSHIP** invariant may **never** be baselined (a row may not
  freeze a structural lie). `full-blocking` ignores the baseline (the terminal
  rung after the corpus reaches the bar and the allow-list is retired).
- A `design_only` FunctionPoint discharges nothing beyond declaration; a
  `mock_functional` one needs an `anchors` edge + an L2 design only.

---

## §4 — Admission checklist (per unit of implementation work)

Before writing or promoting any FunctionPoint, walk this list top-down. Each box
is admissible only when every box above it is checked. Cite the live carrier for
each — do **not** assert it from memory.

- [ ] **1. ProductClaim.** The value is a `PC-NNN` in `product/claims.yaml`
  (product-owner wording for any new normative claim).
- [ ] **2. Requirement.** A `REQ-NNN` in `product/requirements.yaml` names this
  claim (`source_claim`), the realising `feature` + `function_points`, the
  `source_adr`, and fact-backed `acceptance_criteria`. The new FunctionPoint
  traces to this Requirement (or an ADR-created structural need) — **never from
  prose alone**.
- [ ] **3. ADR.** A `docs/adr/NNNN-*.yaml` exists, is in `adrs.json`, and (for a
  fully citeable DECISION axis) has a `docs/adr/normalized/ADR-NNNN.yaml` view in
  `active` guidance. No L2/runtime detail is parked at L0/L1.
- [ ] **4. EngineeringFrame.** A `SAA EngineeringFrame` element exists,
  `contains`-owned by exactly one Module, carrying `saa.cardPath` (+
  `saa.primaryPackage` if `shipped`); its Frame Card exists and its identity
  block matches the DSL byte-for-byte; a `shipped` frame `anchors` ≥1
  FunctionPoint.
- [ ] **5. FunctionPoint.** A `SAA FunctionPoint` element exists with
  `saa.requirement` + `saa.sourceAdr`, `anchors`-owned by **exactly one** Frame,
  with an owning-module `implements` edge, `requires`-demanded by ≥1 Feature.
- [ ] **6. Contract.** A `contract-op/<id>` resolves in
  `contract-surfaces.json` (and/or an SPI named in `contract-catalog.md`); the
  FunctionPoint's `saa.contract_op_refs` cites it. Runtime detail lives in the
  contract / L2 sink, not in the Card or L1 prose.
- [ ] **7. Facts.** The class (`code-symbol/<kebab-fqn>`), method
  (`public_methods[]` descriptor), and test (`test/<kebab-fqn>`) resolve in the
  generated facts; the FunctionPoint's refs and the Frame-Card `fact_refs` all
  resolve.
- [ ] **8. Verification & Gate.** A `verifies` edge links the test to the
  FunctionPoint; the architecture-sync gate is green on Linux/WSL (Rule G-7); the
  four-axis bar (§3) is discharged or every residual is a dated, in-scope
  baseline row.

If any box is unchecked, the unit of work is **inadmissible**: stop and discharge
the missing node before proceeding (Rule D-1 root-cause posture).

---

## §5 — How the chain is mechanically traversable today

The chain is not aspirational — every link is a machine-checkable edge or
property carried by a live authority surface, and the gate fails closed when a
link is missing. The W29 companion [`acceptance-walk.md`](acceptance-walk.md)
demonstrates this end-to-end for one shipped feature (`FP-CREATE-RUN` /
`EF-ACCESS-ADMISSION`), walking `workspace.dsl -> Frame -> Frame Card ->
generated facts -> FunctionPoint -> gate` and confirming every hop resolves,
with the single tolerated, dated DECISION-axis residual recorded honestly.

The enforcers that make each link checkable (all in
`docs/governance/enforcers.yaml`, keyed by `constraint_ref`):

| Link | Rule / kernel rule / enforcer |
|---|---|
| Claim ↔ Feature | Rule 133, Rule 135 |
| Frame ↔ Module / ADR | Rule 139 / G-22; Rule 140 / G-23 |
| Frame Card ↔ DSL ↔ facts | Rule 146 / G-29 / E196 |
| FunctionPoint four-axis bar + ownership | Rule 147 / G-30 / E197 |
| Reading-path materializability | Rule 148 / G-31 / E198 |
| Dual-track map derivation (no Feature-owns-Frame) | Rule 149 / G-32 / E199 |
| ADR-ID uniqueness / normalized view | Rule 150 / G-33 / E200 |
| Layer purity (no L2 detail at L0/L1) | Rule 145 / G-27 / E194-E195 |

---

## §6 — Reconcile-step ownership note

This advisory file does NOT touch any shared authority surface
(`docs/governance/architecture-status.yaml`, `README.md`, `gate/README`,
`docs/governance/enforcers.yaml`, `docs/governance/recurring-defect-families.yaml`,
`architecture/profile/*`, `architecture/features/engineering-frames.dsl`, or any
`architecture/facts/generated/*` / `architecture/generated/*`). Every node,
property, fact ID, rule, and enforcer named above is **read** from the live tree
at authoring time and copied verbatim — no ID and no relationship is invented.
Where this document and a DSL element disagree, the DSL wins; where the DSL and a
generated fact disagree, the fact wins (ADR-0154 authority cascade). Any change
to the surfaces this document points at is owned by the reconcile step, not by
this readable interpretation layer.
