---
date: 2026-06-11
status: approved
approved_by: owner (2026-06-11)
approvals:
  - "§1 wire-level egress gateway as the W2 LLM-gateway shape, including honest non-coverage of remote runtimes"
  - "§2 ChatClient deferral behind the thin UpstreamModelClient seam until the 2026-08-01 Spring AI re-evaluation — stages, not reverses, locked ADR-0002"
  - "§3 gateway-local LlmCallListener seam as the W2 reading of §4 #56 under a retired #16"
  - "§4 recording-only cost promotion (spend log without budget enforcement) for W2"
---

# W2 LLM Gateway — minimal egress-gateway proposal honest to today's runtime

**Audience:** product owner + architecture team.
**Phase:** design proposal (pre-ADR). No code in this change; the staged plan in §8 names the ADR/lockstep wave that would precede implementation.
**Wave label:** W2 (LLM gateway un-freeze per `architecture/docs/L0/ARCHITECTURE.md:69` — "developer-ergonomics surface lands at W2 (Hook SPI + LLM gateway un-freeze)").

---

## 0. Today's model-call reality (verified against the repo, 2026-06-11)

Every claim below was checked against source, not prose:

1. **There is no platform gateway between any agent and any LLM today.** The W0 shipped-subset statement says so explicitly: "The Run domain kernel, **LLM gateway**, tool registry … are staged as W1–W4 design contracts … not present as half-built runtime paths" (`architecture/docs/L0/ARCHITECTURE.md:77`).
2. **openJiuwen (in-process):** `agent-sdk` parses agent YAML into `ModelSpec(provider, name, baseUrl, apiKey, sslVerify, headers)` (`agent-sdk/src/main/java/com/huawei/ascend/agentsdk/spec/model/ModelSpec.java:5-11`, populated at `agent-sdk/src/main/java/com/huawei/ascend/agentsdk/spec/yaml/AgentYamlParser.java:60-67`, provider default `openai-compatible`, `baseUrl`/`apiKey` required). The react builder hands those five values straight to openJiuwen's own model client: `ReActAgentConfig…configureModelClient(provider, apiKey, baseUrl, name, sslVerify, null, headers)` (`agent-sdk/src/main/java/com/huawei/ascend/agentsdk/adapter/react/OpenJiuwenReactAgentBuilder.java:45-52`). The HTTP client lives **inside** openJiuwen (`com.openjiuwen.core`); the platform never sees the call.
3. **AgentScope (in-process SDK form):** the example builds `io.agentscope.core.model.OpenAIChatModel` directly from `apiKey`/`apiBase`/`endpointPath` (`examples/agent-runtime-a2a-llm-e2e/src/main/java/com/huawei/ascend/examples/a2a/AgentScopeE2eConfiguration.java:213-221`). Default `apiBase` is `http://localhost:4000/v1` (`…AgentScopeE2eConfiguration.java:57`) — i.e. dev practice already assumes an OpenAI-compatible local proxy in front of the model.
4. **AgentScope runtime / LangGraph (remote form):** the adapters are pure REST/SSE clients to a remote agent process (`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/langgraph/LangGraphRuntimeClient.java`, `…/engine/agentscope/AgentScopeRuntimeClient.java`). **Those remote processes own their model configuration entirely**; no in-process Java seam can intercept their LLM traffic.
5. **Spring AI is on the pom but not in main code.** `agent-runtime/pom.xml:140-155` declares `spring-ai-starter-model-anthropic` / `-openai` / `-vector-store-pgvector`, all `<optional>true</optional>`; zero `org.springframework.ai` imports exist in any main source (only test references: an auto-config exclusion in `agent-runtime/src/test/java/com/huawei/ascend/runtime/app/RuntimeAppTest.java:69` and the vacuous `AudienceBExtensionSeamsArchTest`). This matches §4 #56's parenthetical "Wave C1 Spring AI shells remain design-only until W2 hook binding ships" (`architecture/docs/L0/ARCHITECTURE.md:933`).
6. **The Hook SPI is RETIRED / design_only.** §4 #16 (`architecture/docs/L0/ARCHITECTURE.md:430-455`): "no `HookPoint` / `RuntimeMiddleware` / `HookDispatcher` Java type exists and `docs/contracts/engine-hooks.v1.yaml` is `design_only`"; the 10 hook positions (incl. `before_llm_invocation` / `after_llm_invocation`) are retained as **design reference, NOT a current MUST**. `docs/contracts/engine-hooks.v1.yaml:7-11,22` confirms: retired by the agent-runtime pure rebuild (ADR-0159), "NOT runtime-enforced and NOT shipped on any classpath."
7. **§4 #56 (GENERATION span schema)** requires every LLM invocation in posture=research/prod to emit a span with `gen_ai.system`, `gen_ai.request.model`, `gen_ai.usage.input_tokens`, `gen_ai.usage.output_tokens`, `langfuse.cost_usd`, `langfuse.latency_ms`, payloads via `payload_ref://` only, and names `LlmGatewayHookChainOnlyTest` + `GenerationSpanSchemaIT` as W2 enforcers (`architecture/docs/L0/ARCHITECTURE.md:933`). Neither test class exists in the repo today, and #56's package text (`service.runtime.llm.*`) predates the ADR-0159 rename to `com.huawei.ascend.runtime.*` — a design-mode FQN refresh is owed (flagged in §9).
8. **Spring AI 2.0.0-M5 milestone risk** is on record: "`gate/check_spring_ai_milestone.sh` enforces re-evaluation by 2026-08-01 … W2 LLM-gateway surfaces consuming `ChatClient`/Advisor APIs (§4 #16, §4 #56) are most exposed to API drift between M5 and GA" (`architecture/docs/L0/ARCHITECTURE.md:276`). Note: that gate script does not currently exist under `gate/` — stale citation, flagged in §9.
9. **ADR-0002 (locked)** chose Spring AI over LangChain4j for the LLM-gateway + vector-store role, with "reversal cost: medium (LlmRouter is the only adapter; provider beans isolate vendor surface)" (`docs/adr/locked/0002-spring-ai-llm-gateway.md:30-47`).
10. **Cost governance is design_only** with a wired-to-hooks promotion path: `docs/contracts/cost-governance.v1.yaml` (status `design_only`, authority ADR-0156) defines `pre_call_budget_check` / `post_call_spend_record` and a `TokenBudgetStore` SPI homed in the **retired** `agent-middleware` module — that home is stale post-ADR-0159 and needs re-homing whenever the contract promotes.
11. **Metric + tenant rules already constrain the design:** all custom meters use prefix `springai_ascend_` (`architecture/docs/L0/ARCHITECTURE.md:336-337`; the one shipped example is `springai_ascend_traceparent_invalid_total`, `agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/TraceParentFilter.java:41`). §4 #57: every span carries `tenant.id`, while `TenantTagMeterFilter` **strips `tenant_id` from meter tags** for cardinality protection (`architecture/docs/L0/ARCHITECTURE.md:935`) — so per-tenant cost cannot ride Prometheus labels; it must ride spans + a spend-log record.
12. **`TraceContext` SPI exists** as a neutral type in `agent-bus` (`agent-bus/src/main/java/com/huawei/ascend/bus/spi/engine/TraceContext.java`), per the L1.x staging in `architecture/docs/L0/ARCHITECTURE.md:103`.

