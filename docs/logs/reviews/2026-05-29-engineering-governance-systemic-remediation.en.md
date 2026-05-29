# Engineering Governance Systemic Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rebuild the repository governance system so any AI agent can form an unbiased understanding of product intent, architecture authority, engineering boundaries, implementable behaviors, constraints, contracts, facts, and verification state.

**Architecture:** The remediation separates product, architecture, ADR, DSL, EngineeringFrame, FunctionPoint, contract, generated fact, and gate responsibilities into distinct authority lanes. Product and engineering tracks converge through FunctionPoints for behavior and through EngineeringFrames for navigation, with an explicit mapping artifact making the relationship queryable and enforceable.

**Tech Stack:** Markdown, YAML, Structurizr DSL, existing Java/Maven architecture extractor, existing shell gate entrypoint, Python gate helpers under `gate/lib/`, governance rule cards under `docs/governance/rules/`.

Date: 2026-05-29
Audience: architecture reviewers, engineering leads, governance implementers, AI coding agents
Status: remediation plan
Decision posture: apply inside the current repository, not by opening a new repository

---

## 1. Executive Decision

The current problem should be treated as a governance architecture defect, not as a documentation quality defect.

The repository already contains meaningful core assets: DSL workspace, L0 architecture surface, L1 documentation, ADR corpus, rule cards, contracts, gates, generated facts, and architecture-status baseline. Starting a new repository would preserve the conceptual insight but would discard the existing trace evidence and would recreate the same ambiguity unless the governance model is made explicit first.

The recommended decision is:

1. Keep the current repository.
2. Freeze new architecture expansion temporarily.
3. Normalize the existing ADR corpus.
4. Establish layer purity rules for L0, L1, and L2.
5. Promote EngineeringFrame to the engineering landing and navigation point.
6. Promote FunctionPoint to the behavioral join point.
7. Make the product-to-engineering and engineering-to-evidence mappings explicit and gated.
8. Move implementation detail out of L0/L1 into L2, contracts, generated facts, or gates.
9. Require future architecture reviews to review normalized ADR state, not raw historical ADR text alone.

This plan intentionally turns the AI learning curve into a governed software engineering delivery model.

---

## 2. Problem Statement

The repository is trying to support three different audiences with overlapping artifacts:

- Humans need product intent, architectural rationale, and implementation guidance.
- Engineering teams need module boundaries, API/SPI contracts, package ownership, and testable work items.
- AI agents need a stable, unbiased path from high-level intent to concrete implementation facts.

The current artifact system does not separate these needs cleanly enough. As a result, the same document family can contain product claims, architecture decisions, implementation plans, source file facts, test inventory, migration logs, rule assertions, and gate expectations.

The surface symptoms are:

- ADRs appear inconsistent because they mix decision levels and time states.
- L0 and L1 appear noisy because they contain details that belong in L2 or generated facts.
- Governance gates appear weak because they do not fully validate semantic layer purity.
- Architecture reviews are hard to automate because reviewers cannot reliably distinguish current authority from historical evidence.
- AI agents can load many documents and still produce biased or inconsistent conclusions because the repository lacks one explicit learning map.

The root cause is:

> The project began with architecture modeling before a stable product-definition-to-engineering-frame learning curve had been made explicit and enforceable.

The remediation should not merely rewrite documents. It must make the learning curve a first-class engineering control system.

---

## 3. Remediation Principles

### 3.1 Separate authority from evidence

Human-authored prose may describe intent, rationale, and expected behavior. It must not be the source of truth for implementation facts when generated facts exist.

Generated facts own factual claims about code symbols, contracts, tests, module build shape, runtime config, and ADR inventory.

### 3.2 Separate decision level from affected level

An ADR can be decided at one level and affect other levels. These are not the same field.

Example:

- Decision level: L1
- Affected levels: L1, L2, contract, gate

This distinction prevents L0/L1 documents from becoming containers for lower-level implementation detail.

### 3.3 Separate product ownership from engineering navigation

Product artifacts own claims, requirements, user value, non-goals, acceptance expectations, and release semantics.

Engineering artifacts own module boundaries, package roots, EngineeringFrames, FunctionPoints, contracts, code facts, test facts, and gates.

Product does not own EngineeringFrames. Product requirements reach EngineeringFrames through FunctionPoints.

### 3.4 Separate frame from behavior

EngineeringFrame and FunctionPoint must not be conflated.

- EngineeringFrame answers: where should an AI or engineer land in the engineering system?
- FunctionPoint answers: what behavior is implemented, contracted, tested, and verified?

### 3.5 Make every architecture review ADR-based but not raw-ADR-based

Future architecture reviews should inspect ADRs, but the review authority must come from normalized ADR state:

- active guidance
- partial guidance
- superseded
- historical evidence
- remediation record

Raw historical ADR text can remain available, but it must not be treated as current authority without a normalized review record.

---

## 4. Target Operating Model

The repository should behave like a layered, queryable governance system:

```text
Product definition
  -> product claims
  -> requirements
  -> features
  -> FunctionPoints
  -> contracts
  -> generated facts
  -> gates

Engineering architecture
  -> modules
  -> EngineeringFrames
  -> FunctionPoints
  -> package roots / public surfaces
  -> L2 technical designs
  -> generated facts
  -> gates

Architecture decision governance
  -> normalized ADR index
  -> decision-level taxonomy
  -> layer-purity checks
  -> review gates
  -> architecture-status baseline
```

The system should allow an AI agent to answer these questions without relying on memory or inference:

1. What product outcome is this repository trying to produce?
2. Which requirements and features express that outcome?
3. Which FunctionPoints realize a feature?
4. Which EngineeringFrame anchors each FunctionPoint?
5. Which package roots, public interfaces, classes, functions, contracts, and tests provide evidence?
6. Which ADRs authorize the decision?
7. Which gates enforce the constraints?
8. Which documents are intent, which are authority, and which are generated facts?

---

## 5. Authority Lanes

The repository must adopt these authority lanes.

