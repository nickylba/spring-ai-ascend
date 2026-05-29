# Progressive AI Learning Curve Engineering Correction Checklist

Date: 2026-05-29
Audience: Engineering team
Status: requested
Scope: product-to-code software engineering artifacts, DSL/governance integration, EngineeringFrame redefinition, FunctionPoint-to-method traceability, generated-fact grounding

## Executive Verdict

Do not treat the current EngineeringFrame model as complete. It is a useful structural index, but it is not yet strong enough to be the engineering anchor for a new AI agent or a new engineer.

The target is a progressive software engineering learning curve:

```text
ProductClaim
  -> Requirement
    -> L0 Architecture Constraint / ADR
      -> EngineeringFrame
        -> Java package or package-cluster boundary
          -> Type inventory and collaboration map
            -> FunctionPoint
              -> class/method implementation anchors
                -> Contract
                  -> generated CodeFact/TestFact
                    -> Gate and verification evidence
```

The correction below is not "write more prose." It is a required artifact chain. Each node must have a named owner file, a machine-checkable shape where practical, and a verification rule that prevents drift.

## Source Basis

External software engineering sources:

- ISO/IEC/IEEE 29148:2018 specifies requirements engineering processes, required information items, required contents, and formatting guidance for requirements artifacts: https://www.iso.org/standard/72089.html
- ISO/IEC/IEEE 42010:2022 specifies how architecture descriptions are structured and expressed, including architecture viewpoints, frameworks, languages, and model kinds: https://www.iso.org/standard/74393.html
- C4 defines hierarchical architecture abstractions from software systems to containers, components, and code: https://c4model.com/
- arc42 Building Block View requires static decomposition into building blocks such as modules, components, classes, interfaces, packages, functions, and their dependencies, with blackbox/whitebox descriptions: https://docs.arc42.org/section-5/
- arc42 Runtime View documents concrete runtime behavior and interactions through scenarios, interfaces, operations, and error/exception paths: https://docs.arc42.org/section-6/
- OpenAPI defines a standard language-agnostic HTTP API description so humans and computers can discover service capabilities without source-code inspection: https://spec.openapis.org/oas/v3.1.1.html
- AsyncAPI defines machine-readable descriptions for message-driven APIs across protocols: https://www.asyncapi.com/docs/reference/specification/v3.0.0
- Cucumber treats executable specifications as plain-text behavior examples tied to implementation code and living documentation: https://cucumber.io/docs/
- SWEBOK v4 identifies software requirements, architecture, design, construction, testing, operations, maintenance, configuration management, quality, and security as core software engineering knowledge areas: https://blp.ieee.org/software-engineering-body-of-knowledge-swebok/
- OMG UML provides standard modeling support for software structures and behaviors: https://www.omg.org/UML/

Repository authority sources:

- `AGENTS.md:30-50` requires generated facts to be read before prose, to outrank prose for code/contract/test/runtime claims, and to be modified only through extractors or source authorities.
- `AGENTS.md:16` makes `architecture/workspace.dsl` plus `architecture/README.md` the architecture authoring root for the Structurizr workspace closure.
- `architecture/README.md:10` states that `architecture/workspace.dsl` is the sole main entry for the architecture design system.
- `architecture/workspace.dsl:150` includes `features/engineering-frames.dsl` as part of the authored structural axis.
- `architecture/profile/profile.yaml:38`, `architecture/profile/required-properties.yaml:60`, and `architecture/profile/relationship-types.yaml:97-105` already define `SAA EngineeringFrame`, required properties, and the `anchors` / `traverses` relation vocabulary.
- `architecture/docs/L1/engineering-frames.md:20-35` defines the structural axis as `Module -> EngineeringFrame -> FunctionPoint` and the product/value axis as `ProductClaim -> Feature -> FunctionPoint`.
- `architecture/docs/L1/engineering-frames.md:65-80` defines the two-track operating model: Track A may create EngineeringFrames through ADR-gated architecture work; Track B must land on existing EngineeringFrames or escalate.
- `docs/adr/0157-engineering-frame-ontology.yaml:50-87` introduces EngineeringFrame, defines `contains`, `anchors`, and `traverses`, makes frames claim-agnostic, and states that demand work must land on an existing frame.
- `architecture/features/engineering-frames.dsl:1-12` currently describes EngineeringFrames as authored structural anchors, but only at a thin summary level.
- `architecture/features/function-points.dsl:21-70` shows the current FunctionPoint pattern with code entrypoint and test refs for HTTP run lifecycle behaviors.
- `docs/governance/rules/rule-G-15.md:21` requires generated fact files, provenance, byte-identity checks, and hard evidence fields for shipped FunctionPoints.
- `docs/governance/rules/rule-G-22.md:17` already requires accepted ADR frame-map decisions to be reflected in `architecture/features/engineering-frames.dsl`.
- `docs/governance/rules/rule-G-23.md:19` already requires every shipped EngineeringFrame to anchor at least one FunctionPoint unless an ADR-backed allowlist exception exists.
- `docs/governance/enforcers.yaml:1804-1823` registers the current EngineeringFrame governance enforcers for Rules G-22 and G-23.
- `docs/governance/architecture-status.yaml:143-166` makes `architecture_sync_gate.baseline_metrics` the canonical baseline for active rules, gates, enforcers, graph nodes, and graph edges.

