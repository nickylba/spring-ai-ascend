# ADR Conflicts, Redundancy Clusters & Supersession Hygiene — 2026-06-01 Rebalancing Inventory

**Scope:** every ADR on the current working tree — branch `governance/knowledge-governance-separation` (== `origin/main`, HEAD `d70030b`). Locations: `docs/adr/*.md`, `docs/adr/*.yaml`, `docs/adr/locked/*.md`, `docs/adr/archive/` (pointer index only — 0 bodies), and the demoted set under `docs/logs/adr-amendment-narratives/` (0026, 0083, 0084, 0085).
**Verification:** re-enumerated from disk; nothing trusted from prior runs. A `docs/adr/normalized/` layer **does not exist** on this tree (the `ADR-0019.yaml`/`ADR-0021.yaml` normalized files from the abandoned branch are absent here).
**Companion:** `01-adr-census.md` holds the full per-ADR census/classification; this file holds only (1) conflicts, (2) redundancy clusters + merge-map, (3) supersession hygiene + relocate recommendation. Findings below were re-derived independently and corroborate 01's mis-slug warning.
**Read-only artifact:** no existing ADR or index was modified.

---

## 1. CONFLICTS

**Conflict-pair count: 11** (C1 is a 10-file class counted as one finding; C2–C11 are discrete pairs). Of these, **8 are unresolved** and **3 are managed-but-asymmetric** (the supersession is recorded on only one side).

### C1 — Filename slug contradicts file content (systemic mis-slug) — **10 files, UNRESOLVED**

For ten ADRs the filename slug describes a *different decision* than the file's own H1 + body. The H1 and the README index title agree with each other and with the body; only the **slug in the filename is wrong**. Verified by reading each body (e.g. `0013-vault-secrets-management.md` opens `# 0013. UUIDv7 for surrogate IDs, not snowflake / sequence` and its Decision Drivers are about surrogate-ID generation, not Vault).

| File on disk | Slug claims | Actual H1 / decision in body |
|---|---|---|
| `docs/adr/locked/0005-tenant-isolation-guc-set-local.md` | tenant-isolation-guc | "Row-level security with SET LOCAL … GUC" (matches — borderline; kept for completeness) |
| `docs/adr/locked/0006-posture-model-dev-research-prod.md` | posture-model | "**ActionGuard 5-stage chain** (cycle-9 truth-cut), not 11-stage" |
| `docs/adr/0008-resilience4j-circuit-breaker.md` | resilience4j-circuit-breaker | "**OPA sidecar for authorization**, not in-process Cedar / custom" |
| `docs/adr/0009-micrometer-observability.md` | micrometer-observability | "**HashiCorp Vault (OSS) for secrets**, not env vars / K8s Secrets only" |
| `docs/adr/locked/0010-spring-security-oauth2.md` | spring-security-oauth2 | "**Keycloak (OSS) as default IdP**, but customer can BYO" |
| `docs/adr/locked/0011-flyway-schema-migration.md` | flyway-schema-migration | "**Spring Cloud Gateway as ingress**, not Kong / Traefik" |
| `docs/adr/0012-valkey-session-cache.md` | valkey-session-cache | "**Maven multi-module**, not Gradle" |
| `docs/adr/0013-vault-secrets-management.md` | vault-secrets-management | "**UUIDv7 for surrogate IDs**, not snowflake / sequence" |
| `docs/adr/locked/0014-contract-spine-versioning-policy.md` | contract-spine-versioning-policy | "**3-posture model** (dev/research/prod), not 5 or 2" |
| `docs/adr/locked/0015-layered-architecture-capability-model.md` | layered-architecture-capability-model | "**Defer multi-framework dispatch** (Python sidecar, LangChain4j) to W4+" |

The slugs read like a *contiguous, shifted* copy of an older numbering (each slug names the decision that ~belongs to a neighboring number). Prior commit `c1f9f96` ("correct 4 mis-slugs") fixed part of this; these 10 (0006, 0008–0015 minus already-fixed, in active + locked) remain. **This is the single largest content/metadata conflict in the corpus.**