| Lane | Primary surfaces | Owns | Must not own |
|---|---|---|---|
| Product definition | `product/PRODUCT.md`, `product/claims.yaml`, `product/requirements.yaml`, `product/personas.yaml`, `product/journey.md` | product positioning, users, value claims, non-goals, user-visible acceptance expectations | Java classes, package roots, module gates, source-file migrations |
| AI onboarding | `README.md`, `CLAUDE.md`, `AGENTS.md`, `docs/onboarding/ai-understanding-path.md`, `docs/governance/ai-reading-path.yaml` | reading sequence, collaboration rules, fact-reading switch, entry map | architecture decisions, implementation truth, duplicated rule inventory |
| ADR governance | `docs/adr/*.yaml`, `docs/adr/normalized/*.yaml`, `docs/adr/review-index.md` | decision record, option tradeoffs, current authority state, supersession state | migration diary, exhaustive file inventories, generated fact replacement |
| DSL graph | `architecture/workspace.dsl`, `architecture/profile/*`, `architecture/features/*.dsl` | architecture identity, element IDs, relationships, views, graph traversal | long prose rationale, implementation evidence |
| L0 architecture | `architecture/docs/L0/ARCHITECTURE.md` | system boundary, global architectural constraints, cross-module invariants | class names, method names, test class lists, route implementation detail |
| L1 architecture | `architecture/docs/L1/`, `architecture/docs/L1/frames/` | module responsibility, EngineeringFrame boundary, package root, public API/SPI surface, inter-frame contract | private method call chain, persistence detail, test inventory, line-level facts |
| L2 design | `architecture/docs/L2/` | FunctionPoint technical design, class/method anchor, sequence, state transition, persistence, error path, test mapping | global policy, product claim, cross-module principle |
| Runtime contracts | `docs/contracts/`, OpenAPI/contracts | wire shape, route behavior, SPI signatures, runtime promise | product rationale, architecture taxonomy |
| Explicit mapping | `architecture/mappings/ai-understanding-map.yaml`, `architecture/mappings/ai-understanding-map.md` | traceability between product, architecture, frames, function points, contracts, facts, gates | raw prose rationale, direct implementation truth |
| Generated facts | `architecture/facts/generated/*.json` | code symbols, contract surfaces, tests, build/runtime facts, extracted ADR facts | hand-authored intent or future design |
| Governance | `docs/governance/rules/*.md`, `docs/governance/enforcers.yaml`, `gate/*`, `docs/governance/architecture-status.yaml` | enforceable policy, executable checks, baselines, conformance state | product or architecture design content |

Acceptance rule:

> Every artifact must be classifiable into exactly one primary authority lane. Cross-links are allowed; authority duplication is not.

---

## 6. Dual-Track Join Semantics

The repository must not collapse product/value concerns and engineering/structure concerns into one linear chain.

There are two tracks and two convergence concepts.

### 6.1 EngineeringFrame is the engineering landing and navigation point

EngineeringFrame answers:

- Which module does this work belong to?
- Which package root or package cluster is the engineering anchor?
- Which classes and interfaces form the stable boundary?
- Which public API/SPI surfaces are allowed to be used by other frames?
- Which responsibilities are in scope?
- Which responsibilities are explicitly out of scope?
- Which FunctionPoints are anchored here?
- Which contracts, facts, and gates are relevant to this engineering area?

EngineeringFrame should be package-oriented in Java terms, but it should not be reduced to a literal package. A Java package can be a strong implementation anchor, but an EngineeringFrame is a governed architectural object that may include:

- one primary Java package root
- subpackages
- public interfaces
- internal implementation classes
- inbound and outbound dependencies
- allowed collaborators
- forbidden collaborators
- FunctionPoint anchors
- contract references
- generated fact references
- governance rules and gates

### 6.2 FunctionPoint is the behavioral join point

FunctionPoint answers:

- What user-visible or system-visible behavior is being realized?
- Which requirement or feature requires it?
- Which EngineeringFrame anchors it?
- Which contract or SPI describes the promise?
- Which generated facts prove the code exists?
- Which tests verify it?
- Which gates enforce its architectural constraints?

FunctionPoint is where the value track and engineering track join for implementation.

### 6.3 Governed model

```text
Value axis:
ProductClaim -> Requirement -> Feature -> FunctionPoint

Structure axis:
Module -> EngineeringFrame -> FunctionPoint

Evidence axis:
FunctionPoint -> Contract -> GeneratedFact -> Gate

Decision axis:
ADR -> L0/L1/L2/Contract/Gate authority

Derived navigation projection:
Feature -> traverses -> EngineeringFrame
```

The derived projection must not be interpreted as ownership.

`Feature -> traverses -> EngineeringFrame` is derived from:

```text
Feature -> requires -> FunctionPoint
EngineeringFrame -> anchors -> FunctionPoint
```

The governance gate must reject any model where a ProductClaim, Requirement, or Feature directly owns an EngineeringFrame.

### 6.4 Why the join belongs at FunctionPoint

The behavioral join belongs at FunctionPoint because only FunctionPoint can be simultaneously:

- demanded by a product requirement
- anchored by an EngineeringFrame
- described by a contract
- evidenced by generated facts
- verified by tests
- enforced by gates

EngineeringFrame remains essential, but it is a navigation and boundary object, not the behavioral proof object.

---

## 7. AI Understanding Path

The AI learning curve should become a governed path, not an informal reading suggestion.

### 7.1 Required reading sequence

The repository should publish the sequence in `docs/governance/ai-reading-path.yaml` and mirror it in human-readable form at `docs/onboarding/ai-understanding-path.md`.

```yaml
version: 1
status: active

entry_contract:
  rule: "AI agents must load the path in order unless a task is explicitly narrower."
  factual_claim_switch:
    applies_to:
      - code
      - contracts
      - tests
      - dependencies
      - runtime_behavior
      - verification
    read_before_prose:
      - "architecture/facts/generated/code-symbols.json"
      - "architecture/facts/generated/contract-surfaces.json"
      - "architecture/facts/generated/tests.json"
      - "architecture/facts/generated/module-build.json"
      - "architecture/facts/generated/runtime-config.json"
      - "architecture/facts/generated/adrs.json"

orientation_learning_path:
  - step: 1
    name: repository_entry
    surfaces:
      - "README.md"
      - "CLAUDE.md"
      - "AGENTS.md"
    expected_understanding:
      - "How to collaborate in this repository."
      - "Which sources are authoritative."
      - "When generated facts outrank prose."

  - step: 2
    name: product_definition
    surfaces:
      - "product/PRODUCT.md"
      - "product/claims.yaml"
      - "product/requirements.yaml"
      - "product/personas.yaml"
      - "product/journey.md"
    expected_understanding:
      - "What product outcome the repository serves."
      - "Which value claims are active."
      - "Which requirements are in scope and out of scope."

  - step: 3
    name: architecture_anchor
    surfaces:
      - "architecture/workspace.dsl"
      - "architecture/README.md"
      - "architecture/docs/L0/ARCHITECTURE.md"
      - "docs/adr/normalized/index.yaml"
      - "docs/adr/review-index.md"
    expected_understanding:
      - "System boundary."
      - "Architecture element identities."
      - "Current decision authority state."
      - "Global constraints."

  - step: 4
    name: engineering_frame_anchor
    surfaces:
      - "architecture/features/engineering-frames.dsl"
      - "architecture/docs/L1/engineering-frames.md"
      - "architecture/docs/L1/frames/"
      - "architecture/docs/L1/"
    expected_understanding:
      - "Which EngineeringFrames exist."
      - "Which package roots and module boundaries they govern."
      - "Which FunctionPoints they anchor."

  - step: 5
    name: demand_to_behavior_mapping
    surfaces:
      - "architecture/features/features.dsl"
      - "architecture/features/function-points.dsl"
      - "architecture/mappings/ai-understanding-map.yaml"
      - "architecture/docs/L2/"
    expected_understanding:
      - "Which features require which FunctionPoints."
      - "Which FunctionPoints belong to which EngineeringFrames."
      - "Which L2 designs own implementation detail."

  - step: 6
    name: contract_and_evidence
    surfaces:
      - "docs/contracts/"
      - "architecture/facts/generated/"
      - "gate/check_architecture_sync.sh"
    expected_understanding:
      - "Which runtime promises exist."
      - "Which generated facts prove implementation state."
      - "Which gates must pass before work is accepted."
```