**Root cause framing (Rule D-1):** the design corpus speaks of an in-process "LlmGateway" guarded by a HookChain, but today's actual LLM traffic is *wire-level OpenAI-compatible HTTP issued by third-party framework internals* (openJiuwen, AgentScope) that can never call a platform Java interface, plus *remote runtimes* the platform doesn't host. Any W2 gateway that wants to see real traffic must therefore interpose at the **HTTP egress**, not at a Java method boundary. The strongest valid reading of "W2 LLM gateway un-freeze" is: *put a platform-owned, tenant-attributing, telemetry-emitting OpenAI-compatible endpoint in the model path of the call paths the platform actually controls* — and be explicit about the paths it does not.

---

## 1. Recommended scope (a): a wire-level egress gateway, with honest call-path coverage

**Recommendation: build `runtime.llm.gateway` — an OpenAI-compatible chat-completions egress endpoint inside `agent-runtime`** (`POST /v1/chat/completions`, non-streaming + SSE), which authenticates a platform-minted per-agent credential, resolves the upstream provider, forwards the request transparently, extracts usage, and emits the §4 #56 GENERATION telemetry. Adoption is by **`ModelSpec` indirection**: the platform rewrites/provisions `model.baseUrl` to point at the gateway and replaces `model.apiKey` with a minted scoped token (OpenAI-compatible clients already send the apiKey as `Authorization: Bearer …`, so the token rides the existing header with zero framework changes).