### C2 — Three index registries disagree with each other and with the files — **UNRESOLVED**

Three corpus indices carry three *different* slug/title sets for the same numbers, and none is fully consistent with the on-disk H1:

- `docs/adr/README.md` (Index table): titles match the **file H1** but the embedded **link slug** is the mis-slug (e.g. row 0011 links `0011-flyway-schema-migration.md` titled "Spring Cloud Gateway as ingress").
- `docs/adr/ADR-CLASSIFICATION.md`: a **third** slug set — `0011 | structured-logging-logback-json`, `0019 | competitive-positioning`, `0022 | suspend-resume-checkpoint`, `0024 | payload-envelope-codec`, etc. None of these match either the filename slug or the file H1 (the real 0019 is "SuspendSignal … taxonomy"; the real 0022 is "PayloadCodec SPI").
- `docs/adr/taxonomy.md` (rendered from `adr-level-module-map.yaml`): yet another mapping, internally self-consistent but built on the same drift.

Because `ADR-CLASSIFICATION.md` declares itself "**consumed by `gate/migrate_adrs_to_yaml.py` and `gate/build_architecture_graph.sh`**", its stale slugs are not merely cosmetic — they are a generator input.

### C3 — Dangling README link to a nonexistent ADR file — **UNRESOLVED**

`docs/adr/README.md:54` links `[0042](0042-test-evidence-enforcement-for-rule-G-2.md)` but the file on disk is `docs/adr/0042-test-evidence-enforcement-for-rule-25.md`. The link target does not exist (the "rule-25 → Rule G-2" namespace rename was applied to the link text but not to the file, or vice-versa). Broken cross-reference.

### C4 — Suspension primitive: checked exception vs value-based yield — **MANAGED, ASYMMETRIC**

- `docs/adr/0019-suspend-signal-and-suspend-reason-taxonomy.md` decides: *"`SuspendSignal extends Exception` (checked) … the runtime's one interrupt primitive"* and defends the checked form as a design feature.
- `docs/adr/0112-engine-stateless-executor-value-based-yield.yaml` decides the opposite: *"At W0.5+, `StatelessEngine.execute(...)` returns `Mono<StateDelta>` … **No checked exception in SPI signature**"* and lists `supersedes_partial: [ADR-0019] # checked-suspension doctrine — partially superseded`.

The link is **one-directional**: 0112 records the partial supersession, but **0019's `**Status:**` line only mentions that its *variant names* are superseded by ADR-0146** — it says nothing about the checked-exception doctrine being superseded by 0112. A reader who opens 0019 first sees "accepted" with no pointer to the doctrine reversal in 0112. (0112 itself notes the two coexist phase-wise — SuspendSignal stays canonical until the W0.5 Part-C migration — so this is a *phased* conflict, not a flat contradiction, but the back-link is missing.)

### C5 — Skill SPI package home: two different roots — **UNRESOLVED**

The same `Skill` SPI is placed in two different package roots by two ADRs in the same wave, with no supersedes edge between them:

- `docs/adr/0122-tool-skill-semantic-resolution.yaml:52` — *"One unified `Skill` SPI lives in `com.huawei.ascend.skill.spi.Skill`"* (+ `skill.spi.SkillRegistry`).
- `docs/adr/0127-skill-spi-tool-unification.yaml:32` — *"Land the `Skill` SPI under `com.huawei.ascend.service.skill.spi`"*.

0127 `relates_to: ADR-0122` and says it merely "specifies the Java surface" of 0122's decision, yet it silently relocates the package root (`skill.spi` → `service.skill.spi`) without a `supersedes` link or any reconciliation note. Two live ADRs name two homes for one interface.

### C6 — Memory SPI package home: split across modules — **UNRESOLVED (LIKELY)**

