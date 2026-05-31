# 06 — R-C / E28 Scope + CLAUDE/README/AGENTS Link Graph

**Inventory wave:** AI-governance / knowledge-system rebalancing (keystone + refresh).
**Branch surveyed:** `governance/knowledge-governance-separation` @ `d70030b` ("W0 — branch reset + program charter").
**Method:** read-only inspection of the live working tree (a prior exploration used a different, abandoned branch; every path below was re-verified against `d70030b`).
**Date:** 2026-06-01.

> Two precise mapping jobs:
> 1. **R-C / E28 scope** — the keystone target. Locate the Code-as-Contract rule, its E28 enforcer, and the gate check that implements the "every must/forbidden/required sentence maps to an enforcer row" coverage meta-rule; document the current scan corpus and the precise narrowing change.
> 2. **CLAUDE/README/AGENTS link graph** — the refresh worklist. BFS the outbound markdown links from the three (four) root entry docs, dedup the reachable set, and flag the nodes that reference the soon-to-change model.

---

# Part 1 — R-C / E28 scope (keystone)

## 1.1 The R-C kernel, verbatim

Source: `docs/governance/rules/rule-R-C.md` (front-matter `kernel:` block, lines 13–14). Reproduced character-for-character:

> **Every active normative constraint in the platform corpus MUST be enforced by code, registered in `docs/governance/enforcers.yaml`, and reach ≥1 of: an ArchUnit test, a `gate/check_architecture_sync.sh` rule, an integration test, a storage-layer schema constraint (NOT NULL / UNIQUE / CHECK / PRIMARY KEY), or a compile-time check (`@ConfigurationProperties + @Valid`, sealed types, package-info enforcement). Module-evolution invariants split to Rule R-C.1; run-spine invariants split to Rule R-C.2 per ADR-0094.**

Card facts:
- `rule_id: R-C`, `title: "Code-as-Contract"`, `level: L1`, `principle_ref: P-C`, `status: active`, `scope_phase: design`, `kernel_cap: 8`.
- `authority_refs: [ADR-0064, ADR-0068, ADR-0094]`.
- `enforcer_refs: [E15, E16, E17, E18, E19, E27, E28, E29, E30]` — **nine** enforcers; E28 is the coverage meta-rule.
- Post-rc17 (ADR-0094) the original sub-clauses `.b/.c/.d/.e` were extracted to siblings `R-C.1` (Independent Module Evolution) and `R-C.2` (Contract Spine / Run-state / Tenant-purity). The remaining card is bounded to sub-clause `.a` only.

The phrase **"in the platform corpus"** is the unbounded term this wave targets: it is read by the gate family (below) as "every active `.md`/`.yaml` in the repo, plus every ADR, plus every `ARCHITECTURE.md`," which sweeps narrative knowledge prose into a code-enforcement obligation.

## 1.2 The E28 enforcer row, verbatim

Source: `docs/governance/enforcers.yaml`, lines 278–285.

```yaml
- id: E28
  constraint_ref: "CLAUDE.md Rule R-C.a (meta); ADR-0059"
  kind: gate-script
  level: L0
  view: scenarios
  artifact: gate/check_architecture_sync.sh#constraint_enforcer_coverage
  asserts: "every must/must not/forbidden/required sentence in the architecture corpus maps to at least one row in this file"
  product_claim: "PC-003"
```

The `asserts:` string is the literal coverage promise the wave must re-scope. Note it already says **"architecture corpus"** — narrowing the *prose* of `asserts` to "main-path safety/compat/public-contract/tenant/release invariants" is part of the change.

## 1.3 What the coverage scan actually executes today

The E28 family is **not a single check**. The `artifact:` anchor `#constraint_enforcer_coverage` resolves to two byte-identical copies (the modular extract and the monolith):
- `gate/rules/rule-028.sh` (auto-extracted; the per-rule runner used by `gate/check_parallel.sh`).
- `gate/check_architecture_sync.sh` lines 1908–1934 (the monolithic source of truth).

**Surprise / load-bearing finding — E28 itself is a presence stub, not a sentence scanner.** Both copies do only:

```bash
if [[ -f "$_efile" ]] && [[ -f 'CLAUDE.md' ]]; then
  grep -q 'CLAUDE.md'     "$_efile" || fail "...does not reference CLAUDE.md..."
  grep -q 'ARCHITECTURE.md' "$_efile" || fail "...does not reference ARCHITECTURE.md..."
fi
```

The in-code header is explicit (`rule-028.sh` lines 6–14):

> **L1 scope (Phase L truthful naming):** baseline presence check only … it does NOT parse every "must"/"forbidden"/"required" sentence in the corpus and cross-reference each one. Full natural-language parsing is deferred (no executable enforcer is feasible without committing to a brittle regex over evolving prose).

So the **literal `asserts:` text overclaims** what the code does. The "corpus → enforcer" obligation is therefore realised **not by E28's own body** but by the *sibling and neighbour* rules that DO open files and treat them as the normative corpus. Those are the surfaces that conflate knowledge prose with enforceable constraint, and they are the real scan-scope to narrow:

| Rule / enforcer | File | What it opens as "the corpus" | ADR prose in scope? | `*/ARCHITECTURE.md` in scope? |
|---|---|---|---|---|
| **28 / E28** (coverage meta) | `gate/rules/rule-028.sh` · `check_architecture_sync.sh:1908` | `CLAUDE.md` + `ARCHITECTURE.md` (presence grep only) | no (presence only) | root `ARCHITECTURE.md` only |
| **28g / E30** (no prose-only marker) | `gate/rules/rule-028g.sh` · `:303`/E30 row | `CLAUDE.md`, `ARCHITECTURE.md`, `agent-service/ARCHITECTURE.md`, **glob `docs/adr/00[5-9][0-9]-*.md`** (every L1+ ADR, ADR-0059 exempt) | **YES — every 0050–0099 ADR** | root + agent-service |
| **28h / E31** (L1 review checklist) | `gate/rules/rule-028h.sh` | `docs/adr/0055..0060` | **YES — 0055–0060** | — |
| **30 / E47** (telemetry §4 coverage) | `gate/rules/rule-030.sh` | `ARCHITECTURE.md` §4 #53–#59 ↔ `enforcers.yaml` | — | root `ARCHITECTURE.md` |
| **18 / (no E)** deleted-name sweep | `gate/rules/rule-018.sh:25` | `find . -name '*.md' -o -name '*.yaml'` minus exclusions = **ACTIVE_NORMATIVE_DOCS** | excluded (see below) | included |
| **22 / E—** lowercase-metric sweep | `check_architecture_sync.sh:953` | same ACTIVE_NORMATIVE_DOCS corpus | excluded | included |
| **23 / E—** active-doc link resolve | `gate/rules/rule-023.sh:20` | `os.walk('.')` minus EXCLUDE_DIRS | excluded | included |
| **44 / E63** frozen-doc edit (adjacent) | `check_architecture_sync.sh:2488` | `find . -maxdepth 2 -name ARCHITECTURE.md` + `docs/L2/*.md` + `docs/adr/*.yaml` | YAML ADRs in scope | every depth-≤2 `ARCHITECTURE.md` |

### The canonical "ACTIVE_NORMATIVE_DOCS" corpus definition

Rules 18 / 22 / 23 are the real corpus enumerators. Their scope is "**every `*.md` / `*.yaml` in the repo, minus an exclusion list**". The two equivalent forms in the tree today:

`gate/rules/rule-018.sh:25-27` and `check_architecture_sync.sh:757-759` (shell):
```bash
find . -name '*.md' -o -name '*.yaml' \
  | grep -vE '/docs/(archive|logs/reviews|adr|delivery|v6-rationale|plans|competitive|superpowers)/|/third_party/|/target/|/\.git/'
```

`gate/rules/rule-023.sh:20-23` (python `EXCLUDE_DIRS`):
```python
('./docs/archive/', './docs/logs/', './docs/adr/', './docs/delivery/',
 './docs/v6-rationale/', './docs/plans/', './docs/superpowers/',
 './third_party/', './discovery/')   # + dir names {target, .git, node_modules}
```

