# Entry-Path and Requirements-Layer Gap Inventory

Date: 2026-05-29
Audience: governance remediation implementers, architecture reviewers, AI coding agents
Status: advisory inventory (Wave W0 — baseline truth, no authority surface changes)
Scope: the current `README.md` / `AGENTS.md` / `SESSION-START-CONTEXT.md` reading path versus the product-first 8-node progressive learning curve; the missing `product/requirements.yaml` Requirement layer.

> This is a read-only baseline. It records what the entry path says today and where it
> diverges from the target so later waves can plan against measured facts, not memory.
> It changes no shared authority surface (no `architecture-status.yaml`, README, gate, DSL,
> profile, enforcers, or generated facts). Edits to those surfaces are owned by the reconcile step.

## 1. Target reading path (8-node progressive learning curve)

The remediation target is a product-first learning curve whose first two nodes are product
intent, before any architecture interpretation:

```text
Product Definition (PRODUCT.md, claims.yaml, personas.yaml, journey.md)
  -> Requirement Definition (ISO/IEC/IEEE 29148: requirements.yaml + acceptance criteria)
    -> L0 Architecture (ISO/IEC/IEEE 42010 + C4 context/container)
      -> EngineeringFrame (C4 Component / arc42 L2 building block; a Java package / package-cluster anchor)
        -> FunctionPoint (a concrete method / scenario)
          -> Contract Surface (OpenAPI / AsyncAPI / SPI)
            -> Implementation Facts (generated)
              -> Verification & Gate
```

The two remediation source documents already commit the repository to this order:

- `docs/logs/reviews/2026-05-29-engineering-governance-systemic-remediation.en.md:304-377`
  defines the governed AI understanding path. Step 2 (`product_definition`) lists
  `product/PRODUCT.md`, `product/claims.yaml`, `product/requirements.yaml`,
  `product/personas.yaml`, `product/journey.md` and only then does step 3
  (`architecture_anchor`) open the DSL / L0.
- The same plan's authority-lanes table at
  `docs/logs/reviews/2026-05-29-engineering-governance-systemic-remediation.en.md:164`
  declares the Product-definition lane as `product/PRODUCT.md`, `product/claims.yaml`,
  **`product/requirements.yaml`**, `product/personas.yaml`, `product/journey.md`.
- `docs/reviews/2026-05-29-progressive-learning-curve-engineering-correction-checklist.en.md:464-482`
  (ECR-9, "Update the AI Reading Path") prescribes the same order: step 1
  `product/PRODUCT.md` + `product/claims.yaml`, step 2 `product/requirements.yaml` and
  acceptance criteria, step 3 generated facts, step 4 workspace.dsl, and so on.
- ECR-2 ("Establish Requirement Artifacts") at
  `docs/reviews/2026-05-29-progressive-learning-curve-engineering-correction-checklist.en.md:187-216`
  names `product/requirements.yaml` and `product/acceptance-criteria/` as the required
  Requirement-layer artifacts.

## 2. Current reading path (as authored today)

There are three authored entry surfaces. None of them is product-first; none of them names
the Requirement layer.

### 2.1 `README.md` Reading path — 7 steps, architecture-first

`README.md:108-120` defines the canonical Reading path:

| Step | Surface (current) | Slice named by README |
|---|---|---|
| 1 | `architecture/workspace.dsl` + `architecture/README.md` | structure (architecture authority) |
| 2 | `architecture/docs/L0/ARCHITECTURE.md` | declarative L0 constraints |
| 3 | `CLAUDE.md` | enforceable rules |
| 4 | `architecture/docs/L1/README.md` | L1 module design |
| 5 | `docs/contracts/contract-catalog.md` | runtime promise |
| 6 | `docs/quickstart.md` | operational onboarding |
| 7 | `docs/overview.md` | narrative tour |

`README.md:120` summarizes the sequence as
"workspace (structure) -> constraints (declarative) -> rules (enforceable) -> L1 (module
design) -> contracts (runtime) -> quickstart (boot) -> overview (narrative)". Product
definition is **absent**: no step opens `product/PRODUCT.md`, `product/claims.yaml`,
`product/personas.yaml`, or `product/journey.md`, and none opens any Requirement artifact.

### 2.2 `AGENTS.md` "load this set" — 8 steps, facts-first

`AGENTS.md:26-39` ("For AI assistants — load this set") defines the AI load order:

