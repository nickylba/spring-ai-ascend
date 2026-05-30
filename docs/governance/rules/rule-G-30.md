---
rule_id: G-30
title: "FunctionPoint Readiness"
level: L1
view: scenarios
principle_ref: P-C
authority_refs: [ADR-0159]
enforcer_refs: [E197]
status: active
scope_phase: design
kernel_cap: 8
governance_infra: true
scope_surfaces:
  - docs/governance/feature-readiness-policy.yaml
  - architecture/features/function-points.dsl
  - architecture/features/engineering-frames.dsl
  - architecture/features/features.dsl
  - architecture/facts/generated/code-symbols.json
  - architecture/facts/generated/tests.json
  - architecture/facts/generated/contract-surfaces.json
  - docs/adr/normalized
  - architecture/docs/L2
  - gate/lib/check_feature_readiness.py
kernel: |
  A FunctionPoint is the behavioral JOIN POINT of the progressive learning curve — the single object a requirement demands, anchored by exactly one EngineeringFrame, described by a contract, evidenced by generated facts, verified by tests, enforced by gates. Rule G-30 evaluates every `SAA FunctionPoint` element in `architecture/features/function-points.dsl` against the readiness bar its `saa.status` resolves to under the policy `docs/governance/feature-readiness-policy.yaml` (`status_vocabulary`: `design_only` → `proposed` requires nothing; `mock_functional` → `active` requires an EngineeringFrame `anchors` edge + an L2 detailed-design landing; `shipped` → the full per-axis acceptance bar). The acceptance bar is stated per axis: STRUCTURE — anchored by exactly one EngineeringFrame + an owning-module `implements` edge; VALUE — at least one Feature `requires` it; EVIDENCE — a contract ref or an explicit no-contract rationale, a generated-fact reference that resolves in `architecture/facts/generated/*.json`, a test ref or an approved exception, and a gate reference; DECISION — `saa.sourceAdr` resolves to a normalized ADR view (`docs/adr/normalized/ADR-NNNN.yaml`) in `active_guidance` or `partial_guidance` (the two citeable states). It also enforces the OWNERSHIP INVARIANT: only an EngineeringFrame may `anchors` a FunctionPoint — a `ProductClaim`, `Requirement`, or `Feature` source on an `anchors` edge is a structural lie (the derived `Feature --traverses--> EngineeringFrame` navigation is not ownership) and blocks in any blocking mode regardless of changed-file scope. The single gate Rule 147 invokes `gate/lib/check_feature_readiness.py` (E197), which invents no id and no relationship and never outranks a generated fact — it reads the policy file as the schema, the DSL elements + edges as the identity authority, the generated facts as the factual authority, and the normalized-ADR views as the decision authority, and reports which obligations a FunctionPoint has NOT discharged for its declared state. `architecture/features/function-points.dsl` is greenfield-vacuous when it declares no FunctionPoint; the instant one exists, the policy file + DSL surfaces + generated facts MUST be readable or the check fails closed (exit 2) in every mode — a missing authority is never an advisory condition. Runs ADVISORY at this landing rung per the ADR-0159 §13.3 ratchet (advisory → changed-files-blocking → full-blocking, the terminal rung once the corpus reaches the acceptance bar): the helper evaluates every FunctionPoint, reports findings to the gate log, and always exits 0. A missing helper fails closed; a missing python interpreter is a vacuous pass (Rule G-7 lists WSL as the canonical env).
---

# Rule G-30 — FunctionPoint Readiness

## What

Evaluates every FunctionPoint against the readiness bar its declared lifecycle
state demands, across the four axes of the progressive learning curve. A
FunctionPoint (`FP-…`) is the *behavioral join point* ADR-0159 places at the
center of the eight-node chain `ProductClaim → Requirement → L0 Constraint/ADR →
EngineeringFrame (package-cluster) → Type Inventory → FunctionPoint (method) →
Contract → CodeFact/TestFact → Gate`: it is the single object a requirement
demands, the one node shared by the value axis and the structure axis. The rule
is the executable form of the policy acceptance rule — "no unit of work is
shippable unless every required axis is complete" — read off the data file
`docs/governance/feature-readiness-policy.yaml`, never minted in the gate.

