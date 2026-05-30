---
level: L2
view: scenarios
status: template
feature: "<function-point-slug>"
relates_to:
  - "architecture/features/function-points.dsl"
  - "architecture/features/engineering-frames.dsl"
  - "architecture/docs/L1/frames/<frame-id>.md"
  - "architecture/docs/L1/<owner-module>/features/<frame-feature>.md"
authority: "ADR-0068 (Layered 4+1 + Architecture Graph) + ADR-0161 (EngineeringFrame package-cluster anchor + Card over DSL) + <FP-specific ADRs>"
---

# L2 FunctionPoint Spec — Template (`_function-point-template.md`)

This file is the **canonical shape** every per-FunctionPoint L2 detailed-design
document follows. It is **NOT** a FunctionPoint spec itself — `_function-point-template.md`
is a skeleton that is **copied**, never linked-to-as-authority. Tooling and gate
path-globs that walk `architecture/docs/L2/**` for authored FunctionPoint specs
exclude any basename beginning with `_` (this template and any sibling
`_*.md` scaffold).

> **What a FunctionPoint L2 spec is.** A FunctionPoint is a *concrete
> method / scenario* — one entry verb, one runtime behaviour — on the granularity
> chain `ProductClaim -> Requirement -> L0 Constraint/ADR -> EngineeringFrame
> (package-cluster) -> Type Inventory -> FunctionPoint (method) -> Contract ->
> CodeFact/TestFact -> Gate`. This L2 spec is the **detail home** that carries the
> method call chain, runtime sequence, error paths, wire/contract behaviour, and
> test inventory that the layer-purity verdict ruled does **NOT** belong in L0 / L1
> prose (Rule 145 / E194-E195). It is the migration target for that leaked detail,
> not a second source of truth.

> **This document is a READABLE INTERPRETATION layer (Rule 146 / E196 discipline).**
> It invents no FunctionPoint ID, no frame ID, no operation ID, no status code, no
> error code, and no method name. Every identity is **copied** from the authoring
> DSL; every fact is **cited** from the generated facts. Where this prose and the
> DSL disagree, the DSL wins; where the DSL and generated facts disagree, the
> **generated facts win** (ADR-0154 cascade: `generated facts > DSL > Card/prose`).

## How to copy this

Bootstrapping a new FunctionPoint L2 spec:

```bash
cp architecture/docs/L2/_function-point-template.md \
   architecture/docs/L2/<feature-slug>/<function-point-slug>.md
# or, when the FP shares a feature-slug directory with sibling FPs / views:
#   architecture/docs/L2/<feature-slug>/<view>.md
# Then: replace every <placeholder>; delete sections that do not apply
# (a view a FunctionPoint does not exercise is omitted, not stubbed — Rule 33).
# The SECTION SHAPE stays: the eight headed sections below are the contract.
```

Naming: a single-FunctionPoint file is `docs/L2/<feature-slug>/<function-point-slug>.md`;
a FunctionPoint whose detail spreads across 4+1 views uses the per-view form
(`logical.md` / `development.md` / `process.md` / `physical.md` / `scenarios.md`)
under the same `<feature-slug>/` directory, exactly as the existing
`run-http-contract/` sink does. See [`README.md`](README.md) for the L2 corpus
naming convention.

## Authority chain (read top-down)

Fill each rung with the concrete IDs for THIS FunctionPoint. Do not invent — copy
from the DSL, cite from the facts.

1. **FunctionPoint identity (authoring DSL)** — element `<fpElementName>` in
   [`../../features/function-points.dsl`](../../features/function-points.dsl),
   `saa.id` = `FP-<NAME>`. Copy its `saa.status`, `saa.channel`, `saa.actor`,
   `saa.trigger`, `saa.requirement`, and `saa.sourceAdr` verbatim into the
   sections below; this spec adds **no** property the element does not declare.
2. **Owning EngineeringFrame (structural parent)** — the frame that holds the
   `anchors` edge to this FunctionPoint in
   [`../../features/engineering-frames.dsl`](../../features/engineering-frames.dsl)
   (or the re-tagged agent-service elements in
   [`../../features/features.dsl`](../../features/features.dsl)). Frame ID =
   `EF-<NAME>`; its Frame Card is `architecture/docs/L1/frames/<frame-id>.md`
   (a per-instance path — fill in the real frame id and link it from the copied
   spec, where the relative depth is one level deeper than this template). A
   FunctionPoint this spec describes MUST be one the frame `anchors` in the DSL
   (Rule 146 clause 3); naming a non-anchored FP is a card/spec violation.