### 7.2 Required AI output discipline

AI agents should be required to cite IDs, not merely prose file names, when making claims about:

- code symbols
- contracts
- tests
- feature state
- FunctionPoint state
- EngineeringFrame state
- ADR authority state
- gate coverage

The remediation should add a rule that rejects architecture review comments that claim implementation truth without a fact ID or gate reference when generated facts exist.

---

## 8. Explicit Mapping Artifact

The repository needs one queryable map that makes the dual-track relationship visible.

### 8.1 Files to create

- `architecture/mappings/README.md`
- `architecture/mappings/ai-understanding-map.yaml`
- `architecture/mappings/ai-understanding-map.md`
- `architecture/mappings/schema/ai-understanding-map.schema.yaml`

### 8.2 Schema shape

```yaml
version: 1
status: active

required_axes:
  - value_axis
  - structure_axis
  - join
  - evidence_axis
  - decision_axis
  - governance_axis

entries:
  - id: map.run_submission.behavior
    value_axis:
      product_claim: claim.run_observability
      requirement: req.submit_run
      feature: feature.run_submission
    structure_axis:
      module: module.service_runtime
      engineering_frame: frame.run_http_boundary
    join:
      function_point: fp.submit_run
      join_semantics: behavioral_join
      derived_navigation:
        feature_traverses_engineering_frame: true
        ownership: false
        derivation:
          - "feature.run_submission requires fp.submit_run"
          - "frame.run_http_boundary anchors fp.submit_run"
    evidence_axis:
      contracts:
        - contract.http.runs.post
      generated_facts:
        - code-symbol.com.huawei.ascend.service.runtime.api.runscontroller
        - contract-surface.http.post.v1.runs
      tests:
        - test.runscontroller.submit_run
      gates:
        - gate.architecture_sync
        - gate.feature_readiness
    decision_axis:
      adrs:
        - adr.0147
        - adr.0150
        - adr.0154
      constraints:
        - L0.C-XX
      rules:
        - G-15
    governance_axis:
      frame_card: architecture/docs/L1/frames/run-http-boundary.md
      l2_design: architecture/docs/L2/run-submission/README.md
      readiness_state: shipped
```

### 8.3 Map invariants

The map must enforce:

- every shipped Feature maps to at least one FunctionPoint
- every shipped FunctionPoint maps to exactly one primary EngineeringFrame
- every shipped FunctionPoint has at least one contract or explicit no-contract rationale
- every shipped FunctionPoint has generated fact evidence
- every shipped FunctionPoint has test evidence or an explicit approved exception
- every mapped EngineeringFrame has a DSL identity and a frame card
- no ProductClaim, Requirement, or Feature owns an EngineeringFrame
- every derived Feature-to-EngineeringFrame navigation edge is computed from Feature-to-FunctionPoint and EngineeringFrame-to-FunctionPoint edges

---

## 9. ADR Governance Model

Historical ADR cleanup is the highest-priority remediation because future architecture review depends on ADR clarity.

### 9.1 Required ADR states

Every ADR must be classified into exactly one current authority state:

| State | Meaning | Review behavior |
|---|---|---|
| `active_guidance` | Current decision authority. | Can be cited as governing architecture. |
| `partial_guidance` | Some guidance remains active, but some content is stale, lower-level, or superseded. | Can be cited only through normalized active clauses. |
| `superseded` | Replaced by later decision. | Cannot be cited as current authority except for history. |
| `historical_evidence` | Explains why the system evolved, but does not govern future work. | Can be cited for context only. |
| `remediation_record` | Captures cleanup work or review findings. | Can be cited for governance traceability, not architecture authority. |

### 9.2 Required normalized ADR fields

Create `docs/governance/adr-governance-policy.yaml`:

```yaml
version: 1
status: active

normalized_adr_required_fields:
  - adr
  - raw_path
  - current_state
  - decision_level
  - affected_levels
  - view
  - decision_type
  - clean_decision_summary
  - active_guidance
  - non_authoritative_legacy_content
  - supersedes
  - superseded_by
  - dsl_refs
  - l0_constraint_refs
  - l1_refs
  - l2_refs
  - contract_refs
  - fact_refs
  - gate_refs
  - review_notes

state_rules:
  active_guidance:
    active_guidance_required: true
    superseded_by_allowed: false
  partial_guidance:
    active_guidance_required: true
    non_authoritative_legacy_content_required: true
  superseded:
    superseded_by_required: true
    active_guidance_allowed: false
  historical_evidence:
    active_guidance_allowed: false
  remediation_record:
    gate_refs_required_when_gate_change_claimed: true
```

### 9.3 Decision taxonomy

Create `docs/governance/adr-taxonomy.yaml`:

```yaml
version: 1
status: active

decision_levels:
  L0:
    allowed_decision_types:
      - system_boundary
      - global_constraint
      - architecture_principle
      - cross_module_policy
      - governance_invariant
    forbidden_decision_types:
      - implementation_class
      - source_file_move
      - method_signature
      - test_class_inventory
      - private_call_chain

  L1:
    allowed_decision_types:
      - module_responsibility
      - engineering_frame_boundary
      - package_root
      - public_api_surface
      - public_spi_surface
      - cross_frame_contract
      - module_dependency_policy
    forbidden_decision_types:
      - private_method_anchor
      - implementation_call_chain
      - database_index_detail
      - line_level_test_inventory

  L2:
    allowed_decision_types:
      - function_point_design
      - class_method_anchor
      - runtime_sequence
      - state_transition
      - persistence_detail
      - error_path
      - test_mapping
    forbidden_decision_types:
      - product_value_claim
      - global_architecture_policy

  contract:
    allowed_decision_types:
      - http_contract
      - spi_contract
      - envelope_shape
      - compatibility_rule

  gate:
    allowed_decision_types:
      - enforcement_rule
      - verification_command
      - baseline_metric
```