The authority direction is fixed and one-way; the rule reads four authorities and
asserts none of its own:

    ADR-0159
      -> docs/governance/feature-readiness-policy.yaml          (schema: the bars)
        -> architecture/features/function-points.dsl            (identity: the FPs)
          -> architecture/features/{engineering-frames,features}.dsl  (anchors/requires/implements edges)
            -> architecture/facts/generated/*.json              (factual authority)
              -> docs/adr/normalized/ADR-NNNN.yaml              (decision authority)
                -> gate Rule 147 / E197                         (this check)

- The policy file (`feature-readiness-policy.yaml`) — the SCHEMA. It declares the
  per-state hard requirements (`status_rules`) and the status → readiness-bar map
  (`status_vocabulary`). It enumerates no FunctionPoint and mints no ID.
- The DSL FunctionPoint elements + their `anchors` / `implements` / `requires`
  edges — the IDENTITY authority (the evaluation subjects + their structural and
  value edges).
- The generated facts (`code-symbols` / `tests` / `contract-surfaces`) — the
  FACTUAL authority for the evidence axis. A cited fact ref MUST resolve.
- The normalized-ADR views (`docs/adr/normalized/ADR-NNNN.yaml`) — the DECISION
  authority. A shipped FunctionPoint's `saa.sourceAdr` MUST resolve to a view in
  `active_guidance` or `partial_guidance`; raw prose, or a superseded /
  historical_evidence / remediation_record view, is not current authority.

## Why

ADR-0159 fixes the FunctionPoint as the behavioral join point and the four axes
(value / structure / evidence / decision) as the directed reading orders over the
curve. The 2026-05-29 engineering-governance systemic-remediation review accepted
that model only with a structural guard: without one, a Feature or FunctionPoint
could be declared `shipped` while missing an axis — anchored by no frame, required
by no Feature, citing no contract / fact / test, or resolving only to raw ADR
prose — and the corpus would claim a completeness it had not earned. Worse, a
value-axis node (`ProductClaim` / `Requirement` / `Feature`) could `anchors` a
frame or FunctionPoint and collapse the dual-track model into single-axis
ownership. This rule closes both risks structurally: an under-evidenced shipped
FunctionPoint and a value-axis-owned frame are findings the gate reports (and, at
the blocking rungs, a PR may not ADD), rather than silent gaps the corpus carries.
The readiness policy stays subordinate to the authority spine — it declares which
facts must EXIST for a state to be valid; it never asserts a fact.

## How it works

The single gate Rule 147 invokes one helper:

- `gate/lib/check_feature_readiness.py` (E197) — parses the FunctionPoint
  elements + their anchor / implements / requires edges, loads the policy schema
  + the three generated fact files + the normalized-ADR views, and evaluates each
  FunctionPoint against its bar. It reports, FunctionPoint-oriented and naming the
  axis + a short machine code (`STRUCTURE-NO-ANCHOR`, `VALUE-NO-FEATURE`,
  `EVIDENCE-NO-CONTRACT`, `EVIDENCE-NO-FACT`, `EVIDENCE-FACT-UNRESOLVED`,
  `EVIDENCE-NO-TEST`, `EVIDENCE-NO-GATE`, `DECISION-NO-ADR`,
  `DECISION-NO-NORMALIZED-VIEW`, `DECISION-ADR-NOT-CITEABLE`,
  `OWNERSHIP-NONFRAME-ANCHOR`), every obligation a FunctionPoint has not
  discharged for its declared state. It invents no ID and no relationship and
  never outranks a generated fact — it is a classifier over the FunctionPoints
  against the policy.

Ownership invariant. A non-frame source on an `anchors` edge
(`OWNERSHIP-NONFRAME-ANCHOR`) is computed over the WHOLE model, not per-state: the
dual-track model is invalid the instant a value-axis node owns a frame, so it
blocks in any blocking mode irrespective of changed-file scope.

Greenfield / vacuity posture. `architecture/features/function-points.dsl` is
vacuously clean when it declares no FunctionPoint element. The instant one
FunctionPoint exists, the policy file, the DSL surfaces, and the generated facts
MUST be readable, or the check fails closed (exit 2) in EVERY mode including
advisory — a FunctionPoint cannot be judged against authorities that vanished, so
a missing authority is never an advisory condition.

## Ratchet

advisory (this landing rung: evaluate every FunctionPoint, report findings to the
gate log, always exit 0 — the ADR-0159 §13.3 first-cleanup-wave posture) →
changed-files-blocking (a PR may not ADD or WORSEN a finding on a FunctionPoint
whose authoring surfaces it touches; pre-existing findings on untouched
FunctionPoints stay advisory) → full-blocking (the terminal posture once the
corpus reaches the acceptance bar). The helper `--mode` flags (`advisory` /
`changed-files-blocking` / `full-blocking`) implement the rungs; the changed-files
rung derives its scope from `--base` (default `origin/main`, else `HEAD`) — the
same git-deriving pattern as Rule 145 / E194 `check_layer_purity.py` and Rule 146
/ E196 `check_frame_card_consistency.py` — and falls back to full-corpus
evaluation when git cannot resolve the base. A change to any shared authoring
surface (the policy file / `function-points.dsl` / `engineering-frames.dsl` /
`features.dsl`) re-scopes EVERY FunctionPoint (shared dependency graph); a change
to an L2 design dir scopes the FunctionPoint it describes. Ownership findings
block at every blocking rung regardless of scope. Promotion past advisory is
gated on a clean-corpus soak per ADR-0159 §13.3, mirroring the ADR-0161 (Rule
G-29) and ADR-0159 (Rule G-27) ratchets. A missing helper fails closed; a missing
python interpreter is a vacuous pass (Rule G-7 lists WSL as the canonical env).

## Test fixtures

  - VALID  : no FunctionPoint element yet is vacuously clean (greenfield) — every
             mode passes with zero findings.
  - VALID  : a fully-shipped FunctionPoint that satisfies every axis (anchored by
             one frame, implemented by a module, required by a feature, carries
             contract + test + fact refs, cites a citeable normalized ADR view)
             passes full-blocking.
  - VALID  : a `design_only` FunctionPoint missing all evidence resolves to the
             `proposed` bar and produces no finding.
  - INVALID: a shipped FunctionPoint dropping exactly one obligation
             (contract / test / fact / normalized-ADR view / citeable ADR state /
             frame anchor / module implements / feature requires) yields the
             matching axis finding under full-blocking.
  - INVALID: a `mock_functional` FunctionPoint missing its frame anchor + L2
             design landing yields `STRUCTURE-NO-ANCHOR` + `STRUCTURE-NO-L2-DESIGN`.
  - INVALID: a value-axis node (`Feature`) `anchors`-owning a FunctionPoint yields
             `OWNERSHIP-NONFRAME-ANCHOR` and blocks even in changed-files mode.
  - VALID  : advisory mode reports findings but never blocks (exit 0) — the
             ratchet landing posture.
  - INVALID: a vanished policy file / fact file fails closed (exit 2) even in
             advisory mode — a missing authority is never an advisory condition.

## Cross-references

  - ADR-0159 — Progressive Learning Curve and Authority Lanes (the authority this
    rule enforces: the eight-node chain, the four axes, the layer-purity lane
    invariant, and the §13.3 advisory landing rung)
  - ADR-0151 — L1 Feature Registry (the 9-state lifecycle the readiness states
    read from; Rule G-14 enforces the transition validity this rule's bars assume)
  - ADR-0157 — EngineeringFrame Ontology (the structural-axis layer
    Module → EngineeringFrame → FunctionPoint the structure-axis bar checks)
  - ADR-0160 — ADR Normalization (the normalized-ADR view the decision-axis bar
    cites; Rule G-28 pins that view below the facts)
  - ADR-0161 — EngineeringFrame as Package-Cluster Anchor + Card-over-DSL (the
    one-frame-per-owning-module rule the structure-axis bar asserts; Rule G-29 is
    the Frame-Card sibling of this readiness check)
  - ADR-0154 — Fact-Layer Authority (the cascade `generated facts > DSL >
    Card/prose` the policy never outranks)
  - Rule G-15 — Fact-Layer Integrity (the generated facts the evidence axis cites
    are the apex factual authority)