- `docs/adr/0123-memory-unified-spi.yaml:31` lands the unified `MemoryStore` SPI in `com.huawei.ascend.service.runtime.memory.spi`.
- `docs/adr/0133-conversation-memory-spi-variant.yaml:45` lands `ConversationMemory` — explicitly *"a `MemoryStore<String, ConversationWindow>` variant"* — in `com.huawei.ascend.middleware.memory.spi`.

A subtype of `MemoryStore` lives in a different module (`agent-middleware`) than its base SPI (`agent-service` runtime). Either the base or the variant is in the wrong module; no ADR reconciles the split. (Softer than C5 — could be an intentional decorator placement — but neither ADR states that, so it reads as drift.)

### C7 — ADR-CLASSIFICATION `view` for 0060 contradicts the L0/L1 scheme — **UNRESOLVED (minor)**

`ADR-CLASSIFICATION.md` row `0060 | phase-l-reviewer-remediation | L1 | scenarios` assigns view `scenarios` to an **L1** ADR, but the same file's own legend defines `scenarios` as the **L0** cross-view golden-link/meta-governance view. Status-metadata-vs-its-own-rubric inconsistency.

### C8 — Status "accepted" with body wholly superseded: ADR-0079 still cited 44× — **MANAGED, but high blast radius**

`docs/adr/0079-engine-extraction-runtime-core.yaml` correctly carries `status: superseded` + `superseded_by: [ADR-0088]`. The hygiene problem (detailed in §3) is that **47 corpus references still point at ADR-0079** as if live; the supersession is recorded on the node but not propagated to its citers.

### C9 — 0048 microservice commitment vs 0078/0088 module consolidation — **MANAGED via amendment prose, no status change**

`docs/adr/0048-service-layer-microservice-architecture-commitment.md` commits "Service Layer deployed as long-running microservices" and the README annotates it *"**amended** by ADR-0050 … narrowed by ADR-0049/0050/0051"*. Separately, `0078` folds two reactor modules into one and `0088` dissolves `agent-runtime-core` and redistributes SPI. The module-topology decisions (0078/0088) and the deployment-topology commitment (0048) are reconciled only in prose; 0048's status remains plain "accepted" with the amendments living in the README cell, not in 0048's own status line or a structured `amended_by` edge.

### C10 — Engine boundary decided three times — **PARTIALLY MANAGED**

`0071`/`0072`/`0073`/`0074` (engine envelope + hooks + S2C, "L1 expressions of P-M"), then `0112` (stateless executor value-yield), then `0140` (engine adapter Layer-5 split), then `0158` (EnginePort transport-agnostic boundary "absorbed into agent-bus"). 0140 `supersedes_partial: ADR-0138 §3`; 0158 carries no supersedes edge to the earlier engine-contract set even though it re-states the Service↔Engine boundary. Whether 0158 *replaces* or *layers on* 0072/0074 is not stated structurally.

### C11 — `agent-runtime-core` existence: created then dissolved — **MANAGED (clean), listed for completeness**

`0079` *creates* `agent-runtime-core`; `0088` *dissolves* it. This pair is the one fully-clean supersession (0079.status=superseded, 0079.superseded_by=[0088], 0088.supersedes=[0079]). Included only to show the contrast with C4/C5/C8 where the edge is missing or one-sided.

---

## 2. REDUNDANCY CLUSTERS — MERGE-MAP

Each cluster below is a set of ADRs covering one topic that should collapse into a single knowledge doc. Members are listed by number; the proposed merged target names a single consolidated document.