## Required Methodology Change

### Current Problem

The repository now has product claims, ADRs, a Structurizr workspace, EngineeringFrames, FunctionPoints, contracts, generated facts, and gates. The problem is not that these surfaces are absent. The problem is that the learning curve is not yet a software engineering artifact chain.

EngineeringFrame is currently too thin. It identifies a structural slice, but it does not consistently answer:

- which Java package or package cluster is the anchor;
- which classes, interfaces, records, DTOs, adapters, and test fixtures exist inside the boundary;
- which public methods and SPI methods carry FunctionPoints;
- which classes call each other;
- which protocols and contracts connect the frame to other frames or modules;
- what the module can do and cannot do;
- which state the frame owns;
- which dependencies are allowed or forbidden;
- which generated fact IDs prove the claims;
- which verification commands must run before a change is accepted.

### Corrected Definition

An EngineeringFrame is a package-level or package-cluster engineering anchor inside a Maven module. It defines the capability boundary, internal type inventory, class collaboration, communication protocols, forbidden dependencies, and FunctionPoint-to-class/method mappings for one stable engineering area.

Default rule:

- One EngineeringFrame should normally anchor one primary Java package root.
- If the true boundary spans sibling packages, the frame may declare an explicit package cluster.
- A frame must list included and excluded packages so AI agents do not infer the boundary from naming alone.
- A cross-module concern must not become one cross-module frame by default. It should be represented as one frame per owning module plus contracts between them.

## Integration With Existing DSL and Governance

The correction must not create a second architecture registry. The existing Structurizr DSL and governance architecture remain the authority spine. Frame Cards are downstream explanatory artifacts that make the DSL actionable for engineers and AI agents; they must not invent IDs, owners, status values, or relationship edges outside the DSL.

Canonical authority chain:

```text
ADR
  -> architecture/profile/*
    -> architecture/workspace.dsl
      -> architecture/features/engineering-frames.dsl
        -> generated architecture facts
          -> architecture/docs/L1/frames/<frame-id>.md
            -> gate/check_architecture_sync.sh
              -> docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics
```

Required integration rules:

- `architecture/workspace.dsl` remains the sole architecture entry point and must continue to include `architecture/features/engineering-frames.dsl`.
- `architecture/profile/profile.yaml`, `architecture/profile/required-properties.yaml`, and `architecture/profile/relationship-types.yaml` define the valid `SAA EngineeringFrame` shape and relationship vocabulary.
- `architecture/features/engineering-frames.dsl` owns frame identity, module containment, frame ownership, frame status, and `anchors` / `traverses` graph edges.
- `architecture/docs/L1/frames/<frame-id>.md` explains the engineering boundary, but every identity field in the card must match the DSL element.
- Generated facts own factual claims about packages, classes, methods, contracts, tests, and build/runtime configuration.
- Governance rule cards under `docs/governance/rules/` own the enforceable policy text.
- Enforcer rows under `docs/governance/enforcers.yaml` must cite the rule card, gate artifact, and assertion for every new governance check.
- `gate/check_architecture_sync.sh` and `gate/test_architecture_sync_gate.sh` must provide fail-closed validation plus positive/negative fixtures.
- `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics` must be updated only after rule/gate/enforcer/graph counts actually change.