### 9.4 Normalized ADR example

Create one normalized file per historical ADR under `docs/adr/normalized/`.

```yaml
adr: ADR-0147
raw_path: docs/adr/0147-architecture-workspace-root.yaml
current_state: active_guidance
decision_level: L0
affected_levels:
  - L0
  - L1
  - DSL
  - gate
view: architecture_authority
decision_type: architecture_authority_root
clean_decision_summary: >
  The architecture workspace is the authority root for architecture identity,
  relationships, views, documentation links, and ADR closure.
active_guidance:
  - "Architecture authoring must flow through architecture/workspace.dsl and its closure."
  - "Generated views and included docs must remain synchronized with the workspace."
non_authoritative_legacy_content: []
supersedes: []
superseded_by: []
dsl_refs:
  - architecture/workspace.dsl
l0_constraint_refs: []
l1_refs: []
l2_refs: []
contract_refs: []
fact_refs:
  - architecture/facts/generated/adrs.json
gate_refs:
  - gate/check_architecture_sync.sh
review_notes:
  reviewer: architecture-governance-remediation
  reviewed_on: 2026-05-29
  notes:
    - "Keep as active architecture authority root decision."
```

### 9.5 ADR remediation ledger

Create `docs/governance/adr-remediation-ledger.yaml`:

```yaml
version: 1
status: active

entries:
  - adr: ADR-0001
    raw_path: docs/adr/0001-example.yaml
    normalized_path: docs/adr/normalized/ADR-0001.yaml
    current_state: partial_guidance
    remediation_action: split_active_guidance_from_legacy_detail
    required_follow_up:
      - move_method_level_detail_to_l2
      - replace_source_file_claims_with_generated_fact_refs
    owner: architecture-governance
    due_wave: wave_1
```

### 9.6 ADR review index

Create `docs/adr/review-index.md` with:

- total ADR count
- normalized ADR count
- unclassified ADR count
- active guidance count
- partial guidance count
- superseded count
- historical evidence count
- remediation record count
- ADRs blocked from architecture review
- ADRs allowed as current authority
- ADRs allowed only as context

Acceptance rule:

> Architecture review must fail if an ADR cited as authority has no normalized record.

---

## 10. Layer Purity Policy

Layer purity must be expressed as data and enforced by gates.

### 10.1 Files to create

- `docs/governance/layer-purity-policy.yaml`
- `docs/governance/layer-purity-temporary-violations.yaml`
- `gate/lib/check_layer_purity.py`
- `gate/lib/check_l2_detail_sink.py`

### 10.2 Policy shape

```yaml
version: 1
status: active

layers:
  L0:
    owns:
      - system_boundary
      - global_constraint
      - cross_module_invariant
      - architectural_principle
    may_reference:
      - adr_id
      - dsl_element_id
      - rule_id
    forbids:
      - java_class_name
      - method_name
      - source_file_path
      - test_class_name
      - endpoint_handler_class
      - implementation_sequence

  L1:
    owns:
      - module_responsibility
      - engineering_frame_boundary
      - package_root
      - public_interface
      - public_api_or_spi_surface
      - inter_frame_dependency
    may_reference:
      - function_point_id
      - contract_id
      - generated_fact_id
      - l2_design_id
    forbids:
      - private_method_call_chain
      - line_level_test_inventory
      - persistence_algorithm_detail
      - implementation_migration_diary

  L2:
    owns:
      - function_point_design
      - class_anchor
      - method_anchor
      - sequence_detail
      - state_transition
      - persistence_detail
      - error_path
      - test_mapping
    forbids:
      - product_positioning
      - global_constraint
      - cross_module_policy
```

### 10.3 Temporary violation process

Temporary violations should be visible and time-boxed:

```yaml
version: 1
status: active

violations:
  - id: violation.layer.L1.method-detail.001
    file: architecture/docs/L1/example-module.md
    detected_pattern: private_method_call_chain
    allowed_until: 2026-06-07
    required_resolution:
      - move_detail_to_l2
      - replace_l1_text_with_function_point_reference
    owner: architecture-governance
    reason: "Existing content predates L2 detail sink activation."
```

Acceptance rule:

> The gate may run in advisory mode during the first cleanup wave, but no new L0/L1 layer impurity may be introduced after the policy is active.

---

## 11. EngineeringFrame Cards

EngineeringFrame must become a real engineering anchor, not a loose label.

### 11.1 Files to create

- `architecture/docs/L1/frames/README.md`
- `architecture/docs/L1/frames/_template.md`
- `architecture/features/engineering-frames.dsl`

### 11.2 Required EngineeringFrame card sections

Each EngineeringFrame card must include:

```markdown
# <EngineeringFrame Name>

Frame ID: `frame.<id>`
Module ID: `module.<id>`
Primary package root: `<java.package.root>`
Status: proposed | active | deprecated

## Capability Position

What this frame can do.

## Explicit Non-Capabilities

What this frame must not do.

## Package Boundary

- Primary package root:
- Included subpackages:
- Excluded packages:

## Public Surfaces

- Inbound API/SPI:
- Outbound API/SPI:
- Published events:
- Consumed events:

## Internal Class Inventory Policy

This section may reference generated fact IDs and L2 class anchors.
It must not hand-maintain a full class inventory when generated facts exist.

## FunctionPoint Anchors

| FunctionPoint | Role | L2 design | Contract | Facts | Tests |
|---|---|---|---|---|---|

## Allowed Collaborators

| Collaborator frame | Direction | Contract | Rationale |
|---|---|---|---|

## Forbidden Collaborators

| Forbidden dependency | Reason | Gate |
|---|---|---|

## ADR Authority

| ADR | Current state | Clause |
|---|---|---|

## Gates

| Gate | What it enforces |
|---|---|
```

### 11.3 EngineeringFrame gate rules

The gate must fail if an active EngineeringFrame:

- has no DSL identity
- has no frame card
- has no primary package root
- has no capability statement
- has no non-capability statement
- has no FunctionPoint anchor or explicit empty-frame rationale
- references raw ADR authority without normalized ADR state
- hand-maintains class facts that should come from generated facts

---

## 12. FunctionPoint Design Model

FunctionPoint is the implementable behavior unit.

### 12.1 Files to create or strengthen

- `architecture/features/function-points.dsl`
- `architecture/docs/L2/_template.md`
- `architecture/docs/L2/<function-point-id>/README.md`
- `docs/governance/feature-readiness-policy.yaml`
- `gate/lib/check_feature_readiness.py`