### Cluster R-A — Suspend / Interrupt / Resume taxonomy
- **Members:** 0019, 0024, 0025, 0030 (lifecycle/suspend portion), 0070 (AwaitChild axis), 0074 (AwaitClientCallback canonical), 0112 (value-yield + A2A InterruptType↔SuspendReason), 0137 (SuspendSignal-vs-InterruptSignal glossary), 0146 (taxonomy canonical alignment).
- **Topic:** the one interrupt primitive + the sealed `SuspendReason`/`InterruptSignal` 6-variant set + write-atomicity + checkpoint-ownership of a suspended run. The taxonomy drifted across **five** ADRs (0019 names `AwaitChildren/AwaitExternal/AwaitApproval`; 0070 `AwaitChild`; 0074 `AwaitClientCallback`; 0112 `ChildRun/AwaitTool/AwaitApproval`; 0146 ratifies `{AwaitClientCallback, AwaitChildRun, AwaitToolResult, AwaitTimer, RequiresApproval, RateLimited}`).
- **Merged target:** **`knowledge/suspend-resume-model.md`** — one doc with the canonical variant table (0146 as authority), the primitive's current shape (0112 value-yield forward / SuspendSignal legacy), and the write-atomicity + checkpoint-ownership rules (0024/0025) as sections.

### Cluster R-B — Spring-AI SPI surface (model / tool / memory / vector / planner / prompt / advisor / output)
- **Members:** 0121 (ModelGateway), 0122 (Tool≡SkillKind), 0123 (MemoryStore unified), 0124 (Vector/Retriever/Embedding), 0125 (Spring-AI integration boundary), 0126 (Planner), 0127 (Skill SPI surface), 0129 (streaming ModelGateway), 0130 (StructuredOutputConverter), 0131 (PromptTemplate), 0132 (ChatAdvisor), 0133 (ConversationMemory), 0134 (tool-call iteration loop). Pulls in prose from 0030/0038/0052 (skill) and 0002 (Spring-AI gateway pick).
- **Topic:** "Spring AI is the canonical Model/Tool/Vector/Embedding abstraction; platform SPIs are thin tenant-scoped decorators" (0125 is the governing thesis; 0121–0134 are the per-SPI instantiations). All carry `status: design_only at rcNN` — a single design wave.
- **Merged target:** **`knowledge/spring-ai-spi-surface.md`** — one SPI catalog keyed by SPI name, each with package home, signature, Spring-AI reference adapter, and `design_only → runtime_enforced` trigger. **Must resolve C5 (skill.spi vs service.skill.spi) and C6 (memory module split) during the merge.**

### Cluster R-C — Engine-contract wave
- **Members:** 0071 (umbrella), 0072 (envelope+strict-matching), 0073 (hooks+middleware), 0074 (S2C callback), 0079 (engine extraction — **superseded**), 0088 (runtime-core dissolution), 0090 (engine semantic-home), 0112 (stateless executor), 0113 (hook ordering/failsafe), 0140 (engine adapter Layer-5 split), 0158 (EnginePort transport-agnostic).
- **Topic:** the Service↔Engine boundary — its envelope, hooks, S2C callback, package home, executor primitive, and transport-agnostic port — restated across ~11 ADRs and three reorgs (extract→dissolve→port).
- **Merged target:** **`knowledge/engine-contract.md`** — current EnginePort surface (0158) as the authority head, with 0072/0073/0074 folded as the envelope/hook/S2C sections and 0079/0088/0090 retained only as a "topology history" appendix.

### Cluster R-D — Resilience / Skill-capacity
- **Members:** 0008 (circuit-breaker pick — note mis-slug), 0030 (Skill lifecycle/resource matrix), 0038 (skill resource-tier classification), 0052 (skill topology scheduler + capability bidding), 0070 (cursor-flow + skill-capacity two-axis), 0080 (ResilienceContract `.spi` package alignment), 0081 (ResilienceContract dual-surface reconciliation).
- **Topic:** `ResilienceContract` + `SkillCapacityRegistry` arbitration keyed on `(tenantId, skillKey)`, plus the skill resource/tier/sandbox matrix. 0081 already records `supersedes_partial` of 0030's `(tenantId, operationId)` evolution claim by 0070's skill axis — i.e. the cluster is already cross-patching itself.
- **Merged target:** **`knowledge/resilience-and-skill-capacity.md`** — one doc: the capacity two-axis (0070), the dual-surface contract (0080/0081), and the skill resource/tier matrix (0030/0038/0052) as sections.

