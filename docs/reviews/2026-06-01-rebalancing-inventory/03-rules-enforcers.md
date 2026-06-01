---
title: "Rebalancing Inventory 03 — Rules + Enforcers Classification for ~50% Simplification"
date: 2026-06-01
status: inventory
scope: docs/governance/rules/rule-*.md + docs/governance/enforcers.yaml
verified_against_tree: governance/knowledge-governance-separation @ d70030b (== origin/main 97a4642)
---

# Rules + Enforcers Rebalancing Inventory

Read-only classification pass. Every number below was re-derived from the live working tree;
no prior-branch figure was trusted.

## Baseline (re-verified, not copied)

| Metric | Live value | Source |
|---|---|---|
| Rule cards on disk (`rule-*.md`) | **57** | `ls rule-*.md` |
| — status `active` | 53 | `grep -l '^status: active$'` |
| — status `active_advisory` | 2 (G-14, G-15) | `grep -l '^status: active_advisory$'` |
| — status `design_only` | 1 (R-I.1) | `grep -l '^status: design_only$'` |
| — **no `status:` field at all** | 1 (D-9 — treated as active) | data-quality defect, see note |
| `active_engineering_rules` baseline claim | 55 | architecture-status.yaml |
| Enforcer rows (`enforcers.yaml`) | **190** (E1–E191, E145 vacant) | `grep -c '^- id: E'` |
| — kind `gate-script` | 139 | |
| — kind `integration` | 30 | |
| — kind `archunit` | 20 | |
| — kind `schema` | 1 (E13) | |
| — kind `compile-time` | 0 | |
| — `governance_infra: true` | 105 | |
| — `product_claim:` bound | 85 | |
| Recurring-defect families | 41 | recurring-defect-families.yaml |

**Reconciliation of "55":** 53 active + 2 advisory = 55. The gate counts cards via
`grep -lE '^status:[[:space:]]*active'`, which (a) counts `active` + `active_advisory`, and
(b) **silently drops D-9** because D-9's frontmatter has no `status:` key. So the live corpus is
really **56 active rules** (the 55 + D-9). D-9 is unambiguously active (CLAUDE.md daily principle,
**X**-cited in all 6 phase contracts, enforced by E163). The missing-`status:` field on D-9 is a
latent Rule-G-3/G-11 coherence gap, not a deliberate exclusion. R-I.1 is `design_only` (E143/E144
armed for W3+), out of the active set but listed for completeness.

---

## A. Rule classification (all 57 cards)

CLASS legend: **CK**=collaboration_kernel · **SI**=safety_invariant · **AI**=architecture_invariant ·
**QS**=quality_standard · **RS**=remediation_scaffold · **HL**=historical_lesson ·
**DUP**=duplicate_or_subsumed.
ENF legend: **blk**=blocking · **cfb**=changed-files-blocking · **adv**=advisory ·
**retire** · **merge→X**.