3. **Generated facts (binding factual authority)** — the
   `code-symbol/*`, `test/*`, and `contract-op/*` facts in
   [`../../facts/generated/code-symbols.json`](../../facts/generated/code-symbols.json),
   [`../../facts/generated/tests.json`](../../facts/generated/tests.json), and
   [`../../facts/generated/contract-surfaces.json`](../../facts/generated/contract-surfaces.json).
   Every anchor cited below MUST resolve in these files. Facts are
   **never hand-edited** — they are extracted by `tools/architecture-workspace`.
4. **Contract surface (binding wire / SPI authority)** — the OpenAPI / AsyncAPI /
   SPI document the FunctionPoint's `saa.contract_op_refs` points at
   (e.g. [`../../../docs/contracts/openapi-v1.yaml`](../../../docs/contracts/openapi-v1.yaml)).
   The contract document is the binding wire authority; the Contracts section
   below is a readable expansion of it, not a replacement.
5. **L0 constraint authority** — the `architecture/docs/L0/ARCHITECTURE.md` §4
   constraint(s) / ADR(s) that name the boundary without carrying its runtime
   detail. This spec carries the detail; L0 keeps the invariant.

---

## 1. Behavior

State, in one paragraph, the single behaviour this FunctionPoint realizes — the
*what*, not yet the *how*. Bind it to the value axis (`ProductClaim ->
Requirement -> Feature -> FunctionPoint`) and the structural axis (`Module ->
EngineeringFrame -> FunctionPoint`).

| Field | Value (copy from the DSL element) |
|---|---|
| FunctionPoint ID | `FP-<NAME>` |
| Status | `<shipped \| design_only>` (`saa.status`) |
| Owning EngineeringFrame | `EF-<NAME>` (the `anchors` parent) |
| Owner module | `<agent-service \| agent-bus \| agent-middleware \| agent-execution-engine \| agent-evolve \| agent-client \| graphmemory-starter>` (`saa.owner`) |
| Requirement | `REQ-<NNN>` (`saa.requirement`) |
| Channel | `<http \| internal \| spi>` (`saa.channel`) |
| Actor | `<actor>` (`saa.actor`) |
| Trigger | `<trigger>` (`saa.trigger`) |
| Source ADR | `ADR-<NNNN>` (`saa.sourceAdr`) |

> Worked exemplar (delete in instances): for `FP-CREATE-RUN` this row reads
> status `shipped`, frame `EF-ACCESS-ADMISSION`, owner `agent-service`,
> requirement `REQ-001`, channel `http`, trigger `HTTP POST /v1/runs`, source
> `ADR-0020`.

## 2. I/O

The input the FunctionPoint accepts and the output it produces. For an `http`
channel, reference the contract request / response schema by name (do not inline
the wire format — cite it). For an `internal` / `spi` channel, name the input and
return types by their `code-symbol/*` facts.

- **Input** — `<request schema name / input type>`; carried by
  `<X-Tenant-Id header / method parameter / event payload>`. Cite the schema or
  type, do not re-spell its fields here unless a field is load-bearing for the
  behaviour.
- **Output (success)** — `<response schema name / return type>` at
  `<success status / return shape>`.