Minimum DSL extensions to consider:

- Add `saa.cardPath` as a required property for every `SAA EngineeringFrame`.
- Add `saa.primaryPackage` as a required property for shipped frames.
- Keep long package include/exclude lists in the Frame Card and generated facts unless the profile team explicitly decides that they belong in DSL properties.
- Add profile-validator coverage whenever a new required property is added; YAML profile changes without validator parity are not sufficient.

Required Frame Card frontmatter shape:

```yaml
frame_id: EF-ENGINE-PORT
dsl_element: efEnginePort
owner_module: agent-bus
status: shipped
primary_package: com.huawei.ascend.bus.spi.engine
included_packages:
  - com.huawei.ascend.bus.spi.engine
excluded_packages:
  - com.huawei.ascend.engine.orchestration.spi
source_adrs:
  - ADR-0157
  - ADR-0158
fact_refs:
  - code-symbol/...
```

Gate expectations:

- Gate fails if a DSL frame lacks `saa.cardPath`.
- Gate fails if `saa.cardPath` does not point to an existing Frame Card.
- Gate fails if Frame Card frontmatter disagrees with DSL `saa.id`, `saa.owner`, `saa.status`, or `saa.primaryPackage`.
- Gate fails if a shipped Frame Card cites a package, class, method, test, or contract absent from generated facts.
- Gate fails if a shipped frame has no `anchors` edge to a FunctionPoint and is not ADR-allowlisted.
- Gate fails if a Frame Card lists FunctionPoints that are not represented by DSL `anchors` edges or explicit participating-frame references.
- Gate fails if a new rule/gate/enforcer changes live counts but `architecture-status.yaml#architecture_sync_gate.baseline_metrics` is not updated.

## Engineering Correction Checklist

### ECR-1: Establish Product Definition Artifacts

Goal: make the product intent the first layer of the learning curve.

Required artifacts:

- `product/PRODUCT.md`
- `product/claims.yaml`
- persona and stakeholder list
- business scenarios or use cases
- product non-goals
- measurable success criteria
- source-input archive for original-language material, if needed

Checklist:

- [ ] Every ProductClaim has a stable ID.
- [ ] Every ProductClaim states user value, target persona/stakeholder, scope, non-scope, and success measure.
- [ ] Every Feature links to at least one ProductClaim.
- [ ] No EngineeringFrame carries product-claim ownership directly; product claims bind to the value axis.
- [ ] Original-language inputs are archived outside the always-loaded AI authority path; active authority prose is English.

Acceptance:

- A new AI agent can answer "why this exists" before reading architecture or code.
- `rg "saa.productClaim" architecture/features/engineering-frames.dsl architecture/features/features.dsl` must not find product claims on EngineeringFrame elements.

### ECR-2: Establish Requirement Artifacts

Goal: separate product intent from software obligations.

Required artifacts:

- stakeholder requirements
- system/software requirements
- non-functional requirements
- acceptance criteria
- requirement-to-ProductClaim links
- requirement-to-Feature links
- requirement traceability table

Recommended repository location:

- `product/requirements.yaml`
- `product/acceptance-criteria/`
- `architecture/features/feature-slices.dsl` or equivalent, if FeatureSlice remains part of the model

Checklist:

- [ ] Each requirement has ID, source ProductClaim, rationale, priority, status, and acceptance criteria.
- [ ] Each non-functional requirement names the affected architecture constraint or ADR.
- [ ] Every requirement either maps to an existing Feature/FunctionPoint or is marked out-of-scope/deferred.
- [ ] Acceptance criteria are written as concrete observable behavior, not architecture wishes.

Acceptance:

- No FunctionPoint may be added solely from prose discussion; it must trace back to a requirement or an ADR-created structural need.

### ECR-3: Make L0 Architecture the Constraint Layer

Goal: make L0 explain system-wide constraints before any module/package detail.

Required artifacts:

- L0 architecture description
- system context view
- container view
- global quality attribute scenarios
- ADRs for significant cross-cutting decisions
- L0 constraint to rule mapping
- gate ownership for each enforceable constraint

Checklist:

- [ ] `architecture/workspace.dsl` remains the machine-readable architecture root.
- [ ] `architecture/docs/L0/ARCHITECTURE.md` carries system boundary and numbered constraints only.
- [ ] `CLAUDE.md` carries enforceable rules, not architecture inventory.
- [ ] Each global constraint maps to an ADR and, where practical, a gate or ArchUnit test.
- [ ] L0 does not include package-level class inventories; that belongs to EngineeringFrame cards.

Acceptance:

- A new AI agent can answer "what must never be violated globally" before opening a Java package.

### ECR-4: Redefine EngineeringFrame as the Engineering Anchor

Goal: make each frame a package/package-cluster map that a worker can use before touching code.

Required artifacts:

- `architecture/features/engineering-frames.dsl` for frame IDs and relationships.
- `architecture/profile/required-properties.yaml` for required frame properties such as `saa.cardPath` and `saa.primaryPackage`.
- profile-validator code and tests for any new DSL/profile requirement.
- `architecture/docs/L1/frames/<frame-id>.md` for the detailed Frame Card.
- optional generated `architecture/facts/generated/engineering-frames.json` after extractor support lands.
- gate coverage for DSL-to-card parity.

Required Frame Card sections:

```text
1. Identity
   - frame id
   - DSL element variable
   - DSL cardPath
   - owning Maven module
   - primary Java package
   - included packages
   - excluded packages
   - status
   - authority ADRs

2. Capability Boundary
   - can do
   - cannot do
   - owned state
   - external dependencies
   - forbidden dependencies

3. Type Inventory
   - public API types
   - SPI interfaces
   - abstract/base classes
   - implementations
   - records/DTOs/events
   - adapters
   - test fixtures

4. Internal Collaboration
   - class call graph
   - construction path
   - synchronous/asynchronous boundaries
   - error propagation
   - state transitions

5. Communication Contracts
   - Java SPI
   - HTTP/OpenAPI
   - event/AsyncAPI
   - persistence contract
   - state machine

6. FunctionPoint Mapping
   - owned or anchored FunctionPoints
   - entry classes/methods
   - participating classes/methods
   - contract refs
   - test refs
   - generated fact IDs

7. Constraints and Verification
   - relevant ADRs
   - L0 constraints
   - rules/gates
   - verification commands
   - known drift risks
```

Checklist:

- [ ] Every EngineeringFrame has one Frame Card.
- [ ] Every EngineeringFrame DSL element declares `saa.cardPath`.
- [ ] Every shipped EngineeringFrame DSL element declares `saa.primaryPackage`.
- [ ] Every Frame Card frontmatter matches the DSL element ID, owner, status, and card path.
- [ ] Every Frame Card declares `primary_package`.
- [ ] Every Frame Card declares `included_packages` and `excluded_packages`.
- [ ] Every Frame Card has a Type Inventory section generated or checked against `code-symbols.json`.
- [ ] Every shipped frame anchors at least one shipped FunctionPoint or has an explicit ADR-backed exception.
- [ ] Every design-only frame states what proof is missing before promotion to shipped.
- [ ] Every mock-functional frame states why it is not a real production implementation.
- [ ] The EngineeringFrame DSL remains the graph index; the Frame Card carries the engineering explanation.

Acceptance:

- A new AI agent can open a Frame Card and know which Java packages/classes are in scope before editing.
- Gate must fail if a shipped frame has no Frame Card.
- Gate must fail if a Frame Card cites a class/method absent from generated facts.
- Gate must fail if DSL identity/status/owner/package fields and Frame Card frontmatter drift.

### ECR-5: Redefine FunctionPoint as the Behavior-to-Method Anchor

Goal: make FunctionPoints concrete implementation behaviors, not feature prose.

Required artifacts:

- `architecture/features/function-points.dsl`
- FunctionPoint details in each Frame Card
- generated facts for code symbols and tests
- contract refs

Required FunctionPoint fields:

- ID
- behavior name
- status
- actor/trigger/channel
- primary EngineeringFrame
- entry class and method
- participating classes and methods
- input contract refs
- output contract refs
- test refs
- generated fact IDs
- verification command

Checklist:

- [ ] A FunctionPoint represents a concrete behavior, not a broad feature.
- [ ] Shipped HTTP/SPI FunctionPoints have code entrypoint refs, test refs, and contract refs.
- [ ] FunctionPoints may cross multiple frames, but exactly one frame must be marked primary for ownership.
- [ ] Cross-frame FunctionPoints list participating frames and the contract/protocol between them.
- [ ] Method-level anchors use generated fact IDs where available, not only human-authored paths.
- [ ] A removed endpoint or removed function is deleted or explicitly marked historical; it must not remain in shipped feature prose.

Acceptance:

- Given a FunctionPoint ID, an AI agent can navigate to entry method, participating methods, contracts, tests, and verification command without guessing.

### ECR-6: Formalize Contract Surfaces

Goal: make communication explicit and machine-checkable.

Required artifacts:

- OpenAPI for HTTP APIs
- AsyncAPI or equivalent YAML for message/event APIs
- Java SPI signature catalog
- DTO/schema catalog
- error model
- state machine/lifecycle model
- contract catalog

Checklist:

- [ ] Every inbound protocol has a contract file.
- [ ] Every outbound protocol has a contract file or SPI declaration.
- [ ] Every contract has status: design_only, schema_shipped, mock_functional, or runtime_enforced.
- [ ] Contract status matches implementation and tests.
- [ ] Each contract maps to FunctionPoints and EngineeringFrames.
- [ ] Java SPI contracts include package, type, method signatures, ownership, and expected error behavior.

Acceptance:

- A consumer can understand how to call a module/frame without reading implementation code.
- A worker cannot mark a transport shipped if it is only mock-functional or in-process.

### ECR-7: Build Generated Engineering Facts

Goal: make factual code understanding extractor-backed.

Required generated facts:

- `code-symbols.json`: packages, classes, interfaces, records, methods, visibility, annotations.
- `contract-surfaces.json`: OpenAPI, contract YAML, SPI surfaces.
- `tests.json`: unit, integration, ArchUnit, contract, and E2E tests.
- `module-build.json`: Maven modules and dependencies.
- `runtime-config.json`: configuration properties and defaults.
- `adrs.json`: accepted decisions and affected surfaces.
- proposed `engineering-frames.json`: frame-to-package, frame-to-type, frame-to-functionpoint, frame-to-contract, frame-to-test links.

Checklist:

- [ ] Extractors, not humans, produce generated facts.
- [ ] Generated facts include stable fact IDs.
- [ ] Frame Cards cite fact IDs for code/test/contract claims.
- [ ] Generated facts are byte-identical under check mode.
- [ ] Root verification must not pass if the fact-layer byte identity test fails.

Acceptance:

- Prose never outranks facts. If a Frame Card disagrees with generated facts, the gate must fail or the prose must be corrected.

### ECR-8: Add Gates for the Learning Curve

Goal: prevent the artifact chain from becoming aspirational documentation.

Required gate rules:

- ProductClaim -> Feature traceability.
- Feature -> EngineeringFrame traversal exists for in-scope features.
- DSL `SAA EngineeringFrame` -> `saa.cardPath` exists.
- DSL `SAA EngineeringFrame` -> Frame Card frontmatter parity.
- EngineeringFrame -> Frame Card exists.
- Frame Card package scope resolves to real Java packages or declared design-only package skeletons.
- Frame Card type inventory resolves to generated code-symbol facts.
- Shipped EngineeringFrame anchors at least one shipped FunctionPoint.
- Shipped FunctionPoint resolves to class/method facts, contract refs, and test refs.
- Contract status matches implementation evidence.
- Rule card -> enforcer row -> gate implementation -> self-test fixture parity.
- Live rule/gate/enforcer/graph counts -> `architecture-status.yaml#architecture_sync_gate.baseline_metrics` parity.
- AI authority reading path is English-only, excluding explicitly archived source inputs and fixtures.
- Root quality verification includes fact-layer byte identity or directly invokes the extractor check.

Checklist:

- [ ] Add negative fixtures for each gate.
- [ ] Add positive fixtures for each gate.
- [ ] Gate output must name the exact file and missing link.
- [ ] No advisory-only gate may be cited as acceptance for shipped status.

Acceptance:

- A broken traceability chain must fail before merge.
- A broken DSL/profile/governance chain must fail before merge.

### ECR-9: Update the AI Reading Path