### 12.2 Required FunctionPoint L2 design sections

```markdown
# <FunctionPoint Name>

FunctionPoint ID: `fp.<id>`
Owning EngineeringFrame: `frame.<id>`
Required by Feature: `feature.<id>`
Status: proposed | active | shipped | deprecated

## Behavior Contract

The externally visible or system-visible behavior.

## Inputs and Outputs

| Input | Source | Validation | Output |
|---|---|---|---|

## Runtime Sequence

Step-by-step flow at class/interface level.

## Class and Method Anchors

| Anchor | Type | Generated fact ID | Notes |
|---|---|---|---|

## Error Paths

| Condition | Response | Contract | Test |
|---|---|---|---|

## Persistence and State

Only include if this FunctionPoint owns persistence or state transitions.

## Contract References

| Contract | Promise |
|---|---|

## Test Evidence

| Test fact | Coverage purpose |
|---|---|

## Gate Evidence

| Gate | Expected assertion |
|---|---|
```

### 12.3 Feature readiness policy

Create `docs/governance/feature-readiness-policy.yaml`:

```yaml
version: 1
status: active

required_axes_for_shipped_work:
  value_axis:
    required:
      - product_claim_or_requirement
      - feature
      - function_point
  structure_axis:
    required:
      - module
      - engineering_frame
      - function_point_anchor
  join:
    required:
      - function_point_behavioral_join
      - derived_feature_to_frame_navigation
  evidence_axis:
    required:
      - contract_or_no_contract_rationale
      - generated_fact_ref
      - test_ref_or_exception
      - gate_ref
  decision_axis:
    required:
      - normalized_adr_ref

status_rules:
  proposed:
    may_lack_facts: true
  active:
    requires_l2_design: true
    requires_frame_anchor: true
  shipped:
    requires_contract_or_exception: true
    requires_generated_facts: true
    requires_tests_or_exception: true
    requires_gate_ref: true
    requires_normalized_adr: true
```

Acceptance rule:

> No Feature can be marked shipped unless every required axis is complete.

---

## 13. Governance Gates

### 13.1 New gate helpers

Add these executable checks:

- `gate/lib/check_layer_purity.py`
- `gate/lib/check_adr_taxonomy.py`
- `gate/lib/check_historical_adr_governance.py`
- `gate/lib/check_l2_detail_sink.py`
- `gate/lib/check_feature_readiness.py`
- `gate/lib/check_ai_reading_path.py`
- `gate/lib/check_ai_understanding_map.py`
- `gate/lib/check_engineering_frame_cards.py`

### 13.2 Gate integration

Modify:

- `gate/check_architecture_sync.sh`
- `gate/test_architecture_sync_gate.sh`
- `docs/governance/enforcers.yaml`
- `docs/governance/architecture-status.yaml`

### 13.3 Gate behavior

The gate should run in three modes:

| Mode | Purpose | When |
|---|---|---|
| advisory | report existing violations without blocking | first cleanup wave |
| changed-files-blocking | block new violations in changed files | after policy files land |
| full-blocking | block all violations | after historical cleanup reaches acceptance bar |

### 13.4 Required checks

The governance gate must check:

- L0 does not contain implementation-level detail.
- L1 does not contain FunctionPoint-level implementation detail unless referenced to L2.
- L2 does not contain product positioning or global architecture policy.
- Every cited authority ADR has a normalized state.
- Every raw ADR has a ledger entry.
- Every active EngineeringFrame has a frame card.
- Every frame card has package boundary, capability, non-capability, public surfaces, and FunctionPoint anchors.
- Every shipped FunctionPoint has value, structure, evidence, decision, and governance axes.
- Every derived Feature-to-EngineeringFrame edge is derivable through FunctionPoint.
- Generated facts are used for factual claims.
- Architecture-status baseline is updated only through explicit governance changes.

---

## 14. Documentation Architecture Cleanup

### 14.1 README and onboarding

Update:

- `README.md`
- `CLAUDE.md`
- `AGENTS.md`
- `docs/quickstart.md`
- `architecture/README.md`
- `docs/onboarding/ai-understanding-path.md`

The top-level documentation must communicate:

1. Start from product definition.
2. Anchor architecture through DSL and normalized ADRs.
3. Use L0 only for global constraints.
4. Use L1 to find EngineeringFrames and module boundaries.
5. Use FunctionPoints to join requirements to implementation behavior.
6. Use contracts and generated facts for implementation truth.
7. Use gates for acceptance.

### 14.2 L0 cleanup

L0 must be reduced to:

- system boundary
- global constraints
- cross-module invariants
- architecture principles
- references to governing ADRs and DSL elements

Move out:

- class names
- method names
- test class lists
- source path migrations
- route implementation detail
- implementation sequences

### 14.3 L1 cleanup

L1 must be reduced to:

- module purpose
- frame boundaries
- package roots
- public API/SPI surfaces
- dependencies between frames
- FunctionPoint anchor references
- L2 design links
- contract links
- generated fact links

Move out:

- private call chains
- state-transition details
- persistence implementation details
- line-level or class-level test inventories
- migration diaries

### 14.4 L2 activation

L2 must become the accepted home for:

- FunctionPoint technical design
- class and method anchors
- runtime sequences
- persistence details
- error paths
- test mappings
- implementation constraints that are too specific for L1

---

## 15. Required Governance Rule Cards

Create new rule cards:

- `docs/governance/rules/rule-G-27-layer-purity.md`
- `docs/governance/rules/rule-G-28-adr-normalization.md`
- `docs/governance/rules/rule-G-29-engineering-frame-cards.md`
- `docs/governance/rules/rule-G-30-function-point-readiness.md`
- `docs/governance/rules/rule-G-31-ai-understanding-map.md`
- `docs/governance/rules/rule-G-32-ai-reading-path.md`
- `docs/governance/rules/rule-G-33-generated-fact-citation.md`

Each rule card must include:

- rule ID
- status
- purpose
- authority ADR
- governed artifacts
- required behavior
- forbidden behavior
- enforcer command
- failure examples
- remediation examples
- deferred sub-clauses if needed

No rule card should carry baseline counts directly. Baselines belong in `docs/governance/architecture-status.yaml`.

---

## 16. Detailed Remediation Tasks

### Task 1: Establish layer purity policy

**Files:**

- Create: `docs/governance/layer-purity-policy.yaml`
- Create: `docs/governance/layer-purity-temporary-violations.yaml`
- Create: `docs/governance/rules/rule-G-27-layer-purity.md`
- Modify: `docs/governance/enforcers.yaml`

