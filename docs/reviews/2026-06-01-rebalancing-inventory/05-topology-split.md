# 05 — Current Topology Map + Knowledge ↔ Governance Split

Date: 2026-06-01
Phase: INV (re-grounded inventory) for the Knowledge ↔ Governance Rebalancing program
Branch: `governance/knowledge-governance-separation` (== current `origin/main`)
Method: live read-only inspection of the working tree + git history. **Re-verified against current
`main`, which is mid-reorganization** (the human team's recent `合并… / 文档整理…` commits are file
moves). All paths and counts below are observed live, not inherited from the charter or proposal.

> Companion docs (already on disk): the program charter
> `docs/reviews/2026-06-01-knowledge-governance-rebalancing-charter.en.md` and the Codex proposal
> `docs/reviews/2026-05-31-ai-governance-knowledge-system-rebalancing-proposal.en.md` (§6.1/§6.2
> define the knowledge vs governance criteria this split aligns to).

---

## Part 0 — Headline findings (read this first)

1. **The reorg moved the top-level `architecture/` tree under `docs/architecture/` and broke the
   gate's architecture-authority bindings.** Top-level `architecture/workspace.dsl` and
   `architecture/docs/L0/ARCHITECTURE.md` **no longer exist**, but the gate scripts
   (`check_architecture_workspace.sh`, `check_architecture_sync.sh`, `always-loaded-budget.txt`,
   `check_architecture_views.sh`, `lib/check_doc_coherence.py`) still point at those dead paths.
   This is a live breakage the rebalancing program inherits — flagged in Part 3.

2. **`docs/architecture/architecture2/` is NOT an abandoned merge artifact — it is the genuine
   architecture-of-record (Structurizr workspace authority), just mis-located and mis-named.** Its
   `workspace.dsl` is **byte-identical** to the pre-reorg top-level `architecture/workspace.dsl`, and
   its `docs/L0/ARCHITECTURE.md` is **byte-identical** to the pre-reorg top-level
   `architecture/docs/L0/ARCHITECTURE.md` (1021 lines). The reorg staged it first as
   `docs/architecture2/` then renamed it (R100, pure move) to `docs/architecture/architecture2/`.

3. **`docs/architecture/l0` (+ `l1`) is a DIFFERENT lineage — an A2D delivery/governance corpus**, not
   a copy of architecture2. It is a newer, Chinese-language, `status: draft` scaffolding tree
   (`00-overview` … `10-governance`, 118 files) for delivery packets / A2D phases / review packets.
   So the "duplication" is **two distinct artifacts parked side by side under one umbrella**, not two
   copies of the same authority. See the verdict in Part 2.

4. **There is no `normalized/` ADR layer** (the abandoned `governance/adr-corpus-systematic-cleanup`
   branch's normalization artifacts are gone, consistent with the charter's "discarded" note).

5. **`governed_invariant` does not yet exist.** 0 ADRs carry it; all current YAML ADRs use
   `status: accepted` (87) / `superseded` (1). It is a charter-proposed bit (reuse target), not a
   present mechanism — the G-track promotion step must mint it.

---

## Part 1 — Topology map (where everything actually lives now)

File counts are `find -type f` over each subtree (live, 2026-06-01).

### `docs/adr/` — 161 files  ·  ADR corpus
| Path | Count | What it is |
|---|---|---|
| `docs/adr/*.yaml` (root) | 89 | Current-format ADRs, `0068`…`0158`. The live decision corpus. |
| `docs/adr/*.md` (root) | 60 | Legacy-format ADRs (`0021`…`0067`-ish), not yet migrated to YAML. Mixed format = drift. |
| `docs/adr/locked/` | 11 | `0001`…`0014` foundational locked decisions (Java 21, Postgres, tenant GUC, Flyway, OAuth2, contract-spine versioning). The truly load-bearing invariants. |
| `docs/adr/archive/` | 1 | `INDEX.md` only (archive stub). |

Highest ADR number observed: **0158**. Root ADRs are dual-format (89 yaml + 60 md) — a normalization
debt the reorg did not resolve.

