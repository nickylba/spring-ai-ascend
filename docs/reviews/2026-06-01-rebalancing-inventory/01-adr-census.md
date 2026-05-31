# ADR Census & Classification — 2026-06-01 Rebalancing Inventory

**Scope:** every ADR on the current working tree (branch `governance/knowledge-governance-separation`, == `origin/main`).
**Verification:** re-enumerated from disk; nothing trusted from prior runs. A `normalized/` ADR layer does **not** exist on this tree.
**Read-only artifact:** no existing file was modified to produce this census.

## 0. Where the ADRs live

| Location | Glob | Files |
|---|---|---|
| Top-level Markdown | `docs/adr/*.md` (numbered) | 56 |
| Top-level YAML | `docs/adr/*.yaml` (numbered) | 88 |
| Locked Markdown | `docs/adr/locked/*.md` | 11 |
| Archive | `docs/adr/archive/**` | **0 ADRs** — only `INDEX.md` (a pointer index; predecessor bodies not retained) |
| **Total ADR files** | | **155** |

- **Distinct ADR numbers: 154.** ADR-0155 exists as **both** `.md` and `.yaml` (duplicate decision file — see Anomalies).
- The numbered top-level `.md` files (`0003`–`0067`) are legacy, never migrated to YAML; `0068`+ are YAML-native.
- Non-ADR companions in `docs/adr/` (excluded from the census, not decisions): `INDEX.md`, `README.md`, `ADR-CLASSIFICATION.md`, `taxonomy.md`, `adr-level-module-map.yaml`, `archive/INDEX.md`.
- **Stale-metadata warning:** `ADR-CLASSIFICATION.md`, `INDEX.md`, and `adr-level-module-map.yaml` (rendered as `taxonomy.md`) carry slug/title mappings that DIVERGE from the actual file contents (e.g. they call 0008 "resilience4j-circuit-breaker" but the file's H1 is "OPA sidecar for authorization"; 0012 "valkey-session-cache" but the file is "Maven multi-module, not Gradle"; 0013 "vault" but the file is "UUIDv7 for surrogate IDs"). **This census reads the actual H1/title from each file, not those maps.**

## 1. Classification scheme

- **PRODUCT** — about the system being built: SPI, module runtime role, contract surface, boundary, runtime behavior, domain/data model, state machine, protocol.
- **FOUNDATIONAL-TECH** — selection of a stack component / dependency (Java, Spring Boot, Spring AI, Postgres, Temporal, Vault/Keycloak, Maven, Flyway, OPA, Resilience4j, Micrometer, GraalVM, Valkey, Spring Cloud Gateway, UUIDv7, RFC7807, …).
- **META** — about the governance/process/corpus itself: ADRs about ADRs, rules, gates, fact-layer, reading-path, workspace authority, layer-purity, namespace/package-root migration, module-topology reorg, release-note-truth, recurring-defect families, wave authority.

**Decision rule for the many `governance_infra: true` ADRs:** that flag is a hint, not a verdict. The **subject** of the decision decides. If the subject is a runtime/SPI/contract/domain artifact, it is PRODUCT even when the ADR also tightens a gate; if the subject is the governance machinery, corpus organization, rules, or codebase module/package topology, it is META.

**PREMATURE-SELECTION flag (`PB`)** — set when the ADR binds concrete implementation that an architecture-level decision should defer: library/version pins (`x.y.z`), fully-qualified package/class names, record/field names, SQL DDL, HTTP verb+path, or `src/.../*.java` file paths. Derived from a body scan (semver hits ×2 + SQL + HTTP-verb-path ×2 + backtick-verb + java-path + record-decl ×2, plus FQ-package count) and confirmed by reading the worst offenders. `sev` = severity (H/M/L).

## 2. Per-ADR table

Legend: `Fmt` = md/yaml · `GI` = `governance_infra:true` · `PB` = premature-binding offender · `sev` = PB severity · score columns from the body scan.

### 2.1 Locked (`docs/adr/locked/*.md`) — 11

| # | Title (actual H1) | Fmt | Status | Nature | PB | sev | Why PB |
|---|---|---|---|---|---|---|---|
| 0001 | Java 21 + Spring Boot 4.0.5 as the runtime baseline | md | accepted | FOUNDATIONAL | ✔ | M | pins `4.0.5` patch version |
| 0002 | Spring AI 2.0.0-M5 as the LLM gateway, not LangChain4j | md | accepted | FOUNDATIONAL | ✔ | M | pins `2.0.0-M5` milestone |
| 0004 | PostgreSQL 16 with RLS + pgvector, not separate vector DB | md | accepted | FOUNDATIONAL | – | | major version only; acceptable for a locked stack pick |
| 0005 | Row-level security with SET LOCAL transaction-scoped GUC | md | accepted | FOUNDATIONAL | ✔ | M | `SET LOCAL app.tenant_id`, RLS DDL idiom baked in |
| 0006 | ActionGuard 5-stage chain (cycle-9 truth-cut), not 11-stage | md | accepted | PRODUCT | – | | runtime guard-chain shape, no concrete pins |
| 0010 | Keycloak (OSS) as default IdP, but customer can BYO | md | accepted | FOUNDATIONAL | – | | names product but leaves BYO seam |
| 0011 | Spring Cloud Gateway as ingress, not Kong / Traefik | md | accepted | FOUNDATIONAL | – | | product pick, no version |
| 0014 | 3-posture model (dev/research/prod), not 5 or 2 | md | accepted | PRODUCT | – | | posture domain model |
| 0015 | Defer multi-framework dispatch (Python sidecar, LangChain4j) to W4+ | md | accepted | PRODUCT | – | | deferral / scope boundary |
| 0020 | RunLifecycle SPI Separation and RunStatus Formal DFA | md | accepted | PRODUCT | ✔ | M | SQL columns + `RunStatus`/`RunStateMachine` class names + method sigs |
| 0027 | Idempotency Scope at W0: Header Validation Only | md | accepted | PRODUCT | – | L | names `IdempotencyHeaderFilter` + V2 SQL in prose |

> Note: locked filenames are **also** mis-slugged vs. content. `locked/0006-posture-model-dev-research-prod.md` actually decides the **ActionGuard 5-stage chain**; `locked/0014-contract-spine-versioning-policy.md` actually decides the **3-posture model**; `locked/0011-flyway-schema-migration.md` actually decides **Spring Cloud Gateway as ingress**. The H1 (column above) is authoritative.

### 2.2 Top-level Markdown (`docs/adr/*.md`) — 56

| # | Title (actual H1) | Fmt | Status | Nature | PB | sev | Why PB |
|---|---|---|---|---|---|---|---|
| 0003 | Temporal Java SDK 1.35.0 for durable workflows | md | accepted | FOUNDATIONAL | ✔ | M | pins `1.35.0` |
| 0007 | At-least-once outbox in Postgres, not Kafka, for v1 | md | accepted | FOUNDATIONAL | – | | strategy pick |
| 0008 | OPA sidecar for authorization, not in-process Cedar/custom | md | accepted | FOUNDATIONAL | – | | strategy pick |
| 0009 | HashiCorp Vault (OSS) for secrets, not env vars / K8s | md | accepted | FOUNDATIONAL | – | | product pick |
| 0012 | Maven multi-module, not Gradle | md | accepted | FOUNDATIONAL | – | | build-tool pick |
| 0013 | UUIDv7 for surrogate IDs, not snowflake / sequence | md | accepted | FOUNDATIONAL | – | L | ID-scheme pick (one `uuid` token) |
| 0016 | A2A Federation — Strategic Deferral to Post-W4 | md | accepted | PRODUCT | – | | deferral; cites SPI names only |
| 0017 | Dev-time Trace Replay Surface — MCP Server, No Admin UI | md | accepted | PRODUCT | – | | boundary decision |
| 0018 | Sandbox Executor SPI for ActionGuard Bound Stage | md | accepted | PRODUCT | ✔ | M | GraalVM version + FQ class names + `record` |
| 0019 | SuspendSignal: Checked-Exception Primitive + Sealed SuspendReason | md | accepted (variants superseded by 0146) | PRODUCT | ✔ | **H** | 6 `record` variant decls + executor FQNs + Java-21-coupled |
| 0021 | Layered SPI Taxonomy: Cross-Tier Core vs Tier-Specific Adapters | md | accepted | PRODUCT | ✔ | M | `src/.../*.java` paths + method sigs |
| 0022 | PayloadCodec SPI and Typed Payload Contract | md | accepted | PRODUCT | ✔ | **H** | 8 `record` decls + FQNs |
| 0023 | Cross-Boundary Context Propagation: Tenant, Trace, MDC, Metric Tags | md | accepted | PRODUCT | – | L | a few carrier FQNs |
| 0024 | Suspension Write Atomicity: Checkpointer + RunRepository Txn Contract | md | accepted | PRODUCT | – | L | one SQL token |
| 0025 | Checkpoint Ownership Boundary: Executor Cursors vs Orchestrator Run Row | md | accepted | PRODUCT | – | L | java paths |
| 0028 | Causal Payload Envelope and Semantic Ontology Tags | md | accepted | PRODUCT | ✔ | M | `record` + java paths + FQN |
| 0029 | Cognition-Action Separation Principle | md | accepted | PRODUCT | – | | principle-level |
| 0030 | Skill SPI: Lifecycle, Resource Matrix, Posture-Mandatory Sandbox | md | accepted | PRODUCT | ✔ | M | 4 `record` decls + sig |
| 0031 | Three-Track Channel Isolation for Northbound Streaming | md | accepted | PRODUCT | – | L | one record + path |
| 0032 | Scope-Based Run Hierarchy and Planner Contract Minimal | md | accepted | PRODUCT | ✔ | M | 3 `record` decls + java paths |
| 0033 | Logical Identity Equivalence and Deployment-Locus Vocabulary | md | accepted | PRODUCT | – | | vocabulary/domain |
| 0034 | Memory and Knowledge Taxonomy at L0 | md | accepted | PRODUCT | – | L | domain taxonomy; explicitly DEFERS mem0/Graphiti/Cognee default |
| 0035 | Posture Enforcement Single-Construction-Path | md | accepted | META | ✔ | M | HTTP-verb cites + many java paths (gate/enforcement) |
| 0036 | Contract-Surface Truth Generalization | md | accepted | META | – | | corpus-truth rule |
| 0037 | Wave Authority Consolidation | md | accepted | META | – | | single canonical wave plan |
| 0038 | Skill SPI Resource Tier Classification | md | accepted | PRODUCT | – | | SPI tiering taxonomy |
| 0039 | Payload Migration Adapter Strategy | md | accepted | PRODUCT | – | L | one version + path |
| 0040 | W1 HTTP Contract Reconciliation | md | accepted | PRODUCT | ✔ | **H** | concrete HTTP verbs+paths (`DELETE /v1/runs/{id}`, `POST …/cancel`), enum values, `RunStatus.java` |
| 0041 | Active-Corpus Truth Sweep | md | accepted | META | – | | corpus sweep |
| 0042 | Test-Evidence Enforcement for Rule G-2.a (Gate Rule 19) | md | accepted | META | – | | gate rule |
| 0043 | Active Normative Doc Catalog and Peripheral Drift Prevention | md | accepted | META | – | | corpus catalog + gate |
| 0044 | SPI Contract Precision and Memory Metadata Reconciliation | md | accepted | PRODUCT | ✔ | M | many java paths + method sigs |
| 0045 | Shipped-Row Evidence Path Existence + Peripheral Wave-Qualifier Gate | md | accepted | META | ✔ | L | java-path evidence rows (gate) |
| 0046 | Release-Note Shipped-Surface Truth Gate | md | accepted | META | ✔ | M | 12 java-path/sig refs in gate spec |
| 0047 | Active-Entrypoint Truth and System-Boundary Prose Convention | md | accepted | META | – | | corpus prose convention |
| 0048 | Service-Layer Microservice-Architecture Commitment | md | accepted | PRODUCT | – | | deployment-topology commitment |
| 0049 | C/S Dynamic Hydration Protocol and Cursor Handoff | md | accepted | PRODUCT | – | L | java paths |
| 0050 | Workflow Intermediary Bus, Mailbox Backpressure, Rhythm Track | md | accepted | PRODUCT | – | L | java paths |
| 0051 | Memory and Knowledge Ownership Boundary | md | accepted | PRODUCT | – | | C-side vs S-side boundary |
| 0052 | Skill Topology Scheduler and Capability Bidding | md | accepted | PRODUCT | – | | runtime scheduler |
| 0053 | Cohesive Agent Swarm Execution — Workflow Authority + SpawnEnvelope | md | accepted | PRODUCT | – | | runtime exec model |
| 0054 | Long-Connection Containment — Logical Runtime Handle Invariant | md | accepted | PRODUCT | – | | runtime handle invariant |
| 0055 | Permit `agent-platform → agent-runtime`; Forbid the Reverse | md | accepted | META | ✔ | L | dep-direction rule; HTTP-verb + FQNs |
| 0056 | JWT Validation and Tenant Claim Cross-Check | md | accepted | PRODUCT | – | | security contract |
| 0057 | Durable Idempotency Claim/Replay | md | accepted | PRODUCT | ✔ | **H** | 14 SQL DDL tokens (CREATE TABLE/columns) |
| 0058 | Posture Boot Guard | md | accepted | PRODUCT | – | | boot-time guard |
| 0059 | Code-as-Contract Architectural Enforcement (Rule R-C.a) | md | accepted | META | ✔ | L | introduces a rule; SQL/path cites |
| 0060 | Phase L Reviewer Remediation (closes P0/P1 set) | md | accepted | PRODUCT | ✔ | **H** | concrete HTTP verbs+paths + java paths + versions (mixed: also META corpus fixes) |
| 0061 | Telemetry Vertical Layer | md | accepted | PRODUCT | – | L | one SQL + sigs |
| 0062 | Trace → Run → Session Identity (N:M with session_id) | md | accepted | PRODUCT | ✔ | M | SQL schema + identity field names |
| 0063 | Client SDK Observability Contract (W3) | md | accepted | PRODUCT | – | L | java paths |
| 0064 | Layer-0 Governing Principles + CLAUDE.md Restructure | md | accepted | META | – | | governing principles P-A..P-D |
| 0065 | Competitive Baselines (Four Pillars) | md | accepted | META | – | | baseline.yaml governance |
| 0066 | Independent Module Evolution | md | accepted | META | – | | per-module module-metadata.yaml |
| 0067 | SPI + DFX + TCK Co-Design | md | accepted | META | – | | corpus co-design discipline |
| 0155 | AgentService L1 v1.2 internal module design absorption (**md dup**) | md | accepted | PRODUCT | – | | duplicate of 0155.yaml |

### 2.3 Top-level YAML (`docs/adr/*.yaml`) — 88

| # | Title | Fmt | Status | GI | Nature | PB | sev | Why PB |
|---|---|---|---|---|---|---|---|---|
| 0068 | Layered 4+1 and Architecture Graph as Twin Sources of Truth | yaml | accepted | ✔ | META | – | | corpus-of-record structure |
| 0069 | Layer-0 Ironclad Rules (P-E..P-L) | yaml | accepted | ✔ | META | – | | governing principles |
| 0070 | Cursor Flow + Skill Capacity Runtime (Rules 36.b/41.b) | yaml | accepted | | PRODUCT | ✔ | L | HTTP-verb cites |
| 0071 | Engine Contract Structural Wave (umbrella + P-M) | yaml | accepted | | PRODUCT | ✔ | L | java paths |
| 0072 | Engine Envelope + Strict Matching (first L1 of P-M) | yaml | accepted | | PRODUCT | ✔ | L | FQNs + paths |
| 0073 | Engine Lifecycle Hooks + Runtime-Owned Middleware SPI | yaml | accepted | | PRODUCT | ✔ | L | java paths |
| 0074 | S2C Capability Callback Protocol (third L1 of P-M) | yaml | accepted | | PRODUCT | ✔ | M | 8 java paths + record + FQN |
| 0075 | Evolution Scope Boundary (fourth L1 of P-M) | yaml | accepted | | PRODUCT | ✔ | L | java paths |
| 0076 | R2 Pilot — Runtime Self-Validates Engine Envelope on Boot | yaml | accepted | ✔ | PRODUCT | ✔ | L | java paths + FQN |
| 0077 | Schema-First Domain Contracts (Rule M-2.a + gate 60) | yaml | accepted | ✔ | META | – | | enum-definition gate |
| 0078 | agent-service consolidation: fold platform+runtime into one Maven module | yaml | accepted | ✔ | META | ✔ | M | pins `com.huawei.ascend.service.*` package root; module-topology reorg |
| 0079 | T2.B2 engine extraction with shared agent-runtime-core | yaml | **superseded** | ✔ | META | ✔ | **H** | 22 java-path/FQN refs; module-extraction topology |
| 0080 | ResilienceContract .spi package alignment (+ Rule R-D.f) | yaml | accepted | ✔ | META | ✔ | M | 8 FQNs; package-alignment rule |
| 0081 | ResilienceContract dual-surface reconciliation | yaml | accepted | ✔ | PRODUCT | – | L | contract reconciliation |
| 0082 | GraphMemoryRepository canonical ownership + SPI-ownership SSOT | yaml | accepted | ✔ | META | ✔ | M | ownership-topology SSOT; FQNs + paths |
| 0086 | Rule Namespace Ratchet (P-/R-/D-/G-/M- scheme) | yaml | accepted | ✔ | META | – | | rule-namespace governance |
| 0087 | L0 rc12 authority ratchet + deploy-truth + terminal-verb scope | yaml | accepted | ✔ | META | ✔ | M | HTTP verbs + 11 java paths (mixed corpus/runtime) |
| 0088 | Dissolve agent-runtime-core; redistribute kernel SPI to semantic-home modules | yaml | accepted | ✔ | META | ✔ | **H** | 31 java-path/FQN refs; module-topology dissolution |
| 0089 | Edge-Plane Ingress Gateway Mandate (client→bus→server only) | yaml | accepted | | PRODUCT | ✔ | M | 12 java paths + 5 FQNs (topology mandate) |
| 0090 | rc14 Cross-Authority Parity + Engine Package Semantic-Home | yaml | accepted | ✔ | META | ✔ | M | 8 FQNs; package-home reconciliation |
| 0091 | rc15 Structural-Carrier Parity + Terminal-State Scope Widening | yaml | accepted | ✔ | META | – | L | corpus-parity (mixed: terminal-state runtime scope) |
| 0092 | Ultimate Architecture Ledger Ack + Agent-OS Scope Boundary | yaml | accepted | ✔ | META | – | | scope-boundary acknowledgment |
| 0093 | rc16 Recurring-Family Comprehensive Closure + META Scope Completeness | yaml | accepted | ✔ | META | – | | recurring-defect families |
| 0094 | rc17 Recurring-Defect Family Truth + Rule Consolidation | yaml | accepted | ✔ | META | – | | rule consolidation |
| 0095 | rc18 Comprehensive Hardening (Rule 111 + pattern sweep + enforcer norm) | yaml | accepted | ✔ | META | – | | gate/rule hardening |
| 0096 | rc19 META-Recursion Permanent Close (Python YAML parser + Rules 112-114) | yaml | accepted | ✔ | META | – | | gate tooling |
| 0097 | rc20 — Meta-recursion close + D-9 + G-7 invocation extension | yaml | accepted | ✔ | META | – | | rule cards |
| 0098 | rc21 — 6-phase scenario contracts + Rules G-10/G-11 | yaml | accepted | ✔ | META | – | | phase-contract machinery |
| 0099 | rc22 — L1 Architecture Depth & Grounding (Rule G-1.1 + scanners) | yaml | accepted | ✔ | META | – | | L1-doc grounding gate |
| 0100 | rc22 — agent-service L1 runtime-role decomposition (5-component, Run/Task/Session, 3 SPIs) | yaml | accepted | ✔ | PRODUCT | ✔ | L | 6 FQNs; genuine runtime architecture |
| 0101 | rc22 — Polymorphic Deployment Topology (Mode A/B + deployment-loci SSOT) | yaml | accepted | ✔ | PRODUCT | – | | deployment topology |
| 0102 | rc22 — Evolution Plane Online/Offline Duality (2×2 matrix) | yaml | accepted | ✔ | PRODUCT | – | | evolution-plane model |
| 0103 | rc22 — agent-middleware naming + Capability-Services distribution | yaml | accepted | ✔ | META | – | | module naming/distribution |
| 0104 | rc22 — Package-root migration to com.huawei.ascend | yaml | accepted | ✔ | META | ✔ | M | package-root migration (decision) |
| 0105 | rc32 — residual corrective + family sanitizer fix | yaml | accepted | ✔ | META | – | | family-surface sanitizer |
| 0106 | Run.version field — two-phase W1.5+W2 migration | yaml | accepted | | PRODUCT | – | | runtime field + migration plan |
| 0107 | Federation acyclicity — central RunRegistry ancestor reconstruction | yaml | accepted | | PRODUCT | – | | runtime federation rule |
| 0108 | Tenant re-authorization widening + GraphMemoryRepository tenant traversal | yaml | accepted | | PRODUCT | ✔ | **H** | 5 HTTP verb+path cites + java paths |
| 0109 | S2C callback + ingress envelope — server-identity proof (mTLS/JWT) | yaml | accepted | | PRODUCT | – | | security protocol |
| 0110 | Audit log tamper-evidence (hash-chain + Merkle) + Hook PII failsafe | yaml | accepted | ✔ | PRODUCT | – | L | 2 SQL tokens |
| 0111 | Sandbox startup gate + Vault rotation + OTLP tenant binding + Outbox replay | yaml | accepted | | PRODUCT | ✔ | L | HTTP-verb cite |
| 0112 | Engine stateless executor — value-based yield; A2A InterruptType→SuspendReason | yaml | accepted | | PRODUCT | ✔ | M | 5 FQNs + record + paths |
| 0113 | Hook chain — two-level failure semantics + @Order tie-break | yaml | accepted | | PRODUCT | – | | hook semantics |
| 0114 | Implementation feasibility batched closures (10 R1 findings) | yaml | accepted | ✔ | PRODUCT | ✔ | M | versions + HTTP-verb; contract-surface amendments |
| 0115 | agent-service L1 expansion acceptance (dual modes, 4-layer state, A2A boundary) | yaml | accepted | ✔ | PRODUCT | ✔ | L | 4 FQNs + paths |
| 0116 | rc36 exhaustive-audit corrective: kernel-truth gate + cancel-CAS | yaml | accepted | ✔ | META | – | L | gate un-deadening (mixed: cancel-CAS runtime) |
| 0117 | rc37 strategic repositioning: Ascend/Kunpeng; drop FSI as lead vertical | yaml | accepted | ✔ | META | – | | product positioning/brand |
| 0118 | rc38 audit-corrective: latent-correctness + deploy-packaging; register family | yaml | accepted | ✔ | META | – | L | recurring family + deploy fixes (mixed) |
| 0119 | Single-Source Rendering for derived docs (Rule G-13 + Falcon roadmap) | yaml | accepted | ✔ | META | – | | derived-doc rendering |
| 0120 | Brand & Audience B alignment: KEEP spring-ai-ascend identity | yaml | accepted | ✔ | META | – | L | brand/positioning |
| 0121 | ModelGateway SPI (pure-Java `ModelResponse invoke(ModelInvocation)`) | yaml | accepted | | PRODUCT | ✔ | M | FQ package `…middleware.model.spi.ModelGateway` + method sig |
| 0122 | Tool vs Skill semantic resolution (`Tool` is a `SkillKind`) | yaml | accepted | | PRODUCT | ✔ | M | FQNs + 5 method sigs |
| 0123 | Memory unified SPI (`MemoryStore<K,V>` by `MemoryCategory` M1-M6) | yaml | accepted | | PRODUCT | ✔ | L | FQN + generic sig |
| 0124 | VectorStore / Retriever / EmbeddingModel SPIs (Spring AI decorators) | yaml | accepted | | PRODUCT | ✔ | L | 3 Spring AI FQNs |
| 0125 | Spring AI integration boundary (Spring AI canonical; SPIs decorate) | yaml | accepted | | PRODUCT | ✔ | M | `spring-ai-bom 2.0.0-M5` + FQNs |
| 0126 | Planner SPI (`Planner.plan(PlanningRequest)` → `Plan` DAG) | yaml | accepted | | PRODUCT | ✔ | L | FQN + method sig |
| 0127 | Skill SPI: unified lifecycle + `SkillKind` discriminator | yaml | accepted | | PRODUCT | ✔ | L | FQN + sig |
| 0128 | Agent first-class SPI (`Agent` + `AgentDefinition` + `AgentRegistry`) | yaml | accepted | | PRODUCT | ✔ | L | FQN |
| 0129 | Streaming-aware ModelGateway (`Stream<ModelResponseChunk>`) | yaml | accepted | | PRODUCT | ✔ | M | FQN + 4 method sigs |
| 0130 | StructuredOutputConverter<T> SPI (pure-Java; Spring AI ref) | yaml | accepted | | PRODUCT | ✔ | L | FQN + sigs |
| 0131 | PromptTemplate SPI (tenant-scoped, sealed-source) | yaml | accepted | | PRODUCT | ✔ | L | 4 FQNs |
| 0132 | ChatAdvisor SPI (around-call interceptor over ModelGateway) | yaml | accepted | | PRODUCT | ✔ | L | 3 FQNs + sig |
| 0133 | ConversationMemory (`MemoryStore<String,ConversationWindow>` variant) | yaml | accepted | | PRODUCT | ✔ | L | 3 FQNs |
| 0134 | Tool-Call Iteration Loop (agent-driven vs planner-driven) | yaml | accepted | | PRODUCT | – | | execution-mode model |
| 0135 | AgentSession as Run-Projection (no separate AgentSession SPI) | yaml | accepted | | PRODUCT | – | L | one record |
| 0136 | Vocabulary Reconciliation: PR71 'Task' ≠ Run alias | yaml | accepted | ✔ | PRODUCT | ✔ | M | 9 java-path refs (vocabulary→code) |
| 0137 | SuspendSignal canonical; InterruptSignal/Reason are L1 glossary synonyms | yaml | accepted | | PRODUCT | ✔ | L | java paths |
| 0138 | agent-service 5-layer L1 ratification (Access/Session&Task/Queue/Control/Adapter) | yaml | accepted | ✔ | PRODUCT | – | | L1 layer model |
| 0139 | Fast-Path / Slow-Path narrowed semantics | yaml | accepted | | PRODUCT | – | | runtime path semantics |
| 0140 | Engine Adapter Layer split (Layer 5a/5b) | yaml | accepted | | PRODUCT | – | | L1 layer decomposition |
| 0141 | Internal Event Queue is design_only until sub-package lands | yaml | accepted | | PRODUCT | ✔ | L | java paths |
| 0142 | Run aggregate single-owner (Layer 2 owns Run record/RunStatus/SM/Repo) | yaml | accepted | | PRODUCT | ✔ | M | 7 java paths + 4 sigs |
| 0143 | rc53 review-log demotion + L1 canonical move | yaml | accepted | ✔ | META | – | | corpus authority move |
| 0144 | Layer ↔ Package matrix (rc22 5-component × rc53 5-layer) | yaml | accepted | ✔ | META | – | L | view-mapping matrix |
| 0145 | Sealed RunEvent hierarchy specification | yaml | accepted | | PRODUCT | ✔ | M | 9 java paths + 2 sigs |
| 0146 | SuspendReason taxonomy canonical alignment (6-variant set) | yaml | accepted | | PRODUCT | ✔ | L | java paths; concrete variant names |
| 0147 | Structurizr workspace closure as authoring root | yaml | accepted | ✔ | META | ✔ | L | workspace-authority; version pins |
| 0148 | Wave 0 spike results — Structurizr workspace feasibility | yaml | accepted | ✔ | META | ✔ | M | 7 tool/version pins (spike) |
| 0149 | Structurizr workspace authority — W0..W5 shipped | yaml | accepted | ✔ | META | – | | workspace authority |
| 0150 | Architecture design system unified under architecture/ | yaml | accepted | ✔ | META | – | | corpus directory authority |
| 0151 | L1 Feature Registry canonical schema (SAA Feature + aiBoundary + 9-state) | yaml | accepted | ✔ | META | – | L | registry schema (one version) |
| 0152 | Uniform L1 per-view mechanism + L0 mounting | yaml | accepted | ✔ | META | – | | corpus mechanism |
| 0153 | L1 Feature Registry closure (Rule G-14 blocking flip) | yaml | accepted | ✔ | META | – | | registry gate |
| 0154 | Fact-Layer Authority — generated facts as AI's primary L1 input | yaml | accepted | ✔ | META | – | | fact-layer machinery |
| 0155 | AgentService L1 v1.2 internal module design (**yaml dup**) | yaml | accepted | | PRODUCT | – | L | duplicate of 0155.md; FQNs |
| 0156 | Product Authority and Traceability Chain (ProductClaim binding axis) | yaml | accepted | ✔ | META | – | | traceability machinery |
| 0157 | EngineeringFrame Ontology (structural axis Module↔FunctionPoint) | yaml | accepted | ✔ | META | – | L | architecture-modeling ontology |
| 0158 | Engine Boundary (EnginePort) — transport-agnostic Service→Engine | yaml | accepted | | PRODUCT | ✔ | L | 3 FQNs + 3 sigs |

## 3. Tallies

### 3.1 By format (distinct decisions = 154; one number, 0155, has two files)

| Format | Files | Distinct decisions counted |
|---|---|---|
| `.md` (top-level) | 56 | 56 |
| `.md` (locked) | 11 | 11 |
| `.yaml` (top-level) | 88 | 88 (0155.yaml duplicates 0155.md) |
| Archive | 0 | 0 |
| **Total files** | **155** | **154 distinct + 1 dup** |

### 3.2 By nature (154 distinct decisions; ADR-0155 dup counted once)

| Nature | Count (distinct decisions) | Share | (files, incl. 0155 dup) |
|---|---:|---:|---:|
| **PRODUCT** | **87** | **56.5%** | 88 |
| **META** | **55** | **35.7%** | 55 |
| **FOUNDATIONAL-TECH** | **12** | **7.8%** | 12 |
| **Total** | **154** | 100% | 155 |

**Ratio PRODUCT : FOUNDATIONAL : META = 87 : 12 : 55** (≈ 7.3 : 1 : 4.6).

- FOUNDATIONAL-TECH set (12, exact): 0001, 0002, 0003, 0004, 0005, 0007, 0008, 0009, 0010, 0011, 0012, 0013 — pure stack/dependency picks. Note 0006/0014/0015/0020/0027 live in `locked/` but are runtime/domain decisions (PRODUCT), not stack picks.
- META set (55, exact): 0035, 0036, 0037, 0041, 0042, 0043, 0045, 0046, 0047, 0055, 0059, 0064, 0065, 0066, 0067, 0068, 0069, 0077, 0078, 0079, 0080, 0082, 0086, 0087, 0088, 0090, 0091, 0092, 0093, 0094, 0095, 0096, 0097, 0098, 0099, 0103, 0104, 0105, 0116, 0117, 0118, 0119, 0120, 0143, 0144, 0147, 0148, 0149, 0150, 0151, 0152, 0153, 0154, 0156, 0157 — dominated by rc-wave-closure + gate/rule + corpus-authority + module-topology families.
- PRODUCT (87) is dominated by the SPI/runtime clusters: SuspendSignal/payload/skill/run (0019-0034, 0038-0040, 0048-0054, 0056-0063), engine contract (0070-0076, 0081, 0112-0115, 0138-0146, 0158), and the Spring-AI SPI wave (0100-0102, 0106-0111, 0121-0142).

### 3.3 Premature-selection offenders

**Count: 89 of 154 decisions (58%) flagged `PB`.** Severity split: **High 8 · Medium 32 · Low 49.**

The 8 **High-severity** offenders (worst — concrete impl an architecture-level decision should defer):

| ADR | Nature | What it prematurely binds |
|---|---|---|
| **0019** | PRODUCT | 6 `record` variant declarations + executor FQNs; Java-21-coupled checked-exception design |
| **0022** | PRODUCT | 8 `record` declarations (typed payload contract) + FQNs |
| **0040** | PRODUCT | Concrete HTTP verbs+paths (`DELETE /v1/runs/{id}`, `POST /v1/runs/{id}/cancel`), enum literals (`CREATED`/`PENDING`), `RunStatus.java`/`TenantContextFilter` class names |
| **0057** | PRODUCT | 14 SQL DDL tokens — `CREATE TABLE`, column defs, constraints for the idempotency-claim table |
| **0060** | PRODUCT | Concrete HTTP verb+path set + java paths + library versions across a multi-finding remediation |
| **0079** | META | 22 FQN/java-path references (superseded engine-extraction module layout) |
| **0088** | META | 31 fully-qualified package/class + `src/.../*.java` path references for a module-topology dissolution |
| **0108** | PRODUCT | 5 HTTP verb+path cites (read/resume re-auth routes) + java paths |

Next-worst (Medium-severity, exact 32): 0001, 0002, 0003, 0005, 0018, 0020, 0021, 0028, 0030, 0032, 0035, 0044, 0046, 0062, 0074, 0078, 0080, 0082, 0087, 0089, 0090, 0104, 0112, 0114, 0121, 0122, 0125, 0129, 0136, 0142, 0145, 0148.

**Patterns:**
- The **Spring-AI SPI wave (0121-0133)** systematically pins fully-qualified package roots (`com.huawei.ascend.middleware.model.spi.*`) and exact method signatures inside what are nominally L0/L1 boundary decisions — a coherent premature-binding cluster (mostly Low/Medium individually, high in aggregate).
- The **module-topology META ADRs (0078, 0079, 0080, 0082, 0088, 0090, 0104)** bind concrete Java package roots and file paths; these are the densest FQN offenders.
- The **early FOUNDATIONAL picks (0001, 0002, 0003)** pin exact patch/milestone versions (`4.0.5`, `2.0.0-M5`, `1.35.0`) rather than version *ranges/floors*.

## 4. Anomalies worth flagging to the caller

1. **ADR-0155 is duplicated** — present as both `docs/adr/0155-agent-service-l1-v1-2-internal-module-design.md` and `…​.yaml` (same number, same slug, two formats). One should be removed/superseded.
2. **ADR-0079 is `status: superseded`** — the only non-`accepted` decision in the corpus; every other ADR reads `accepted`/`Accepted`.
3. **Filename/slug ↔ content drift is pervasive.** The numbered `.md` files and the three classification maps (`ADR-CLASSIFICATION.md`, `INDEX.md`, `adr-level-module-map.yaml`/`taxonomy.md`) describe slugs that do not match the files' actual H1 titles for many IDs (0006, 0008, 0011, 0012, 0013, 0014, and others). Those maps also stop at ~0139 and predate 0140-0158. They are stale and should not be used as the census of record.
4. **Number reuse across the archive boundary.** `archive/INDEX.md` says ADR-0019, 0021-0027, 0059-0067 were consolidated/retired; the **current** 0019, 0021-0067 files are *new decisions re-using those numbers*, not the archived predecessors. The archive retains no bodies — only the pointer index.
5. **`docs/adr/archive/` contains zero ADRs.** The task's `archive/**` scope yielded only `INDEX.md`. Genuinely superseded narratives live under `docs/logs/adr-amendment-narratives/` (per `INDEX.md`), which is outside the `docs/adr/` tree this census covers.
</content>
</invoke>