Call-path coverage, stated honestly:

| Call path | Routes through gateway at W2? | Mechanism |
|---|---|---|
| openJiuwen in-process agents (agent-sdk `ModelSpec` → `configureModelClient`) | **Yes** | `baseUrl` → gateway URL; `apiKey` → minted token. openJiuwen's model client is openai-compatible HTTP, so it needs no code change (`OpenJiuwenReactAgentBuilder.java:45-52`). |
| AgentScope SDK-embedded agents (`io.agentscope…OpenAIChatModel`) | **Yes (config-level)** | Same `apiBase`/`apiKey` substitution; the e2e example already parameterises both (`AgentScopeE2eConfiguration.java:56-61`). |
| AgentScope runtime / LangGraph **remote** processes | **No — and the proposal says so.** | Those runtimes own their model config remotely. Gateway adoption is opt-in: the operator points the remote's model `base_url` at the platform gateway and uses a minted token. The platform cannot force this at W2 and must not claim coverage it doesn't have. Their LLM spend is attributable only when they opt in. |
| Platform-originated LLM calls (planner, summariser, memory) | **N/A today** | Zero such call sites exist in main code. When they appear (W2+), they MUST call through the same gateway path (in-process short-circuit allowed, same listener chain — §3). |

This is the only gateway shape that intercepts *actual* W0/W1 traffic without forking framework internals, and it simultaneously fixes a real W1 defect: raw provider API keys currently sit in agent YAML (`AgentYamlParser.java:65` makes `model.apiKey` required) and in example env defaults. With indirection, raw provider credentials live only in gateway upstream config (Vault-backed; `spring-cloud-starter-vault-config` is already a dependency, `agent-runtime/pom.xml:115`).

---

## 2. Decision (b): thin provider-neutral surface now; Spring AI ChatClient stays shelved until the 2026-08-01 re-evaluation

**Recommendation: do NOT introduce `ChatClient` into main code in W2.** The wire-level proxy needs an HTTP forwarder (Spring `RestClient`/`WebClient` + SSE pass-through), not a chat-model abstraction — re-marshalling an OpenAI-compatible request through `ChatClient` and back would (i) couple the hot path to M5 APIs that §3 itself flags as the highest-drift surface (`ARCHITECTURE.md:276`), (ii) lossily translate provider-specific request fields, and (iii) complicate streaming fidelity for zero benefit, because both sides of the proxy speak the same dialect.

Instead, W2 lands one **thin provider-neutral internal seam**: a gateway-internal `UpstreamModelClient` port (resolve model alias → forward request → typed usage/latency result). This is deliberately the `LlmRouter` adapter seam ADR-0002 already names as the reversal hinge (`docs/adr/locked/0002-spring-ai-llm-gateway.md:47`). **ADR-0002 is not contradicted**: Spring AI remains the decided abstraction for the *platform-call* surface (future ChatClient call sites, VectorStore/pgvector, ChatMemory — none of which exist in main code yet), and the optional starters stay on the pom. ChatClient adoption is re-decided at the §3 re-evaluation gate (by 2026-08-01, ideally against Spring AI GA), at which point platform-originated callers get `ChatClient` configured with the gateway as its OpenAI-compatible backend — making ChatClient a *client of* the gateway, not its implementation.

---

## 3. Decision (c): the smallest hook seam under a retired §4 #16

§4 #16 is RETIRED/design_only — there is no `HookPoint`/`HookDispatcher` to bind to, and resurrecting the 10-position middleware chain is explicitly not a current MUST (`ARCHITECTURE.md:430-434`). Yet §4 #56 says GENERATION spans must be hook-emitted and "Direct LLM calls bypassing `HookChain` are a ship-blocking defect" (`ARCHITECTURE.md:933`). The honest reconciliation:

**Recommendation: a single, gateway-local listener seam — `LlmCallListener`** (package `com.huawei.ascend.runtime.llm.gateway.spi`):