### `docs/governance/` — 134 files  ·  governance machinery
| Path | Count | What it is |
|---|---|---|
| `rules/rule-*.md` | 57 | Rule cards — the sole rule authority (per CLAUDE.md). Statuses: 54 `active`, 2 `active_advisory`, 1 `design_only`. |
| `principles/P-*.md` | 13 | Governing principles P-A…P-M. |
| `contracts/` | 5 | The 5 phase contracts (`architecture-design`, `engineering-implementation`, `integration-verification`, `system-commit`, `review-response`) loaded by the `/…-mode` skills. |
| `templates/` | 30 | ADR / module / review-packet templates. |
| root `*.yaml` | 14 | `enforcers.yaml`, `architecture-status.yaml`, `architecture-graph.yaml`, `architecture-workspace-graph.yaml`, `architecture-workspace-impact-matrix.yaml`, `recurring-defect-families.yaml`, `principle-coverage.yaml`, `competitive-baselines.yaml`, `deployment-loci.yaml`, `bus-channels.yaml`, `evolution-modalities.yaml`, `evolution-scope.v1.yaml`, `sandbox-policies.yaml`, `skill-capacity.yaml`. Mixed: some are *governed invariants* (enforcers, status), some are *knowledge maps* (graph, impact-matrix, competitive-baselines). |
| root `*.md` | ~10 | `rule-history.md`, `recurring-defect-families.md`, `retired-rules-audit.md`, `dev-environment.md`, `escalations.md`, `logs-folder-policy.md`, `posture-coverage.md`, `whitepaper-alignment-matrix.md`, `structurizr-workspace-w6-sunset-roadmap.md`, `SESSION-START-CONTEXT.md`. Mostly history/reference (knowledge), a few operational. |
| `release-readiness/` | 2 | Release-gate readiness checklists. |

### `gate/` — 155 rule scripts + 12 top-level scripts + `lib/`  ·  the live enforcement engine
| Path | Count | What it is |
|---|---|---|
| `gate/rules/rule-NNN.sh` | 155 | The actual blocking rule engine (one script per rule). |
| `gate/*.sh` / `*.ps1` (top) | 12 | `run_operator_shape_smoke`, `check_architecture_sync`, `check_architecture_workspace`, `check_architecture_views`, `check_formal_release_transaction`, `check_parallel`, `check_spring_ai_milestone`, etc. |
| `gate/lib/` | — | Python/shell helpers (`check_doc_coherence.py`, `check_layer_purity.py`, …). |
| `gate/log/`, `gate/release-ci-evidence/` | — | Historical run logs + CI evidence (knowledge, not engine). |

**Broken bindings (see Part 3):** several gate scripts hard-code `architecture/workspace.dsl` and
`architecture/docs/L0/ARCHITECTURE.md` and `docs/architecture-views` — all now dead paths.

### `docs/architecture/` — 258 files  ·  THREE distinct sub-trees (the mess)
| Sub-tree | Files | What it is | Authority verdict |
|---|---|---|---|
| `architecture2/` | ~130 | **Structurizr workspace authority**: `workspace.dsl`, `profile/`, `views/`, `facts/generated/*.json`, `generated/*.dsl`, `features/*.dsl`, `docs/L0/ARCHITECTURE.md`, `docs/L1/<module>/` (4+1), `decisions/`. Byte-identical to pre-reorg top-level `architecture/`. | **AUTHORITATIVE** architecture-of-record. Mis-named + mis-located. |
| `l0/` | 118 | **A2D delivery / governance-scaffolding corpus** (Chinese, `status: draft`): `00-overview`…`10-governance`, `l1/<service>/`, `architecture-views/` (puml+png+svg exports), delivery packets, A2D phases, review packets, version-intents. Different lineage. | Knowledge / delivery corpus — **not** the of-record DSL authority. |
| `l1/` | 1 | `2026-05-13-l1-architecture-design-guidance.en.md` — a single guidance doc, orphaned. | Knowledge (guidance). |
| `trustworthy/` | 12 | Trust-boundary matrix, AI risk-control map, DfX evidence policy, verification matrix, deployment-plane contract, release-validation checklist, etc. | Mixed: contracts/invariants lean governed; assessments/maps lean knowledge. |