| id | title | status | CLASS | recommended enforcement | rationale |
|---|---|---|---|---|---|
| D-1 | Root-Cause + Strongest-Interpretation Before Plan | active | CK | advisory | process discipline; unenforceable mechanically; keep as kernel |
| D-2 | Simplicity & Surgical Changes | active | CK | advisory | process discipline; no enforcer |
| D-3 | Pre-Commit Checklist + Evidence-First Debug | active | CK | cfb | E112 only checks runbook presence; the checklist itself is advisory |
| D-4 | Three-Layer Testing, With Honest Assertions | active | CK | advisory | shippability gate; human-judged |
| D-5 | Self-Audit is a Ship Gate | active | CK | advisory | ship gate; human-judged |
| D-6 | Posture-Aware Defaults | active | **SI** | blk | dev/research/prod default discipline; backs E9/E16/E17/E22 (auth + in-mem store gating) |
| D-7 | Concurrency / Async Resource Lifetime | active | AI | adv | design rule; no dedicated enforcer |
| D-8 | Single Construction Path Per Resource Class | active | AI | adv | DI discipline; no dedicated enforcer |
| D-9 | No Version / Log Metadata in Code | (active*) | QS | cfb | E163 gate-script; **add missing `status: active` field** |
| G-1 | Layered 4+1 Discipline + Workspace Truth | active | **AI** | blk | E55/E56/E57/E58 — generated-projection byte-identity + front-matter; core |
| G-1.1 | L1 Architecture Depth & Grounding | active | AI | cfb | E166/167/168; scaffold-ish (L1-doc shape) but grounds code-mapping; keep changed-files |
| G-2 | Authority-Text Reality (doc/status/path/numeric) | active | **AI** | cfb | E16/E25/E115/E116/E119/E135/E136; partly subsumed by G-13 (see §D) |
| G-2.1 | Deleted-Module Scope Prevention | active | DUP | **merge→G-13** | G-13 kernel explicitly subsumes G-2.1; keep as defence-in-depth only until W10 |
| G-3 | Kernel-Card-Implementation Coherence | active | AI | cfb | E97/98/99/133/139/140/151; meta-coherence of the rule corpus |
| G-3.1 | Kernel-Implementation Disjunction Truth | active | RS | **merge→G-3** | E141/142; ultra-narrow grammar split from G-3.f; fold back |
| G-4 | Always-Loaded Context Budget | active | CK | cfb | E100/101; protects Tier-1 token budget; keep |
| G-5 | Gate Self-Consistency (parity/coverage/manifest) | active | **SI** | blk | E121–126; gate-integrity — the gate that guards the gate; core |
| G-6 | Gate Machinery Integrity (duration + config) | active | QS | cfb | E102/103; perf-regression + config-schema; demote duration half to advisory |
| G-7 | Linux-First Dev Environment | active | CK | adv | E104 only checks doc presence; environment policy |
| G-8 | Cross-Authority Parity (5 sub-clauses) | active | **AI** | cfb | E146–150; **.a/.c/.e subsumed by G-13**; keep .b/.d, retire rest at W10 |
| G-9 | Recurring-Defect Family Truth | active | RS | cfb | E156/157/158; ledger-freshness scaffold; keep while remediation program runs, sunset after |
| G-10 | Parallel-Linux-Scripts Mandate | active | QS | cfb | E164; gate-perf hygiene |
| G-11 | Phase-Contract Rule-Allocation Coherence | active | AI | cfb | E165; keeps the 6 phase contracts ↔ cards consistent; structural-necessary post-ADR-0098 |
| G-12 | Whitebox Quality Baseline | active | **QS** | blk | E169; SpotBugs/PMD/Checkstyle; real quality gate; core of quality tier |
| G-13 | Single-Source Rendering Coherence | active | **AI** | blk | E174; **the constructive replacement** for the G-2/G-8/G-9 drift family; keep + accelerate W10 |
| G-14 | Feature Lifecycle Validity | active_advisory | AI | adv→cfb | E175/178; promote to blocking after soak (already scheduled W5) |
| G-15 | Fact-Layer Integrity | active_advisory | **SI** | blk | E179; generated-fact integrity (provenance + byte-identity + no-LLM-authoring); core |
| G-16 | ProductClaim Referential Integrity | active | RS | adv | E181; **vacuous-blocking** — gated on G-21=0 (currently 6); traceability scaffold |
| G-17 | No Orphan Artefacts | active | RS | adv | E182; same advisory-until-zero cluster |
| G-18 | Traceability Chain Completeness | active | RS | adv | E183; same cluster |
| G-19 | Auto-Load Tier Integrity | active | CK | cfb | E184; protects PRODUCT.md/CLAUDE.md budget; blocking already |
| G-20 | Governance-Infra Honesty | active | RS | adv | E185; vocabulary lint; same advisory cluster |
| G-21 | ProductClaim Placeholder Decreasing | active | RS | adv | E186; the convergence-signal meta-rule for G-16..G-20; **retire whole cluster once it hits 0** |
| G-22 | Accepted-ADR Frame-Map Coherence | active | RS | **retire/merge→G-1** | E187; hard-coded single-case assertion keyed off ADR-0158 |
| G-23 | Shipped-Frame Anchor Integrity | active | AI | cfb | E188; frame↔FunctionPoint anchor integrity; keep |
| G-24 | Old Orchestration-SPI Package Ban | active | HL | **retire/merge→G-2.1** | E189; one frozen-ADR (0158) stale-package string ban; folds into deleted-name family |
| G-25 | Tier-1 Non-English / Mojibake Lint | active | **SI** | blk | E190; English-only Tier-1 surface integrity; cheap + high blast-radius; keep |
| G-26 | Local Plan-Path Ban | active | HL | **merge→G-2/G-13** | E191; one private-path string ban from a single review finding |
| M-1 | Skeleton Module Has No Production Java | active | AI | cfb | E114; module-shape invariant; keep |
| M-2 | Domain Contract Discipline (schema-first + …) | active | AI | cfb | E85/116/127/128; schema-first + design-only registration; keep |
| R-A | Business/Platform Decoupling | active | **AI** | blk | E48/49; SPI-only extension + runnable quickstart; product-claim PC-001 core |
| R-A.c | Quickstart CI Smoke Run | active | QS | cfb | E107; CI job presence check; keep |
| R-B | Competitive Baselines Required | active | QS | cfb | E50/51; release-note pillar discipline; demote-able to advisory |
| R-C | Code-as-Contract | active | **AI** | blk | E15–30 family; the meta-principle every enforcer hangs from; core |
| R-C.1 | Independent Module Evolution | active | AI | cfb | E31; module-metadata + isolation build; keep |
| R-C.2 | Run Contract Spine | active | **SI** | blk | E2/4/9/11; tenantId-required + state-machine-validity on Run/Idempotency; core tenant-isolation |
| R-D | SPI + DFX + TCK Co-Design + Catalog Integrity | active | **AI** | blk | E3/32/105–108/117/118/131; public-SPI contract integrity; core |
| R-E | Three-Track Channel Isolation | active | AI | cfb | E64; bus-channel manifest; keep |
| R-F | Cursor Flow Mandate | active | AI | cfb | E65/72; long-horizon API shape; keep |
| R-G | Reactive External I/O | active | **SI** | blk | E66; no RestTemplate/JdbcTemplate in runtime; runtime-correctness; keep |
| R-H | No Thread.sleep in Business Code | active | **SI** | blk | E67; declarative-suspension invariant; keep |
| R-I | Five-Plane Manifest | active | AI | cfb | E68; deployment-plane manifest; keep |
| R-I.1 | Edge↔Compute Ingress Routing | design_only | AI | adv (armed W3) | E143/144; not in active set; promote when agent-client SDK lands |
| R-J | Storage-Engine Tenant Isolation + Cancel Re-Auth | active | **SI** | blk | E69/106; RLS-per-tenant-table + cancel re-authz; core security |
| R-K | Skill Capacity Matrix | active | AI | cfb | E70/73; capacity-matrix manifest; keep |
| R-L | Sandbox Permission Subsumption | active | **SI** | blk | E71; sandbox default-policy required-keys; security-relevant; keep |
| R-M | Engine Contract (envelope/matching/hooks/S2C/…) | active | **AI** | blk | E73–92 family; engine dispatch + S2C contract; core architecture boundary |