### Cluster R-E — rc-wave META governance ADRs (the "≈0090+" changelog cluster)
- **Members:** 0083, 0084, 0085 (already demoted to `docs/logs/adr-amendment-narratives/`), 0086, 0087, 0090, 0091, 0092, 0093, 0094, 0095, 0096, 0097, 0098, 0105, 0114, 0116, 0118. (0099–0104 are rc22 *product* decisions — keep separate.)
- **Topic:** per-rc-cycle closure narratives — "rcNN recurring-family closure / authority ratchet / meta-recursion close / comprehensive hardening." Almost all are `level: L0, view: process` and read as governance changelog entries, not durable decisions.
- **Merged target:** **`docs/logs/governance-waves.md`** (already the documented sink per `INDEX.md`) — these are change-history, not knowledge. Collapse each rcNN ADR into a dated section; retain only the *durable* rule/principle each introduced as a pointer into the rule cards.

### Cluster R-F — Agent-service L1 decomposition (evolution narrative)
- **Members:** 0048, 0078, 0088, 0100, 0115, 0136, 0138, 0140, 0142, 0144, 0155 (+ `0155.md`/`0155.yaml` duplicate).
- **Topic:** the agent-service module's internal L1 shape — restated repeatedly as it evolved (microservice commitment → module consolidation → runtime-core dissolution → 5-component model → 5-layer model → layer↔package matrix → v1.2 absorption). 0144 explicitly exists *to reconcile* the 0100 5-component view with the 0138 5-layer view ("the two are NOT competing").
- **Merged target:** **`knowledge/agent-service-l1-design.md`** — the current 5-layer + 5-component matrix (0138/0140/0142/0144/0155) as the live design; 0048/0078/0088/0100 demoted to a topology-history appendix.

### Cluster R-G — Structurizr / architecture-authoring + L1 feature registry
- **Members:** 0147, 0148, 0149, 0150, 0152 (authoring root + mounting) and 0151, 0153, 0154 (feature registry + fact-layer), with 0068/0144 as relations.
- **Topic:** "architecture/workspace.dsl + architecture/docs is the authoring root; L1 feature registry + generated fact-layer is the AI input." A tightly-chained `extends`/`supersedes` lineage (0147→0149→0150→0152; 0151→0153; 0154).
- **Merged target:** **`knowledge/architecture-authoring-and-fact-layer.md`** — current authoring topology (0150/0152) + feature-registry schema (0151/0153) + fact-layer (0154); 0147/0148/0149 as a spike/rollout appendix.

### Cluster R-H — Memory & knowledge ownership boundary
- **Members:** 0034 (L0 6-category taxonomy), 0051 (C-side ontology vs S-side trajectory ownership), 0082 (GraphMemoryRepository canonical ownership), plus the SPI-side 0123/0133 (also in R-B).
- **Topic:** who owns memory/knowledge (C-side vs S-side), the 6-category (M1–M6) taxonomy, and the canonical repository. Overlaps R-B on the SPI surface.
- **Merged target:** **`knowledge/memory-knowledge-model.md`** — taxonomy (0034) + ownership boundary (0051/0082); cross-link the SPI surface to R-B's doc rather than duplicating it.

---

## 3. SUPERSESSION HYGIENE

**Supersession-issue count: 9.**

