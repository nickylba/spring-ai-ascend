# Rebalancing Inventory — Synthesis & Re-Grounded Execution Guide

Date: 2026-06-01 · Branch: `governance/knowledge-governance-separation`
Source: the six artifacts 01–06 in this directory (re-grounded against current main; all prior
exploration numbers from the abandoned branch are void).

## Re-grounded baselines (verified on current main)

| Corpus | Count | Notes |
|---|---|---|
| ADRs | **155 files / 154 decisions** | 56 `.md` + 88 `.yaml` root + 11 locked; archive has only INDEX.md |
| ADR nature | PRODUCT 87 · FOUNDATIONAL 12 · META 55 | meta ≈ 36% |
| Premature-selection | **89 / 154 (58%)** | binds versions / FQNs / record fields / SQL / HTTP verbs |
| Rule cards | **57** | 53 active + 2 advisory + 1 design_only + D-9 (no `status:` — gate's "55" drops it) |
| Enforcers | **190** | E145 vacant; 139 gate-script / 30 integration / 20 archunit / 1 schema |
| Gate checks | **157** | strictly **binary** — no advisory tier exists |
| Safety core | **~18 rules / ~18–27 gates** | the irreducible keep set |
| Retire/demote tail | **~118 gates / ~40 enforcers** | target gate count ~60–110 (≥50% cut) |
| Families / grandfather lists | 41 / 16 txt | 3 allowlists are EMPTY (make their gate vacuous) |

## Surprises that change the plan

1. **The base is partially BROKEN (pre-existing, independent of this program):**
   - The architecture-of-record (the only `workspace.dsl` + L0 `ARCHITECTURE.md`, byte-identical to the
     pre-reorg `architecture/`) is **mislocated at `docs/architecture/architecture2/`**, and the gate
     still points at the old top-level `architecture/*` paths → **architecture/workspace checks pass
     VACUOUSLY**. A green gate today is partly fake. `architecture2` is genuine authority, just misnamed —
     **not** a duplicate to delete; `docs/architecture/l0` is a separate A2D delivery corpus (knowledge).
   - **33 broken doc links** from CLAUDE.md/README/AGENTS (the arch move); ADR README 17 broken;
     `docs/contracts/*.v1.yaml` 11 absent.
   - **5 dead gate rules** (Rule 120 literal `pass_rule`; Rule 100 vacuous via empty allowlist;
     Rules 96/99 lost their subject when CLAUDE-deferred.md was removed; Rule 44 no-op).
   - **ADR slug↔content drift on ~10 files** (e.g. `0013-vault-secrets-management.md` actually decides
     UUIDv7; `0011-flyway-schema-migration.md` actually decides Spring Cloud Gateway); 3 out-of-sync
     index registries (one is a generator input); ADR-0155 duplicated (`.md` + `.yaml`).

2. **The keystone is SIMPLER than diagnosed.** E28's gate body is a presence stub; ADRs + plans are
   **already excluded** from the corpus scan (`ACTIVE_NORMATIVE_DOCS`, Rules 18/22/23). The only
   conflation channel for a future `knowledge/` tree is that it would be **auto-INCLUDED** (matches no
   exclusion). So **G1 keystone = add `knowledge/` to the exclusion + unify the 3 out-of-sync exclusion
   lists (18/22/23) behind one vocabulary file + narrow the R-C kernel + E28 `asserts:` prose** — NOT an
   E28 logic rewrite.

3. **No advisory tier exists** (gate is binary). So "demote to advisory" → **retire-by-DELETE** (fits the
   subtractive directive). Genuine ongoing non-blocking signals move to the knowledge advisory integrity
   scripts, not a new gate tier.

4. **A simplification lever already exists, unstarted:** G-13's kernel declares it subsumes
   G-2.b/.d/.1, G-8.a/.c/.e, G-9.c "until W10 cleanup." Execute that subsumption.

## Prerequisites (precede the K/G tracks)

- **PRE1 — main-path integrity:** repoint the gate's dead architecture paths to the real
  architecture-of-record so checks stop passing vacuously. (Renaming `architecture2` to a clean name is
  the owner's call — flag, don't force.)
- **PRE2 — ADR data quality:** fix the ~10 slug↔content drifts + the 0155 dup + reconcile/retire the 3
  index registries **before any cluster-merge**, else the merge bakes in drift.
- **PRE3 — relocation unblock:** cut the gate-freshness/baseline binding on
  `recurring-defect-families.yaml` + active release notes before relocating them, else the move
  re-creates the gate the program is removing.

## ADR cluster merge-map (→ 7 knowledge docs + 1 logs sink; ~70 ADRs collapse)

- R-A Suspend/Resume (0019,0024,0025,0030,0070,0074,0112,0137,0146) → `knowledge/suspend-resume-model.md`
- R-B Spring-AI SPI (0121–0134) → `knowledge/spring-ai-spi-surface.md`
- R-C Engine contract (0071–0074,0079,0088,0090,0112,0113,0140,0158) → `knowledge/engine-contract.md`
- R-D Resilience/Skill-capacity (0008,0030,0038,0052,0070,0080,0081) → `knowledge/resilience-and-skill-capacity.md`
- R-E rc-wave META (0083–0098,0105,0114,0116,0118) → `docs/logs/governance-waves.md` (changelog sink, not knowledge)
- R-F Agent-service L1 (0048,0078,0088,0100,0115,0136,0138,0140,0142,0144,0155) → `knowledge/agent-service-l1-design.md`
- R-G Structurizr+Feature-registry (0147–0154) → `knowledge/architecture-authoring-and-fact-layer.md`
- R-H Memory/Knowledge model (0034,0051,0082) → `knowledge/memory-knowledge-model.md`

## Refined wave sequencing

`W0 ✓ → INV ✓ → {PRE1 ∥ PRE2 ∥ PRE3} → G1 keystone (exclusion + R-C narrow) →
{ G-retire (DELETE tail, exec G-13 subsumption) ∥ K-relocate (clusters → knowledge/) } →
G-reconcile baselines + R-refresh (fix 33 links, rewrite CLAUDE.md/README) → self-check (gate GREEN via WSL, non-vacuous)`

## The "self-check green" caveat

Current main's gate is partly vacuous (dead arch paths) and carries broken links. A *true* green requires
PRE1 first; otherwise green is partly fake. The user's "fix bugs if any" mandate covers these pre-existing
defects. Baseline gate run (WSL) establishes the real starting state before any edit.
