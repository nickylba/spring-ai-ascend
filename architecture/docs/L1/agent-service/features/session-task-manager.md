---
level: L1
view: development
module: agent-service
status: proposed
authority: "Absorbed from docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.{cn,en}.md §6.2. Anchors back to canonical Session & Task Manager (Layer 2) in ../logical.md §1 + Run aggregate single-owner per ADR-0142. + docs/logs/reviews/2026-05-28-agent-service-m1-m6-design-draft.cn.md §M2 (v1.2 expansion F09-F10 per ADR-0155)"
---

# Session & Task Manager — Feature Inventory (AS-L1-F09..F16)

> Module: Session & Task Manager (Layer 2 per ADR-0138).
> Sovereign for: Run aggregate (ADR-0142 single-owner), Task control state, Session context state, attempt/checkpoint references, parent/child/remote correlation, Run-creation configuration snapshot, tenant-first persistence + lifecycle audit.
> Does NOT own: ingress routing, runtime decisions, engine execution, model/tool translation. Layer 4 holds a typed reference to the `RunRepository` SPI and delegates Run-state transitions to it — Layer 2 remains the single writer. The atomic transition primitive behind that SPI is an L2 concern (see [`../development.md`](../development.md) §5.3).
>
> **Altitude discipline (L1).** This inventory names each feature's
> capability, its scenario clusters, its SPI / layer collaborators, and
> the exception classes it must cover. It does NOT carry code-level
> detail: the CAS state-transition mechanics and atomic SQL primitive
> backing `RunRepository` (owned by the [`../development.md`](../development.md)
> §5.3 Postgres RLS L2 Boundary Contract), the per-table persistence
> schema and RLS policy bodies for the Run / Task / Session /
> `IdempotencyRecord` aggregates (owned by the same §5.3 zone), the
> cancel-vs-complete winner / loser arbitration decision table (owned by
> ADR-0155 §6 and narrated at L1 by [`../process.md`](../process.md) §P3 /
> §P6), and the response-snapshot writeback transition mechanics (owned
> by the §5.3 zone, with the terminal RunEvent emission shape owned by
> [`run-event.v1.yaml`](../../../../../docs/contracts/run-event.v1.yaml)).
> The cells below name the obligation and the owning authority; they do
> not reproduce the sequence.

| Feature ID | Category | Covered clusters | Capability | Inputs / Outputs | Collaborators | Exception coverage | OSS reference |
| --- | --- | --- | --- | --- | --- | --- | --- |
| AS-L1-F09 | Run execution-state source of truth | AS-SC01-AS-SC12 | Own the Run aggregate, the RunStatus DFA, and RunStateMachine validation, exposing the single sanctioned transition path through the `RunRepository` SPI (validation invoked atomically inside the transition). The atomic-CAS primitive that backs the transition is an L2 concern ([`../development.md`](../development.md) §5.3). | Inputs: create request, transition intent. Outputs: Run record, transition result, audit material. | Access Layer, Task-Centric Control Layer, Internal Event Queue. | Illegal transition, cancel-vs-complete race, terminal no-op, engine_mismatch. | Temporal workflow history, Conductor durable task state. |
| AS-L1-F10 | Task control-state source of truth | AS-SC02, AS-SC03, AS-SC07, AS-SC13 | Maintain protocol-visible Task control state expressing submitted / working / input_required / completed / failed and whyStopped semantics. | Inputs: Run projection, suspend reason, terminal result. Outputs: Task state, cursor status, whyStopped. | Access Layer, Task-Centric Control Layer. | Task / Run drift, input_required confused with RunStatus. | A2A TaskState final / interrupted predicates. |
| AS-L1-F11 | Session context-state source of truth | AS-SC07, AS-SC08, AS-SC13 | Maintain conversation messages, variables, Session projection, Run-to-Session relation, and ContextProjector input to support context recovery after disconnect. | Inputs: conversation update, runId, sessionId, memory reference. Outputs: Session snapshot, context projection source. | Translation & Tool-Intercept, Task-Centric Control Layer. | Stale projection, concurrent memory mutation, cross-tenant session read. | AgentScope externalized session/state, LangChain4j `@MemoryId` risk. |
| AS-L1-F12 | Context version and compaction projection anchor | AS-SC08, AS-SC24 | Preserve the version relationship between original session facts and compacted / summarized / projected context so overflow handling remains explainable, recoverable, and auditable. | Inputs: context window pressure, messages, compression result. Outputs: projection version, summary reference, audit link. | Translation & Tool-Intercept, Internal Event Queue. | Compression loss, prompt overflow, stale summary, memory mutation race. | OpenAI Agents sessions, LangGraph state snapshot, CrewAI memory scopes. |
| AS-L1-F13 | Attempt / checkpoint / rollback reference | AS-SC10, AS-SC11 | Use attemptId, parentNodeKey, checkpoint reference, and RunEvent correlation to express execution switching and rollback-to-prior-state retry anchors. | Inputs: retry intent, checkpoint id, failure reason. Outputs: new attempt marker, rollback reference, audit event material. | Task-Centric Control Layer, Engine Dispatch & Execution, Internal Event Queue. | Missing checkpoint, non-idempotent side effect, exhausted retry budget. | Temporal retry/history, LangGraph checkpoint, Conductor retry. |
| AS-L1-F14 | Parent / child / remote invocation correlation | AS-SC15-AS-SC18 | Store parentRunId, childRunId, remoteAgentId, remoteTaskId / remoteThreadId, callbackId, traceId, and tenantId correlation. | Inputs: spawn request, third-party invocation handle, child terminal result. Outputs: correlation record, join material. | Task-Centric Control Layer, Engine Dispatch & Execution, Access Layer. | Orphan child, lost remote handle, duplicate child completion, tenant mismatch. | A2A task id, OpenAI Agents handoff, AutoGen AgentId / message id. |
| AS-L1-F15 | Configuration snapshot / reference source of truth | AS-SC19-AS-SC24 | Record a snapshot or stable reference for resolved model / engine / adapter / client / tool / routing profiles at Run creation time for resume / retry. | Inputs: resolved configuration set. Outputs: config snapshot reference, drift audit material. | All execution-related modules. | Config drift, adapter version mismatch, model option drift, credential scope error. | Temporal deterministic config discipline, Conductor workflow input snapshot. |
| AS-L1-F16 | Tenant-first persistence and lifecycle audit | AS-SC01-AS-SC24 | Ensure all Run / Task / Session / idempotency / lifecycle audit records carry tenantId and obey RLS in durable backends; project state changes into lifecycle audit and RunEvent material. | Inputs: tenant-bound aggregate changes. Outputs: RLS-bound record, audit row, event source material. | Internal Event Queue, physical deployment plane, Access Layer. | RLS bypass, anonymous event, tenant inference, audit loss. | Multi-tenant workflow services, Temporal namespace isolation. |

