---
level: L1
view: development
status: active
authority: "ADR-0161 (EngineeringFrame as Package-Cluster Anchor + Card-over-DSL); ADR-0157 (EngineeringFrame Ontology); ADR-0154 (Fact-Layer Authority)"
---

# `architecture/docs/L1/frames/` — EngineeringFrame Cards

This directory holds one **Frame Card** per `EngineeringFrame`. A Frame Card is the
per-frame readable artifact that lets a new AI agent or a new engineer, landing on a
frame from [`../../../workspace.dsl`](../../../workspace.dsl), learn — from artifacts
alone — **which Java package(s), classes, interfaces, methods, contracts, and tests are
in scope before editing**, and verify every factual claim against generated facts.

The collective structural narrative lives one level up in
[`../engineering-frames.md`](../engineering-frames.md) (`Module → EngineeringFrame →
FunctionPoint`, ADR-0157). This directory is the per-frame zoom-in of that map.

## Card-over-DSL — the one-way authority rule

A Frame Card is a **READABLE INTERPRETATION layer, never an authority** (ADR-0161 §3). It
**invents no IDs, owners, statuses, or relationship edges.** It sits at the lowest
interpretation tier of the ADR-0154 authority cascade:

```text
ADR
  -> architecture/profile/*
    -> architecture/workspace.dsl
      -> architecture/features/engineering-frames.dsl        (+ re-tagged frame elements in features.dsl)
        -> architecture/facts/generated/*.json
          -> architecture/docs/L1/frames/<frame-id>.md       (Frame Card: derived, this directory)
            -> gate
              -> docs/governance/architecture-status.yaml
```

Authority direction is fixed and one-way:

- **Where the Card and the DSL disagree, the DSL wins.**
- **Where the DSL and generated facts disagree, generated facts win.**

Concretely, every Card field is one of two kinds, and the template marks which is which:

| Field kind | Source of truth | Rule |
|---|---|---|
| **Identity** — `frame_id`, `dsl_element`, `owner_module`, `status`, `primary_package` | the frame's DSL element in [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl) (or, for the six agent-service frames, the re-tagged element in [`../../../features/features.dsl`](../../../features/features.dsl)) | **COPIED** from DSL; MUST match byte-for-byte. |
| **Factual** — every code / type / method / contract / test claim | the generated fact layer under [`../../../facts/generated/`](../../../facts/generated/) | **CITED** by generated fact ID; MUST resolve in a `generated/*.json` file. |

A Card carries no design rationale of its own — intent, trade-offs, and non-goals stay in
the ADRs. The Card explains *structure*; it never re-binds a `ProductClaim` onto the
structural axis (frames are claim-agnostic, ADR-0157 / ADR-0156).

## Fact-ID citation formats

Every factual claim in a Card cites a generated fact by its `fact_id`. These IDs are
emitted by the deterministic extractors into `architecture/facts/generated/*.json` and are
**never hand-edited**. The four forms a Card uses:

| Claim | Fact-ID form | Lives in | Example |
|---|---|---|---|
| A class / interface / record / enum | `code-symbol/<kebab-fqn>` | `code-symbols.json` | `code-symbol/com-huawei-ascend-bus-spi-engine-agentevent` |
| A method on a class | the matching JVM-descriptor entry in that class fact's `observed_value.public_methods[]` | `code-symbols.json` | `runId()Ljava/lang/String;` |
| A test class | `test/<kebab-fqn>` | `tests.json` | `test/com-huawei-ascend-bus-spi-engine-orchestrationspiarchtest` |
| A contract operation | `contract-op/<kebab-op-id>` | `contract-surfaces.json` | `contract-op/createrun` |

`<kebab-fqn>` is the fully-qualified name with dots replaced by hyphens, lowercased
(`com.huawei.ascend.bus.spi.engine.AgentEvent` → `com-huawei-ascend-bus-spi-engine-agentevent`).
A method is not a top-level fact — it is an entry in the owning class fact's
`public_methods[]` list, so cite it as `<class fact-id> :: <JVM descriptor>`.