| Step | Surface (current) |
|---|---|
| 1 | `architecture/facts/generated/` (read first for any factual claim) |
| 2 | `architecture/workspace.dsl` + `architecture/README.md` |
| 3 | `architecture/docs/L0/ARCHITECTURE.md` |
| 4 | `CLAUDE.md` |
| 5 | `architecture/docs/L1/README.md` + `architecture/docs/L1/<module>` |
| 6 | `docs/contracts/contract-catalog.md` |
| 7 | `docs/quickstart.md` |
| 8 | `docs/overview.md` |

`AGENTS.md:28` states this order "matches `README.md#Reading-path` step-for-step." It does:
both are architecture/facts-first and neither names the Product-definition lane or the
Requirement layer. The rhetorical-stance table (`AGENTS.md:54-62`) likewise enumerates
workspace.dsl, L0, CLAUDE.md, contracts, L1, quickstart, overview — with no product row.

### 2.3 `docs/governance/SESSION-START-CONTEXT.md` — 14-row table, architecture/governance-only

`SESSION-START-CONTEXT.md:25-42` is the "always-load" reading-order table for AI sessions.
Its rows are: workspace.dsl (+ function-points/capabilities on demand), L0 ARCHITECTURE,
CLAUDE.md (+ rule/principle cards), L1, contract-catalog, quickstart, overview,
architecture-status.yaml, two architecture-graph projections, enforcers.yaml, ADR YAMLs,
CLAUDE-deferred.md, debug-first-evidence runbook. `SESSION-START-CONTEXT.md:23` asserts this
"matches `README.md#Reading-path` step-for-step." Again product-first intent and the
Requirement layer are not present.

## 3. The decisive nuance — product is auto-loaded but not in the documented path

The product authority files are **already in the always-loaded budget** and therefore enter
every AI session's context, yet none of the three authored reading paths mentions them. The
auto-load mechanism and the documented narrative are out of sync.

Evidence:

- `gate/always-loaded-budget.txt:112-116` lists `CLAUDE.md`, `product/PRODUCT.md`,
  `product/claims.yaml`, `product/personas.yaml`, and `product/journey.md` with byte
  ceilings — i.e. they are in the always-loaded set policed by gate Rule 70.
- `product/PRODUCT.md:3` declares itself the "Tier-1 product authority ... Auto-loaded on
  every AI session per `gate/always-loaded-budget.txt`."
- `CLAUDE.md` "Where else to look" marks `product/PRODUCT.md`, `claims.yaml`,
  `personas.yaml`, `journey.md` as "Tier-1 auto-loaded."

Consequence: a session that reads only README / AGENTS / SESSION-START as instructed is told
to start at the architecture workspace and is never told that product intent is the first
node — even though product intent is sitting in its context window. The repository behaves
product-first at the auto-load layer while reading product-last at the documented-narrative
layer. The remediation must reconcile the three authored paths to the product-first order so
the documented sequence agrees with what is actually loaded.

## 4. The missing Requirement layer (`product/requirements.yaml`)

### 4.1 The file does not exist

`product/` currently contains exactly:

| Present | Node of the 8-node chain it serves |
|---|---|
| `product/PRODUCT.md` | Product Definition |
| `product/claims.yaml` (PC-001 .. PC-005) | Product Definition |
| `product/personas.yaml` (Persona-A .. Persona-F) | Product Definition |
| `product/journey.md` (12 stages) | Product Definition |
| `product/source-inputs/` | archived original-language inputs (non-authority) |

`product/requirements.yaml` is **absent**. A repository-wide search for any `requirements.yaml`
returns no file anywhere. `product/acceptance-criteria/` (named by ECR-2) also does not exist.

### 4.2 There is no Requirement node anywhere — the chain skips node 2

The Requirement layer is missing not only as a `product/` file but as a model element. A
search of `architecture/features/*.dsl` for `requirement` / `req.` / `product/requirements`
finds nothing. The value axis is wired today as:

```text
ProductClaim (claims.yaml: PC-001..PC-005)
  -> Feature (architecture/features/features.dsl: saa.productClaim -> PC-NNN)
    -> FunctionPoint (architecture/features/function-points.dsl)
```

That is, Feature binds **directly** to ProductClaim. The intended value axis is
`ProductClaim -> Requirement -> Feature -> FunctionPoint`. The Requirement node — the
ISO/IEC/IEEE 29148 stakeholder/system/non-functional requirements and acceptance criteria
that should sit between a Product Claim and a Feature — has no carrier:

- no `product/requirements.yaml` data file;
- no `product/acceptance-criteria/` surface;
- no `SAA Requirement` element, DSL file, profile tag, or relationship verb;
- no reading-path step.