| AS-L1-F52 | CorrelationRecord discriminated handle | AS-SC18, AS-SC15 | Persist cross-Run / remote-Agent handles as a discriminated union (`LocalChildHandle{childRunId}` or `RemoteAgentHandle{remoteAgentId, remoteTaskId, remoteThreadId, callbackId}`) carrying a settlement-status discriminator (`PENDING` / `ACTIVE` / `SETTLED`). The fill / settlement transition mechanics (creation, remote-handle assignment, child-settlement) are owned by `docs/contracts/correlation-record.v1.yaml` (design_only) and its L2 realisation. | Inputs: handle type + creation context. Outputs: STM-06 record; status advances on remote-handle assignment and child settlement. | Engine Dispatch & Execution (assigns the remote handle), Task-Centric Control Layer (creation + cancel cascade reads). | Handle lost (CorrelationRecord incomplete), remote-handle assignment conflict, settle-on-cancelled-parent race. | Temporal child-workflow handle, A2A task linkage. |
| AS-L1-F53 | Response snapshot writeback (terminal hook) | AS-SC09, AS-SC12 | Per ADR-0155 §1 H4 reversal, STM-08 is the sovereign store for the terminal response snapshot, accepting a one-way writeback from TCC-03 once a Run reaches a terminal state; STM-08 owns the storage, M1 only reads. The trigger-ordering relative to the terminal transition, and the binding write itself, are the L2 concern of the [`../development.md`](../development.md) §5.3 Postgres RLS L2 Boundary Contract — this row names the storage ownership and the binding obligation rather than the transition sequence. | Inputs: runId, terminal state, finalArtifactRef, reason. Outputs: bound terminal response snapshot (single-bind obligation; the persisted `IdempotencyRecord` schema is owned by §5.3). | Task-Centric Control Layer (sole writer). | Snapshot already bound (terminal-to-terminal), bound-on-non-terminal attempted (rejected). | Stripe-style idempotency snapshot binding. |

## Cross-references

- **Canonical Layer 2 definition**: [`../logical.md`](../logical.md) §1 (Run aggregate single-owner per ADR-0142) + §2 (Run/Task/Session ER) + §3 (RunStatus state machine).
- **Scenario anchors**: [`../scenarios.md`](../scenarios.md) AS-SC01-AS-SC12 (lifecycle), AS-SC07-AS-SC08 (recovery + compaction), AS-SC15-AS-SC18 (parent/child/remote), AS-SC19-AS-SC24 (configuration).
- **Process sequences**: [`../process.md`](../process.md) P1 (synchronous intake → dispatch → resume), P3 (cancel-race WINNER), P6 (cancel-race LOSER). These L1 narratives delegate the CAS / atomic-transition mechanic to the [`../development.md`](../development.md) §5.3 L2 Boundary Contract and the winner / loser arbitration decision table to ADR-0155 §6.
- **Persistence-plane tenancy posture**: [`../physical.md`](../physical.md) §2 (RLS-bound Run / Task / Session / `IdempotencyRecord` aggregates + lifecycle-audit trail). The concrete per-table schema, RLS policy bodies, and CAS realisation are the [`../development.md`](../development.md) §5.3 Postgres RLS L2 Boundary Contract.
- **SPI 4-way parity**: [`../spi-appendix.md`](../spi-appendix.md) — `RunRepository`, `TaskStateStore`, `ContextProjector`, `IdempotencyStore`.

## Originating source

This file absorbs §6.2 of [`docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.en.md`](../../../../../docs/logs/reviews/2026-05-26-agent-service-module-capability-feature-list.en.md).