Goal: make the learning curve deterministic for any AI session.

Required reading order:

```text
1. product/PRODUCT.md and product/claims.yaml
2. product/requirements.yaml and acceptance criteria
3. architecture/facts/generated/*.json
4. architecture/workspace.dsl and architecture/README.md
5. architecture/docs/L0/ARCHITECTURE.md
6. architecture/docs/L1/engineering-frames.md
7. architecture/docs/L1/frames/<frame-id>.md
8. architecture/features/function-points.dsl
9. architecture/features/features.dsl
10. docs/contracts/contract-catalog.md and contract files
11. code and tests
```

Checklist:

- [ ] Update AGENTS.md reading path after Frame Cards exist.
- [ ] Update architecture README to include Frame Cards as first-class L1 surfaces.
- [ ] Ensure the reading path starts with product intent and generated facts before implementation prose.
- [ ] Ensure FunctionPoint details come after EngineeringFrame boundaries.

Acceptance:

- Any AI agent following the reading path reaches the same product/engineering state without relying on model memory.

### ECR-10: Wire the Correction Into the Existing Governance Architecture

Goal: make the new learning curve enforceable by the current governance system instead of leaving it as guidance.

Required artifacts:

- ADR amendment or new ADR defining the refined EngineeringFrame/Card contract.
- `architecture/profile/profile.yaml` only if a new tag is needed.
- `architecture/profile/required-properties.yaml` for new required EngineeringFrame properties.
- `architecture/profile/relationship-types.yaml` only if a new relationship verb is needed.
- profile-validator implementation and tests for the new required properties.
- `architecture/features/engineering-frames.dsl` updated with `saa.cardPath` and, for shipped frames, `saa.primaryPackage`.
- `architecture/docs/L1/frames/README.md` and `architecture/docs/L1/frames/_template.md`.
- at least one real `architecture/docs/L1/frames/<frame-id>.md` pilot card.
- `gate/lib/check_engineering_frame_cards.py` or equivalent reusable checker.
- `gate/check_architecture_sync.sh` integration.
- `gate/test_architecture_sync_gate.sh` positive and negative fixtures.
- new or amended `docs/governance/rules/rule-G-*.md` cards.
- `docs/governance/enforcers.yaml` rows for the new checks.
- regenerated governance graph/status artifacts where the existing process requires them.
- `docs/governance/architecture-status.yaml#architecture_sync_gate.baseline_metrics` updated only after live counts are verified.

Checklist:

- [ ] The ADR states whether Frame Cards are authority, derived explanation, or both; the expected answer is "derived explanation over DSL/facts authority".
- [ ] The DSL profile has the minimum properties needed to bind Frame Cards without duplicating long code inventories.
- [ ] The Frame Card template explicitly says which fields are copied from DSL and which fields are fact-backed.
- [ ] The gate checks DSL -> card path -> card frontmatter -> generated facts -> FunctionPoint edges.
- [ ] The gate emits file/line-oriented failures for every broken link.
- [ ] The self-test suite contains at least one negative fixture for each new failure mode.
- [ ] Enforcer rows cite the rule card and the exact gate artifact.
- [ ] Baseline counts in `architecture-status.yaml` match the live gate/enforcer/graph counts after the change.

Acceptance:

- A new AI agent can start from `architecture/workspace.dsl`, land on a frame, open its card, verify its package/class/method claims through generated facts, and understand which governance rule/gate keeps the path honest.

## Deliverable Sequence

### Phase 1: DSL Binding, Schema, and Template

- [ ] Add or amend the ADR that defines the refined EngineeringFrame/Card contract.
- [ ] Add `saa.cardPath` to the `SAA EngineeringFrame` required-property model.
- [ ] Add `saa.primaryPackage` to shipped-frame requirements or document why the profile team chose a different enforceable shape.
- [ ] Update the profile validator and validator tests for every new required property.
- [ ] Add `architecture/docs/L1/frames/README.md` explaining Frame Cards.
- [ ] Add `architecture/docs/L1/frames/_template.md` with the required sections above.
- [ ] Update `architecture/docs/L1/engineering-frames.md` to define EngineeringFrame as a package/package-cluster engineering anchor.
- [ ] Update ADR-0157 or add a superseding ADR to lock this refined definition.