`product/journey.md:41` shows the live traceability chain the team currently smoke-tests:
`PC-001 <- claims.yaml <- features.dsl[FEAT-RUN-LIFECYCLE-CONTROL] <- ADR-0020 <-
rule-R-C.2.md <- Enforcer E2`. It steps from ProductClaim straight to Feature with no
Requirement hop, confirming the chain is currently 7-node (Claim -> Feature -> ...), not the
target 8-node (Claim -> Requirement -> Feature -> ...).

### 4.3 What a `product/requirements.yaml` is expected to carry

Per ECR-2
(`docs/reviews/2026-05-29-progressive-learning-curve-engineering-correction-checklist.en.md:187-216`)
and the authority-lanes table (Product-definition lane), each requirement entry should carry:
stable ID, source ProductClaim, rationale, priority, status, and concrete observable
acceptance criteria; each requirement must map to an existing Feature/FunctionPoint or be
marked out-of-scope/deferred; each non-functional requirement must name the affected L0
architecture constraint or ADR. ECR-2's acceptance bar states no FunctionPoint may be added
from prose alone — it must trace back to a Requirement or an ADR-created structural need.
Authoring the file is product-owner authority for any new normative requirement wording;
`product/PRODUCT.md:90-94` reserves claim / pitch / buyer-scope wording to the product owner,
and the Requirement layer that derives from claims inherits the same authority constraint.

## 5. Gap summary

| # | Gap | Current state | Target state | Primary evidence |
|---|---|---|---|---|
| G-1 | README Reading path is architecture-first | `README.md:108-120` starts at `architecture/workspace.dsl`; no product step | step 1 = product definition before architecture | `README.md:108-120` vs en-plan:304-377 |
| G-2 | AGENTS load-set is facts-first, product-silent | `AGENTS.md:26-39` starts at generated facts then workspace.dsl | product definition precedes architecture anchor | `AGENTS.md:26-39` vs ECR-9:470-482 |
| G-3 | SESSION-START table is architecture/governance-only | `SESSION-START-CONTEXT.md:25-42` has no product row | product-definition rows added as first steps | `SESSION-START-CONTEXT.md:25-42` |
| G-4 | Auto-load vs documented-path mismatch | product Tier-1 auto-loaded (`always-loaded-budget.txt:112-116`) but absent from all three reading paths | documented path agrees with auto-load order | `always-loaded-budget.txt:112-116`; `product/PRODUCT.md:3` |
| G-5 | `product/requirements.yaml` missing | file absent; no `requirements.yaml` anywhere | requirements registry exists with ID/claim/rationale/priority/status/acceptance | `product/` listing; ECR-2:187-216 |
| G-6 | No Requirement node in the model | Feature binds directly to ProductClaim; no `SAA Requirement` element / verb / reading-path step | `ProductClaim -> Requirement -> Feature -> FunctionPoint` | `architecture/features/*.dsl` (no `requirement` match); `product/journey.md:41` |
| G-7 | No acceptance-criteria surface | `product/acceptance-criteria/` absent | acceptance criteria as observable behavior per requirement | ECR-2:187-216 |

## 6. Out of scope for this inventory (owned by later / reconcile steps)

This W0 inventory does not author or modify any of the following — they are recorded here so
the boundary is explicit:

- editing `README.md`, `AGENTS.md`, `CLAUDE.md`, or `SESSION-START-CONTEXT.md` reading paths
  (the entry-path rewrite is a later wave; see en-plan Task 9 at
  `docs/logs/reviews/2026-05-29-engineering-governance-systemic-remediation.en.md:1354-1377`);
- creating `product/requirements.yaml` or `product/acceptance-criteria/` (Requirement-layer
  authoring; product-owner sign-off required for normative wording);
- adding a `SAA Requirement` profile tag, relationship verb, or DSL element
  (`architecture/profile/*`, `architecture/features/*.dsl` are reconcile-owned);
- changing `gate/always-loaded-budget.txt`, `docs/governance/architecture-status.yaml`,
  `docs/governance/enforcers.yaml`, `docs/governance/recurring-defect-families.yaml`, or any
  `architecture/facts/generated/*` artifact.

## 7. Authority chain note

Findings above are grounded in the single-source-of-truth cascade
(generated facts > DSL > Card/prose). Where this inventory cites prose surfaces
(`README.md`, `AGENTS.md`, `product/journey.md`), it cites them as the authored entry-path
narrative under assessment, not as factual claims about code. The absence of a
`SAA Requirement` element is asserted from the DSL search over `architecture/features/*.dsl`;
the absence of `product/requirements.yaml` is asserted from the `product/` directory listing.
No fact IDs are invented and no IDs or relationships are created by this document.
