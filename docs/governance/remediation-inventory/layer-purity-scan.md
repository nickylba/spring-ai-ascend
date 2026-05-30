---
level: L0
view: scenarios
status: advisory
governance_infra: true
scan_date: 2026-05-30
scanner: governance/progressive-learning-curve-remediation Wave W0
rescan_date: 2026-05-30
rescanner: governance/progressive-learning-curve-remediation Wave R2
rescan_note: "W0 §1.2 baseline re-scanned after the W12 L0 in-place drains; 7 §0.5.3/§4 rows reclassified LEAKED→drained (see §1.5). Corroborated by no-loss-audit.md §1."
authority_refs: [ADR-0068, ADR-0143, ADR-0150]
verdict_source: "Adjudicated verdict — L0/L1 leak critique TRUE (see Summary §0)"
---

# Layer-Purity Scan — L2/Code Detail Leaked into L0 + L1 Docs

> **What this is.** An advisory, line-referenced inventory of concrete L2 /
> code-level detail that currently lives in the L0 root `ARCHITECTURE.md`
> and the L1 module `ARCHITECTURE.md` corpus, classified **LEAKED** vs
> **DEFENSIBLE** against the adjudicated verdict (§0). It is the W0
> baseline-truth input for the layer-purity migration; it does NOT itself
> move any content, edit any authority surface, or invent IDs or
> relationships. The authority cascade (generated facts > DSL >
> Card/prose) is untouched by this document.
>
> **What this is NOT.** A migration plan, an ADR, or a gate. It records
> WHERE the leaks are and WHY each is leaked-vs-defensible. The actual
> migration of LEAKED rows to `architecture/docs/L2/` /
> `docs/contracts/*.v1.yaml`, and any frontmatter/gate change, are
> downstream waves and downstream subtasks.
>
> **How to read a row.** Each row cites `file:line` (or a line range) and
> the smallest verbatim trigger phrase that makes it L2/code detail. A row
> is **LEAKED** only if it is one of the verdict's leaked categories AND is
> not redeemed by a verdict-defensible framing in the same block.
>
> **Re-scan supersession (2026-05-30).** §1.2 records the **W0 baseline** and
> was NOT re-run after the W12 L0 in-place drains. Seven of its rows (§0.5.3,
> §4 #3/#4/#16/#20/#28/#37) have since been **drained in place at L0** and are
> re-classified into **§1.5 — REMEDIATED IN PLACE (re-scanned)**, which also
> corrects the §4 constraint **line map** (the numbering shifted after the
> edits) and the §4 migration **priority ranking**. Before copying ANY §1.2
> row into a downstream grandfather list, check §1.5 first — a struck-through
> (`~~…~~`) §1.2 row is drained, not live.

---

## §0 — Adjudicated verdict (classification rubric)

The "L0/L1 contains L2/code detail" critique is **TRUE**. The L0 root
declares (in §0.6) that it carries no runtime contracts / wire shapes /
SPI signatures, yet §0.5.3 and several §4 constraints do; the L1
`agent-service` corpus carries SQL / RLS / GUC / HTTP-status /
filter-order / method-CAS detail and a literal "L2 zone" section.

**DEFENSIBLE at L0/L1 (keep):**

- D1 — Naming a public SPI **as a boundary identity** (FQN + one-line role),
  WITHOUT inlining its method bodies, call chains, or wire payloads.
- D2 — Development-view **package / module decomposition** (which package
  owns which concern).
- D3 — Citing an **ArchUnit enforcer / gate rule** as the *mechanism* that
  guards a constraint (the enforcer name, not the runtime behaviour it
  asserts).

**LEAKED (migrate to L2 / `docs/contracts/`):**

- L1 — **Method call chains** and inter-method dispatch sequences.
- L2 — **Runtime sequences** (sequence diagrams, race orderings, "winner /
  loser" CAS narratives).
- L3 — **SQL / RLS / GUC / persistence** detail (`SET LOCAL`, `ON CONFLICT`,
  RLS policy wiring, migration-file names, `WHERE status NOT IN (...)`).
- L4 — **HTTP status / route-verb / header behaviour** (201/400/403/404/409/422,
  verb-on-route semantics, header parse/emit rules).
- L5 — **Filter ordering** (numeric `order` values, chain position rules).
- L6 — **Wire formats** (OTLP/HTTP, attribute namespaces, sampling rates).
- L7 — **Method signatures** (parameter lists, return types, JVM-level shapes).
- L8 — **Test-class inventories** (named test classes asserting runtime behaviour).

A single block can carry both a defensible identity statement AND a leaked
detail; such blocks are listed once, tagged with the leaked category, with
a note that the identity portion is the part that may stay.

---

## §1 — L0 root: `architecture/docs/L0/ARCHITECTURE.md`

### 1.1 Self-declared scope (the claim the leaks contradict)

| line | Verbatim trigger | Status | Note |
|---|---|---|---|
| 36 | "It states what the platform commits to STRUCTURALLY. It does NOT carry:" | self-claim | The §0.6 rhetorical-stance promise. |
| 39 | "Runtime contracts (wire shapes, route behavior, SPI signatures) — read … contract-catalog.md for that." | self-claim | The §0.6 promise that the §1.2 LEAKED rows broke at W0 baseline. The 2026-05-30 re-scan (§1.5) confirms the seven §0.5.3 / §4 telemetry-wire-and-runtime rows that broke it most heavily are now drained in place, so for those loci the promise now holds; the residue still tracked LEAKED in §1.2 (§4 #19, #23 — verified still present) keeps a narrower breach open. |

### 1.2 LEAKED rows

> **Re-scan supersession (2026-05-30 — see §1.5).** The rows in this table
> were captured at the **W0 baseline** and were NOT re-run after the W12 L0
> in-place drains (`no-loss-audit.md` §1). Seven rows below — §0.5.3, §4 #3,
> §4 #4, §4 #16, §4 #20, §4 #28, §4 #37 — are **STALE: their leaked content
> has since been drained in place at L0** and the loci are now leak-free.
> They were re-classified out of LEAKED into **§1.5 — REMEDIATED IN PLACE
> (re-scanned)** with the verified current line/constraint map; do NOT copy
> them into any downstream grandfather list. The remaining rows (§4 #19, #23
> verified still present at L0; §4 #41/#44 left at their W0 classification by
> this targeted re-scan) stay LEAKED. The strike-through marker `~~…~~` on a
> row below means the re-scan drained it; its live home is in §1.5.

| file:line | Verbatim trigger | Category | Why leaked |
|---|---|---|---|
| ~~L0 §0.5.3 : 99~~ → **DRAINED, see §1.5** | "Wire format: OTLP/HTTP (Langfuse-compatible attribute namespace `gen_ai.*` + `langfuse.*`) … Sampling is posture-aware (dev=100 %, research=10 %, prod=1 % head + tail-on-error at W4)." | ~~L6 wire format~~ | **Re-scanned 2026-05-30: DRAINED in place.** L0 §0.5.3 line 99 now carries only the structural statement; line 101 delegates wire/sink/sampling to `../L2/telemetry-vertical/` + `docs/telemetry/policy.md`. No longer a leak. Migrated home recorded in §1.5. |
| ~~L0 §4 #3 : 360~~ → **DRAINED, see §1.5** | "add `SET LOCAL app.tenant_id = :id` GUC inside each transaction; enable Postgres RLS policies on tenant tables." | ~~L3 SQL/RLS/GUC~~ | **Re-scanned 2026-05-30: DRAINED in place.** §4 #3 (now lines 350-370) keeps only the tenant-isolation invariant; the `SET LOCAL` GUC + RLS policy bodies are delegated to `docs/security/rls-policy.sql` + `../L2/fp-tenant-cross-check/`. No longer a leak. |
| ~~L0 §4 #4 : 364-368~~ → **DRAINED, see §1.5** | "validates the `Idempotency-Key` header … missing returns 400 … concurrent duplicate returns 409; backed by Postgres `idempotency_dedup` table." | ~~L4 HTTP status + L3 persistence~~ | **Re-scanned 2026-05-30: DRAINED in place.** §4 #4 (now lines 375-390) keeps only the at-most-once-admission invariant; the header / 400 / 409 / dedup table are delegated to `contract-op/createrun` + `../L2/fp-idempotency-claim/`. No longer a leak. |
| ~~L0 §4 #16 : 471-487~~ → **DRAINED, see §1.5** | "the chain is ordered (registration order; lower `@Order` fires earlier; `BEFORE_*` ascending, `AFTER_*` reverse — LIFO unwind) … `@Order` tie-breaking … resolved by `Class.getName()` lexicographic order" | ~~L5 ordering + L1 call chain~~ | **Re-scanned 2026-05-30: DRAINED in place.** §4 #16 (now ~lines 490-514) keeps only the universal-interception + position-contract invariant; the firing order + `Class.getName()` tie-break are delegated to `docs/contracts/engine-hooks.v1.yaml` + `../L2/fp-hook-dispatch/`. No longer a leak. |
| L0 §4 #19 : 510-517 | "`ChildRun(UUID childRunId, ChildFailurePolicy, Instant deadline)` \| `AwaitChildren(List<UUID> …)` …" | L7 method signature | Full sealed-variant constructor parameter lists with JVM types — a signature catalog. Naming `SuspendReason` as a boundary is defensible; the parameter lists are L2. **(Re-scan 2026-05-30: still present at L0 lines 537-538 — stays LEAKED.)** |
| ~~L0 §4 #20 : 520-535~~ → **DRAINED, see §1.5** | "`PENDING → RUNNING \| CANCELLED`; … Every `Run.withStatus(newStatus)` MUST invoke `RunStateMachine.validate(from, to)` … `cancel` on already-cancelled run returns 200 … returns 409 … `persisted.version != run.version() - 1`" | ~~L1 call chain + L2 sequence + L4 status + L3 CAS~~ | **Re-scanned 2026-05-30: DRAINED in place.** §4 #20 (now lines 547-565) keeps only the formal-DFA / atomic-advance / audited-lifecycle invariant; the transition table, the `validate` validator hop, the `updateIfNotTerminal` CAS, the optimistic-version migration, and the cancel status codes are delegated to `../L2/fp-run-state-transition/` + `contract-op/cancelrun`. No longer a leak (was the W0 "heaviest L0 leak"). |
| L0 §4 #23 : 596-601 | "`RunRepository.save(suspended)` and `checkpointer.save(runId, nodeKey, payload)` MUST be observable atomically … single-threaded, sequential on same call stack (invariant documented in `SyncOrchestrator.executeLoop` javadoc) … both in one `@Transactional` block … transactional outbox" | L1 call chain + L2 sequence | The named persistence-write hops (`RunRepository.save` + `checkpointer.save`) and the tiered atomicity ordering ("observable atomically", "sequential on same call stack", "one `@Transactional` block", "transactional outbox") are a runtime write-sequence. The "the suspension write is atomic" *invariant* is the defensible L0 commitment; the method hops + the per-tier atomicity mechanism are L2. (The line-43 disclaimer names this as "the suspension-atomicity sequence (§4 #23)"; this row supplies the LEAKED inventory that named disclosure presumed.) |
| ~~L0 §4 #28 : 614-616~~ → **DRAINED, see §1.5** | "bounded buffer (default 64 events, DROP_OLDEST overflow — Terminal events never dropped)" | ~~L1/L6 runtime detail~~ | **Re-scanned 2026-05-30: DRAINED in place.** §4 #28 (now lines 641-654) keeps only the physical three-track-isolation invariant ("bounded buffering under which terminal events are never dropped" as a property, not a value); the buffer depth, the overflow strategy, and the cadence bound are delegated to the deferred W2 streaming contract surface + its L2 detail. No longer a leak. |
| ~~L0 §4 #37 : 702-710~~ → **DRAINED + RENUMBERED, see §1.5** | "`X-Tenant-Id` header stays required … (403 on mismatch). The initial run status is `PENDING` … `POST /v1/runs/{id}/cancel` (not `DELETE`)" | ~~L4 HTTP status + route-verb~~ | **Re-scanned 2026-05-30: DRAINED in place + line map stale.** The constraint is still numbered #37 but moved to lines 736-748, where it keeps only the cross-checked-not-replaced / DFA-initial-status / cancel-is-a-transition invariant; verbs, routes, status codes, header names are delegated to the OpenAPI surface (`contract-op/createrun`/`getrun`/`cancelrun`) + `../L2/run-http-contract/` + `docs/contracts/http-api-contracts.md`. **The old line citation `702-710` is now stale: line 702 is a DIFFERENT constraint — #33 "Contract-surface truth"** (the §4 numbering map shifted after the in-place edits). No longer a leak. |
| L0 §4 #41 : 732-737 | "`RunContext` is classified as `interface` (not `record`); … `embeddingModelVersion` is the canonical field name … Gate Rule 17 is extended to verify `RunContext` is labeled \"interface\" in the catalog." | L7 signature/shape | SPI Java-shape classification + canonical field name — this is contract-catalog precision, not an L0 structural commitment. Verdict cites #41 explicitly. (The gate-rule citation portion is D3-defensible; the type-shape assertion is the leak.) **(Re-scan 2026-05-30: left at W0 classification — out of this targeted residual's scope; `no-loss-audit.md` §1.3 treats the type-kind/field-name as FACT-OWNED at `code-symbol/com-huawei-ascend-bus-spi-engine-runcontext`. The line citation `732-737` predates the W12 edits.)** |
| L0 §4 #44 : 755-768 | "Method lists on shipped SPIs must be a subset of the canonical interface (e.g. `RunContext` is `runId`/`tenantId`/`checkpointer`/`suspendForChild` — `posture()` is forbidden …)" | L7 method signature | Enumerated SPI method lists + forbidden method name — a signature inventory embedded in a release-note-truth constraint. Verdict cites #44 explicitly. **(Re-scan 2026-05-30: left at W0 classification — out of this targeted residual's scope; `no-loss-audit.md` §1.4 treats the method set as FACT-OWNED via the `RunContext` fact's `public_methods`. The line citation `755-768` predates the W12 edits.)** |

### 1.3 DEFENSIBLE rows (keep at L0)

| file:line | Verbatim trigger | Defensible-class | Note |
|---|---|---|---|
| L0 §2 : 148-157 | Module → plane → owner → maturity table | D2 package/module decomposition | Development-view module layout; correct at L0. |
| L0 §2 : 257-276 | "Module dependency direction (enforced by `ApiCompatibilityTest`, `RuntimeMustNotDependOnPlatformTest`, …)" | D3 enforcer citation | Cites the ArchUnit enforcers as the mechanism; the direction is structural. |
| L0 §0.7 : 49-53 | "§4 #20 … ↔ Rule R-C.2 … enforcers E2, E58." | D3 enforcer citation | Constraint↔rule↔enforcer mapping (identity, not behaviour). |
| L0 §4 #7 : 379-385 | "`com.huawei.ascend.middleware..spi..` imports only `java.*` … pins it in `SpiPurityGeneralizedArchTest`." | D1 SPI identity + D3 enforcer | Names the SPI purity boundary + the enforcer; no method bodies. |
| L0 §4 #32 : 654-662 | "All posture reads MUST flow through `AppPostureGate.requireDevForInMemoryComponent(...)` … Gate Rule 12 enforces …" | D1 boundary + D3 enforcer | Single-construction-path boundary identity + gate citation. (Borderline: the specific method name leans toward L7; treat the *single-path constraint* as the durable part.) |

### 1.5 REMEDIATED IN PLACE (re-scanned 2026-05-30) — formerly LEAKED, now drained at L0

> **Why this subsection exists.** §1.2 is the declared **baseline-truth
> input** that every downstream grandfather row is copied from, but it was
> captured at the **W0 baseline** and **not re-run after the W12 L0 in-place
> drains** (`no-loss-audit.md` §1, audit_date 2026-05-30). The stale baseline
> therefore reported already-drained content as live leaks **and** mis-stated
> the §4 constraint line map (the §4 numbering shifted after the in-place
> edits — e.g. old line 702, cited as §4 #37, now lands on constraint #33).
> This subsection re-scans those loci against the **live** L0 root
> (`architecture/docs/L0/ARCHITECTURE.md`, now **912 lines** — down from the
> 1022 the §4 method note recorded at W0) and re-classifies the seven drained
> rows out of LEAKED. The drain itself was performed by the layer-purity
> migration (W12); this is a re-scan of the result, not a new migration — it
> moves no content and invents no IDs.

Each row: the W0 LEAKED citation, the **current** L0 locus (verified by direct
read of the live file), and the authoritative L2 / contract / fact home the
drained detail now points at (cross-checked against `no-loss-audit.md`).

| W0 LEAKED citation | Current L0 locus (verified live) | What the live L0 now states | Drained detail's authoritative home |
|---|---|---|---|
| §0.5.3 : 99 (L6 wire) | §0.5.3, **line 99** (structural statement) + **line 101** (delegation) | The named telemetry vertical + single `TraceContext` carrier + 3 boundary entities — structural only; line 101 explicitly delegates "export/sink mechanics, attribute namespaces, the propagation handshake, MDC field shapes, per-posture sampling, the reference-hook inventory". | `../L2/telemetry-vertical/` (required by Rule G-27); SSOT `docs/telemetry/policy.md`. (`no-loss-audit.md` §1.1: OTLP/HTTP, `gen_ai.*`/`langfuse.*`, sampling — all MIGRATED.) |
| §4 #3 : 360 (L3 SQL/RLS/GUC) | §4 #3, **lines 350-370** | The end-to-end tenant-isolation invariant only — "L0 owns this invariant, not the persistence mechanism, the transaction-scoping statement, the row-isolation policy, or the header name". | `docs/security/rls-policy.sql` (design-only, W2 schema migration) + `../L2/fp-tenant-cross-check/`; FunctionPoint `FP-TENANT-CROSS-CHECK`. |
| §4 #4 : 364-368 (L4 status + L3 persistence) | §4 #4, **lines 375-390** | The at-most-once-admission invariant + boundary identities only; "The header name, the mutating verbs …, the malformed-key and duplicate-conflict status codes, the durable claim row, and its composite key are runtime-contract + persistence facts below L0". | `contract-op/createrun` (`docs/contracts/openapi-v1.yaml`) + `../L2/fp-idempotency-claim/` (ADR-0057). |
| §4 #16 : 471-487 (L5 ordering + L1 chain) | §4 #16, **~lines 490-514** | The universal-interception invariant + position contract only; "the concrete chain firing order, the deterministic tie-break, the two-level fail-fast … are runtime mechanics". | `docs/contracts/engine-hooks.v1.yaml` + `../L2/fp-hook-dispatch/`; proven by test fact `test/com-huawei-ascend-middleware-hookdispatcherfireordertest`. |
| §4 #20 : 520-535 (heaviest W0 leak) | §4 #20, **lines 547-565** | The formal-DFA / atomic-advance / audited-lifecycle invariant only; "L0 owns this invariant, not the transition table, the validator method contract, the cancel-idempotency status codes, or the optimistic-lock realization". | `../L2/fp-run-state-transition/` (ADR-0118 atomic CAS + ADR-0142 single owner); fact `code-symbol/…-runstatemachine`, `code-symbol/…-runrepository#updateIfNotTerminal`; `contract-op/cancelrun`; test fact `test/com-huawei-ascend-service-runtime-architecture-runrepositoryatomiccontracttest`. |
| §4 #28 : 614-616 (L1/L6 runtime) | §4 #28, **lines 641-654** | The physical three-track-isolation invariant only (terminal-events-never-dropped stated as a property, not a `64`/`DROP_OLDEST` value); "L0 owns this physical-isolation invariant, not the buffer depth, the overflow strategy, the cadence bound, or the track method/type signatures". | Deferred W2 streaming contract surface + its L2 detail; capability-ledger keys `three_track_channel_isolation`, `run_dispatcher_spi`. |
| §4 #37 : 702-710 (L4 status + verb) **— line map stale** | §4 #37, **lines 736-748** (constraint kept its number; **old line 702 is now constraint #33 "Contract-surface truth"**) | The cross-checked-not-replaced / DFA-initial-status / cancel-is-a-transition invariant only; "L0 owns the invariant, not the wire detail. The verbs, routes, status codes, header names, and initial `RunStatus` value are runtime-contract facts below L0". | OpenAPI surface (`contract-op/createrun`/`getrun`/`cancelrun`) + `../L2/run-http-contract/` + `docs/contracts/http-api-contracts.md`. (`no-loss-audit.md` §1.2: `X-Tenant-Id`/403, initial `PENDING`, POST-not-DELETE — all MIGRATED.) |

**Net effect on the priority ranking (§4):** the two rows the W0 scan ranked
as the #1 and #2 migration priorities — **§4 #20** and **§0.5.3** — are both
in this drained set, so the §4 ranking below is corrected to remove them
(see §4). **No L2 sink referenced above is missing**: all of
`../L2/{telemetry-vertical, fp-run-state-transition, fp-idempotency-claim,
fp-tenant-cross-check, fp-hook-dispatch, run-http-contract}/` exist on disk
(verified 2026-05-30).

**Caveat (scope of this re-scan).** This re-scan covers exactly the seven
loci above. §4 #19 and §4 #23 were re-checked and are **still present** at
the live L0 (lines 537-538 and 596-597 respectively) — they stay LEAKED in
§1.2. §4 #41 and §4 #44 were **left at their W0 classification** (out of this
targeted residual's scope); `no-loss-audit.md` §1.3/§1.4 treats their
type-shape / method-set as FACT-OWNED, so a future re-scan may drain those
rows too. No other §1.2 / §2 / §3 row was re-evaluated here.

---

## §2 — L1 `agent-service` (the verdict's primary L1 leak site)

The canonical L1 4+1 source for this module is the per-view file set under
`architecture/docs/L1/agent-service/` (per ADR-0143); the sibling
`architecture/docs/L1/agent-service/ARCHITECTURE.md` is the scenarios-view +
shipped-state-grounding companion. Both carry leaks. Rows below are grouped
by file.

### 2.1 `architecture/docs/L1/agent-service/ARCHITECTURE.md` — LEAKED

| file:line | Verbatim trigger | Category | Why leaked |
|---|---|---|---|
| §2.A idempotency : 71-74 | "hashes `method:path:body` (SHA-256 → base64url) … Collisions return 409 `idempotency_conflict` … or 409 `idempotency_body_drift`" | L3 hashing + L4 status | Hash algorithm + encoding + HTTP 409 error codes. |
| §2.A idempotency : 78-83 | "INSERT … ON CONFLICT (tenant_id, idempotency_key) DO NOTHING; SELECT on collision. Flyway `V2__idempotency_dedup.sql` … CHECK constraint on `status` (CLAIMED\|COMPLETED\|FAILED)." | L3 SQL/persistence | Literal SQL upsert + migration-file name + CHECK constraint enum. |
| §2.A runs API : 164-181 | "`POST /v1/runs` … → 201 … `GET /v1/runs/{runId}` → 200 … 404 … `POST /v1/runs/{runId}/cancel` → 200 … 409 `illegal_state_transition` … Cancellation is POST, never DELETE … → 422 `invalid_run_spec` … → 400 `invalid_request` … → 500" | L4 HTTP status + route-verb | Full route × verb × status-code matrix + error-code strings. |
| §2.A tenant cross-check : 128-134 | "`JwtTenantClaimCrossCheck` (L1, order 15 — after Spring Security's `BearerTokenAuthenticationFilter`, before `TenantContextFilter` at 20) … Mismatch → 403 `tenant_mismatch`" | L5 filter order + L4 status | Numeric filter order + 403 behaviour. |
| §2.A observability : 199-218 | "`TraceExtractFilter` … runs at order 10 … parses the W3C version-00 `traceparent` header … emits `traceresponse: 00-<trace_id>-<span_id>-01` … Filter chain order (L1.x): 1. … (order 10) 2. … (order 15) 3. … (order 20) 4. …" | L5 filter order + L4 header behaviour | Full numeric filter-chain ordering + traceparent/traceresponse wire parsing/emission. |
| §7 Tests : 509-549 | "`HealthEndpointIT` … `RunHttpContractIT` … `RunStateMachineTest` … `RunCancelDuringResumeRaceIT` …" (named test-class inventory with asserted runtime behaviour) | L8 test inventory | A ~40-row test-class catalog with per-test runtime assertions. |
| §11.2 SPI : 698-701 | "`Execute(TaskMetadata, InjectedContext) → StateDelta` … Projects a `SessionContext` view …" | L7 method signature | SPI method signatures with parameter/return types. |
| *L2 Constraint Linkage* : 797-799 | "Postgres RLS migration sequence (rc25)." (named under an in-file "L2 Constraint Linkage" heading) | L3 persistence (forward-ref) | Even as a forward pointer, names the RLS migration sequence inline. (The *delegation* framing is defensible; the named persistence sequence is the leak.) |

### 2.1.1 `agent-service/ARCHITECTURE.md` — DEFENSIBLE

| file:line | Verbatim trigger | Defensible-class | Note |
|---|---|---|---|
| §12 dev view : 724-758 | Target directory tree (package decomposition) | D2 package decomposition | Development-view package tree; correct at L1. |
| *SPI Appendix* : 771-781 | "`com.huawei.ascend.service.runtime.runs.spi.RunRepository` … `service.runtime.runs.spi` … Run persistence …" | D1 SPI identity | FQN + package + one-line role; no method bodies. |
| §2.B engine : 356-367 | "Every Run dispatch goes through `EngineRegistry.resolve(envelope)` (Rule R-M.a …) … protected by Rule 76" | D3 enforcer citation | Names the boundary + the guarding rules. (Borderline: the `resolve(envelope)` call leans L1; the durable part is "dispatch routes through the registry".) |

### 2.2 `architecture/docs/L1/agent-service/logical.md` — LEAKED

| file:line | Verbatim trigger | Category | Why leaked |
|---|---|---|---|
| 31, 39, 67 | "Run aggregate (single owner): … RunRepository (atomic CAS via updateIfNotTerminal)" / "RunStateMachine validation invoked via Layer 2 CAS" / "updateIfNotTerminal(tenantId, runId, λ) — atomic CAS" | L1 call chain + L3 CAS | The CAS method (`updateIfNotTerminal`) + the validate-inside-CAS call chain. |
| 92-96 | "Persistence under RLS (Rule R-J.a) with `RunRepository.updateIfNotTerminal(...)` atomic CAS … validation (`RunStateMachine.validate(from, to)`) is invoked" | L3 RLS + L1 call chain | RLS persistence + method-level call chain. |
| 207-209 | "Every table above carries `tenant_id` column with RLS policy enabled … `IdempotencyRecord` (`V2__idempotency_dedup.sql`) ships RLS-bound" | L3 SQL/RLS | RLS policy + migration-file name. |
| 217-262 | "## 3. RunStatus State Machine (cancel-race-aware, CAS-annotated)" — full DFA with `RunRepository.updateIfNotTerminal CAS` on every edge, `POST /v1/runs`, `POST /v1/runs/{runId}/cancel`, 409, `RunStateMachine.java:37` source ref, `WHERE status NOT IN (...)` | L2 sequence + L1 call chain + L3 CAS + L4 status | The entire annotated state machine is L2: per-edge CAS calls, route verbs, HTTP 409, a literal source-file line reference, and the SQL `WHERE` predicate. |
| 474 | "RunStateTransitionEvent … (→CANCELLED) … (CAS no-op)" | L2 runtime detail | Per-event CAS no-op semantics. |

### 2.3 `architecture/docs/L1/agent-service/process.md` — LEAKED

| file:line | Verbatim trigger | Category | Why leaked |
|---|---|---|---|
| 22, 89, 113, 165, 207, 259 | `sequenceDiagram` blocks (P1-P6) | L2 runtime sequence | Six runtime sequence diagrams — the canonical "runtime sequence" leak category. |
| 45 | "409 idempotency_conflict OR 409 idempotency_body_drift … (200 cached response branch is W2-design …)" | L4 HTTP status | Per-branch HTTP status codes. |
| 55-79 | "updateIfNotTerminal(runId, λ→RUNNING) — ATOMIC CAS … RunStateMachine.validate(PENDING, RUNNING) — atomic inside CAS … λ→SUSPENDED … λ→SUCCEEDED" | L1 call chain + L3 CAS | Method-level dispatch + CAS lambda + validate call. |
| 128-145 | "404 not_found … 403 tenant_mismatch … updateIfNotTerminal(tid_request, runId, λ … withStatus(CANCELLED)) — ATOMIC CAS … CAS WHERE status NOT IN (CANCELLED, SUCCEEDED, FAILED, EXPIRED) … 409 illegal_state_transition" | L4 status + L3 CAS/SQL + L1 call chain | Status codes + SQL `WHERE` predicate + method-CAS dispatch. |
| 259-286 | "P6" cancel-race loser sequence: "CAS WINS … LOSES because status is now SUCCEEDED … post-CAS re-read … => 409 illegal_state_transition" | L2 race sequence + L4 status | Winner/loser CAS race narrative + status code — the verdict's named method-CAS leak. |

### 2.4 `architecture/docs/L1/agent-service/development.md` — MIXED ("L2 zone" section)

The verdict names a literal "L2 zone" section in the agent-service L1
corpus; it is in this file (§5). The **delegation frame is DEFENSIBLE**
(Rule G-1.1.c publishes a Boundary Contract — inputs/outputs/DFX — and
delegates the detail to L2). The **inlined persistence detail inside the
frame is LEAKED**.

| file:line | Verbatim trigger | Status | Why |
|---|---|---|---|
| 187-189 | "## 5. L2 Boundary Contracts (Rule G-1.1.c — E168) … Five L2 zones are delegated from this L1 design." | DEFENSIBLE | Explicit delegation + enforcer citation; this is the correct way to reference L2 from L1. |
| 193, 212, 231, 251, 272 | "### 5.x L2 zone — <name> (… candidate)" headings | DEFENSIBLE | Naming the delegated zone + its DFX expectations is allowed. |
| 203-208 | "Tenant-bound RLS coverage extending to the projection tables … cross-tenant projection blocked at RLS layer" | LEAKED (L3) | RLS persistence detail inlined into the Boundary Contract. |
| 239-248 | "V?__tenant_rls.sql series creating RLS policies on all tenant_id- … SET LOCAL app.tenant_id GUC wiring via R2DBC … RLS-blocked SELECTs counted per tenant" | LEAKED (L3) | Migration-file series + `SET LOCAL` GUC + RLS SELECT-counting — concrete persistence design, not a boundary contract. |
| 101, 173 | "service.queue/ (design_only — DOES NOT EXIST on filesystem …) … Boundary Contract published at design time in ADR-0141." | DEFENSIBLE | design_only mechanism marking + ADR delegation; no leaked detail. |

### 2.5 `architecture/docs/L1/agent-service/physical.md` + `scenarios.md` + `spi-appendix.md` + `features/*`

These files also matched the leak-marker scan (RLS / 3-track bus / status
codes / SPI signatures). They are LOWER-PRIORITY than §2.2-§2.4 but carry
the same categories:

| file | Representative leaked category | Note |
|---|---|---|
| `physical.md` (matched RLS / 3-track / sandbox) | L3 persistence + deployment topology | Physical view legitimately carries deployment topology (defensible); the RLS-policy + per-table detail is the leak to extract. |
| `spi-appendix.md` (matched SPI signatures) | L1 D1-vs-L7 boundary | SPI *identities* are defensible (D1); any method-body / parameter-list detail is L7-leaked. Row-level pass deferred. |
| `scenarios.md` (matched status codes) | L4 HTTP status | Scenario narratives that quote HTTP status codes inherit the L4 leak. |
| `features/session-task-manager.md`, `features/task-centric-control.md` | L2/L3 mixed | Feature notes that restate CAS / RLS detail; row-level pass deferred. |

---

## §3 — Other L1 module docs (mostly DEFENSIBLE; few targeted leaks)

The remaining single-narrative module `ARCHITECTURE.md` files are
predominantly verdict-defensible: they name SPIs as boundary identities
(D1), give package decomposition (D2), and cite ArchUnit enforcers (D3).
A small number of rows leak runtime behaviour and should be extracted.

### 3.1 `architecture/docs/L1/agent-bus/ARCHITECTURE.md`

| file:line | Verbatim trigger | Status | Note |
|---|---|---|---|
| 78, 163 | "`IngressGateway` — single-method SPI interface; `routeClientRequest(IngressEnvelope) → IngressResponse`." | DEFENSIBLE (borderline) | D1 boundary identity. The one-line signature is the SPI's identity, not a method body; acceptable at L1, but the contract surface (`ingress-envelope.v1.yaml`) is correctly the signature authority. |

No L3/L4/L5 SQL/RLS/status/order leaks found in this file.

### 3.2 `architecture/docs/L1/agent-execution-engine/ARCHITECTURE.md`

| file:line | Verbatim trigger | Status | Why |
|---|---|---|---|
| 23, 100, 123 | "`com.huawei.ascend.engine.spi/` — `ExecutorAdapter`, `GraphExecutor`, … (… SPI purity per Rule 77 / OrchestrationSpiArchTest)" | DEFENSIBLE | D1 SPI identities + D3 enforcer. |
| 63-68 | "## 3. Strict matching … A Run with `engine_type=X` executes only on the adapter registered under `X`. Mismatch → `EngineMatchingException` → `Run.FAILED` with reason `engine_mismatch`." | LEAKED (L1/L2) | The dispatch-and-fail runtime sequence + the `Run.FAILED` / `engine_mismatch` outcome is runtime behaviour. The *boundary identity* ("registry resolves engine by type") is defensible; the runtime outcome chain is L2. |
| 58-61 | "`EngineEnvelope` Java record mirrors the schema (required fields validated on construction) … `EngineRegistry.resolve(...)`" | DEFENSIBLE (borderline) | Names the record↔schema mirror + the resolve boundary; no field-by-field signature. Treat as D1; the schema authority is the contract YAML. |

### 3.3 `architecture/docs/L1/agent-middleware/ARCHITECTURE.md`

| file:line | Verbatim trigger | Status | Why |
|---|---|---|---|
| 64 | "Hook ordering = middleware registration order." | LEAKED (L5, mild) | Ordering rule is runtime-dispatch behaviour. Far milder than the L0 §4 #16 leak (no `Class.getName()` tie-break here), but the same category. The "hooks are ordered" identity is defensible; "= registration order" is the mechanism that belongs in the hook contract / L2. |

### 3.4 `agent-client`, `agent-evolve`, `graphmemory-starter` `ARCHITECTURE.md`

No L1-L8 leak markers matched (these are skeleton/SPI-shell modules whose
docs stay at boundary-identity + package-decomposition level). DEFENSIBLE
as-is; re-scan when their implementations land.

---

## §4 — Scan coverage + method

- **Files scanned in full:** `L0/ARCHITECTURE.md` (1022 lines at the W0
  baseline, §0.4-§4; **now 912 lines** after the W12 in-place drains — see
  the 2026-05-30 re-scan in §1.5), `L1/agent-service/ARCHITECTURE.md`
  (804 lines at W0).
- **Files scanned by leak-marker pattern** (SQL/RLS/GUC, HTTP status,
  filter `order`, `@Order`/tie-break, wire formats, `updateIfNotTerminal`/
  CAS, `sequenceDiagram`, SPI signatures, test-class inventory):
  all 8 module `L1/*/ARCHITECTURE.md`, and the agent-service per-view set
  (`logical.md`, `process.md`, `development.md`, `physical.md`,
  `scenarios.md`, `spi-appendix.md`, `features/*`).
- **Marker set used:** `SET LOCAL`, `RLS`, `ON CONFLICT`, `SHA-256`,
  `base64url`, `\b(201|400|403|404|409|422|500)\b`, `order 10|15|20`,
  `@Order`, `Class.getName()`, `DROP_OLDEST`, `gen_ai.`, `langfuse.`,
  `OTLP`, `traceparent`, `persisted.version`/`version() - 1`,
  `updateIfNotTerminal`, `WHERE status NOT IN`, `sequenceDiagram`,
  `extends GraphExecutor`, `EngineMatchingException`/`engine_mismatch`.

**Priority for migration (highest first) — corrected by the 2026-05-30
re-scan (§1.5).** The W0 ranking led with **L0 §4 #20** then **L0 §0.5.3**;
both are now **drained in place** (§1.5) and are removed from the queue, as
are the drained L0 §4 #3 / #4 / #16 / #37 from the original L0 tail. The
remaining L0 residue is the still-present §4 #19 / #23 (and the
borderline-FACT-OWNED §4 #41 / #44, deferred to a future re-scan). The
remaining queue is therefore **L1-led**:

`agent-service` `logical.md` §3 state machine (2.2) → `agent-service`
`process.md` sequences (2.3) → `agent-service` `ARCHITECTURE.md` §2.A
routes/SQL/order (2.1) → `development.md` §5 inlined RLS detail (2.4) →
L0 §4 #19/#23 (1.2, still present) → L0 §4 #41/#44 (1.2, FACT-OWNED — re-scan
before migrating) → `agent-execution-engine` §3 + `agent-middleware`
line 64 (3.2-3.3).

**Out of scope of this scan (do not migrate based on this doc):** anything
in `architecture/facts/generated/*`, `architecture/profile/*`,
`workspace.dsl`, `engineering-frames.dsl`, `architecture-status.yaml`,
README, gate, enforcers.yaml, recurring-defect-families.yaml — those are
authority surfaces owned by other waves/subtasks.