- Two methods mirroring the two LLM hook *positions* from `engine-hooks.v1.yaml:30-31` (`before_llm_invocation` / `after_llm_invocation`) so a future Hook-SPI resurrection can adopt these call sites verbatim — but **no** `HookPoint` enum, **no** generic `RuntimeMiddleware`, **no** dispatcher, **no** outcome consumption (`ShortCircuit`/`Fail` semantics stay design_only per `engine-hooks.v1.yaml:22-23`). Listeners are observers; at W2 they cannot veto a call. This stays strictly *inside* the design-reference vision of #16 without re-shipping the retired machinery.
- **The listener chain is the sole emission path** for GENERATION spans and spend records: the gateway's forwarder emits nothing itself; `LlmSpanEmitterListener` and `SpendRecordListener` are the two reference listeners. This operationally satisfies §4 #56's intent ("hooks are the sole emission path for LlmCall", `ARCHITECTURE.md:454-455`) and §4 #53 (adapters never emit telemetry directly).
- **Enforcement now, with post-0159 names:** a new ArchUnit test in `agent-runtime` — working name `LlmGatewayEmissionBoundaryArchTest` — asserts (i) no class outside `runtime.llm.gateway` imports the upstream-forwarding client, and (ii) no class outside the listener package writes GENERATION spans. This is the W2-realisable analogue of the (non-existent, stale-FQN) `LlmGatewayHookChainOnlyTest`; the design-mode wave in §8 PR-1 updates #56's enforcer naming rather than shipping a test whose locked FQN references a package layout ADR-0159 deleted.
- Precedent in-repo: the openJiuwen adapter already exposes exactly this pattern at adapter scope — `openJiuwenRails(context)` installing `AgentRail`s before execution (`agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/openjiuwen/OpenJiuwenAgentRuntimeHandler.java:99,140-146`). The gateway listener is the same idea at the egress seam.

What this deliberately does **not** do: fire `before/after_tool_invocation`, memory, suspension or yield positions; create `engine-hooks.v2`; or flip `engine-hooks.v1.yaml` from `design_only`. The contract stays a design reference; the ADR in §8 PR-1 records that W2 implements the two LLM positions as a gateway-local seam and defers the rest.

---

## 4. Decision (d): tenant + cost attribution

**Identity:** the minted gateway token (the `Bearer` credential from §1) maps server-side to `(tenant_id, agent_id, model_alias)`; remote opt-ins get operator-issued tokens with the same shape. The gateway therefore attributes every call without parsing platform JWTs and without trusting caller-supplied headers.

**Spans (per-call truth):** `LlmSpanEmitterListener` emits the §4 #56 GENERATION span — `gen_ai.system` (upstream provider), `gen_ai.request.model`, `gen_ai.usage.input_tokens` / `gen_ai.usage.output_tokens` (parsed from the OpenAI-compatible `usage` object; streaming requires injecting `stream_options.include_usage` or reading the final usage chunk; when absent, estimate and mark `gen_ai.usage.estimated=true`), `langfuse.cost_usd` (from a per-model-alias pricing table in runtime config; unpriced models omit the attribute and increment a counter), `langfuse.latency_ms`, plus `tenant.id` per §4 #57. **No prompt/completion content in attributes** — at W2 the gateway records no payload at all, trivially satisfying §4 #58 until a `PayloadStore` exists (it doesn't today).

**Metrics (aggregates):** `springai_ascend_llm_requests_total{model_alias,provider,outcome}`, `springai_ascend_llm_tokens_total{model_alias,provider,direction}`, `springai_ascend_llm_upstream_latency_seconds`, `springai_ascend_llm_cost_unpriced_total` — prefix per `ARCHITECTURE.md:336-337`, and **no `tenant_id` meter tag**, consistent with the `TenantTagMeterFilter` cardinality rule in §4 #57 (`ARCHITECTURE.md:935`). Per-tenant cost questions are answered by the spend log, not Prometheus.

**Spend log (per-tenant ledger):** `SpendRecordListener` persists `cost-governance.v1.yaml`'s `post_call_spend_record` shape into a Flyway-managed `llm_spend_log` table keyed `(tenant_id, agent_id, model_alias, day)`. This is a **partial, recording-only promotion** of the cost contract: `pre_call_budget_check` (budget *enforcement*, `TokenBudgetStore`) stays design_only — its declared SPI home `com.huawei.ascend.middleware.cost.spi` points at the retired `agent-middleware` module and needs a re-homing decision in the §8 PR-1 ADR before any enforcement wave.

---

## 5. Decision (e): test strategy without live LLM keys

Three layers per Rule D-4, none requiring a live provider:

1. **Unit:** usage-object parsing across recorded provider dialects (the SSE/stream-shape fixture style already exercised in the W1 stream-adapter work); pricing-table cost computation incl. unpriced fallback; minted-token resolution; `ModelSpec` rewrite logic in agent-sdk.
2. **Integration (WireMock):** `wiremock-standalone` is already a test dependency (`agent-runtime/pom.xml:201`). Stand up the gateway via MockMvc/RestAssured with WireMock as the "provider": non-streaming round-trip, SSE pass-through (WireMock chunked-dribble for stream fidelity), usage-in-final-chunk, upstream 401/429/5xx mapping, missing/forged token → 401 with no upstream call, and `GenerationSpanSchemaIT` — assert the emitted span carries all six #56 attributes + `tenant.id` against an in-memory `TraceContext`/span sink. Cross-check request fidelity byte-wise: what the framework client sent is what the upstream received (header allowlist excepted) — this is the "judge adapters against real third-party wire formats" discipline applied to the gateway.
3. **Architecture:** `LlmGatewayEmissionBoundaryArchTest` (§3); extend the existing example-seam guard pattern (`AudienceBExtensionSeamsArchTest`) so example code may not construct upstream HTTP clients against raw provider URLs once the gateway example flip lands.
4. **E2E without keys:** the existing `examples/agent-runtime-a2a-llm-e2e` flow re-pointed at gateway-with-WireMock upstream proves the full A2A → openJiuwen → gateway → "provider" chain in CI. Smoke with a real key remains a manual, env-gated path (current `SAA_SAMPLE_LLM_API_KEY` mechanism), never a CI dependency.

---

## 6. Explicit non-goals (W2)

- **No request re-marshalling / provider SDK translation** — transparent OpenAI-compatible pass-through only; Anthropic-native or other dialect upstreams enter via a later `UpstreamModelClient` implementation, not by W2 scope creep.
- **No Spring AI ChatClient in main code; no VectorStore/ChatMemory work** (§2; re-evaluated by 2026-08-01).
- **No Hook SPI resurrection** — no `HookPoint`/dispatcher/outcome consumption; `engine-hooks.v1.yaml` stays `design_only` (§3).
- **No budget enforcement** — `pre_call_budget_check`, `TokenBudgetStore`, and `RateLimited` rejection stay design_only; W2 records spend only (§4).
- **No payload capture / `PayloadStore`** — gateway stores no prompt/completion content at W2.
- **No model routing, fallback, caching, or retry orchestration** — one alias → one upstream; resilience4j wrapping limited to a basic circuit breaker name (`"llm-call"` already reserved in config vocabulary, `ARCHITECTURE.md:366`).
- **No mandate on remote runtimes** — LangGraph/AgentScope-runtime model ownership is unchanged; gateway adoption is operator opt-in (§1).
- **No admin UI / no HTTP replay surface** — preserves §4 #59 (MCP-only replay, W4).

---

## 7. Alternatives considered (one paragraph each)

**A1 — In-process Spring AI `ChatClient` gateway now.** Activate the optional starters, build `LlmGateway` as ChatClient beans, and call it the W2 gateway. Rejected: there are zero platform-originated LLM call sites in main code, and the real traffic (openJiuwen/AgentScope internals, remote runtimes) cannot call a Java bean — the result would be a shelf gateway that satisfies the ADR-0002 letter while intercepting nothing, and it would maximise exposure to exactly the M5→GA drift §3 warns about (`ARCHITECTURE.md:276`).

**A2 — Deploy LiteLLM (or another OSS proxy) instead of building the endpoint.** Strong OSS-first candidate (`ARCHITECTURE.md` §1 principle 6), and dev practice already proxies via `localhost:4000`. Rejected for the platform path because the gateway must live inside the platform's spines — minted-token → tenant resolution, spend log into RLS-governed Postgres, GENERATION spans through the `TraceContext` vertical, and the Form-2/3 embedded-library deployment shape (ADR-0159) where a mandatory Python sidecar breaks the self-hostable bank on-prem story. The wire dialect stays LiteLLM-compatible, so dev environments can keep an external proxy interchangeably.

**A3 — Fork/wrap the framework model clients** (inject a platform HTTP client into openJiuwen / AgentScope). Rejected: both SDKs construct their model clients internally (`OpenJiuwenReactAgentBuilder.java:45-52`, `AgentScopeE2eConfiguration.java:213-221`); interposing requires forking third-party internals per framework per version — unmaintainable, and still does nothing for remote runtimes.