| # | Issue | Evidence |
|---|---|---|
| S1 | **Asymmetric supersession (0019 ⇄ 0112).** 0112 declares `supersedes_partial: [ADR-0019]` (checked-suspension doctrine) but 0019 carries **no** back-pointer to 0112 — its Status line cites only ADR-0146 for *variant-name* supersession. | `0112-…value-based-yield.yaml:20-21`; `0019-…taxonomy.md` `**Status:**` line (only mentions 0146). |
| S2 | **Promised superseded_by never written (0055).** ADR-0078 lists `supersedes: [ADR-0055]` and its own rollout step says *"Verify ADR-0055 frontmatter carries `superseded_by: ADR-0078`."* But `0055-permit-platform-to-runtime-direction.md` status is still *"Accepted (supersedes ADR-0026)"* — **no `superseded_by` edge present**. 0055 still presents as live authority. | `0078-…consolidation.yaml:10-11,191`; `0055-…direction.md:3`. |
| S3 | **Promised extended_by never written (0066).** ADR-0078 says *"ADR-0066 carries `extended_by: ADR-0078`."* 0066's header lists cross-links to 0026/0055/0064 but **no `extended_by: ADR-0078`**. | `0078-…consolidation.yaml:191`; `0066-…evolution.md` header. |
| S4 | **Superseded node still mass-cited (0079).** `status: superseded` (by 0088) but **47 references** to ADR-0079 remain across the corpus (incl. the amendment-narratives) — the highest-cited superseded ADR; citers were not re-pointed to 0088. | `grep -o ADR-0079` across `docs/adr/` + `docs/logs/adr-amendment-narratives/` = 47; `0079-…runtime-core.yaml:3-5`. |
| S5 | **`supersedes_partial` is a non-standard, unenforced edge type.** 0081, 0112, 0140, 0152 use `supersedes_partial:` (and 0140/0112 use prose "PARTIALLY SUPERSEDES"). The graph builder validates `supersedes`/`extends`/`relates_to` (per 0068 Gate Rule R-H); `supersedes_partial` likely isn't traversed, so these partial reversals are invisible to the DAG. | `0081:16`, `0112:20`, `0140:30`, `0152:17`. |
| S6 | **Prose-only supersession of an external authority (0106).** `0106-run-version-two-phase-migration.yaml:68` states its CAS semantics *"supersedes the ARCHITECTURE.md §4 #20 W1"* — a supersession of a non-ADR doc, captured only in prose with no structured edge and no marker in ARCHITECTURE.md. | `0106:68`. |
| S7 | **Supersession targets that are Rules, not ADRs (0119, 0149).** `0119:128` and `0149:79` annotate items `superseded_by: Rule G-13` / `superseded_by: Rule G-1.b (Wave 5) / ADR-0147`. Mixed ADR-and-Rule supersession targets are not representable in the ADR-to-ADR DAG; these edges live only in prose. | `0119:128`, `0149:79`. |
| S8 | **Index ↔ archive mismatch.** `docs/adr/INDEX.md` "Archive" table lists 0026/0083/0084/0085 under `docs/logs/adr-amendment-narratives/` (those files exist — good), but the table rows for 0083/0084/0085 are **truncated mid-cell** (`| [0083](../logs/adr-amendment-narratives/0083` with no closing). Meanwhile `docs/adr/archive/INDEX.md` is a second, separate archive pointer — two archive indices, one malformed. | `INDEX.md:40-43`; `docs/adr/archive/INDEX.md`. |
| S9 | **Self-superseded class deprecation not cross-checked (0030).** 0081 raises the option *"Mark ADR-0030 as fully superseded and remove the W2 evolution claim,"* and 0127 says it *"consolidates and supersedes prose"* of 0030 — yet 0030's `**Status:**` is still plain "accepted." Two later ADRs partially retire 0030's content with no status change or `superseded_by`. | `0081:124`, `0127:15`; `0030-…matrix.md:3`. |

**No referenced-but-absent ADR numbers:** every `ADR-NNNN` citation in the corpus resolves to a file in `docs/adr/`, `docs/adr/locked/`, or `docs/logs/adr-amendment-narratives/`. All numbers 0001–0158 are present exactly once each (except **0155, which exists as both `.md` and `.yaml`** — a duplicate decision file, flagged in 01-adr-census). No citation points beyond 0158.

---

## 4. RELOCATE RECOMMENDATION — which clusters become single knowledge docs

The corpus is ~154 distinct ADRs where a large fraction are either change-history (rc-waves) or repeated restatements of an evolving design. Recommendation: **promote the durable design clusters into a small set of living `knowledge/` docs, and demote the changelog cluster into the logs sink.**