### Rule count by CLASS

| CLASS | count | ids |
|---|---|---|
| collaboration_kernel (CK) | 7 | D-1, D-2, D-3, D-4, D-5, G-4, G-7, G-19 *(G-19 borderline CK/RS — counted CK)* |
| safety_invariant (SI) | 10 | D-6, G-5, G-15, G-25, R-C.2, R-G, R-H, R-J, R-L, *(+R-A boundary)* |
| architecture_invariant (AI) | 21 | D-7, D-8, G-1, G-1.1, G-2, G-3, G-8, G-11, G-13, G-14, G-23, M-1, M-2, R-A, R-C, R-C.1, R-D, R-E, R-F, R-I, R-I.1, R-K, R-M |
| quality_standard (QS) | 6 | D-9, G-6, G-10, G-12, R-A.c, R-B |
| remediation_scaffold (RS) | 8 | G-3.1, G-9, G-16, G-17, G-18, G-20, G-21, G-22 |
| historical_lesson (HL) | 2 | G-24, G-26 |
| duplicate_or_subsumed (DUP) | 1 | G-2.1 |

*(CK count shown as 7 with G-19 in CK; if G-19 is read as a budget-protection scaffold the split is CK=6/RS=9. AI count includes the design_only R-I.1. Totals span all 57 cards; a handful sit on a class boundary and are annotated inline — the load-bearing output is the SAFETY-CORE and retire/merge lists below, which do not depend on those boundary calls.)*