**Key consequence (the conflation, stated mechanically):**
- `docs/adr/` and `docs/plans/` are **already excluded** from the broad ACTIVE_NORMATIVE_DOCS sweeps (18/22/23). ADR *prose* leaks back into the coverage obligation only through the **narrow E28-family globs 28g/28h** (which deliberately enumerate `docs/adr/00[5-9][0-9]` / `0055-0060`) and through **Rule 44's** `docs/adr/*.yaml`.
- A **future top-level `knowledge/` tree is NOT in any exclusion list** → it would be **auto-included** by rules 18/22/23 the moment it lands (it matches `*.md`/`*.yaml` and dodges every `grep -vE` / `EXCLUDE_DIRS` term). This is the precise mechanism by which "narrative knowledge prose" would be dragged under Code-as-Contract enforcement. **No `knowledge/` token exists anywhere under `gate/` today** (verified: zero matches across `gate/**/*.sh`).

### Live-tree caveat (this branch only)

On `d70030b` there is **no root `ARCHITECTURE.md`, no `architecture/` tree, and no `docs/cross-cutting/`** (the whole architecture authority was relocated to `docs/architecture/…`; see Part 2). Therefore rules **28g (its `ARCHITECTURE.md`/`agent-service/ARCHITECTURE.md` arms), 28 (presence grep), and 30 (§4 arm) currently match zero files and pass vacuously**. The scope narrowing must be designed against the *intended* corpus, not the transiently-empty one — and a non-vacuity guard should be considered so the coverage rule fails (not silently passes) when its corpus enumerates to zero.

## 1.4 The precise change needed

**(a) Narrow the R-C kernel + the E28 `asserts:` prose.** Replace the unbounded "Every active normative constraint **in the platform corpus**" / "every … sentence **in the architecture corpus**" with a bounded predicate over **main-path safety / compatibility / public-contract / tenant-isolation / release invariants** only. Concretely, the obligation should bind to constraints that are:
- §4 architectural constraints (the numbered ARCHITECTURE.md corpus), and
- public runtime-contract promises (`docs/contracts/*`), and
- tenant-isolation / run-state / idempotency / posture (R-C.2 + R-J family), and
- release-transaction invariants —
and should **explicitly NOT** bind to explanatory ADR rationale prose or to a `knowledge/` learning tree.

**(b) Make the coverage / corpus scan EXCLUDE knowledge prose + ADR prose.** Add the following paths to the exclusion regex (`grep -vE …`) in `gate/rules/rule-018.sh` + `check_architecture_sync.sh:758` (Rule 18) and `check_architecture_sync.sh:965` (Rule 22), and to `EXCLUDE_DIRS` in `gate/rules/rule-023.sh:20` (Rule 23), and re-scope the E28-family ADR globs in `gate/rules/rule-028g.sh` / `rule-028h.sh`:

**Concrete scan-scope paths to EXCLUDE (add to the corpus enumerators):**

