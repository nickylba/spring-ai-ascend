---
inventory_id: ADR-CENSUS
governance_infra: true
wave: W0
phase: advisory-inventory
generated_at: 2026-05-29
source_of_truth: architecture/facts/generated/adrs.json
source_repo_commit: 98a58c17fb671793994c38110caa20ff047be36e
source_schema_version: 1
authority_note: |
  This census is a READABLE INTERPRETATION layer over the generated fact source.
  It invents no IDs and no relationships. The authoritative enumeration is
  architecture/facts/generated/adrs.json (extractor: tools/architecture-workspace#AdrFactExtractor),
  which is itself derived from the docs/adr/*.yaml source files. Authority cascade:
  generated facts > DSL > Card/prose. Do NOT hand-edit the JSON; re-run the extractor.
---

# ADR Census - Wave W0 Advisory Inventory

## Purpose

Baseline-truth enumeration of every ADR carried by the generated fact layer
(`architecture/facts/generated/adrs.json`). For each ADR this census records its
`id`, `status`, source `format` (md / yaml), and whether a **normalized view**
exists for it. This is an advisory inventory for the progressive-learning-curve
remediation; it changes no authority surface and is regenerated, not amended.

## Scope and the format-coverage finding

`adrs.json` is produced by `AdrFactExtractor`, which parses only the **YAML**-format
ADR source files under `docs/adr/`. Every fact in the JSON therefore reports
`format = yaml`. The repository, however, holds ADRs in **two** source formats
(`docs/adr/*.{md,yaml}`, per `docs/adr/INDEX.md`):

| Source set | Format | Count | In `adrs.json` (fact layer)? |
|---|---|---|---|
| Active YAML ADRs (`docs/adr/*.yaml`) | yaml | 88 | Yes - all 88 enumerated below |
| Active / deferred Markdown ADRs (`docs/adr/*.md`, e.g. ADR-0003..0067) | md | 56 | **No - absent from the fact layer** |
| Locked foundational ADRs (`docs/adr/locked/*.md`, ADR-0001/0002/0004/0005/0006/0010/0011/0014/0015/0020/0027) | md | 11 | **No - absent from the fact layer** |
| Archived / superseded ADRs (`docs/logs/adr-amendment-narratives/*`, e.g. ADR-0026/0083/0084/0085) | md | 4 | No - intentionally archived |

**Baseline-truth finding (advisory):** the fact layer covers only the YAML ADR
corpus. The 56 Markdown-format ADRs and the 11 locked Markdown ADRs are real,
governing decisions that do **not** appear in `architecture/facts/generated/adrs.json`.
Within the YAML range the numbering is contiguous **except** for ADR-0083/0084/0085,
which were superseded by ADR-0086 (Rule Namespace Ratchet) and demoted to the
archive partition; their absence from the JSON is expected. Any downstream
consumer that treats `adrs.json` as the complete ADR set (rather than the complete
**YAML** ADR set) will under-count the corpus. Remediation of this gap (extending
the extractor to the Markdown ADRs, or converging the corpus to a single format)
is out of scope for this Wave-W0 inventory and is recorded here as input to planning.

## Normalized-view status

A **normalized view** is a generated, schema-uniform reading projection over an
ADR (the readable-interpretation layer described in the authority chain). **No
normalized views exist yet** for any ADR: there is no `*normaliz*` directory or
artifact under `docs/` or `architecture/`. Accordingly the *Normalized view*
column below is `none` for every row. This column exists so that, as normalized
views are introduced in later waves, this census can be regenerated to track
coverage without changing its shape.

## Census table

All 88 rows below are transcribed from `architecture/facts/generated/adrs.json`
(`observed_value.id`, `observed_value.status`, `source_path` extension). Status is
`accepted` for every ADR except ADR-0079 (`superseded`, by ADR-0088). Em-dashes,
section signs, and arrows in titles are rendered in ASCII for English-only output;
the canonical glyphs live in the YAML source and the JSON.

| ID | Status | Format | Normalized view | Title (from fact source) |
|---|---|---|---|---|
| ADR-0068 | accepted | yaml | none | Layered 4+1 and Architecture Graph as Twin Sources of Truth |
| ADR-0069 | accepted | yaml | none | Layer-0 Ironclad Rules - promotion of LucioIT W1 section 6 / section 7 to governing principles |
| ADR-0070 | accepted | yaml | none | Cursor Flow + Skill Capacity Runtime - activation of Rules 36.b and 41.b |
| ADR-0071 | accepted | yaml | none | Engine Contract Structural Wave - umbrella for ADR-0072..0075 + L0 principle P-M |
| ADR-0072 | accepted | yaml | none | Engine Envelope + Strict Matching - first L1 expression of P-M |
| ADR-0073 | accepted | yaml | none | Engine Lifecycle Hooks + Runtime-Owned Middleware SPI - second L1 expression of P-M |
| ADR-0074 | accepted | yaml | none | Server-to-Client (S2C) Capability Callback Protocol - third L1 expression of P-M |
| ADR-0075 | accepted | yaml | none | Evolution Scope Boundary -- server-controlled evolution surface, fourth L1 expression of P-M |
| ADR-0076 | accepted | yaml | none | R2 Pilot - Runtime Self-Validates Engine Envelope Schema on Boot |
| ADR-0077 | accepted | yaml | none | Schema-First Domain Contracts - codify the W2.x prose-enum prohibition (P-M cross-cutting) |
| ADR-0078 | accepted | yaml | none | agent-service consolidation: fold agent-platform + agent-runtime into a single Maven module |
| ADR-0079 | superseded | yaml | none | T2.B2 engine extraction with shared agent-runtime-core (resolves the Run/RunContext back-dep) [superseded_by ADR-0088] |
| ADR-0080 | accepted | yaml | none | ResilienceContract .spi package alignment + Rule R-D sub-clause .f prevention |
| ADR-0081 | accepted | yaml | none | ResilienceContract dual-surface reconciliation (operation-policy + skill-capacity) |
| ADR-0082 | accepted | yaml | none | GraphMemoryRepository canonical ownership + module-metadata.yaml is single source of truth for SPI ownership topology |
| ADR-0086 | accepted | yaml | none | Rule Namespace Ratchet - adopt P-/R-/D-/G-/M- prefix scheme [supersedes ADR-0083/0084/0085] |
| ADR-0087 | accepted | yaml | none | L0 rc12 authority ratchet + deploy-truth + contract truth + terminal-verb scope |
| ADR-0088 | accepted | yaml | none | Dissolve agent-runtime-core; redistribute kernel SPI to semantic-home modules; align L0 reactor with 6-module narrative |
| ADR-0089 | accepted | yaml | none | Edge-Plane Ingress Gateway Mandate - client -> bus -> server is the only allowed C2S topology |
| ADR-0090 | accepted | yaml | none | rc14 Cross-Authority Parity + Engine Package Semantic-Home (L-alpha..L-eta wave) |
| ADR-0091 | accepted | yaml | none | rc15 Structural-Carrier Parity + Terminal-State Scope Widening (M-alpha..M-eta wave) |
| ADR-0092 | accepted | yaml | none | Ultimate Architecture Ledger Acknowledgment + Agent-OS Scope Boundary |
| ADR-0093 | accepted | yaml | none | rc16 Recurring-Family Comprehensive Closure + META Scope Completeness |
| ADR-0094 | accepted | yaml | none | rc17 Recurring-Defect Family Truth + Rule Consolidation |
| ADR-0095 | accepted | yaml | none | rc18 Comprehensive Hardening - Rule 111 Self-Hardening + Pattern Sweep + Naming/Structural Cleanup + Enforcer Normalization + META |
| ADR-0096 | accepted | yaml | none | rc19 META-Recursion Permanent Close - Python YAML Parser + Rule 112/113/114 + Multi-Wave Runbook |
| ADR-0097 | accepted | yaml | none | rc20 - Meta-recursion actually-close + D-9 (no version/log metadata in code) + G-7 invocation extension |
| ADR-0098 | accepted | yaml | none | rc21 - 6-phase scenario-loaded contracts + Rule G-10 (parallel-Linux-scripts) + Rule G-11 (phase-contract allocation coherence) |
| ADR-0099 | accepted | yaml | none | rc22 - L1 Architecture Depth & Grounding (Rule G-1.1) + Tree-Parser + SPI-Appendix-Scanner |
| ADR-0100 | accepted | yaml | none | rc22 - agent-service L1 runtime-role decomposition (5-component model + Run<=Task<=Session lifecycle + 3 new SPIs + Yield/SuspendSignal coexistence) |
| ADR-0101 | accepted | yaml | none | rc22 - Polymorphic Deployment Topology (Mode A Platform-Centric + Mode B Business-Centric) + deployment-loci SSOT |
| ADR-0102 | accepted | yaml | none | rc22 - Evolution Plane Online/Offline Duality (Offline T+1 + Online Dual-Track Fast/Slow) + 2x2 Mode x Modality matrix |
| ADR-0103 | accepted | yaml | none | rc22 - agent-middleware naming resolution (REJECT rename + REJECT 7th module) + Capability-Services distribution across the six modules |
| ADR-0104 | accepted | yaml | none | rc22 - Package-root migration (decision; rc22.5 executes) |
| ADR-0105 | accepted | yaml | none | rc32 - residual corrective + F-bulk-scrub-orphan-syntax registration + family-surface sanitizer glob expansion + per-rule timeout bump |
| ADR-0106 | accepted | yaml | none | Run.version field - two-phase W1.5 + W2 migration to avoid in-flight CAS gap |
| ADR-0107 | accepted | yaml | none | Federation acyclicity - central RunRegistry ancestor reconstruction (caller list advisory) |
| ADR-0108 | accepted | yaml | none | Tenant re-authorization widening (read + resume) + GraphMemoryRepository tenant-scoped traversal |
| ADR-0109 | accepted | yaml | none | S2C callback + ingress envelope - server-identity proof (mTLS fingerprint or signed JWT) |
| ADR-0110 | accepted | yaml | none | Audit log tamper-evidence (per-tenant hash-chain + Merkle anchor) + Hook PII failsafe carve-out via @SafetyCritical |
| ADR-0111 | accepted | yaml | none | Sandbox W2-W3 startup gate + Vault rotation policy + OTLP per-tenant binding + Outbox replay safety |
| ADR-0112 | accepted | yaml | none | Engine stateless executor - value-based yield (no checked exception); A2A InterruptType <-> SuspendReason mapping |
| ADR-0113 | accepted | yaml | none | Hook chain - two-level failure semantics (chain fail-fast + invocation failsafe) + @Order tie-break determinism |
| ADR-0114 | accepted | yaml | none | Implementation feasibility batched closures (10 R1-feasibility findings) |
| ADR-0115 | accepted | yaml | none | agent-service L1 expansion acceptance - dual modes, 4-layer state, Dual-Track router, A2A as protocol boundary |
| ADR-0116 | accepted | yaml | none | rc36 exhaustive-audit corrective: kernel-truth gate un-deadening + cancel-CAS + cross-authority alignment |
| ADR-0117 | accepted | yaml | none | rc37 strategic repositioning: Ascend/Kunpeng hardware-synergy platform; drop FSI as lead vertical; resolve brand identity |
| ADR-0118 | accepted | yaml | none | rc38 audit-corrective: parallel-review latent-correctness + deploy-packaging fixes; register the non-atomic-run-status-write family |
| ADR-0119 | accepted | yaml | none | Single-Source Rendering for derived architecture documents (release notes, READMEs, contract catalog, recurring-families.md, L1 ARCHITECTURE.md): Rule G-13 + Falcon 11-wave roadmap |
| ADR-0120 | accepted | yaml | none | Brand & Audience B alignment: KEEP spring-ai-ascend identity and Audience B promise; close the L0 agentic-primitive contract gap |
| ADR-0121 | accepted | yaml | none | ModelGateway SPI: tenant-scoped, hook-bound, pure-Java ModelResponse invoke(ModelInvocation) shape; Spring AI ChatModel is the reference adapter |
| ADR-0122 | accepted | yaml | none | Tool vs Skill semantic resolution: Tool is a SkillKind enum value; one unified Skill SPI and registry; Spring AI ToolCallback is one Skill adapter |
| ADR-0123 | accepted | yaml | none | Memory unified SPI: MemoryStore<K, V> parameterized by MemoryCategory (M1-M6); CQRS split (MemoryReader / MemoryWriter); M3 SemanticMemoryStore + M5 KnowledgeMemoryStore marker interfaces land alongside existing M2/M4 |
| ADR-0124 | accepted | yaml | none | VectorStore / Retriever / EmbeddingModel SPIs: tenant-scoped decorators of Spring AI types; RAG becomes a first-class platform extension seam |
| ADR-0125 | accepted | yaml | none | Spring AI integration boundary: Spring AI is the canonical Model / Tool / Vector / Embedding abstraction; platform SPIs are thin decorators |
| ADR-0126 | accepted | yaml | none | Planner SPI: Planner.plan(PlanningRequest) returning a Plan DAG (steps, dependencies, branch points, loop annotations); distinguishes planner OUTPUT from plan-projection.v1.yaml scheduler INPUT |
| ADR-0127 | accepted | yaml | none | Skill SPI: unified lifecycle interface with SkillKind discriminator; Spring AI ToolCallback adapts via Wave C1; reuses existing SkillCapacityRegistry + ResilienceContract |
| ADR-0128 | accepted | yaml | none | Agent first-class SPI: Agent binds (identity, modelBinding, toolBindings, memoryBindings, systemPrompt, safetyPolicy); AgentDefinition + AgentRegistry complete the entry-point surface |
| ADR-0129 | accepted | yaml | none | Streaming-aware ModelGateway: pure-Java Stream<ModelResponseChunk> default; Spring AI ChatModel.stream(...) is the reference adapter source |
| ADR-0130 | accepted | yaml | none | StructuredOutputConverter<T> SPI: pure-Java generic converter shape; Spring AI BeanOutputConverter is the reference adapter source |
| ADR-0131 | accepted | yaml | none | PromptTemplate SPI: tenant-scoped, sealed-source, pure-Java template-rendering boundary; Spring AI PromptTemplate is the reference adapter |
| ADR-0132 | accepted | yaml | none | ChatAdvisor SPI: tenant-scoped, around-call interceptor chain over ModelGateway; HookDispatcher stays platform-internal |
| ADR-0133 | accepted | yaml | none | ConversationMemory: MemoryStore<String, ConversationWindow> variant for windowed FIFO + token-budget pruning + summarise-and-compact |
| ADR-0134 | accepted | yaml | none | Tool-Call Iteration Loop: agent-driven vs. planner-driven execution modes for LLM <-> Tool <-> LLM iteration |
| ADR-0135 | accepted | yaml | none | AgentSession as Run-Projection: no separate AgentSession SPI; conversation continuity is a (tenantId, conversationId) projection over the Run sequence + M2_EPISODIC ConversationMemory |
| ADR-0136 | accepted | yaml | none | Vocabulary Reconciliation: PR 71 'Task' is the existing platform Task entity (not Run alias); Run / Task / Session / Memory 4-layer hierarchy preserved per ADR-0100 |
| ADR-0137 | accepted | yaml | none | SuspendSignal remains canonical; InterruptSignal / InterruptReason are L1 glossary synonyms only (no Java rename per ADR-0100 rejected-framings) |
| ADR-0138 | accepted | yaml | none | agent-service 5-layer L1 ratification: PR 71 layers (Access / Session&Task / EventQueue / Task-Centric / EngineAdapter) <-> ADR-0100 components (Dispatcher / Orchestrator / TaskCenter / SessionManager / EngineAdapter) + Run<=Task<=Session<=Memory lifecycle; reject single-queue + 3-mode; require tenantId-first ER + cancel-race-aware state machine |
| ADR-0139 | accepted | yaml | none | Fast-Path / Slow-Path narrowed semantics: Fast-Path = in-process reactive synchronous + tenantId/RLS preserved + metadata persistence mandatory; Slow-Path = persistent reactive + SuspendSignal + ResumeDispatcher; neither path may violate Rule R-G / R-H / R-J.a |
| ADR-0140 | accepted | yaml | none | Engine Adapter Layer split: Layer 5 in ADR-0138 5-layer model decomposes into Layer 5a (Engine Dispatch & Execution: EngineRegistry + ExecutorAdapter impls) and Layer 5b (Translation & Tool-Intercept: ContextProjector + PromptTemplate + StructuredOutputConverter + ChatAdvisor); RuntimeMiddleware moves exclusively into Layer 4 Control |
| ADR-0141 | accepted | yaml | none | Internal Event Queue (ADR-0138 Layer 3) is a design_only sub-section of agent-service L1 until a service.queue/ sub-package + Boundary Contract land; current state shows the Layer 3 binding-only role over the three-track bus; no peer-layer code home exists at rc55 |
| ADR-0142 | accepted | yaml | none | Run aggregate (Run record + RunStatus + RunStateMachine + RunRepository) is OWNED EXCLUSIVELY by Layer 2 Session & Task Manager in the ADR-0138 5-layer L1 model; Layer 4 Task-Centric Control holds a typed reference and invokes RunRepository.updateIfNotTerminal(...) - Layer 4 never writes Run state directly |
| ADR-0143 | accepted | yaml | none | rc53 review file 2026-05-26-agent-service-l1-4plus1-rewrite-wave-1.{en,cn}.md is DEMOTED from canonical L1 4+1 source to historical authoring record; canonical 4+1 source moves to docs/L1/agent-service/{scenarios,logical,process,physical,development,spi-appendix}.md; agent-service/ARCHITECTURE.md section 0.5 rewritten to point at the new canonical home |
| ADR-0144 | accepted | yaml | none | Layer <-> Package matrix unifies the rc22 ADR-0100 5-component package-structural decomposition (Dispatcher/Orchestrator/Task/Session/Engine) with the rc53 ADR-0138 5-layer logical-view decomposition (Access/Manager/Queue/Control/Adapter); the two are NOT competing - they describe orthogonal views and the matrix is the canonical mapping |
| ADR-0145 | accepted | yaml | none | Sealed RunEvent hierarchy specification: defines the polymorphic event type that EvolutionExport enum was designed to discriminate; specifies sealed variants required by S1-S5 scenarios; promotes Rule R-M.e (Every emitted RunEvent declares EvolutionExport) from vacuously-true to actively-gated; the actual Java sealed type lands in a follow-up impl-mode wave |
| ADR-0146 | accepted | yaml | none | SuspendReason taxonomy canonical alignment to 2026-05-22 expansion-proposal-response mapping table: ratifies the 6-variant set {AwaitClientCallback, AwaitChildRun, AwaitToolResult, AwaitTimer, RequiresApproval, RateLimited} as the L1 authority; reconciles prior naming drift across ADR-0019/0070/0074 and ADR-0112 Part B; applies the audit's user-directive precedence rule (doc > ADR) to settle the drift |
| ADR-0147 | accepted | yaml | none | Structurizr workspace closure as the architecture authoring root, with programmatic mounting |
| ADR-0148 | accepted | yaml | none | Wave 0 spike results - Structurizr workspace authority feasibility |
| ADR-0149 | accepted | yaml | none | Structurizr workspace authority - W0..W5 shipped; W6/W7 entry-criteria documented |
| ADR-0150 | accepted | yaml | none | Architecture design system unified under architecture/ - L1 corpus + module ARCHITECTURE.md consolidated; docs/ is work directory [supersedes ADR-0143] |
| ADR-0151 | accepted | yaml | none | L1 Feature Registry canonical schema - SAA Feature tag + AI Execution Boundary + 9-state lifecycle |
| ADR-0152 | accepted | yaml | none | Uniform L1 per-view mechanism + L0 mounting under architecture/docs/L0/ |
| ADR-0153 | accepted | yaml | none | L1 Feature Registry closure - Rule G-14 blocking flip + plan completion |
| ADR-0154 | accepted | yaml | none | Fact-Layer Authority - generated structured facts as the AI's primary L1 input |
| ADR-0155 | accepted | yaml | none | AgentService L1 v1.2 - internal module design absorption (M1-M6 + 6 boundary reversals) |
| ADR-0156 | accepted | yaml | none | Product Authority and Traceability Chain - ProductClaim as the binding axis between product, architecture, and governance |
| ADR-0157 | accepted | yaml | none | EngineeringFrame Ontology - structural axis between Module and FunctionPoint for dual-track architecture |
| ADR-0158 | accepted | yaml | none | Engine Boundary (EnginePort) - transport-agnostic Service <-> Engine contract absorbed into agent-bus for the three deployment forms |

## Tallies (from the fact source)

- ADRs in `adrs.json`: **88** (IDs ADR-0068 .. ADR-0158).
- Format: **yaml = 88**, md = 0 (the extractor parses YAML source only).
- Status: **accepted = 87**, **superseded = 1** (ADR-0079, superseded_by ADR-0088).
- Normalized views present: **0** (none exist yet; column is `none` for all rows).
- Numbering gaps within the JSON range: **ADR-0083, ADR-0084, ADR-0085** (superseded
  by ADR-0086, archived; not in the fact layer - expected).
- Markdown-format ADRs NOT in the fact layer (advisory, see "Scope" above):
  56 active/deferred (`docs/adr/*.md`) + 11 locked (`docs/adr/locked/*.md`).

## Regeneration

This census is regenerated, not hand-amended. To refresh it, re-read
`architecture/facts/generated/adrs.json` (after re-running `AdrFactExtractor` if the
ADR sources changed) and re-emit the table above; never edit the JSON by hand
(`_banner`: "DO NOT EDIT - generated by tools/architecture-workspace#AdrFactExtractor").
The *Normalized view* column should be flipped from `none` to the view path on any
ADR that gains a generated normalized view in a later wave.