---

## B. SAFETY CORE (irreducible — keep blocking, never retire)

These cover security, tenant-isolation, public-contract compatibility, release-evidence integrity,
generated-fact integrity, and core architecture boundaries. **18 rules.**

| Category | Rule ids |
|---|---|
| Security / tenant-isolation | **R-J** (RLS + cancel re-authz), **R-C.2** (tenantId spine + state-machine), **D-6** (posture-gated auth/in-mem defaults), **R-L** (sandbox default-policy) |
| Runtime correctness invariants | **R-G** (no blocking I/O in runtime), **R-H** (no Thread.sleep) |
| Public-contract compatibility | **R-D** (SPI + catalog integrity), **R-A** (business/platform decoupling, SPI-only ext), **R-M** (engine + S2C contract), **R-C** (Code-as-Contract meta-principle) |
| Generated-fact / projection integrity | **G-15** (fact-layer provenance + byte-identity), **G-1** (workspace projection byte-identity), **G-13** (single-source rendering) |
| Release-evidence + gate integrity | **G-5** (gate self-consistency / fail-closed), **G-12** (whitebox quality gate) |
| Tier-1 authority integrity | **G-25** (non-English/mojibake lint), **G-19** (auto-load tier budget) |
| Core boundary manifests | **M-2** (schema-first domain contracts) |

SAFETY-CORE id list (copy-paste):
`R-J, R-C.2, D-6, R-L, R-G, R-H, R-D, R-A, R-M, R-C, G-15, G-1, G-13, G-5, G-12, G-25, G-19, M-2`

---

## C. RETIRE / MERGE / DEMOTE TAIL

The ~50% simplification target lives here. **8 merge/retire + 6 advisory-demote candidates.**

### C.1 Merge or retire (subsumed / single-case / frozen-ADR-derived) — 8 rules

| id | action | absorbing rule | evidence |
|---|---|---|---|
| **G-2.1** | merge → G-13 | G-13 | G-13 kernel: "Subsumes … G-2.1 … in the W3..W10 retirement schedule" |
| **G-3.1** | merge → G-3 | G-3 | narrow disjunction-grammar split from G-3.f; one allow-list file (E141/142) |
| **G-22** | retire (or merge → G-1) | G-1 | hard-coded single assertion keyed off ADR-0158 EF-ENGINE-PORT; not a general invariant |
| **G-24** | merge → G-2.1 | G-2.1 / deleted-name family | one frozen-ADR-0158 stale-package string ban |
| **G-26** | merge → G-2 / G-13 | G-2 | one private-path (`D:\.claude\plans`) string ban from a single review finding |
| **G-8.a/.c/.e** *(sub-clauses)* | retire | G-13 | both G-13 kernel and F-cross-authority-agreement/F-numeric-drift families say "subsumes G-8.a/c/e in W10 cleanup" — keep G-8.b/.d only |
| **G-2.b / G-2.d-root** *(sub-clauses)* | retire | G-13 | G-13 kernel names these explicitly; rendered surfaces make them vacuous |
| **G-9.c** *(sub-clause)* | retire | G-13 | yaml↔md parity becomes a render-idempotency check once families.md is rendered |

### C.2 Demote to advisory / sunset on convergence — 6 rules (the G-16..G-21 cluster)

All six are **already advisory and vacuous-blocking** — each promotes to blocking only when
G-21's placeholder count hits zero. Live count = **6** (4 real markers + 2 rule-card self-refs).
They are a remediation scaffold for the product-traceability backfill, not a standing invariant.