## The seven-section Card shape

Every Card has YAML frontmatter (the copied-from-DSL identity block plus `fact_refs`) and
the seven prose sections defined by ADR-0161 §4, in this fixed order:

| # | Section | Content | Source |
|---|---|---|---|
| 1 | **Identity** | frame id, DSL element, owner module, status, primary package, source ADR, one-line responsibility | COPIED from DSL |
| 2 | **Capability Boundary** | what the frame *can do* / *cannot do*, the state it owns, its external dependencies, and its forbidden dependencies | AUTHORED prose; package names CITED |
| 3 | **Type Inventory** `[GENERATED]` | every in-boundary class / interface / record / enum, each with its `code-symbol/<kebab-fqn>` fact ID | GENERATED from `code-symbols.json` |
| 4 | **Internal Collaboration** `[GENERATED]` | how the in-boundary types collaborate — the structural relationships (implements / extends / references) among them | GENERATED from `code-symbols.json` |
| 5 | **Contracts** | the communication contracts the frame exposes or consumes (SPI package identity, OpenAPI / AsyncAPI operations), each contract op CITED by `contract-op/<id>` | AUTHORED prose; ops CITED |
| 6 | **FunctionPoint Mapping** | each FunctionPoint the frame `anchors` (per the DSL edge), with its entry + participating class/method anchors, contract refs, and test refs, each fact-cited | AUTHORED prose over DSL `anchors` edges; all anchors CITED |
| 7 | **Verification** | the constraints, ArchUnit enforcers, and gate rules that hold the boundary, plus — for non-shipped cards — the missing-proof statement | AUTHORED prose; enforcers / rules CITED |

Two sections — **Type Inventory** and **Internal Collaboration** — are GENERATED: their
bodies are emitted from `code-symbols.json` and live inside managed blocks (see below). The
other five are authored prose constrained to cite, never invent.

### Status-conditional rules

- A **`design_only`** Card MUST state, in section 7, what proof is missing before promotion
  to `shipped` (e.g. "no `primaryPackage` Java home exists yet; anchors zero FunctionPoints").
- A **`mock_functional`** Card MUST state why it is not yet a real production implementation.
- A **`shipped`** Card MUST carry a non-empty fact-cited Type Inventory and declare
  `primary_package`; the gate fails a shipped card that cites a package / class / method /
  test / contract absent from generated facts.

## What a Card MUST NOT carry (altitude discipline)

A Frame Card is an L1 artifact. It names the **boundary, the package root, the public
API/SPI surface, and the inter-frame contract**. It does **not** carry the runtime detail
that belongs in L2 (`architecture/docs/L2/<slug>/...`) — each category below is delegated to
that L2 sink + the contract surface, not restated in the Card:

- private method call chains and runtime sequences; <!-- l2-detail-sink-allow: delegation list NAMES the forbidden L2 category to forbid it; home architecture/docs/L2/<slug>/ -->
- persistence detail — SQL, RLS, GUC, schema; <!-- l2-detail-sink-allow: delegation list NAMES the forbidden L2 category to forbid it; home architecture/docs/L2/<slug>/ -->
- HTTP status codes, route-verb/header behaviour, filter ordering; <!-- l2-detail-sink-allow: delegation list NAMES the forbidden L2 category to forbid it; home architecture/docs/L2/<slug>/ -->
- wire formats and over-the-wire mechanics; <!-- l2-detail-sink-allow: delegation list NAMES the forbidden L2 category to forbid it; home architecture/docs/L2/<slug>/ -->
- exhaustive method-signature dumps or test-class inventories beyond the fact-cited anchors. <!-- l2-detail-sink-allow: delegation list NAMES the forbidden L2 category to forbid it; home architecture/docs/L2/<slug>/ -->

Where a frame's runtime mechanics need a home, link down to its L2 sink (for example
[`../../L2/engine-port-boundary/`](../../L2/engine-port-boundary/) for `EF-ENGINE-PORT`);
do not inline the detail here.

## The two GENERATED managed blocks

