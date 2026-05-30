# AI Understanding Path — the governed reading order for a new AI agent

> Read time: ~6 minutes. This is the human-readable mirror of the machine-readable
> [`docs/governance/ai-reading-path.yaml`](../governance/ai-reading-path.yaml). The
> two MUST stay in lockstep — same steps, same order, same surfaces. The YAML is
> the source an automated session loads; this page is the version a person reads.

Authority: **ADR-0159** (Progressive Learning Curve and Authority Lanes) and
**ADR-0160** (ADR Governance Model — fixes step 3 routing). The sequence below is
the one specified in the 2026-05-29 engineering-governance systemic-remediation
review, section 7.1.

This page is an **onboarding / navigation** surface (authority lane
L2-AI-ONBOARDING). It is **not** an authority over architecture, decisions, or
implementation truth, and it invents no element ids, ADR ids, relationships, or
statuses. Every surface it names is an existing artifact; every claim about what
a surface *owns* is a pointer into that surface's own lane, not a copy of its
content here.

## The two rules before you read any prose

1. **Read in order, product-first.** Start at step 1 and descend, unless your task
   is explicitly narrower (a scoped bugfix in one known frame may start at that
   frame's card). A broad, architecture-touching, or product-touching task starts
   at the top. **Skipping the product step to read code first is forbidden** — an
   agent that reads code before product builds the wrong thing efficiently.

2. **Generated facts outrank prose.** The authority cascade is one-way:
   **generated facts > DSL > Card/prose**. Where this page and a surface disagree,
   the surface wins; where a surface and a generated fact disagree, the fact wins.

### The factual-claim switch

For any claim about **code, contracts, tests, dependencies, runtime behavior, or
verification**, read the generated facts **before** any hand-authored prose. These
files are extractor projections of the source tree and the ADR corpus — never
hand-edited — and are the apex truth for implementation state:

- `architecture/facts/generated/code-symbols.json` — class + method inventory (JVM descriptors)
- `architecture/facts/generated/contract-surfaces.json` — HTTP / SPI / async contract operations
- `architecture/facts/generated/tests.json` — test-class inventory + `verifies` edges
- `architecture/facts/generated/module-build.json` — reactor modules + build dependencies
- `architecture/facts/generated/runtime-config.json` — posture-aware config + runtime knobs
- `architecture/facts/generated/adrs.json` — extracted ADR facts

When you cite one of these, cite the **fact id**, not just the file name:

| Subject | Fact id format |
|---|---|
| class | `code-symbol/<kebab-fqn>` (FQN dots → hyphens, lowercase) |
| method | an entry in the class fact `public_methods[]` (JVM descriptor) |
| test class | `test/<kebab-fqn>` |
| contract operation | `contract-op/<kebab-op-id>` |

A claim of implementation truth without a fact id (or a gate reference) is not
acceptable where a generated fact exists.

## The 8-node path

The reading order walks the systems-engineering deliverable chain, product-first:
**Product Definition → Requirement Definition → L0 Architecture → EngineeringFrame
→ Feature/FunctionPoint → Contract Surface → Implementation Facts → Verification &
Gate.** Each step below names the authority lane(s) it draws from, the surfaces to
read, what you should understand on exit, and — where it matters — what the
surfaces deliberately do **not** carry, so you do not hunt for L2/code detail in an
L0/L1 surface.

A few surfaces are marked **(planned)**: they are part of the governed target
model but may not exist on disk yet in a parallel-wave repository. They are listed
so this path stays stable across the wave that creates them — skip an absent
*(planned)* surface without error.

### Step 1 — Repository entry  ·  *Orientation*  ·  lane L2-AI-ONBOARDING

Read: `README.md`, `CLAUDE.md`, `AGENTS.md`.

You should understand:
- How to collaborate here (phase-entry skills, the daily principles).
- Which sources are authoritative for which kind of question.
- When generated facts outrank prose (the cascade above).

Not here: product claims (step 2), architecture decisions (step 3),
implementation truth (the generated facts).

### Step 2 — Product definition  ·  *Product → Requirement (ISO/IEC/IEEE 29148)*  ·  lane L1-PRODUCT

Read: `product/PRODUCT.md`, `product/claims.yaml`, `product/requirements.yaml`,
`product/personas.yaml`, `product/journey.md`.

You should understand:
- What product outcome the repository serves, and for which personas.
- Which value claims (ProductClaim) are active.
- Which requirements are in scope and which are explicit non-goals.
- Where the v1.0 financial-vertical line is drawn versus the roadmap.

Not here: Java classes, package roots, module gates; runtime promises (step 6).

### Step 3 — Architecture anchor  ·  *L0 Architecture (ISO/IEC/IEEE 42010 + C4)*  ·  lanes L3-DSL-GRAPH, L4-L0-ARCHITECTURE, ADR-governance

Read, in this order:
- `architecture/workspace.dsl` — the sole architecture authority root: element ids, relationships, views.
- `architecture/README.md` — what the workspace closure carries.
- `architecture/docs/L0/ARCHITECTURE.md` — system boundary, the numbered architectural constraint corpus (§4), cross-module invariants.
- `docs/adr/normalized/index.yaml` — the **current** decision-authority state of the ADR corpus (per-ADR authority state + supersession routing), so you do not re-read raw ADR prose to learn what still governs (ADR-0160).
- `docs/adr/review-index.md` — the human-readable companion to that index.

You should understand: the system boundary; architecture element identities from
the DSL; which ADR clauses still govern; the global / cross-module constraints.

Not here: class names, method names, test-class lists, route implementation detail
(those are L2 / contracts / generated facts). Do not use raw historical ADR prose
for *current* authority — the normalized index supersedes prose for that.

### Step 4 — EngineeringFrame anchor  ·  *C4 Component / arc42 L2 — a package / package-cluster anchor*  ·  lane L5-L1-ARCHITECTURE

Read:
- `architecture/features/engineering-frames.dsl` — the structural-axis authority: EngineeringFrame elements, `Module --contains--> Frame`, `Frame --anchors--> FunctionPoint` (ADR-0157).
- `architecture/docs/L1/engineering-frames.md` — the collective `Module → EngineeringFrame → FunctionPoint` narrative across all domain modules.
- `architecture/docs/L1/frames/` — the **Frame Cards**: one readable per frame, telling you which Java packages, classes, interfaces, SPI surfaces, in/out-of-scope responsibilities, anchored FunctionPoints, and relevant contracts/facts/gates apply **before you edit**. Cards invent nothing — they are a readable interpretation layer over the DSL and facts (Card-over-DSL, ADR-0161).
- `architecture/docs/L1/` — the per-module L1 design (responsibilities, boundary contracts, SPI surfaces, invariants).

You should understand: which EngineeringFrames exist and who owns each; which
package root or package-cluster each anchors; which classes/interfaces form a
frame's stable boundary and which SPI surface other frames may use; which
FunctionPoints each frame anchors.

Not here: private method call chains, persistence/SQL/RLS detail, line-level facts,
test inventory — those live at L2 / contracts / generated facts, never in an L1
frame surface.

**Dual-track note.** An EngineeringFrame is the engineering **landing and
navigation** object, not the behavioral proof object. A Feature *traverses* a frame
— a derived edge computed from `Feature --requires--> FunctionPoint` plus
`Frame --anchors--> FunctionPoint` — and **never owns** a frame. The behavioral
proof object is the FunctionPoint (step 5).

### Step 5 — Demand-to-behavior mapping  ·  *Feature + FunctionPoint join*  ·  lanes L1-PRODUCT, L5-L1-ARCHITECTURE, L6-L2-DESIGN, L9-EXPLICIT-MAPPING

Read:
- `architecture/features/features.dsl` — the value / demand axis: SAA Feature elements (value threads) with the AI Execution Boundary + 9-state lifecycle. A Feature drives and traverses; it does not structurally own.
- `architecture/features/function-points.dsl` — the FunctionPoint inventory: the behavioral join point (a concrete method / scenario / API verb).
- `architecture/mappings/ai-understanding-map.yaml` *(planned)* — the explicit, queryable dual-track map (value / structure / join / evidence / decision / governance axes per FunctionPoint). Lands in the explicit-mapping wave; skip if absent.
- `architecture/docs/L2/` — the per-FunctionPoint technical detailed design (class/method anchor, sequence, state transition, persistence, error path, test mapping). This is the home for the L2/runtime detail that L0/L1 must **not** carry.

You should understand: which Features require which FunctionPoints; which
FunctionPoints are anchored by which EngineeringFrames; which L2 designs own the
implementation detail.

Not here: global policy, product claims, cross-module principles — those are
higher-altitude (steps 2–3).

### Step 6 — Contract and evidence  ·  *Contract Surface → Implementation Facts → Verification & Gate*  ·  lanes L7-RUNTIME-CONTRACTS, generated-facts, L8-GOVERNANCE

Read:
- `docs/contracts/contract-catalog.md` — the runtime-promise surface (wire shape, route behavior, SPI signatures), plus `docs/contracts/` for the per-contract OpenAPI / AsyncAPI / SPI v1 schemas.
- `architecture/facts/generated/` — the generated facts: the apex implementation-truth source (code symbols, contract surfaces, tests, build/runtime facts, extracted ADR facts). Never hand-edited.
- `gate/README.md` and `gate/check_architecture_sync.sh` — the gate overview and the canonical conformance check (run via WSL/Linux per Rule G-7).
- `docs/governance/architecture-status.yaml` — the per-capability shipped/deferred ledger and the canonical baselines: the conformance-state single source of truth.

You should understand: which runtime promises (contracts) the platform commits to;
which generated facts prove the current implementation state (cite fact ids, not
prose); which gates must pass before work is accepted, and the current baselines.

Not here: product rationale or architecture taxonomy — those are higher-altitude
(steps 2–4).

## Output discipline

After reading this path, when you make a claim about any of the following, cite the
**fact id or a gate reference**, not merely a prose file name:

- code symbols · contracts · tests
- feature state · FunctionPoint state · EngineeringFrame state
- ADR authority state · gate coverage

Where a generated fact exists for the subject of a claim, assert from the fact, not
from prose.

## Where this fits

- Machine-readable source: [`docs/governance/ai-reading-path.yaml`](../governance/ai-reading-path.yaml)
- Persona onboarding (start here if you are a person joining the project):
  [`developer.md`](developer.md) · [`sre.md`](sre.md) · [`architect.md`](architect.md) · [`compliance-reviewer.md`](compliance-reviewer.md)
- The collaboration kernel every session loads first: [`../../CLAUDE.md`](../../CLAUDE.md)