- **Side effects** — `<state writes, emitted events, persistence>`, each named by
  the boundary it crosses (the owning frame's SPI / aggregate), never by an inlined
  SQL statement (that is a Runtime-Sequence / contract concern, cited not pasted).

## 3. Runtime Sequence

The ordered method/participant hops from trigger to outcome. Use a `mermaid`
`sequenceDiagram` (matching the existing L2 sinks). Every participant that is a
code symbol MUST be nameable as a `code-symbol/*` fact (resolved in §4); every
hop that crosses a boundary names that boundary's SPI / contract op, never an
implementation-private call this spec mints.

```mermaid
sequenceDiagram
    autonumber
    participant Actor as <actor>
    participant Entry as <EntryClass>.<entryMethod>
    participant Boundary as <Frame SPI / aggregate owner>
    %% participant ... add the real hops

    Actor->>Entry: <trigger>
    Entry->>Boundary: <boundary call (name the SPI method, cite it in §4)>
    Boundary-->>Entry: <outcome>
    Entry-->>Actor: <success output (cite the contract op in §6)>
```

If the FunctionPoint has no multi-hop sequence (a pure value object or a single
guard), replace this section with a one-line statement of the single call and
delete the diagram — do not draw a one-arrow diagram.

## 4. Class / Method Anchors (from facts)

Every code anchor for this FunctionPoint, **cited** from the generated facts.
This is the section that makes the spec navigable; it MUST NOT mint a class or
method name.

Citation forms (Rule 146 / E196 — the gate resolves exactly these):

- **Class / type** — `code-symbol/<kebab-fqn>` (the class fact's `fact_id`;
  FQN lowercased, `.` and `$` rendered as `-`).
- **Method** — `code-symbol/<kebab-fqn>#<jvm-method-descriptor>`, where the
  `<jvm-method-descriptor>` is a verbatim entry in that class fact's
  `public_methods[]` array (e.g. `cancel(Ljava/lang/String;)Lorg/springframework/http/ResponseEntity;`).
  A method ref that does not resolve in `public_methods[]` is a violation.

| Role | Symbol | Fact id (+ method descriptor) |
|---|---|---|
| Entry (`saa.code_entrypoint_refs`) | `<EntryClass>.<method>` | `code-symbol/<kebab-fqn>#<descriptor>` |
| Boundary / SPI | `<BoundaryType>.<method>` | `code-symbol/<kebab-fqn>#<descriptor>` |
| Collaborator (type only) | `<Type>` | `code-symbol/<kebab-fqn>` |

> Worked exemplar (delete in instances): `FP-CREATE-RUN`'s entry is
> `RunController.create`, fact
> `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller`, method
> descriptor drawn from that fact's `public_methods[]`. The `saa.code_entrypoint_refs`
> source-path form on the DSL element
> (`agent-service/.../RunController.java#create`) is the human-authored pointer;
> the `code-symbol/*` fact-id form is the gate-resolvable citation — give **both**
> only if useful, but the fact-id form is the one the gate checks.

All fact ids in this section resolve in
[`../../facts/generated/code-symbols.json`](../../facts/generated/code-symbols.json).

## 5. Error Paths

Every non-success outcome, as a function of an observable cause. Name the
`error.code` / exception type and the resulting status / signal — each cited from
the contract op's `responses` block (§6) or a `code-symbol/*` exception fact, not
minted here.

| Cause (observable) | Outcome | Status / signal | `error.code` / exception |
|---|---|---|---|
| `<cause>` | `<rejected / retried / escalated>` | `<status / signal>` | `<error.code or ExceptionType>` |

> Worked exemplar (delete in instances): for a cancel-class FunctionPoint, a
> cancel of a `SUCCEEDED` run yields `409` `illegal_state_transition` (the loser
> side of the not-terminal CAS); this row's status + code are cited from the
> `cancelRun` `responses` block (`contract-op/cancelrun`), not invented here.

For `http` FunctionPoints, every status code in this table MUST appear in the
contract op fact's `response_status_codes`. A status code not in the fact is
either a fact gap (fix the contract + re-extract) or an invented code (remove it).

## 6. Contracts

The binding contract surface(s) this FunctionPoint speaks, cited by fact id and
by the `saa.contract_op_refs` on the DSL element.

| Operation | Fact id | Method + path / SPI signature | Success | Status codes |
|---|---|---|---|---|
| `<operationId>` | `contract-op/<kebab-op-id>` | `<HTTP_VERB> <path>` or `<SPI method>` | `<2xx>` | `<from fact response_status_codes>` |

- Contract-op fact ids resolve in
  [`../../facts/generated/contract-surfaces.json`](../../facts/generated/contract-surfaces.json)
  (each `contract-op/*` carries the canonical `http_method`, `path`, and
  `response_status_codes`).
- The binding wire / SPI authority is the contract document itself
  (`docs/contracts/<...>.yaml` for OpenAPI / AsyncAPI;
  the `*.spi` `code-symbol/*` for an internal SPI surface). This table is a
  readable interpretation of it.
- An `internal`-channel FunctionPoint with no contract op states
  "No external contract surface — internal boundary; the contract is the owning
  frame's SPI type (cited in §4)." rather than leaving this section empty.

## 7. Tests

The three-layer test evidence (Rule D-4), cited by `test/*` fact id and by the
`saa.test_refs` on the DSL element. Name the layer each test covers.

| Layer | Test class | Fact id | Covers |
|---|---|---|---|
| Unit / domain | `<TestClassFqn>` | `test/<kebab-fqn>` | `<the FP behaviour / invariant>` |
| Integration / contract | `<TestClassFqn>` | `test/<kebab-fqn>` | `<the wire / boundary contract>` |
| Architecture / enforcer | `<ArchTestFqn or EnforcerId>` | `test/<kebab-fqn>` or `E<N>` | `<the structural constraint>` |

- Test fact ids resolve in
  [`../../facts/generated/tests.json`](../../facts/generated/tests.json) (each
  `test/*` carries `test_methods[]`). A `test/*` ref that does not resolve is a
  violation (Rule 146 clause 2).
- The `verifies` edges in
  [`../../features/verification.dsl`](../../features/verification.dsl) are the
  authoring-DSL record of which test verifies this FunctionPoint; this table is a
  readable view of those edges joined to the generated `test/*` facts.
- A `design_only` FunctionPoint legitimately has no `test/*` fact yet; state
  "Tests: deferred (`design_only`) — no `test/*` fact exists; verification is
  designed but not yet implemented." rather than citing a non-resolving ref.

## 8. Gates

The gate rules / enforcers that hold this FunctionPoint and its anchors honest.
Cite stable structural identifiers (`# Rule N — slug`, `enforcer E<N>`) — these
are STRUCTURAL identifiers, not version/log metadata (Rule D-9), and remain
allowed.

| Concern | Gate rule / enforcer | What it blocks |
|---|---|---|
| FunctionPoint element well-formedness | `<Rule G-N / E<N>>` | a profile-tagged FP element missing a required `saa.*` property. |
| Frame anchors >= 1 FP (shipped) | Rule G-23 | promoting the owning frame to `shipped` without anchoring >= 1 FunctionPoint. |
| Card / spec is a readable interpretation | Rule 146 / E196 | a citation here (`code-symbol/*`, `test/*`, `contract-op/*`, method descriptor) that does not resolve in the generated facts, or an FP/frame relationship not present in the DSL. |
| No L2 detail left upstream | Rule 145 / E194-E195 | the method-chain / sequence / wire / SQL / filter-ordering / test-inventory detail this spec carries being left in L0 / L1 prose instead. |
| FunctionPoint readiness | Rule 147 / E197 (kernel Rule G-30) | a FunctionPoint marked ready whose required axis obligations (structure: one frame anchors + an owning-module implements; value: a Feature requires; evidence: contract-or-rationale + a resolving generated-fact ref + test-or-exception + a gate ref; decision: a citeable normalized ADR view) are absent — `gate/lib/check_feature_readiness.py`, ADVISORY at the ADR-0159 §13.3 landing rung. |

> The `feature_readiness` row names the readiness gate this template feeds:
> `gate/lib/check_feature_readiness.py` (gate Rule 147 / enforcer E197 / kernel
> Rule G-30), reading the policy `docs/governance/feature-readiness-policy.yaml`.
> It is wired ADVISORY (always exit 0) at the ADR-0159 §13.3 first-cleanup-wave
> rung; promotion to changed-files-blocking then full-blocking follows once the
> corpus reaches the acceptance bar.

---

## What stays upstream (NOT carried here)

Per the layer-purity keep-list, the following remain at L0 / L1 and are only
*referenced* from this spec, never duplicated (Rule 145):

- the L0 §4 constraint / *invariant* the FunctionPoint honours (L0 owns the
  invariant; this spec owns the verbs, routes, status codes, method hops);
- naming the entry class / frame as a **boundary identity** and the
  development-view package decomposition of the owning module (that is L1 / Frame
  Card material);
- citing the ArchUnit / gate enforcer that pins the boundary (named in §8, not
  re-specified).

## Authoring discipline (checklist)

- [ ] Every `<placeholder>` replaced with the concrete ID copied from the DSL.
- [ ] Every code / test / contract anchor cited by its fact id and resolving in
      `architecture/facts/generated/*.json` (no minted names).
- [ ] No status code / error code / operation id / method name introduced that is
      absent from the cited fact (generated facts win — ADR-0154 cascade).
- [ ] Front-matter declares `level: L2` + a `view:` from
      `{logical|development|process|physical|scenarios}` and `status:` other than
      `template`; `relates_to:` links upward to the FP DSL, the owning frame, and
      the contract surface (Rule 37 / Rule 38).
- [ ] Views the FunctionPoint does not exercise are omitted, not stubbed
      (Rule 33).
- [ ] No version / wave / `per ADR-NNNN`-changelog metadata in the prose
      (Rule D-9); `Authority: ADR-NNNN` markers and `# Rule N — slug` /
      `enforcer E<N>` identifiers are structural and allowed.

## Authority

- ADR-0068 — Layered 4+1 + Architecture Graph as twin sources of truth
  ([`../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml`](../../../docs/adr/0068-layered-4plus1-and-architecture-graph.yaml)).
- ADR-0161 — EngineeringFrame package-cluster anchor + Card over DSL
  ([`../../../docs/adr/0161-engineering-frame-package-cluster-anchor-and-card-over-dsl.yaml`](../../../docs/adr/0161-engineering-frame-package-cluster-anchor-and-card-over-dsl.yaml)).
- Rule 33 — Layered 4+1 Discipline; Rule 34 — Architecture-Graph Truth (`CLAUDE.md`).
- Rule 145 — L2 detail sink (no L2 detail left in L0 / L1 prose).
- Rule 146 — Frame Card / FunctionPoint-spec is a readable interpretation, never
  an authority.
- L2 corpus index: [`README.md`](README.md).