Sections 3 (Type Inventory) and 4 (Internal Collaboration) are rendered from
`architecture/facts/generated/code-symbols.json`, filtered to the frame's in-boundary
package(s). Their bodies live between explicit managed-block markers:

```text
<!-- BEGIN GENERATED: type-inventory -->
... rendered table; do not hand-edit ...
<!-- END GENERATED: type-inventory -->
```

```text
<!-- BEGIN GENERATED: internal-collaboration -->
... rendered table; do not hand-edit ...
<!-- END GENERATED: internal-collaboration -->
```

Rules for the managed blocks:

- **Do not hand-edit between the markers.** The region is owned by the Frame-Card generator
  and is overwritten on every re-render; only the content *outside* the two blocks is
  hand-authored.
- The markers and their order are **stable**: the consistency gate locates the blocks by
  marker and fails closed if a marker is missing, duplicated, or out of order (the
  non-vacuity guard for an auto-discovering rule).
- Until the `EngineeringFrameFactExtractor` (ADR-0161 §6 Phase 4-5) emits
  `engineering-frames.json`, the block bodies are populated by the Card generator directly
  from `code-symbols.json`; the markers stay regardless so the gate has a stable anchor.

## Authoring / generating a Card

1. Copy [`_template.md`](_template.md) to `architecture/docs/L1/frames/<frame-id>.md`
   (e.g. `EF-ACCESS-ADMISSION.md`). The filename is the frame's `saa.id`.
2. Fill the frontmatter identity block by **copying** the frame's DSL element fields
   (`saa.id`, `saa.owner`, `saa.status`, `saa.primaryPackage`, `saa.sourceAdr`); these MUST
   match the DSL.
3. Author sections 2, 5, 6, 7 as prose; cite every code / type / method / contract / test
   claim by its generated `fact_id`. Author section 6 strictly over the frame's DSL
   `anchors` edges — list no FunctionPoint the DSL does not anchor to this frame.
4. Populate sections 3 and 4 inside the GENERATED managed blocks from the Card generator
   (sourced from `code-symbols.json`); do not hand-edit between the markers.
5. Bind the DSL element to the Card by setting `saa.cardPath`
   (`architecture/docs/L1/frames/<frame-id>.md`) on the frame element — this edit is owned
   by the DSL/profile lockstep wave, not by the Card.

The pilot Cards land first: the high-fan-out shipped frame `EF-ACCESS-ADMISSION` (9
anchors) and/or the design-only boundary frame `EF-ENGINE-PORT` (agent-bus, ADR-0158).

## Cross-references

- Collective structural narrative: [`../engineering-frames.md`](../engineering-frames.md).
- Frame DSL (authority): [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl) (+ re-tagged agent-service elements in [`../../../features/features.dsl`](../../../features/features.dsl)).
- Generated facts (authority over prose): [`../../../facts/README.md`](../../../facts/README.md) + [`../../../facts/generated/`](../../../facts/generated/).
- FunctionPoint inventory: [`../../../features/function-points.dsl`](../../../features/function-points.dsl).
- L2 detail sinks (runtime mechanics live here, not in Cards): [`../../L2/`](../../L2/).
- Frame & FunctionPoint gap inventory (advisory): [`../../../../docs/governance/remediation-inventory/frame-and-fp-gap.md`](../../../../docs/governance/remediation-inventory/frame-and-fp-gap.md).
- ADR-0161 (this layer): [`../../../../docs/adr/0161-engineering-frame-package-cluster-anchor-and-card-over-dsl.yaml`](../../../../docs/adr/0161-engineering-frame-package-cluster-anchor-and-card-over-dsl.yaml).
- ADR-0157 (EngineeringFrame ontology): [`../../../../docs/adr/0157-engineering-frame-ontology.yaml`](../../../../docs/adr/0157-engineering-frame-ontology.yaml).
- ADR-0154 (fact-layer authority): [`../../../../docs/adr/0154-fact-layer-authority.yaml`](../../../../docs/adr/0154-fact-layer-authority.yaml).