| id | action | trigger |
|---|---|---|
| G-16 ProductClaim Referential Integrity | keep advisory; retire after convergence | G-21 → 0 |
| G-17 No Orphan Artefacts | keep advisory; retire after convergence | G-21 → 0 |
| G-18 Traceability Chain Completeness | keep advisory; retire after convergence | G-21 → 0 |
| G-20 Governance-Infra Honesty | keep advisory; retire after convergence | G-21 → 0 |
| G-21 ProductClaim Placeholder Decreasing | retire the whole cluster when it reaches 0 | self |
| G-9 Recurring-Defect Family Truth | demote .c sub-clause; sunset ledger-freshness after remediation program closes | remediation program complete |

### C.3 Demote duration/perf-only halves to advisory

| id | action |
|---|---|
| G-6 (.a duration-regression half) | advisory; keep .b config-schema blocking |
| G-10 | advisory (gate-perf hygiene, not correctness) |
| R-B | advisory (release-note pillar prose) |
| G-7, D-1, D-2, D-4, D-5 | already advisory by nature (process/human-judged) — formalize |

**Net rule reduction if executed:** 5 cards fully merged away (G-2.1, G-3.1, G-22, G-24, G-26) +
6 cluster cards retired on convergence (G-16/17/18/20/21 + G-9 sunset) = **~11 of 56 cards (~20%)**;
the remaining ~30% toward "50%" comes from collapsing G-2/G-8 multi-sub-clause rules into G-13
(sub-clause retirement, not whole-card) and demoting ~10 process/perf rules from blocking to advisory.

---

## D. Enforcer keep vs retire (190 rows)