| Path / glob to exclude | Where it is currently swept in | Why exclude |
|---|---|---|
| `knowledge/` (future top-level tree) | rules 18 / 22 / 23 (auto-included by default) | the keystone: learning/knowledge prose is not a code contract |
| `docs/adr/` **prose** (`*.md` + `*.yaml`) from the E28 coverage obligation | 28g glob `docs/adr/00[5-9][0-9]-*.md`; 28h `0055-0060`; Rule 44 `docs/adr/*.yaml` | ADRs are rationale, not enforceable sentences; already excluded from 18/22/23 — finish the job for the E28 family |
| `docs/architecture/**` narrative L0/L1 prose (keep §4 ARCHITECTURE.md constraint table in scope, exclude the surrounding `00-overview`, `02-scenarios`, `10-governance`, `trustworthy/`, `l1/*.en.md` design-guidance) | rules 18 / 22 / 23 (included) | design narrative ≠ enforceable constraint |
| `docs/reviews/` , `docs/competitive/` , `docs/harness/` | 18/22/23 (`docs/competitive` already excluded in 18/22 but **not** in 23's EXCLUDE_DIRS; `docs/reviews` + `docs/harness` excluded nowhere) | review/competitive/harness prose is not a constraint surface |
| `architecture/**` (if/when the root tree returns) narrative docs | would be re-included on restore | same rationale as `docs/architecture/**` |

**Exclusion-list drift to fix in passing:** Rule 23's `EXCLUDE_DIRS` is **not** in sync with the Rule 18/22 `grep -vE` set — 23 lacks `competitive` and `superpowers`-vs-`discovery` differs (`18/22` exclude `superpowers`; `23` excludes `superpowers` + `discovery` but not `competitive`). Unify the three enumerators behind one shared list (e.g. a `gate/active-normative-docs-exclusions.txt` vocabulary file) so the knowledge/ADR exclusion is declared once and all three corpus scanners read it — otherwise the narrowing will be applied inconsistently and re-drift.

**(c) Honesty fix (independent of scope):** either upgrade `rule-028.sh` to actually parse must/forbidden sentences, or **soften E28's `asserts:` text** to match the presence-only reality. Today the `asserts:` overclaims; any reviewer comparing the row to the code will (correctly) flag it. The rebalancing wave should pick the soft-narrow path (presence + bounded §4/contract coverage), not commit to brittle NL parsing of an expanding corpus.

---

# Part 2 — CLAUDE / README / AGENTS link graph (refresh worklist)

## 2.1 Seeds and method

BFS over outbound markdown links `](path)` (anchors stripped; `http(s)`/`mailto`/`#`-only skipped), expanding only **existing `.md` nodes**; `.yaml`/`.dsl`/dir targets are recorded as leaf data nodes (not expanded). Seeds: `CLAUDE.md`, `README.md`, `AGENTS.md`, plus `CONTRIBUTING.md` (a fourth root agents-entry doc; it adds nothing new — links only to `CLAUDE.md` + `architecture-status.yaml`).

## 2.2 Graph size

- **Reachable nodes (dedup): 184** (existing + broken; markdown + leaf-data).
- **Broken targets: 33** (link present in a reachable doc, target absent on this branch).
- Existing markdown nodes expanded during BFS: ~70 (governance rule/principle/contract cluster dominates).

## 2.3 The headline finding — the seeds point at the OLD architecture root

The W0 reset relocated the architecture authority from a top-level `architecture/` tree into `docs/architecture/`, but **the three seed docs (and several hubs) still link to the pre-move paths**. These are the highest-priority refresh items because they are on the canonical "Reading path" every human/AI is told to follow:

| Stale link (BROKEN) | Referenced by | Present-tree replacement |
|---|---|---|
| `architecture/docs/L0/ARCHITECTURE.md` | README ×2 (reading-path #2, "at a glance"), AGENTS reading-path #3, `docs/overview.md`, `docs/quickstart.md`, `gate/README.md` | `docs/architecture/architecture2/docs/L0/ARCHITECTURE.md` |
| `architecture/README.md` | AGENTS authoritative-sources + reading-path #2, README reading-path #1 | `docs/architecture/README.md` (or `…/architecture2/README.md`) |
| `architecture/workspace.dsl` | AGENTS authoritative-sources + reading-path #2 | `docs/architecture/architecture2/workspace.dsl` |
| `architecture/docs/L1/agent-bus.md` | ADR-0050 | `docs/architecture/architecture2/docs/L1/agent-bus/ARCHITECTURE.md` |
| `docs/cross-cutting/posture-model.md` | README "Posture model" | dir `docs/cross-cutting/` does not exist; posture content now under `docs/architecture/l0/cross-cutting` / `docs/governance/posture-coverage.md` |

Plus `architecture/facts/generated/*` and `architecture/features/*` cited in AGENTS prose (not markdown links, so not BFS edges, but same relocation rot).

## 2.4 The soon-to-change-model flags

Beyond the relocation, the seeds + hubs encode the **governance-forward, progressive-disclosure model that the rebalancing is replacing**. Flag clusters:

- **Governance-forward loading / phase-entry skills+contracts.** `CLAUDE.md` "Phase Entry" table → 5 phase contracts under `docs/governance/contracts/`; each contract fans out to ~20–40 rule cards. The whole `CLAUDE.md → contracts → rules/principles` subtree (≈ 70 nodes, the bulk of the graph) is the loading model under revision.
- **AI reading path.** `README.md#Reading-path` (7 steps), `AGENTS.md#For-AI-assistants` (8 steps), `docs/governance/SESSION-START-CONTEXT.md` ("same Reading path as always-load table"), and the machine source `docs/governance/ai-reading-path.yaml` (referenced from CLAUDE.md "Where else to look", governed by Rule G-31). All re-order around the same now-stale `architecture/…` anchors.
- **Retired rules.** `docs/governance/retired-rules-audit.md` — reached from **11 principle cards** (P-A…P-M each cross-link it). High fan-in; any retirement-model change ripples broadly.
- **ADR paths that will move (.md → .yaml migration + index staleness).** `docs/adr/README.md` links **54 ADRs as `…-NNNN-*.md`**, of which **17 are BROKEN** because those ADRs were converted to YAML or removed in the reset. Present corpus is a **60 `.md` + 89 `.yaml`** mix; the README index is stale against it. (Sample: `0001`, `0004`, `0020`, `0042` exist as *neither* `.md` nor `.yaml` on this branch.)
- **Baseline-count drift.** README "Project status" hard-codes `65 §4 · 139 ADRs · 157 gate rules · 287 self-tests · 13 principles · 55 rules · 190 enforcers · 674-node/1301-edge graph`; AGENTS deliberately carries none (thin-wrapper). The README numbers will move with the rebalancing and must be re-pinned to `architecture-status.yaml#baseline_metrics`.

## 2.5 Per-subtree reconciliation worklist

Counts are **reachable nodes that need a refresh edit** (stale link, moved target, or model-change reference), grouped by subtree. "Top files" = the highest-fan-in or highest-priority nodes to edit.

### A. agents-entry (the 4 root seeds) — worklist size: **4 files**
- `README.md` — **fix 3 broken architecture links + 1 posture link**, re-point reading-path #1/#2/#"at a glance", re-pin 8 baseline numbers, refresh ADR-count phrasing. **(highest priority)**
- `AGENTS.md` — re-point authoritative-sources table + reading-path #2/#3 to `docs/architecture/…`; the `architecture/facts/generated/*` + `architecture/features/*` prose refs.
- `CLAUDE.md` — "Where else to look" still names `architecture/workspace.dsl` + `architecture/docs/L0/ARCHITECTURE.md` (prose, not a link, but same rot); revisit the Phase-Entry loading-model framing.
- `CONTRIBUTING.md` — low; only confirm `CLAUDE.md` + `architecture-status.yaml` stay valid.

### B. architecture — worklist size: **≈ 6 broken targets + the relocation map**
- All five BROKEN rows in §2.3 resolve here. Decide the canonical post-move path (`docs/architecture/` vs `docs/architecture/architecture2/`) **once**, then sweep every inbound reference (README, AGENTS, overview, quickstart, gate/README, ADR-0050).
- `docs/overview.md`, `docs/quickstart.md`, `gate/README.md` each carry a broken `architecture/docs/L0/ARCHITECTURE.md` link — fix in the same sweep.

### C. governance — worklist size: **≈ 75 nodes** (largest subtree; mostly *not* broken, but model-coupled)
- `docs/governance/contracts/{architecture-design,engineering-implementation,integration-verification,review-response,system-commit}.md` (5) — the phase-contract loading model under revision.
- `docs/governance/rules/rule-*.md` (≈ 55 cards reached) + `docs/governance/principles/P-*.md` (13) — internally consistent today; touch only where the loading/retirement model changes.
- **Top files (high fan-in / model-critical):** `docs/governance/retired-rules-audit.md` (fan-in 11), `docs/governance/recurring-defect-families.md`, `docs/governance/SESSION-START-CONTEXT.md`, `docs/governance/posture-coverage.md`.
- 1 broken intra-subtree link: `rule-R-D.md`→`spring-ai-ascend-beyond-sdd-response.en.md`→`docs/governance/rules/rule-79.md` (legacy numeric rule id, now namespaced) — repair the dangling `rule-79.md` reference.

### D. contracts — worklist size: **11 broken targets**
- `docs/quickstart.md` links **10 `docs/contracts/*.v1.yaml`** files (`agent-definition`, `chat-advisor`, `memory-store`, `model-invocation`, `model-streaming`, `planning-request`, `prompt-template`, `skill-definition`, `structured-output`, `vector-store`) — **all BROKEN**; `docs/contracts/` exists but holds only `dfx/` + `func/` subdirs, no `*.v1.yaml`. `docs/governance/principles/P-F.md`→`docs/contracts/openapi-v1.yaml` is also BROKEN.
- Decide: restore the contract YAMLs, or rewrite quickstart to the present contract layout. **(blocks the quickstart "operational onboarding" path)**

### E. onboarding / runbooks / misc — worklist size: **3 broken targets**
- `docs/runbooks/debug-first-evidence.md` — BROKEN, referenced by `quickstart.md` **and** `rule-R-D`-chain review doc; the dir `docs/runbooks/` does not exist (Rule D-3.b cites this runbook as its operationalisation — a governance dangling ref).
- `docs/dfx` (BROKEN dir link from quickstart) — content moved under `docs/contracts/dfx/` and `docs/architecture/…/dfx`.
- `docs/adr/relative-path` (BROKEN) — a literal placeholder string inside ADR-0043 prose, not a real link (cosmetic; can be left or de-linked).

### Subtree totals at a glance

| Subtree | Reachable nodes touched | Broken targets within | Priority |
|---|---|---|---|
| agents-entry | 4 | 4 (all in README) | P0 |
| architecture | ~6 | 6 | P0 |
| governance | ~75 | 1 | P1 (model-coupled, mostly non-broken) |
| contracts | 11 | 11 | P1 (blocks quickstart) |
| onboarding/runbooks/misc | 3 | 3 | P2 |
| **ADR index (docs/adr/README.md)** | 54 links | **17 broken** + .md/.yaml drift | P1 |

---

## Appendix — full BROKEN target list (33)

```
architecture/README.md
architecture/docs/L0/ARCHITECTURE.md
architecture/docs/L1/agent-bus.md
architecture/workspace.dsl
docs/adr/0001-java-21-spring-boot-runtime.md
docs/adr/0002-spring-ai-llm-gateway.md
docs/adr/0004-postgres-primary-data-store.md
docs/adr/0005-tenant-isolation-guc-set-local.md
docs/adr/0006-posture-model-dev-research-prod.md
docs/adr/0010-spring-security-oauth2.md
docs/adr/0011-flyway-schema-migration.md
docs/adr/0014-contract-spine-versioning-policy.md
docs/adr/0015-layered-architecture-capability-model.md
docs/adr/0020-runlifecycle-spi-and-runstatus-formal-dfa.md
docs/adr/0026-module-dependency-direction-contracts-split.md
docs/adr/0027-idempotency-scope-w0-header-validation.md
docs/adr/0042-test-evidence-enforcement-for-rule-G-2.md
docs/adr/relative-path                         (literal placeholder in ADR-0043 prose)
docs/contracts/agent-definition.v1.yaml
docs/contracts/chat-advisor.v1.yaml
docs/contracts/memory-store.v1.yaml
docs/contracts/model-invocation.v1.yaml
docs/contracts/model-streaming.v1.yaml
docs/contracts/openapi-v1.yaml
docs/contracts/planning-request.v1.yaml
docs/contracts/prompt-template.v1.yaml
docs/contracts/skill-definition.v1.yaml
docs/contracts/structured-output.v1.yaml
docs/contracts/vector-store.v1.yaml
docs/cross-cutting/posture-model.md
docs/dfx
docs/governance/rules/rule-79.md
docs/runbooks/debug-first-evidence.md
```

## Appendix — files inspected (evidence)

R-C/E28: `docs/governance/rules/rule-R-C.md`; `docs/governance/enforcers.yaml` (E26–E30 rows); `gate/rules/rule-028.sh`, `-028a/b/c/d/e/f/g/h/i/j/k.sh`, `-018.sh`, `-023.sh`, `-030.sh`; `gate/check_architecture_sync.sh` (lines 757–759, 953–965, 1908–1934, 2480–2519). Link graph: `CLAUDE.md`, `README.md`, `AGENTS.md`, `CONTRIBUTING.md` + BFS expansion of every existing `.md` reachable from them (184 nodes).