- [ ] Step 1: Add `layer-purity-policy.yaml` with allowed and forbidden content categories for L0, L1, and L2.
- [ ] Step 2: Add `layer-purity-temporary-violations.yaml` with existing known violations and resolution deadlines.
- [ ] Step 3: Add rule card G-27 and map it to an authority ADR.
- [ ] Step 4: Register the future enforcer in `docs/governance/enforcers.yaml`.
- [ ] Step 5: Run a text scan for obvious forbidden patterns in L0 and L1.

Verification:

```bash
python3 gate/lib/check_layer_purity.py --repo . --mode advisory
```

Expected initial result:

```text
ADVISORY: existing layer-purity violations detected
```

### Task 2: Establish ADR taxonomy and normalized ADR schema

**Files:**

- Create: `docs/governance/adr-governance-policy.yaml`
- Create: `docs/governance/adr-taxonomy.yaml`
- Create: `docs/governance/rules/rule-G-28-adr-normalization.md`
- Modify: `docs/governance/enforcers.yaml`

- [ ] Step 1: Add the normalized ADR required field list.
- [ ] Step 2: Add `decision_level` and `affected_levels` taxonomy.
- [ ] Step 3: Add allowed and forbidden decision types for L0, L1, L2, contract, and gate.
- [ ] Step 4: Add rule card G-28.
- [ ] Step 5: Register ADR taxonomy enforcer.

Verification:

```bash
python3 gate/lib/check_adr_taxonomy.py --repo . --mode advisory
```

Expected initial result:

```text
ADVISORY: raw ADRs without normalized state detected
```

### Task 3: Bulk-govern historical ADR corpus

**Files:**

- Create: `docs/adr/normalized/README.md`
- Create: `docs/adr/normalized/index.yaml`
- Create: `docs/adr/normalized/ADR-0001.yaml` through latest ADR
- Create: `docs/adr/review-index.md`
- Create: `docs/governance/adr-remediation-ledger.yaml`

- [ ] Step 1: Inventory every ADR under `docs/adr/`.
- [ ] Step 2: Create one normalized ADR file per raw ADR.
- [ ] Step 3: Classify each ADR into `active_guidance`, `partial_guidance`, `superseded`, `historical_evidence`, or `remediation_record`.
- [ ] Step 4: Extract clean active guidance clauses.
- [ ] Step 5: Mark implementation details and stale content as non-authoritative legacy content.
- [ ] Step 6: Connect each active clause to DSL, L0, L1, L2, contract, fact, and gate refs where applicable.
- [ ] Step 7: Generate `docs/adr/review-index.md`.
- [ ] Step 8: Add unresolved items to `docs/governance/adr-remediation-ledger.yaml`.

Verification:

```bash
python3 gate/lib/check_historical_adr_governance.py --repo . --mode advisory
```

Expected result after Task 3:

```text
PASS: every ADR has normalized state and ledger coverage
```

### Task 4: Add semantic layer-purity gate

**Files:**

- Create: `gate/lib/check_layer_purity.py`
- Modify: `gate/check_architecture_sync.sh`
- Modify: `gate/test_architecture_sync_gate.sh`

- [ ] Step 1: Implement a gate that loads `layer-purity-policy.yaml`.
- [ ] Step 2: Scan L0, L1, and L2 files for forbidden patterns.
- [ ] Step 3: Support `--mode advisory`, `--mode changed-files-blocking`, and `--mode full-blocking`.
- [ ] Step 4: Respect `layer-purity-temporary-violations.yaml`.
- [ ] Step 5: Add shell gate integration.
- [ ] Step 6: Add gate self-tests.

Verification:

```bash
bash gate/test_architecture_sync_gate.sh
bash gate/check_architecture_sync.sh
```

### Task 5: Add ADR taxonomy and historical governance gates

**Files:**

- Create: `gate/lib/check_adr_taxonomy.py`
- Create: `gate/lib/check_historical_adr_governance.py`
- Modify: `gate/check_architecture_sync.sh`
- Modify: `gate/test_architecture_sync_gate.sh`

- [ ] Step 1: Implement taxonomy validation for normalized ADR files.
- [ ] Step 2: Reject missing normalized state for cited authority ADRs.
- [ ] Step 3: Reject `decision_level` values whose decision type is forbidden.
- [ ] Step 4: Reject raw ADR authority citations without normalized ADR record.
- [ ] Step 5: Add self-tests for active, partial, superseded, and historical ADR cases.

Verification:

```bash
python3 gate/lib/check_adr_taxonomy.py --repo . --mode full-blocking
python3 gate/lib/check_historical_adr_governance.py --repo . --mode full-blocking
```

### Task 6: Activate L2 as the detail sink

**Files:**

- Create: `architecture/docs/L2/_template.md`
- Create or update: `architecture/docs/L2/<function-point-id>/README.md`
- Create: `gate/lib/check_l2_detail_sink.py`
- Create: `docs/governance/rules/rule-G-30-function-point-readiness.md`

- [ ] Step 1: Create the L2 template with FunctionPoint technical design sections.
- [ ] Step 2: Move method/class/sequence/persistence/test mapping details out of L0/L1 into L2 designs.
- [ ] Step 3: Replace removed L0/L1 detail with links to FunctionPoint IDs and L2 designs.
- [ ] Step 4: Add a gate that detects implementation detail left in L0/L1.
- [ ] Step 5: Add self-tests showing L1 method-level detail fails and L2 method-level detail passes.

Verification:

```bash
python3 gate/lib/check_l2_detail_sink.py --repo . --mode changed-files-blocking
```

### Task 7: Wire EngineeringFrame cards into DSL governance

**Files:**

- Create: `architecture/features/engineering-frames.dsl`
- Create: `architecture/docs/L1/frames/README.md`
- Create: `architecture/docs/L1/frames/_template.md`
- Create: `gate/lib/check_engineering_frame_cards.py`
- Create: `docs/governance/rules/rule-G-29-engineering-frame-cards.md`

- [ ] Step 1: Define EngineeringFrame DSL element style and relationship vocabulary.
- [ ] Step 2: Create frame card template.
- [ ] Step 3: Add frame cards for the existing active module areas.
- [ ] Step 4: Add package root and public surface sections to every active frame.
- [ ] Step 5: Add FunctionPoint anchor table to every active frame.
- [ ] Step 6: Add gate validation for DSL identity, card existence, package boundary, capability, non-capability, and anchors.

Verification:

```bash
python3 gate/lib/check_engineering_frame_cards.py --repo . --mode changed-files-blocking
```

### Task 8: Make the AI understanding map explicit

**Files:**