### `docs/contracts/` — 51 files  ·  runtime contract surface
| Path | Count | What it is |
|---|---|---|
| `func/` | 44 | Functional `*.v1.yaml` contract surfaces (the SPI/API authority). |
| `dfx/` | 7 | DfX (reliability/observability/security) contract surfaces. |

### `docs/logs/` — 206 files  ·  delivery + review history
| Path | Count | What it is |
|---|---|---|
| `reviews/` | 146 | Historical reviewer-response logs (rc-cycle responses, post-review waves). Pure history. |
| `releases/` | 42 | Release notes / lockstep baselines. History (some baseline-gated by Rule 27/28). |
| `delivery/` | 7 | Delivery logs (SHA-bound). |
| `adr-amendment-narratives/` | 4 | ADR change narratives. |
| `plans/`, `reports/` | 1 each | Plan / report snapshots. |

### Smaller areas
| Area | Files | What it is | Lean |
|---|---|---|---|
| `docs/reviews/` | 2 | The rebalancing charter + Codex proposal (this program's own docs). | Active program docs. |
| `docs/harness/` | 2 | `debug-first-evidence.md`, `multi-wave-release.md` (runbooks). | Governed runbook (operationalises Rule D-3.b). |
| `docs/competitive/` | 34 | Competitor teardowns (langchain, crewai, dify, SAA, mem0, …). | Knowledge (reference). |
| `docs/archive/` | 93 | Superseded plans, retired STATE.md, v6-rationale, architecture-spike, spring-ai-fin. Already-archived. | Knowledge (frozen). |

---

## Part 2 — `architecture2/` verdict (the headline question)

**Question:** Is `docs/architecture/architecture2/` a live duplicate of the real architecture authority,
or an abandoned merge artifact?

**Verdict: it is the LIVE architecture-of-record (Structurizr workspace authority), not an abandoned
artifact — and not a duplicate of `l0/`. It is the authoritative one for the DSL/workspace layer.**

Evidence (git, live):
- `docs/architecture/architecture2/workspace.dsl` (160 lines) `diff` vs pre-reorg
  `architecture/workspace.dsl` → **IDENTICAL**.
- `docs/architecture/architecture2/docs/L0/ARCHITECTURE.md` (1021 lines) `diff` vs pre-reorg
  `architecture/docs/L0/ARCHITECTURE.md` → **IDENTICAL**.
- Git rename trail across the reorg range shows `docs/architecture2/* → docs/architecture/architecture2/*`
  as **R100 (100% pure renames)** — a relocation, not a regeneration or a half-merge.
- It is the **only `workspace.dsl` in the entire repo** (`find . -name workspace.dsl` → one hit).
- It is a complete, internally-consistent workspace: `profile/` (Structurizr profile + property
  authority), `views/`, `facts/generated/*.json` (the fact layer: adrs/code-symbols/contract-surfaces/
  module-build/runtime-config/tests), `generated/*.dsl` (adr-graph, enforcers, modules, principles,
  rules, spi-catalog), `features/*.dsl`.

**Why it looks like a duplicate (and why the charter called it one):** the folder name `architecture2`
+ its co-location under `docs/architecture/` next to `l0/` makes it *look* like a second copy. It is
not. `architecture2` and `l0` are **two different artifacts of two different lineages**:
- `architecture2` = the **Structurizr-DSL architecture-of-record** (workspace + profile + facts +
  generated views). This is what the gate's `check_architecture_workspace.sh` validates.
- `l0` = the **A2D human-authored delivery/governance corpus** (overview → governance, draft, Chinese),
  a parallel narrative tree that hangs L1 services and L2 topics off a numbered skeleton.

**The real duplication / mess to flag** is narrower and threefold:
1. **Naming + location.** The of-record authority is buried at `docs/architecture/architecture2/` under
   a throwaway name. It should be the canonical `architecture/` (or `docs/architecture/of-record/`),
   and the gate paths should track it.
2. **L0 ARCHITECTURE.md exists in two trees with two purposes** — `architecture2/docs/L0/ARCHITECTURE.md`
   (of-record, gate-validated) vs the `l0/` numbered corpus (delivery narrative). These are *not* the
   same file but they *overlap in intent*; a reader cannot tell which is binding. Resolve by declaring
   `architecture2/docs/L0/ARCHITECTURE.md` the of-record and `l0/` the delivery/knowledge corpus.
3. **`docs/architecture/l1/` (1 orphan file) vs `architecture2/docs/L1/` vs `l0/l1/`** — three "L1"
   homes. The of-record L1 is `architecture2/docs/L1/<module>/`.

**Recommendation:** keep `architecture2` content as the architecture-of-record (rename it out of the
`architecture2` name, repoint the gate); treat `l0/`, `l1/`, `trustworthy/` assessments as knowledge /
delivery corpus. **Delete nothing yet** — no byte-for-byte duplicate of architecture2 exists to delete;
the fix is rename + repoint + reclassify, not deletion.

---

## Part 3 — Inherited breakage the reorg introduced (must-fix, flagged for G/R tracks)

These are live and independent of the knowledge/governance split — surfaced because they block any
gate run on current `main`:

1. **Gate architecture-authority paths are dead.** Hard-coded in:
   - `gate/check_architecture_workspace.sh` → `architecture/workspace.dsl` (GONE; real file is
     `docs/architecture/architecture2/workspace.dsl`).
   - `gate/check_architecture_sync.sh` (`_r86_arch="architecture/docs/L0/ARCHITECTURE.md"`) and
     `gate/always-loaded-budget.txt` → `architecture/docs/L0/ARCHITECTURE.md` (GONE; real file is
     `docs/architecture/architecture2/docs/L0/ARCHITECTURE.md`).
   - `gate/check_architecture_views.sh` + `check_architecture_sync.sh` → `docs/architecture-views`
     (GONE; now `docs/architecture/l0/architecture-views`).
   - `gate/lib/check_doc_coherence.py` → expects `architecture/workspace.dsl` + `architecture/README.md`.
   The workspace check degrades to an advisory PASS when the file is missing, so the breakage is
   **silent** — exactly the failure mode the charter's "frozen/subtractive" stance is meant to avoid.
2. **Mixed-format ADR root** (89 yaml + 60 md) — not reorg-caused but co-resident; the dual format will
   complicate the K-track relocation of "knowledge ADRs."
3. **`governed_invariant` bit absent** — the G-track promotion step has nothing to read yet; it must be
   minted on the locked/security/contract ADRs.

---

## Part 4 — Knowledge ↔ Governance split (the verdict table)

Classification criteria (from proposal §6.1 / §6.2):
- **KNOWLEDGE** = helps an agent *understand* (history, rationale, maps, reference, superseded, review
  logs, generated facts). Large, retrieval-on-demand, **advisory** integrity only. → relocate to a
  future top-level `knowledge/` tree.
- **GOVERNED MAIN-PATH** = constraints that must *actively shape* engineering work (security,
  compatibility, public contracts, tenant isolation, release evidence, build/test honesty,
  generated-fact integrity, real coupling boundaries, the slim rule/gate set). → stays governed.
- **DELETE-DUPLICATE** = a byte-for-byte copy with no independent authority.

| # | Area | Path | Verdict | Rationale |
|---|---|---|---|---|
| 1 | Locked foundational ADRs | `docs/adr/locked/` (11) | **stays-governed** | Truly load-bearing invariants (Java 21, Postgres, tenant GUC, Flyway, OAuth2, contract-spine). Candidate carriers of the new `governed_invariant` bit. |
| 2 | ADR history corpus (root) | `docs/adr/*.yaml` (89) + `docs/adr/*.md` (60) | **relocate-to-knowledge** | Decision history + rationale + supersession. Per §6.1 "ADR history and tradeoffs" + "current ADR index." Keep an index pointer in governance; the bodies move. (Security/contract ADRs flagged for `governed_invariant` stay referenced, but the narrative lives in knowledge.) |
| 3 | ADR archive | `docs/adr/archive/` (1) | **relocate-to-knowledge** | Already archival. |
| 4 | Rule cards | `docs/governance/rules/rule-*.md` (57) | **stays-governed** | The slim rule authority. The G-track *retires/demotes ~50%* — retired cards become knowledge, the surviving minority stay. No bulk move now. |
| 5 | Principles | `docs/governance/principles/P-*.md` (13) | **stays-governed** | P-A…P-M anchor enforcers; main-path. |
| 6 | Phase contracts | `docs/governance/contracts/` (5) | **stays-governed** | The `/…-mode` skill loads — active work surface. |
| 7 | Templates | `docs/governance/templates/` (30) | **stays-governed** | Authoring scaffolds for governed artifacts. |
| 8 | Enforcer + status yamls | `enforcers.yaml`, `architecture-status.yaml`, `principle-coverage.yaml`, `release-readiness/` | **stays-governed** | The enforcement/coverage spine + release gating. |
| 9 | Architecture *maps* (yaml) | `architecture-graph.yaml`, `architecture-workspace-graph.yaml`, `architecture-workspace-impact-matrix.yaml`, `competitive-baselines.yaml`, `whitepaper-alignment-matrix.md` | **relocate-to-knowledge** | Descriptive maps/indexes, not constraints. §6.1 "EngineeringFrame/FunctionPoint maps." |
| 10 | Defect ledger + rule history | `recurring-defect-families.{yaml,md}`, `rule-history.md`, `retired-rules-audit.md` | **relocate-to-knowledge** | §6.1 "recurring defect families" + "lessons learned" + deprecated-decision history. (Note: families.yaml is currently gate-freshness-bound — the G-track must cut that binding when it moves, else the move re-creates the very gate it removes.) |
| 11 | Operational governance md | `dev-environment.md`, `escalations.md`, `logs-folder-policy.md`, `posture-coverage.md`, `SESSION-START-CONTEXT.md` | **stays-governed** | Live operating policy / collaboration rules. |
| 12 | Other domain yamls | `deployment-loci.yaml`, `bus-channels.yaml`, `evolution-modalities.yaml`, `evolution-scope.v1.yaml`, `sandbox-policies.yaml`, `skill-capacity.yaml` | **stays-governed** (audit each) | Domain invariants referenced by ADRs/contracts; verify each is enforcement-bearing vs descriptive during G-track; demote descriptive ones to knowledge. |
| 13 | Gate rule engine | `gate/rules/*.sh` (155) + `gate/*.sh` (12) + `gate/lib/` | **stays-governed** | The enforcement engine. G/D-track *retires* a subset → retired scripts deleted, not relocated. Repoint dead architecture paths (Part 3). |
| 14 | Gate run logs / CI evidence | `gate/log/`, `gate/release-ci-evidence/` | **relocate-to-knowledge** | Historical run artifacts; no live authority. |
| 15 | Architecture-of-record (Structurizr) | `docs/architecture/architecture2/` (workspace.dsl, profile, views, generated, L0/L1 docs) | **stays-governed** | The architecture-of-record. **Rename out of `architecture2`** + repoint gate. |
| 16 | Fact layer | `docs/architecture/architecture2/facts/generated/*.json` | **stays-governed** | Generated implementation/test/contract facts — §6.2 "generated-fact integrity." Stays main-path. |
| 17 | A2D delivery corpus | `docs/architecture/l0/` (118) | **relocate-to-knowledge** | Draft delivery/governance narrative (overview→governance, packets, A2D phases, review packets). Knowledge/delivery, not of-record. Keep `architecture-views/` exports with it. |
| 18 | L1 guidance + service narratives | `docs/architecture/l1/` (1), `l0/l1/` | **relocate-to-knowledge** | Guidance + delivery narrative. Of-record L1 is `architecture2/docs/L1/`. |
| 19 | Trustworthy assessments | `docs/architecture/trustworthy/` (12) | **split (audit)** | Contracts/invariants (`deployment-plane-contract.md`, `interface-contract-metadata.md`, `trust-boundary-matrix.md`, `verification-matrix.md`, `release-validation-checklist.md`) → **stays-governed**; assessments/maps (`architecture-assessment.md`, `ai-risk-control-map.md`, `operating-model.md`, `prompt-playbook.md`) → **relocate-to-knowledge**. |
| 20 | Runtime contracts | `docs/contracts/func/` (44) + `dfx/` (7) | **stays-governed** | Public contract surface — §6.2 "public contract invariants." Core main-path. |
| 21 | Review logs | `docs/logs/reviews/` (146) | **relocate-to-knowledge** | §6.1 "review logs." Pure history. |
| 22 | Release notes | `docs/logs/releases/` (42) | **relocate-to-knowledge** (keep gate hook) | History; but Rules 27/28 baseline-bind the *active* notes. Move bodies, leave the canonical-baseline assertion governed (or cut the binding per charter). |
| 23 | Delivery + amendment + plans/reports logs | `docs/logs/delivery/`, `adr-amendment-narratives/`, `plans/`, `reports/` | **relocate-to-knowledge** | Historical delivery/decision narrative. |
| 24 | Harness runbooks | `docs/harness/` (2) | **stays-governed** | `debug-first-evidence.md` operationalises Rule D-3.b; `multi-wave-release.md` is release procedure. |
| 25 | Competitive teardowns | `docs/competitive/` (34) | **relocate-to-knowledge** | Reference. §6.1 "competitive baselines / rationale." |
| 26 | Archive | `docs/archive/` (93) | **relocate-to-knowledge** | Already superseded/frozen — moves wholesale into `knowledge/archive/`. |
| 27 | This program's docs | `docs/reviews/` (charter + proposal + this dir) | **stays-governed (active)** | Live program control docs; remain until the program closes, then archive. |

### Area-count rollup (verdicts, by major area)

| Verdict | Areas (rows above) | Count |
|---|---|---|
| **relocate-to-knowledge** | 2, 3, 9, 10, 14, 17, 18, 21, 22, 23, 25, 26 | **12** |
| **stays-governed** | 1, 4, 5, 6, 7, 8, 11, 12, 13, 15, 16, 20, 24, 27 | **14** |
| **split (audit per-file)** | 19 (trustworthy) | **1** |
| **delete-duplicate** | — *(none: architecture2 is authority, not a copy; no byte-for-byte dup found)* | **0** |

> Net: **12 knowledge-relocate vs 14 governed-keep** major areas, 1 mixed, 0 pure-delete. Consistent
> with the charter's **subtractive** prime directive — the bulk of file *volume* (logs 206, archive 93,
> competitive 34, ADR history 149, l0 corpus 118) flows to knowledge; the governed surface stays the
> slim rule/gate/contract/of-record set.

---

## Part 5 — Notes / open questions for downstream waves

- **G-track must repoint the gate** to `docs/architecture/architecture2/…` (or to the renamed
  of-record path) and re-arm `check_architecture_workspace.sh` so the missing-file advisory PASS stops
  hiding the breakage (Part 3.1).
- **`recurring-defect-families.yaml` and active release notes are gate-freshness/baseline-bound.**
  Relocating them to `knowledge/` without first cutting those bindings would re-introduce a blocking
  gate over knowledge — the exact bug the program exists to remove. Sequence: cut binding → move.
- **`governed_invariant` must be minted** before the G-track can mechanically separate "ADRs that stay
  referenced by governance" from "ADRs that are pure history." Today the only signal is
  `locked/` directory membership + `status: accepted`.
- **The `architecture2` rename is a prerequisite**, not a nicety: every gate path, every doc link, and
  the of-record/delivery distinction depend on it. Recommend renaming to the canonical `architecture/`
  of-record home and treating `l0/`, `l1/` as the delivery/knowledge narrative.