### Phase 2: Pilot One Frame

- [ ] Select one high-value frame, preferably `EF-ACCESS-ADMISSION` or `EF-ENGINE-PORT`.
- [ ] Update `architecture/features/engineering-frames.dsl` for the pilot with `saa.cardPath` and `saa.primaryPackage`.
- [ ] Create its Frame Card.
- [ ] Map primary package, included packages, excluded packages, classes, interfaces, methods, contracts, tests, and FunctionPoints.
- [ ] Cite generated facts for every factual code/test/contract claim.
- [ ] Verify the card's FunctionPoint list against DSL `anchors` edges.
- [ ] Record design-only/mock-functional caveats explicitly.

### Phase 3: Add Gate Coverage

- [ ] Add a gate checking every frame DSL element has a valid `saa.cardPath`.
- [ ] Add a gate requiring every frame to have a Frame Card.
- [ ] Add a gate checking DSL identity/owner/status/package fields against Frame Card frontmatter.
- [ ] Add a gate checking package scope and type inventory against generated facts.
- [ ] Add a gate checking FunctionPoint method anchors against generated facts.
- [ ] Add or amend rule cards and enforcer rows for the new checks.
- [ ] Add negative self-tests.

### Phase 4: Roll Out Across Frames

- [ ] Produce Frame Cards for all shipped frames.
- [ ] Produce lighter design-only Frame Cards for design-only frames.
- [ ] Re-render workspace/catalog outputs.
- [ ] Update governance graph/status artifacts.
- [ ] Update status baselines only after live counts are verified.

### Phase 5: Extract Frame Facts

- [ ] Add `EngineeringFrameFactExtractor`.
- [ ] Emit `architecture/facts/generated/engineering-frames.json`.
- [ ] Make Frame Cards downstream of generated frame facts where practical.
- [ ] Fail the gate on frame/prose/fact disagreement.

### Phase 6: Governance Closure

- [ ] Run the profile validator against `architecture/workspace.dsl`.
- [ ] Run the fact extractor in check mode.
- [ ] Run the architecture sync gate.
- [ ] Run the gate self-test suite.
- [ ] Confirm `architecture-status.yaml#architecture_sync_gate.baseline_metrics` matches live rule/gate/enforcer/graph counts.

## Minimum Resubmission Evidence

The engineering team must provide all of the following:

- The refined EngineeringFrame ADR or ADR amendment.
- The updated DSL profile/required-property changes.
- The updated profile-validator tests.
- The updated `architecture/features/engineering-frames.dsl` pilot element with card path and primary package.
- The Frame Card template.
- One completed pilot Frame Card.
- The updated AI reading path.
- The updated FunctionPoint-to-method mapping for the pilot.
- New or updated gate rules with positive and negative tests.
- New or updated governance rule cards and enforcer rows.
- Updated baseline metrics, if live counts changed.
- Direct generated fact check output.
- Root quality verification output that includes or depends on the fact-layer byte-identity proof.

Required commands:

```text
./mvnw clean verify
./mvnw -Pquality verify
./mvnw -f tools/architecture-workspace/pom.xml exec:java@extract-facts -Dexec.args="--repo . --out architecture/facts/generated --check"
bash gate/check_architecture_sync.sh
```

If any command is not applicable after the correction, the team must explain the replacement command and the authority source that changed the verification contract.

## Non-Negotiable Acceptance Bar

This correction is complete only when a new AI agent can answer the following questions from repository artifacts alone:

1. What product claim or requirement justifies this work?
2. Which L0 constraints and ADRs govern the work?
3. Which EngineeringFrame owns the affected package/package cluster?
4. Which DSL element and relationship edges define that EngineeringFrame?
5. Which Frame Card explains that DSL element?
6. Which Java packages, classes, interfaces, and methods are in scope?
7. Which classes call which other classes at runtime?
8. Which contracts/protocols cross the frame boundary?
9. Which FunctionPoint is being changed?
10. Which exact class/method anchors implement that FunctionPoint?
11. Which generated facts prove those anchors exist?
12. Which governance rule and gate keep the DSL/card/fact chain honest?
13. Which tests and gates prove the change is safe?

If any answer requires guessing from prose, model memory, or local tribal knowledge, the progressive learning curve is not yet real.
