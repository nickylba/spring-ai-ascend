# Engineering Governance Systemic Remediation — Step-1 Reflection / Response

Date: 2026-05-29
Audience: the next architecture reviewer, governance implementers, AI coding agents
Status: review response (step 1 of the remediation — adjudication only, no machinery built yet)
Branch: `governance/progressive-learning-curve-remediation`
Scope: this repository only (`spring-ai-ascend`)

---

## Executive summary

The central critique is **TRUE with nuance**: the L0/L1 layer-purity defect is real and is a
**governance/architecture defect, not a documentation-quality defect**. L0 `ARCHITECTURE.md` §0.6
explicitly disclaims that it carries runtime contracts ("wire shapes, route behavior, SPI
signatures") while §0.5.3 and several §4 constraints carry exactly those — `gen_ai.*`/`langfuse.*`
OTLP wire namespaces and sampling percentages (§0.5.3 / §4 line 99, 372), the route-and-verb
`POST /v1/runs/{id}/cancel` (not `DELETE`) plus HTTP 403 (§4 #37, lines 702-710), and the method
signature `RunContext.tenantId() : String` (§4 #22, line 560). L1 `agent-service` carries
`SET LOCAL app.tenant_id` GUC wiring, the Flyway filename `V2__idempotency_dedup.sql`, RLS table
lists, and literal "L2 zone" sections (`development.md` §5.1-§5.5). A prose reword cannot remediate
unenforced layer purity; only governance machinery can. **Crucially, much of the dual-track model
the remediation proposes already exists**: ADR-0157 (`engineering-frame-ontology.yaml`, status
accepted) ratifies EngineeringFrame as the structural layer between Module and FunctionPoint
(`Module → EngineeringFrame → FunctionPoint`, Feature `--traverses-->` Frame as a derived
non-ownership projection), realized by `architecture/features/engineering-frames.dsl` (11 frame
elements, each `saa.structuralAxis "true"`; 16 Feature→Frame `traverses` edges) and kept honest by
two **active, blocking** gates — Rule G-22 / E187 (frame-map coherence) and Rule G-23 / E188
(shipped-frame anchor integrity). What is missing is not the structural backbone but its **reified
governance machinery** for layer purity, ADR normalization, and the AI reading path: every policy
YAML, the normalized-ADR corpus, rule cards G-27..G-33, the gate helpers, and the Frame Cards are
absent on disk, and the remediation plan itself is an **untracked** document (`git diff main...HEAD`
is empty; working tree holds only two untracked review files). The verdicts below are therefore a
mix of *partially_satisfied* (backbone present, machinery absent), *confirmed_missing* (genuine,
actionable gaps), and two posture claims — one *already_satisfied* (keep-the-repo decision is
documented as binding) and the rest correct-but-not-yet-executed.

**Verdict tally (C-01..C-06):** partially_satisfied ×3 (C-01, C-04, C-06); confirmed_missing ×2
(C-03, C-05); already_satisfied ×1 (C-02).

**Integrity caveat for the orchestrator:** none of these verdicts means "the review is addressed."
C-02 (already_satisfied) covers only the repository-choice preamble; the substantive review demands
(the correction-request's P0-1..P1-4 and the correction-checklist's ECR-1..ECR-10 / Phase 1-6) are
tracked by other claims and remain open.

---

## Verdict: partially_satisfied

The structural intent is adopted (and in C-06's case the navigation backbone is built and gated),
but the governance machinery that would reify it into the authority chain
(ADR → profile → DSL → facts → Card → gate → architecture-status) does not yet exist on disk.

### C-01 — Treat the problem as a governance-architecture defect, not a documentation-quality defect

- **Assertion:** the current problem should be classified as a governance/architecture defect, and
  remediated with governance machinery, rather than reframed as a documentation-quality issue.
- **Verdict:** partially_satisfied.
- **Evidence:**
  - FRAMING ADOPTED (plan prose, verbatim): `docs/logs/reviews/2026-05-29-engineering-governance-systemic-remediation.en.md:20` (§1 Executive Decision) states verbatim: "The current problem should be treated as a governance architecture defect, not as a documentation quality defect." Reinforced by `docs/reviews/2026-05-29-progressive-learning-curve-engineering-correction-checklist.en.md:28` ("not 'write more prose.' It is a required artifact chain") and `:67` ("The problem is not that these surfaces are absent. The problem is that the learning curve is not yet a software engineering artifact chain").
  - SUBSTANTIVELY CORRECT (per the verified verdict): the underlying defect is genuinely structural/governance, not documentation-quality — L0 §0.6 disclaims runtime contracts while §0.5.3 + §4 (#37/#41/#44) carry them, and L1 `agent-service` carries SQL/RLS/HTTP-status/filter-order/method-CAS plus a literal "L2 zone" section. A prose reword cannot fix unenforced layer purity; only governance machinery can.
  - BUT NOT REIFIED INTO THE AUTHORITY CHAIN:
    - The plan doc itself is UNTRACKED (`git status`: `?? docs/logs/reviews/2026-05-29-engineering-governance-systemic-remediation.en.md`; no commit touches it; `git diff main...HEAD` is empty).
    - Every artifact the plan proposes to operationalize the framing is MISSING (all confirmed absent on disk): `docs/governance/layer-purity-policy.yaml`, `docs/governance/adr-governance-policy.yaml`, `docs/governance/adr-taxonomy.yaml`, `docs/governance/adr-remediation-ledger.yaml`, `docs/adr/normalized/` (dir absent), `docs/adr/review-index.md`, `docs/governance/ai-reading-path.yaml`, `docs/onboarding/ai-understanding-path.md`, `architecture/mappings/ai-understanding-map.yaml`, `architecture/docs/L1/frames/` (dir absent), `gate/lib/check_layer_purity.py`, `gate/lib/check_adr_taxonomy.py`, `gate/lib/check_feature_readiness.py`, `gate/lib/check_engineering_frame_cards.py`.
    - Rule cards G-27..G-33 (layer-purity, ADR-normalization, etc.) do not exist; highest present is `docs/governance/rules/rule-G-26.md` (G-27 confirmed absent).
    - No enforcer rows or architecture-status entries reference layer-purity / adr-normalization / adr-taxonomy.
    - `architecture-status.yaml` `baseline_metrics` (graph_nodes 674 / graph_edges 1301 / workspace_elements 620 / workspace_relationships 495, lines 165-168) still reflect the prior `engineport-frame-authority-convergence` wave; no layer-purity / ADR-normalization capability is baselined.
- **Rationale:** C-01 is a meta/posture claim about how to CLASSIFY the problem. On the merits it is the strongest valid reading and is correct: the verified verdict confirms the L0/L1 layer-impurity is a real governance/architecture defect (enforceable layer purity is absent), so a documentation-quality reframe would not remediate it. The framing is also explicitly and verbatim adopted in the repo. However, it lives only as prose in an UNCOMMITTED plan, and none of the governance machinery that would instantiate the framing in the authority chain exists yet. Therefore the posture is correct and declared but not yet realized — partially_satisfied, not already_satisfied (machinery absent + plan untracked), not confirmed_missing (the framing is explicitly present and right), and not rejected (it is a genuine governance defect per the adjudicated verdict, not a doc-quality nit).

### C-04 — Normalize the existing ADR corpus

- **Assertion:** the ADR corpus must be normalized to a uniform shape with consistent metadata, accurate filename↔content↔index correspondence, and gate coverage.
- **Verdict:** partially_satisfied.
- **Evidence:**
  - CORPUS SHAPE (all paths relative to repo root): `docs/adr/` holds 59 active `.md` + 88 active `.yaml` (highest = 0156 / 0157 / 0158); `docs/adr/locked/` holds 11 `.md` (0001, 0002, 0004, 0005, 0006, 0010, 0011, 0014, 0015, 0020, 0027); `docs/logs/adr-amendment-narratives/` holds 4 archived (0026.md, 0083/0084/0085.yaml). IDs are contiguous 0001-0158 with documented gaps (0026 / 0083-0085 archived; the 0067→0068 md→yaml boundary).
  - NORMALIZED HALF (satisfied): all 88 active `.yaml` ADRs carry uniform `id/title/status/level/view` frontmatter. `gate/rules/rule-037.sh` enforces `level:`/`view:` but its target glob is ONLY `docs/adr/*.yaml`. `gate/rules/rule-043.sh` (`new_adr_must_be_yaml`, E62) forces the highest-numbered ADR to be `.yaml`. `gate/migrate_adrs_to_yaml.py` exists (the cutover machinery is real).
  - UN-NORMALIZED HALF (the defect): the active `.md` ADRs (pre-0068) plus the 11 locked `.md` are NOT covered by rule-037 (no `level:`/`view:` gate) and use inconsistent shapes. H1 styles diverge several ways: `# 0003. ...`, `# 0019. ...`, `# ADR-0042: ...`, `# 0048. ...`, `# ADR-0067 — ...`. Status lines diverge: `**Status:** accepted` vs `> Status: accepted | Date: ... | Deciders: ...`. The documented cutover in `docs/adr/ADR-CLASSIFICATION.md` (git-rm-then-regenerate) was started and ABANDONED mid-stream.
  - FILENAME != CONTENT (smoking gun, confirmed live): `docs/adr/0009-micrometer-observability.md` H1 is `# 0009. HashiCorp Vault (OSS) for secrets, not env vars / K8s Secrets only`. `docs/adr/locked/0011-flyway-schema-migration.md` H1 is `# 0011. Spring Cloud Gateway as ingress, not Kong / Traefik`. The on-disk filename slug contradicts the ADR's own title for the 0007-0017 block.
  - FOUR CONTRADICTORY/STALE INDEX SURFACES: (1) `docs/adr/README.md` last refreshed 2026-05-13, max row [0054] (missing ~100 ADRs 0055-0158), titles scrambled (row 0009 = "HashiCorp Vault" vs file slug `micrometer`), and link `[0042](0042-test-evidence-enforcement-for-rule-G-2.md)` is BROKEN (real file is `0042-test-evidence-enforcement-for-rule-25.md`). (2) `docs/adr/INDEX.md` partition view ("~70 active", 11 locked, 4 archive). (3) `docs/adr/ADR-CLASSIFICATION.md` covers only 0001-0068 and its slugs contradict real filenames. (4) `docs/adr-taxonomy/README.md` is the most accurate but stops at 0139 (missing 0140-0158). No two surfaces share coverage; for ADR-0011 four surfaces disagree. `docs/logs/adr-triage-manifest.md` (2026-05-19, "all 85 ADRs") is itself stale (corpus is now 158).
  - GATE COVERAGE GAP: no gate rule checks filename↔H1 consistency, index-surface coverage/freshness, broken intra-corpus ADR links, or `.md`-ADR frontmatter. rule-043 only verifies the single highest-numbered file is `.yaml`.
  - LAYER-PURITY RELATION: C-04 is orthogonal to the verified L0/L1-contains-L2 critique — it is a structural format/index-consistency problem (filename↔content↔index drift, md/yaml split, ~100 missing index rows), not a content-altitude leak. Honoring the verified verdict only constrains the FIX (a normalization rewrite of titles/slugs must not pull L2 detail up); it does not change this finding.
- **Rationale:** partially_satisfied, not confirmed_missing and not already_satisfied. Already_satisfied is wrong: the pre-0068 `.md` corpus is un-normalized (multiple H1 styles, two status formats, no `level`/`view`, not gate-covered), filenames contradict file content (0009, 0011), four index surfaces are mutually contradictory/stale/broken, and the documented cutover was abandoned. Confirmed_missing overstates it: the post-0068 `.yaml` corpus (88 files) IS uniformly normalized and gate-enforced (rule-037 / rule-043), the migration tooling exists (`gate/migrate_adrs_to_yaml.py`), and a classification framework is in place (`ADR-CLASSIFICATION.md`, `adr-taxonomy`, triage-manifest) — so roughly the upper half of the corpus already meets a normalized standard and the remaining work is finishing a stalled migration + reconciling index surfaces + adding a consistency gate, not building from zero. The claim proposes no artifact, so there is nothing to verify-as-built; the corpus state itself shows a genuine, multi-surface, gate-unprotected normalization gap that is real and largely unaddressed for the legacy half.

### C-06 — Promote EngineeringFrame to the engineering landing and navigation point

- **Assertion:** EngineeringFrame must be both the structural NAVIGATION hub (`Module → EngineeringFrame → FunctionPoint`; Feature `--traverses-->` Frame as a derived non-ownership projection) AND the LANDING object an engineer/AI opens to answer module / package / boundary-types / usable-SPI / in-scope / out-of-scope / anchored-FunctionPoints / relevant-contracts-facts-gates.
- **Verdict:** partially_satisfied.
- **Evidence:**
  - NAVIGATION BACKBONE — DONE and enforced:
    - `docs/adr/0157-engineering-frame-ontology.yaml` (status: accepted) introduces EngineeringFrame as the structural layer between Module and FunctionPoint; consequences state "AI and engineers read a stable `Module → EngineeringFrame → FunctionPoint` anchor BEFORE the feature list." This is the C-06 assertion, ratified.
    - `architecture/features/engineering-frames.dsl`: 11 frame elements, each `saa.structuralAxis "true"`; `contains` edges (Module→Frame), `anchors` edges (Frame→FunctionPoint), `traverses` edges (Feature→Frame, 16 edges) with an explicit "value axis ... not ownership" comment.
    - `architecture/profile/relationship-types.yaml` defines the `anchors` + `traverses` navigation vocabulary.
    - `architecture/docs/L1/engineering-frames.md` (canonical narrative) + `architecture/docs/L1/README.md:56` ("Structural axis (read before features)").
    - TWO BLOCKING gates keep the map honest: Rule G-22 / enforcer E187 (`docs/governance/rules/rule-G-22.md`, `status: active`, `enforcer_refs: [E187]`, accepted-ADR frame-map coherence) and Rule G-23 / enforcer E188 (`rule-G-23.md`, shipped-frame anchor integrity). Both active.
  - LANDING POINT — NOT YET realized (the partial gap):
    1. Canonical TOP-LEVEL reading paths never land on EngineeringFrame. `README.md` and `AGENTS.md` contain ZERO mention of EngineeringFrame (confirmed live: both `-match 'EngineeringFrame'` = False). It surfaces only one level down, inside `architecture/docs/L1/README.md:56`. So at the authoritative AI-onboarding surfaces the promotion to "landing point" has not happened.
    2. NO Frame Cards exist: `architecture/docs/L1/frames/` directory is absent; no `saa.cardPath` or `saa.primaryPackage` in `architecture/features/` or `architecture/profile/`. The §6.1 questions a frame "must answer" (boundary classes/interfaces, usable SPI surfaces, in/out-of-scope responsibilities, relevant contracts/facts/gates) are exactly the ECR-4 Frame Card fields and are unbuilt.
    3. Operationalizing artifacts named by remediation §7 / ECR-9 are all absent: `docs/governance/ai-reading-path.yaml`, `docs/onboarding/ai-understanding-path.md`, `docs/governance/rules/rule-G-32-ai-reading-path.md`. The remediation plan's own step lists carry these as unchecked TODOs.
    4. The only Frame-Card-like artifact, `docs/logs/reviews/2026-05-29-agent-service-engineering-frame-dossier.en.md`, is status "review draft" in the logs/interpretation zone (not the `architecture/docs` authority cascade) and covers one module only.
  - LAYER-PURITY (honoring the verified verdict): C-06 as scoped is layer-pure — the remediation §6.4 explicitly says EngineeringFrame "is a navigation and boundary object," not a place to re-host L2/runtime detail. So promoting it does not reintroduce the leak; it is the correct structural home that lets L0/L1 shed altitude.
- **Rationale:** partially_satisfied. The structural NAVIGATION half of the claim is genuinely DONE and enforced by two active blocking gates: ADR-0157 ratifies the ontology, `engineering-frames.dsl` realizes the `Module → EngineeringFrame → FunctionPoint` anchor with non-ownership `traverses` projection, and G-22/G-23 keep the map coherent and anchored. But the LANDING-POINT half is not realized: no Frame Cards exist, the top-level AI-onboarding surfaces (`README.md`, `AGENTS.md`) never route a reader to EngineeringFrame, and the operationalizing artifacts (`ai-reading-path.yaml`, `ai-understanding-path.md`, the dedicated reading-path rule card) are absent — they live only as unchecked TODOs in the untracked plan. Not already_satisfied (the landing object and reading path do not exist), not confirmed_missing (the navigation backbone is real, ratified, and gated). The remaining work is to build Frame Cards in `architecture/docs/L1/frames/` and to promote EngineeringFrame into the canonical top-level reading path with a gate, not to invent the ontology.

---

## Verdict: confirmed_missing

Genuine, currently-unmet recommendations. Each is a real gap (not a non-problem), so the correct
verdict is confirmed_missing, not rejected.

### C-03 — Freeze new architecture expansion temporarily

- **Assertion:** institute a temporary, scoped freeze on new architecture expansion (no new ADRs except remediation ADRs; no broad L0/L1 rewrites except cleanup) while the governance remediation runs.
- **Verdict:** confirmed_missing.
- **Evidence:**
  - SOURCE OF CLAIM: C-03 = item #2 of the Executive Decision in `docs/logs/reviews/2026-05-29-engineering-governance-systemic-remediation.en.md:27` ("Freeze new architecture expansion temporarily"), operationalized in §17 "Wave 0: Freeze and audit" ("Freeze new ADR expansion except remediation ADRs" + "Freeze broad L0/L1 rewrites except cleanup work"). It is a temporary PROCESS POSTURE; proposed artifact "(none)" is correct — it is a decision, not a deliverable.
  - NOT EXECUTED: `git diff main...HEAD` on `governance/progressive-learning-curve-remediation` is EMPTY. The only working-tree changes are two UNTRACKED documents (the plan and the correction-checklist). None of the plan's Wave 0-6 artifacts exist on disk. So the freeze has not been declared, recorded, or enforced anywhere.
  - NO STANDING FREEZE-ON-EXPANSION MECHANISM EXISTS: the only freeze machinery in the repo is a DIFFERENT thing — the `freeze_id` front-matter convention enforced by Gate Rule 44 `frozen_doc_edit_path_compliance` (enforcer E63), code in `gate/check_architecture_sync.sh`. It fires ONLY when an already-`freeze_id`-tagged file is MODIFIED without an accompanying `docs/logs/reviews/*.md` proposal under `affects_artefact:`. It routes EDITS-to-released-docs through review; it does NOT block adding NEW ADRs or NEW L0/L1/frame expansion (a new file carries no `freeze_id` and is never inspected). Coverage is also L0-only: `architecture/docs/L0/ARCHITECTURE.md:5` = `freeze_id: W1-russell-2026-05-14`, but the L1 files are `freeze_id: null` (Rule 44 no-op). `docs/governance/architecture-status.yaml` carries NO freeze/moratorium/wave_0 flag; its `strategic_decisions` block instead records UNFREEZES (e.g. `audience-c-unfreeze`, line 29), confirming the historical "freeze" idiom is doc-edit channeling, not an expansion halt.
  - ACTIVE EXPANSION, THE OPPOSITE OF FROZEN: ADR corpus is at 0158. The two newest, ADR-0157 (`engineering-frame-ontology.yaml`) and ADR-0158 (`engine-port-transport-agnostic-boundary.yaml`), are dated 2026-05-29 and are net-new architecture expansion (new EngineeringFrame ontology + new EnginePort boundary). The reviewed commit that triggered this remediation is `d66749b` (ADR-0158 / `feat(architecture): transport-agnostic EnginePort boundary`). Architecture was being expanded right up to the review — there is no hold-and-clean posture in force.
  - RELATION TO VERIFIED VERDICT: this claim is process/[other], so the L0/L1-contains-L2 adjudication does not alter the verdict, but it reinforces WHY the freeze is warranted: the verified-true leakage persists while new architecture keeps being added on top. Per the plan's own Non-Goals: the freeze must "not block all engineering work before advisory inventory exists" — but the recommended LIMITED freeze on new ADR/L0-L1 expansion is simply absent.
- **Rationale:** C-03 recommends a temporary, scoped freeze on new architecture expansion while governance remediation runs. No such freeze exists as any standing posture, gate, governance flag, or executed action: the remediation branch is undelivered (empty diff vs main; plan exists only as an untracked doc), the sole freeze machinery (Rule 44 / `freeze_id`) merely channels edits to already-tagged L0 docs through review and does not constrain new expansion, and architecture in fact kept expanding through ADR-0157/0158 on 2026-05-29 — the commit that prompted the review. Verdict: confirmed_missing. It is a real, currently-unmet recommendation (not rejected): the verified-true layer leakage justifies holding architecture steady while L0/L1 are cleaned, so the gap is genuine rather than a non-problem.

### C-05 — Establish layer-purity rules for L0, L1, and L2

- **Assertion:** introduce an enforceable layer-purity rule that polices content ALTITUDE — L0/L1 must not carry runtime / SQL / wire / SPI-signature detail that belongs in L2 / contracts / generated facts.
- **Verdict:** confirmed_missing.
- **Evidence:**
  - PROBLEM IS REAL (verified verdict re-confirmed by live repo state):
    - L0 `ARCHITECTURE.md` §0.6 (lines 36-43) declares L0 "does NOT carry ... Runtime contracts (wire shapes, route behavior, SPI signatures)" — yet it leaks exactly those: §0.5.3 (line 99) OTLP/HTTP wire + `gen_ai.*`/`langfuse.*` namespaces + sampling % (dev=100 / research=10 / prod=1); §4 #3 (line 359) SQL/GUC `SET LOCAL app.tenant_id = :id` + Postgres RLS; §4 #22 (line 560) method signature `RunContext.tenantId() : String`; §4 #37 (lines 702-710) route+verb `POST /v1/runs/{id}/cancel` (not `DELETE`) + status 403.
    - L1 `agent-service/development.md` §5 (lines 187-289) leaks `SET LOCAL app.tenant_id GUC wiring via R2DBC` (line 241), Flyway filename `V2__idempotency_dedup.sql` (line 235), RLS table lists, inside literal "L2 zone" sections (§5.1-§5.5, line 193+). DEFENSIBLE nuance: these §5 blocks are governed by Rule G-1.1.c / E168 as "L2 Boundary Contracts" (naming a delegated L2 zone is allowed); the LEAKED part is the GUC/SQL/filename interior detail inside them.
  - RULE IS GENUINELY MISSING (no existing artefact enforces layer-content altitude):
    - Rule G-1.a (`docs/governance/rules/rule-G-1.md`) enforces ONLY that artefacts declare `level:`+`view:` frontmatter and use 4+1 headings — not content altitude.
    - Rule G-1.1 (`rule-G-1.1.md`, enforcers E166/E167/E168) enforces L1 DEPTH/grounding (dev-view tree, SPI appendix, L2 boundary contracts) — the opposite concern.
    - Gate Rule 37 `architecture_artefact_front_matter` (enforcer E55) validates only `level ∈ {L0,L1,L2}` and `view ∈ {5 values}`; never checks content altitude.
    - A broad search across `docs/` and `docs/adr/` for layer-purity / altitude / "L0 MUST NOT carry (wire|SQL|signature)" returned only `F-deleted-module-name-leakage` (Rule G-2.1) and tenant-ThreadLocal-leakage (Rule R-C.2 / D-8) — different defect classes. `recurring-defect-families.yaml` has NO layer-purity / altitude family.
  - PROPOSED-BUT-UNBUILT (source of the claim):
    - Claim originates verbatim in `docs/logs/reviews/2026-05-29-engineering-governance-systemic-remediation.en.md:29` + §10 "Layer Purity Policy". The review PROPOSES (status "Create:", advisory→blocking) four artifacts: `docs/governance/layer-purity-policy.yaml`, `docs/governance/layer-purity-temporary-violations.yaml`, `gate/lib/check_layer_purity.py`, `docs/governance/rules/rule-G-27-layer-purity.md`.
    - All four confirmed ABSENT on disk; gate has zero layer-purity / `check_layer_purity` references; highest existing G- card is G-26 so G-27 is free (matches task brief). NOTE: proposed-artifact field said "(none)" but the originating review specifies this concrete artifact set.
- **Rationale:** layer-purity for L0/L1/L2 is not enforced anywhere. The two adjacent rules solve orthogonal problems: G-1.a polices frontmatter-level declaration and 4+1 organization; G-1.1 polices L1 minimum depth/grounding (too shallow). Neither polices content ALTITUDE (too deep — runtime/SQL/wire/signature detail leaking up into L0/L1). The gate's only level/view rule (E55) is a frontmatter-value check, not a content check, and no defect family or ADR covers this class. Honoring the pre-adjudicated verified verdict, the leakage is demonstrably present in both L0 (the §0.6 self-contradiction is the cleanest proof) and L1 `agent-service`. The remedy is fully specified but unbuilt in the 2026-05-29 systemic-remediation review (policy yaml + temporary-violations yaml + `gate/lib/check_layer_purity.py` + `rule-G-27-layer-purity.md`), and all four are absent. Therefore the claim is a genuine, actionable gap: confirmed_missing.

---

## Verdict: already_satisfied

### C-02 — Keep the current repository (do not start a new one)

- **Assertion:** the delivery should remain in the current repository; opening a new repository is the wrong path, and this is a decided posture, not an open question.
- **Verdict:** already_satisfied.
- **Evidence:** the keep-the-current-repository decision is documented as binding in multiple active surfaces, and every adversarial refutation path collapses.
  1. The reviewer's own correction-request states it as a binding Executive Decision: `docs/reviews/2026-05-29-progressive-ai-learning-curve-delivery-correction-request.en.md:10` — "Do not start a new repository for this delivery."
  2. The remediation plan `docs/logs/reviews/2026-05-29-engineering-governance-systemic-remediation.en.md` restates it four times: line 14 header "Decision posture: apply inside the current repository, not by opening a new repository"; §1 lines 24-26 "The recommended decision is: 1. Keep the current repository." with asset-preservation rationale at lines 20-22; §21 Non-Goals "create a new repository"; §22 Final Recommendation "Do not open a new repository."
  3. The ONLY other new-repo mention is `docs/adr/0158-engine-port-transport-agnostic-boundary.yaml` (W7.4), which is a future, user-gated, single-module extraction of `agent-execution-engine`, NOT a project restart, and is explicitly consistent with the main repo remaining home of record: the neutral `bus.spi.engine` contract "stays in the main repo" is stated three times (lines 83-84, 137, 194).
  4. Operationally enacted: the active branch is `governance/progressive-learning-curve-remediation` on a single origin remote (`github.com/chaosxingxc-orion/spring-ai-ascend.git`) — in-place, no new repo.
  5. A repo-wide search for surfaces re-opening the question (open question / should we start a new repo / undecided repo) found NO active surface treating new-repo as undecided.
- **Rationale:** I tried hard to refute the already_satisfied verdict and could not. Three refutation lines all failed: (A) Internal contradiction — whether ADR-0158's W7.4 new-repo creation contradicts "keep the repo"; it does not, because W7.4 extracts only the `agent-execution-engine` module while the neutral contract explicitly "stays in the main repo," i.e. main repo remains home of record. (B) A surface re-opening the decision — none exists; the "open question" hits are about transports, family-yaml auto-derivation, and archived pricing, never the repo question. (C) Operational divergence — the active branch and single origin remote prove in-place remediation, not a fresh repo. The literal proposition ("keep-the-repo is documented as binding, not an open question") is unambiguously TRUE, so I cannot honestly flip to confirmed_missing/partially_satisfied. Two integrity notes that nonetheless do NOT change the verdict: (i) C-02 is near-tautological — the reviewer made the keep-the-repo decision and the responder echoed it, so there was no adversarial requirement to fail; (ii) the SAME correction-request that carries this decision opens with "the current delivery is not acceptable yet" and lists P0-1 (fact-layer `repo_commit` drift, correction-request lines 76-84), P0-2 (gate parity failure `workspace_elements` 608 vs live 609 / `relationships` 469 vs 478, lines 109-114), P0-3, and P1-1..P1-4 as unmet, plus the correction-checklist's ECR-1..ECR-10 / Phase 1-6 all unchecked. Those are the substantive review demands and remain open — but they belong to OTHER claims, not to C-02. Scoped strictly to the keep-vs-new-repo posture, C-02 is satisfied and is not double-counting any other deliverable.
- **Caveat for the orchestrator:** do not let C-02's already_satisfied status imply the overall review is addressed; it covers only the repository-choice preamble.

---

## Reviewer-handoff note

For the rejected/already_satisfied class, the only member is C-02, and its full reasoning (including
the three failed refutation attempts and the explicit scoping caveat) is recorded above so the next
reviewer can re-audit it without re-deriving it. No claim in this batch (C-01..C-06) was *rejected*:
every one is either a real gap (C-03, C-05 confirmed_missing), a backbone-present-machinery-absent
state (C-01, C-04, C-06 partially_satisfied), or a decided posture (C-02 already_satisfied). The
overall review remains OPEN — the substantive demands (correction-request P0-1..P1-4;
correction-checklist ECR-1..ECR-10 / Phase 1-6) are tracked outside this six-claim adjudication and
are not yet built. This response file is step 1 (adjudication); no governance machinery has been
created on this branch yet (`git diff main...HEAD` is empty).