- Create: `architecture/mappings/README.md`
- Create: `architecture/mappings/ai-understanding-map.yaml`
- Create: `architecture/mappings/ai-understanding-map.md`
- Create: `architecture/mappings/schema/ai-understanding-map.schema.yaml`
- Create: `gate/lib/check_ai_understanding_map.py`
- Create: `docs/governance/rules/rule-G-31-ai-understanding-map.md`

- [ ] Step 1: Add schema for value axis, structure axis, join, evidence axis, decision axis, and governance axis.
- [ ] Step 2: Add entries for current shipped or active features.
- [ ] Step 3: Encode derived Feature-to-EngineeringFrame navigation only through FunctionPoint derivation.
- [ ] Step 4: Add rendered Markdown index for human readers.
- [ ] Step 5: Add gate validation.

Verification:

```bash
python3 gate/lib/check_ai_understanding_map.py --repo . --mode changed-files-blocking
```

### Task 9: Redesign AI documentation entry path

**Files:**

- Create: `docs/governance/ai-reading-path.yaml`
- Create: `docs/onboarding/ai-understanding-path.md`
- Create: `gate/lib/check_ai_reading_path.py`
- Create: `docs/governance/rules/rule-G-32-ai-reading-path.md`
- Modify: `README.md`
- Modify: `CLAUDE.md`
- Modify: `AGENTS.md`
- Modify: `architecture/README.md`

- [ ] Step 1: Add machine-readable reading path.
- [ ] Step 2: Add human-readable onboarding path.
- [ ] Step 3: Update entry docs to point to the same path without duplicating counts or authority.
- [ ] Step 4: Add fact-reading switch requirement.
- [ ] Step 5: Add gate that checks all referenced surfaces exist and entry docs point to the canonical path.

Verification:

```bash
python3 gate/lib/check_ai_reading_path.py --repo . --mode full-blocking
```

### Task 10: Establish generated-fact citation discipline

**Files:**

- Create: `docs/governance/rules/rule-G-33-generated-fact-citation.md`
- Modify: `docs/governance/enforcers.yaml`
- Modify: `gate/check_architecture_sync.sh`
- Modify if needed: `tools/architecture-workspace/`

- [ ] Step 1: Define which claim types require generated fact IDs.
- [ ] Step 2: Add rule examples for valid and invalid code/contract/test claims.
- [ ] Step 3: Ensure extractor output includes stable IDs needed by documentation.
- [ ] Step 4: Add gate warning or failure when prose makes implementation truth claims without fact IDs.

Verification:

```bash
./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts -Dexec.args="--repo . --out architecture/facts/generated --check"
bash gate/check_architecture_sync.sh
```

### Task 11: Clean active L0 and L1 surfaces

**Files:**

- Modify: `architecture/docs/L0/ARCHITECTURE.md`
- Modify: `architecture/docs/L1/README.md`
- Modify: `architecture/docs/L1/<module>.md`
- Modify: `architecture/docs/L1/<module>/`
- Create or modify: `architecture/docs/L2/<function-point-id>/README.md`

- [ ] Step 1: Scan L0 for source paths, class names, method names, test class names, and implementation sequences.
- [ ] Step 2: Move each L0 violation to L1, L2, contract, generated fact, or gate surface as appropriate.
- [ ] Step 3: Scan L1 for private method detail, persistence detail, test inventories, and migration diary content.
- [ ] Step 4: Move each L1 violation to L2 or generated fact references.
- [ ] Step 5: Replace removed detail with stable IDs and links.
- [ ] Step 6: Re-run layer purity and L2 sink gates.

Verification:

```bash
python3 gate/lib/check_layer_purity.py --repo . --mode full-blocking
python3 gate/lib/check_l2_detail_sink.py --repo . --mode full-blocking
```

### Task 12: Governance closure and baseline update

**Files:**

- Modify: `docs/governance/architecture-status.yaml`
- Modify: `docs/governance/enforcers.yaml`
- Modify: `gate/check_architecture_sync.sh`
- Modify: `gate/test_architecture_sync_gate.sh`

- [ ] Step 1: Record new governance capability baselines.
- [ ] Step 2: Record new gate inventory.
- [ ] Step 3: Promote advisory gates to changed-files-blocking after initial cleanup.
- [ ] Step 4: Promote changed-files-blocking gates to full-blocking after historical ADR cleanup.
- [ ] Step 5: Run full verification.

Verification:

```bash
bash gate/test_architecture_sync_gate.sh
bash gate/check_architecture_sync.sh
./mvnw -Pquality verify
```

---

## 17. Rollout Plan

### Wave 0: Freeze and audit

Duration: 1 to 2 days

Actions:

- Freeze new ADR expansion except remediation ADRs.
- Freeze broad L0/L1 rewrites except cleanup work.
- Inventory raw ADRs.
- Inventory L0/L1 layer impurity.
- Inventory missing EngineeringFrame cards.
- Inventory shipped Features and FunctionPoints missing mapping evidence.

Exit criteria:

- `adr-remediation-ledger.yaml` exists.
- `layer-purity-temporary-violations.yaml` exists.
- Advisory gate reports are available.

### Wave 1: Historical ADR normalization

Duration: 3 to 5 days, depending on ADR count

Actions:

- Create normalized ADR file for every raw ADR.
- Classify authority state.
- Extract clean active guidance.
- Mark non-authoritative legacy content.
- Build review index.

Exit criteria:

- No raw ADR is unclassified.
- No architecture review may cite raw ADR authority without normalized state.

### Wave 2: Advisory gates

Duration: 2 to 3 days

Actions:

- Add layer-purity, ADR taxonomy, historical ADR governance, EngineeringFrame, FunctionPoint, AI map, and AI reading path gates in advisory mode.
- Add self-tests.
- Integrate into `gate/check_architecture_sync.sh`.

Exit criteria:

- Advisory gates run from the canonical gate entrypoint.
- Existing violations are visible and triaged.

### Wave 3: L2 and EngineeringFrame activation

Duration: 3 to 7 days

Actions:

- Create EngineeringFrame DSL and cards.
- Create L2 FunctionPoint design templates.
- Move implementation details from L0/L1 into L2.
- Populate FunctionPoint anchors.

Exit criteria:

- Active frames have DSL IDs and cards.
- Shipped FunctionPoints have L2 designs or explicit transition records.

### Wave 4: Blocking promotion for new work

Duration: immediate after Wave 3

Actions:

- Promote gates to changed-files-blocking.
- Block new L0/L1 impurity.
- Block new raw ADR authority citations.
- Block shipped feature state without FunctionPoint evidence.

Exit criteria:

- New work cannot make governance clarity worse.

### Wave 5: Active surface cleanup

Duration: 1 to 2 weeks

Actions:

- Clean L0.
- Clean L1.
- Complete frame cards.
- Complete AI understanding map.
- Complete reading path.
- Replace implementation truth prose with generated fact IDs.

Exit criteria:

- Layer-purity gate passes in full-blocking mode.
- Feature readiness gate passes for shipped work.
- Historical ADR governance gate passes.

### Wave 6: Large-scale implementation readiness

Duration: after full governance gates pass

Actions:

- Resume broad engineering implementation.
- Require every new feature proposal to enter through product definition and FunctionPoint mapping.
- Require architecture review to check normalized ADR state and explicit map entries.
- Require implementation review to check generated facts, contracts, tests, and gates.

Exit criteria:

- AI agents can enter the repo and recover product intent, architecture authority, engineering frames, function points, contracts, facts, and gate state without private context.

---

## 18. Architecture Review Rules After Remediation

Every architecture review must answer:

1. Which product claim, requirement, or feature motivates this work?
2. Which normalized ADR authorizes the architectural decision?
3. What is the decision level?
4. What levels are affected?
5. Which L0 constraint is touched, if any?
6. Which L1 module or EngineeringFrame is touched?
7. Which FunctionPoints are created, modified, or retired?
8. Which contracts change?
9. Which generated facts prove current implementation state?
10. Which gates enforce the decision?
11. Is any L0/L1 detail actually L2 detail?
12. Is any claimed implementation truth missing a generated fact reference?

Architecture review must reject:

- raw ADR authority citations without normalized ADR state
- product artifacts that directly own EngineeringFrames
- L0 documents with class, method, source-path, or test inventory detail
- L1 documents with private method or persistence implementation detail
- shipped features without FunctionPoint evidence
- FunctionPoints without EngineeringFrame anchor
- FunctionPoints without contract/fact/test/gate evidence or approved exception
- generated facts edited by hand

---

## 19. Acceptance Bar

The remediation is complete only when all conditions below are true.

### ADR acceptance

- Every raw ADR has a normalized ADR record.
- Every normalized ADR has a current authority state.
- Every ADR cited as current authority is `active_guidance` or `partial_guidance`.
- Every `partial_guidance` ADR clearly separates active guidance from non-authoritative legacy content.
- Every `superseded` ADR points to its replacement.
- Every architecture review cites normalized ADR state.

### Layer acceptance

- L0 contains no implementation classes, methods, test inventories, source path migrations, or runtime sequences.
- L1 contains no private method call chains, persistence implementation details, or line-level test inventories.
- L2 contains FunctionPoint technical detail and does not redefine product positioning or global architecture policy.

### EngineeringFrame acceptance

- Every active EngineeringFrame has a DSL identity.
- Every active EngineeringFrame has a frame card.
- Every frame card has capability and non-capability statements.
- Every frame card has package boundary and public surface sections.
- Every active EngineeringFrame anchors FunctionPoints or records an approved empty-frame rationale.
- Every active EngineeringFrame links to normalized ADR authority.

### FunctionPoint acceptance

- Every shipped FunctionPoint has a value-axis source.
- Every shipped FunctionPoint has exactly one primary EngineeringFrame anchor.
- Every shipped FunctionPoint has a contract or approved no-contract rationale.
- Every shipped FunctionPoint has generated fact evidence.
- Every shipped FunctionPoint has test evidence or approved exception.
- Every shipped FunctionPoint has gate evidence.

### Mapping acceptance

- `architecture/mappings/ai-understanding-map.yaml` exists.
- Every shipped Feature appears in the map.
- Every shipped FunctionPoint appears in the map.
- Every Feature-to-EngineeringFrame navigation edge is derived through FunctionPoint.
- No ProductClaim, Requirement, or Feature owns an EngineeringFrame.
- The map can recover value axis, structure axis, evidence axis, decision axis, and governance axis by ID.

### AI onboarding acceptance

- README, CLAUDE, AGENTS, architecture README, and onboarding docs point to the same reading path.
- The reading path starts with product definition before architecture interpretation.
- Generated facts are mandatory before factual implementation claims.
- AI agents can answer the eight target questions in Section 4 using only repository artifacts.

### Gate acceptance

- `bash gate/test_architecture_sync_gate.sh` passes.
- `bash gate/check_architecture_sync.sh` passes.
- `./mvnw -Pquality verify` passes.
- New governance baselines are recorded in `docs/governance/architecture-status.yaml`.

---

## 20. Required Final Verification Command Set

Run all commands before declaring remediation complete:

```bash
python3 gate/lib/check_layer_purity.py --repo . --mode full-blocking
python3 gate/lib/check_adr_taxonomy.py --repo . --mode full-blocking
python3 gate/lib/check_historical_adr_governance.py --repo . --mode full-blocking
python3 gate/lib/check_l2_detail_sink.py --repo . --mode full-blocking
python3 gate/lib/check_engineering_frame_cards.py --repo . --mode full-blocking
python3 gate/lib/check_feature_readiness.py --repo . --mode full-blocking
python3 gate/lib/check_ai_understanding_map.py --repo . --mode full-blocking
python3 gate/lib/check_ai_reading_path.py --repo . --mode full-blocking
./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts -Dexec.args="--repo . --out architecture/facts/generated --check"
bash gate/test_architecture_sync_gate.sh
bash gate/check_architecture_sync.sh
./mvnw -Pquality verify
```

---

## 21. Non-Goals

This remediation should not:

- create a new repository
- rewrite product strategy without product owner review
- hand-edit generated facts
- replace Structurizr DSL with prose
- treat historical ADRs as disposable
- collapse EngineeringFrame and FunctionPoint into one object
- make L0 or L1 carry all implementation detail
- block all engineering work before advisory inventory exists

---

## 22. Final Recommendation

Do not open a new repository.

The right move is to keep the current repository and perform a systematic governance remediation while the project is still early enough to clean the architecture system. The repository already has the core assets needed for AI-readable engineering governance, but those assets must be reorganized around a clear progressive learning curve:

```text
Product definition
  -> Requirements and features
  -> L0 architecture constraints
  -> L1 EngineeringFrames
  -> FunctionPoints
  -> L2 technical design
  -> Contracts
  -> Generated facts
  -> Gates
  -> Architecture review through normalized ADRs
```

The crucial correction is that this must not be treated as a single chain. It is a dual-track model:

- Product and value flow into FunctionPoints.
- Engineering structure flows through EngineeringFrames into FunctionPoints.
- FunctionPoints carry the behavior that can be contracted, implemented, tested, and gated.
- EngineeringFrames carry the package-level engineering boundary that AI agents use to land in the codebase.

Once this model is explicit and gated, future AI agents should be able to understand what the project is trying to do, what constraints govern the work, and what the current engineering state is without relying on private conversation history.