| Cluster | Members (count) | Becomes | Disposition of members |
|---|---|---|---|
| R-A Suspend/Resume | 0019, 0024, 0025, 0030*, 0070*, 0074*, 0112, 0137, 0146 (9) | `knowledge/suspend-resume-model.md` | merge → living doc; ADRs become decision-trail appendix |
| R-B Spring-AI SPI surface | 0121–0134 + 0125 thesis (13) | `knowledge/spring-ai-spi-surface.md` | merge → SPI catalog; **resolve C5 + C6 in the merge** |
| R-C Engine contract | 0071–0074, 0079†, 0088, 0090, 0112, 0113, 0140, 0158 (11) | `knowledge/engine-contract.md` | merge; 0079/0088/0090 → history appendix |
| R-D Resilience/Skill-capacity | 0008*, 0030, 0038, 0052, 0070, 0080, 0081 (7) | `knowledge/resilience-and-skill-capacity.md` | merge → living doc |
| R-E rc-wave META | 0083†–0085†, 0086, 0087, 0090–0098, 0105, 0114, 0116, 0118 (≈18) | `docs/logs/governance-waves.md` | **relocate to logs** (change-history, not knowledge); keep only the durable rule each spawned as a card pointer |
| R-F Agent-service L1 | 0048, 0078, 0088, 0100, 0115, 0136, 0138, 0140, 0142, 0144, 0155 (11) | `knowledge/agent-service-l1-design.md` | merge; 0048/0078/0088/0100 → history appendix; **collapse the 0155 .md/.yaml duplicate** |
| R-G Structurizr + Feature registry | 0147–0154 (8) | `knowledge/architecture-authoring-and-fact-layer.md` | merge; 0147/0148/0149 → spike appendix |
| R-H Memory/Knowledge model | 0034, 0051, 0082 (+0123/0133 shared w/ R-B) (3+) | `knowledge/memory-knowledge-model.md` | merge; cross-link SPI surface to R-B |

`*` = ADR appears in more than one cluster (the membership overlaps are themselves evidence the corpus needs consolidation). `†` = already superseded or already demoted.

**Net effect:** ~70 of the active ADRs collapse into **7 living knowledge docs + 1 logs sink**, leaving the genuinely-atomic foundational picks (locked/* stack choices) and one-off decisions as standalone ADRs.

**Prerequisite cleanups before any merge** (so the merge doesn't bake in drift):
1. Fix **C1** (10 mis-slugged filenames) and **C2/C3** (three disagreeing indices + the dangling 0042 link) — the indices are generator inputs (`migrate_adrs_to_yaml.py`, `build_architecture_graph.sh`), so stale slugs would propagate into generated artifacts.
2. Write the missing back-edges **S1/S2/S3** and re-point the 44 **S4** citers before 0079/0055 get folded into history appendices.
3. Decide whether `supersedes_partial` (**S5**) becomes a first-class, graph-traversed edge or is replaced by `supersedes` + a section-scoped note — otherwise the partial reversals (0019↔0112, 0030↔0081/0127, 0138↔0140) stay invisible to the DAG.

---

## Appendix — method

- File enumeration: `docs/adr/*.{md,yaml}` (149 numbered+index files), `docs/adr/locked/*.md` (11), `docs/adr/archive/INDEX.md` (pointer only), `docs/logs/adr-amendment-narratives/{0026,0083,0084,0085}`.
- Status extracted from `**Status:**` (md), `> Status:` (blockquote md variant), `- Status:` (dash md variant), and front-matter `status:` (yaml).
- Supersession edges extracted from `supersedes:` / `superseded_by:` / `supersedes_partial:` / `extends:` / `relates_to:` plus prose "supersede(s/d)" / "PARTIALLY SUPERSEDES".
- Mis-slug confirmed by comparing filename slug → in-file H1 → file body Decision Drivers (read for 0011 and 0013 as representatives; pattern holds for the contiguous 0005/0006/0008–0015 block).
- Reference completeness: all `ADR-[0-9]{4}` tokens across `docs/adr/` + `docs/logs/adr-amendment-narratives/` resolved against the present-file set; no number 1–158 absent, none cited above 0158.