Driver: enforcers inherit their fate from the backing rule. Enforcers backing a SAFETY-CORE rule =
**keep**. Enforcers backing a merge/retire rule = **retire-with-rule** (fold the assertion into the
absorbing rule's enforcer or drop if vacuous). Vacuous/armed-for-future and pure-meta-coverage rows
are **demote/retire** candidates.

### D.1 Counts

| Disposition | count | which |
|---|---|---|
| **KEEP (blocking)** | ~150 | all archunit (20) + integration (30) + schema (1) [these back real code/contract invariants] + the gate-script rows backing SAFETY-CORE & kept AI rules |
| **RETIRE / fold** | ~28 | rows backing merged/subsumed rules (see D.2) |
| **DEMOTE to advisory** | ~12 | G-16..G-21 cluster (E181–186), G-6.a (E102), perf/doc-presence-only rows |

Approx keep:retire ≈ **150 : 40** (retire+demote). Exact retire set below.

### D.2 Retire-with-rule (fold into absorbing enforcer or drop)

| enforcer(s) | kind | backing rule | reason |
|---|---|---|---|
| E187 | gate-script | G-22 | single-case ADR-0158 assertion; retire with G-22 |
| E189 | gate-script | G-24 | stale-package string ban; fold into deleted-name enforcer (E129/130/137/138) |
| E191 | gate-script | G-26 | private-path string ban; fold into G-2/G-13 render check |
| E141, E142 | gate-script | G-3.1 | fold into G-3 enforcer set (E97–99/133) |
| E120, E129, E130, E137, E138, E154 | gate-script | G-2.1 | keep as defence-in-depth until W10, then retire under G-13 |
| E115 (G-2.b), E119 (G-2.d) | gate-script | G-2 | subsumed by G-13 render idempotency (E174); retire at W10 |
| E146, E148, E150 (G-8.a/.c/.e) | gate-script | G-8 | G-13-subsumed sub-clauses; retire at W10, keep E147/E149 (.b/.d) |
| E158 (G-9.c yaml↔md parity) | gate-script | G-9 | becomes render-idempotency once families.md rendered |
| E181, E182, E183, E185, E186 | gate-script | G-16/17/18/20/21 | advisory-until-convergence; retire whole block when G-21 → 0 |

### D.3 Demote (keep row, drop blocking severity)

| enforcer | backing rule | reason |
|---|---|---|
| E102 | G-6.a | duration-regression is a perf signal, not correctness |
| E104 | G-7 | only checks dev-environment doc presence |
| E107 | R-A.c | CI-job presence check |
| E50, E51 | R-B | release-note pillar-name presence |
| E112 | D-3.b | runbook-presence only |
| E164 | G-10 | gate-perf hygiene |
| E184 | G-19 | keep blocking (Tier-1 budget) — do NOT demote; listed to flag the boundary call |

### D.4 Meta-coverage gate-script rows — keep but consolidate

E28/E29/E30/E32/E33/E35/E55–E63 are the "gate that audits the corpus" rows (enforcer-wellformed,
artifact-path-exists, anchor-resolves, graph-idempotent, front-matter). These are **gate integrity**
(SAFETY-CORE adjacent via G-5/G-1) — **keep**, but note ~12 of them duplicate one another
(E33 vs E35 vs E60 all assert "artifact#anchor resolves"). Consolidation candidate, not retirement.

---

## E. Cross-check vs recurring-defect-families.yaml (sunset signals)

The families ledger is the authority on which scaffold rules have done their job. Families that are
`closed` or `structurally_addressed` are the **sunset signal** for their prevention rules.

| family | cleanup_status | prevention rules → sunset implication |
|---|---|---|
| F-progressive-loading-weak-enforcement | **closed** | G-10, G-11 did their job; G-10 demotable to advisory |
| F-l0-agentic-primitive-gap | **closed** | R-A/R-D widenings closed it; no new scaffold needed |
| F-architecture-authority-fragmentation | **closed** | G-1.b closed it; workspace-truth is now standing invariant (keep G-1) |
| F-numeric-drift | partial → G-13 closes by construction | **retire G-2.b, G-8.a at W10** (family says so) |
| F-deleted-module-name-leakage | structurally_addressed → G-13 | **retire G-2.1, G-24 at W10** |
| F-cross-authority-agreement | structurally_addressed → G-13 | **retire G-8.a/.c/.e at W10** (explicit: "subsumes G-8.a/c/e in W10 cleanup") |
| F-recursive-prevention-irony | monitoring | G-3.1, G-9, Rule 111–114 chain — **the meta-of-meta scaffold; prime simplification target** once monitoring closes |
| F-llm-fabricated-factual-claim | structurally_addressed | G-15.d standing; keep G-15 (SAFETY-CORE) |
| F-gate-machinery-fail-open-pattern | structurally_addressed | G-5 standing; keep |
| F-non-english-in-tier1-authority | monitoring | G-25 — keep (SAFETY-CORE, cheap) |
| F-local-plan-path-in-active-authority | monitoring | G-26 — single-string scaffold; **merge into G-2/G-13** |

**Scaffold rules the ledger marks ready (or nearly ready) to sunset:** the G-13 subsumption set
(G-2.1, G-2.b, G-2.d, G-8.a/.c/.e, G-9.c) on the W10 schedule, plus the recursive-prevention chain
(G-3.1, and G-9.c parity) once `F-recursive-prevention-irony` leaves monitoring. The single-finding
string-ban rules (G-24, G-26, G-22) have no standing family backing them — they each closed exactly
one review finding and should fold into a general rule rather than persist as standalone cards.

---

## F. Data-quality defects found during inventory (not fixed — read-only pass)

1. **D-9 has no `status:` field** → invisible to the gate's `^status:` card-count grep; the "55"
   baseline silently excludes it though it is active everywhere else. Add `status: active`.
2. **E145 is a vacant id** in enforcers.yaml (E1–E191 present minus E145). Expected if ids are
   never reused, but worth a one-line comment so future audits don't hunt for it.
3. **G-13 subsumption is declared but not executed** — 7 sub-clauses across G-2/G-8/G-9/G-2.1 remain
   "defence-in-depth until W10 cleanup." The W10 cleanup is the concrete ~50% lever; it is scheduled
   but unstarted in this tree.
4. **G-16..G-21 cluster (6 rules + 6 enforcers) is vacuous-blocking** — gated on a placeholder count
   (6) that has not reached zero; they consume corpus surface without enforcing anything today.