**A4 — Defer the gateway until the Hook SPI is re-designed.** Rejected: it serialises W2 telemetry (§4 #56/#57), the cost story (ADR-0156 / cost-governance, a `v1_0_ship_blocker: true` contract) and credential hygiene behind an unscheduled middleware redesign, against a v1.0 target of 2026-06-30 (`CLAUDE.md` program status). §3's listener seam shows the dependency is not real.

---

## 8. Staged landing plan (PR-sized, each leaving `./mvnw -Pquality verify` + gate green)

1. **PR-1 (design-mode lockstep):** new ADR "W2 LLM egress gateway — wire-level interposition, listener seam, ChatClient deferral" (extends ADR-0002, relates to ADR-0159/0156/0158); L0 updates: §4 #56 enforcer/FQN refresh to post-0159 packages, gateway row in module ownership + W2 staging text; contract-catalog row for the gateway HTTP surface; `architecture-status.yaml` claim update; stale-citation fixes from §9. No code.
2. **PR-2 (gateway skeleton, non-streaming):** `com.huawei.ascend.runtime.llm.gateway` — `ChatCompletionsController` (`POST /v1/chat/completions`), `UpstreamModelClient` port + `RestClient` forwarder, `ModelAliasRegistry` (alias → upstream URL/credential, Vault-resolvable), minted-token auth, error mapping. WireMock ITs + unit tests.
3. **PR-3 (streaming + usage + metrics):** SSE pass-through, `stream_options.include_usage` injection, usage extraction incl. estimated fallback, `springai_ascend_llm_*` meters. Stream-fidelity ITs.
4. **PR-4 (listener seam + telemetry):** `LlmCallListener` SPI + `LlmSpanEmitterListener` (GENERATION span, all six #56 attributes + `tenant.id`), `GenerationSpanSchemaIT`, `LlmGatewayEmissionBoundaryArchTest`.
5. **PR-5 (ModelSpec indirection + example flip):** agent-sdk resolves platform-managed model aliases → gateway baseUrl + minted token (dev posture keeps raw passthrough); `examples/agent-runtime-a2a-llm-e2e` flipped to the gateway path; example-seam ArchUnit extension.
6. **PR-6 (spend log):** Flyway `llm_spend_log` migration (tenant-scoped per the RLS direction of ADR-0005), `SpendRecordListener`, daily roll-up query, partial promotion note on `cost-governance.v1.yaml` (recording-only; enforcement remains design_only).

Sequencing rationale: PR-2/3 are pure additive egress (no caller changes, zero blast radius); telemetry (PR-4) precedes adoption (PR-5) so the first routed call is observable on day one; spend log lands last because it needs migration + contract-promotion ceremony.

---

## 9. Stale-citation follow-ups surfaced by this proposal (for PR-1, not silently fixed here)

1. `architecture/docs/L0/ARCHITECTURE.md:276` cites `gate/check_spring_ai_milestone.sh`; no such script exists under `gate/` today. Either land the script or re-word the re-evaluation mechanism.
2. §4 #56 (`ARCHITECTURE.md:933`) locks enforcer `LlmGatewayHookChainOnlyTest` against `service.runtime.llm.*` — a pre-ADR-0159 package that can no longer exist; the class itself was never created. PR-1 re-locks the enforcer pair to post-0159 names (§3).
3. `docs/contracts/cost-governance.v1.yaml` homes `TokenBudgetStore` in `com.huawei.ascend.middleware.cost.spi` / `agent-middleware` — a module retired by ADR-0159; re-home on promotion (§4).
4. §4 #53 (`ARCHITECTURE.md:927`) pins `TelemetryVerticalArchTest` to `agent-service/src/main/java/com/huawei/ascend/service/{runtime,platform}/observability` — also pre-0159 paths; the gateway's emission boundary (§3) should be reconciled with whatever path #53 is refreshed to.

---

## 10. Decision requested from the owner

1. Approve the **wire-level egress gateway** as the W2 LLM-gateway shape (§1), including the honest non-coverage of remote runtimes.
2. Approve **ChatClient deferral** behind the thin `UpstreamModelClient` seam until the 2026-08-01 Spring AI re-evaluation (§2) — confirming this stages, not reverses, locked ADR-0002.
3. Approve the **gateway-local `LlmCallListener` seam** as the W2 reading of §4 #56 under a retired #16 (§3).
4. Approve **recording-only** cost promotion (spend log without budget enforcement) for W2 (§4).
