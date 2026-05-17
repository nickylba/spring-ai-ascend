---
rule_id: 24
title: "RunLifecycle Re-Authorization (cancel-only at W1)"
level: L1
view: process
principle_ref: P-J
authority_refs: [ADR-0020, ADR-0078]
enforcer_refs: [E106]
status: active
kernel_cap: 12
kernel: |
  **Every `POST /v1/runs/{runId}/cancel` operation MUST re-validate `(request.tenantId == Run.tenantId)`; mismatch returns HTTP 403 `tenant_mismatch`. Idempotent terminal->terminal same-status calls return 200; illegal transitions return 409 `illegal_state_transition`. The cancel surface emits a structured `WARN+` audit log line carrying `(runId, fromStatus, toStatus, actor, occurredAt)` MDC fields. Resume and retry sub-clauses (24.d) remain deferred to the W2 async orchestrator.**
---

# Rule 24 — RunLifecycle Re-Authorization (cancel-only at W1)

## Motivation

The original deferred draft of Rule 24 named cancel + resume + retry as the
three lifecycle operations requiring (a) tenant re-validation, (b) audit
trail, (c) idempotent terminal-to-terminal semantics. The active W1 form
narrows the rule to cancel only — the resume + retry endpoints arrive with
the W2 async orchestrator (deferred sub-clause 24.d).

## Active surface (W1)

`RunController.cancel(runId, tenantHeader)` in
`agent-service/src/main/java/ascend/springai/service/platform/web/runs/RunController.java`:

- Reads `Run` from `RunRepository.findById(runId)`; returns 404 if missing.
- Compares `request.tenantId` with `Run.tenantId`; returns 403 on mismatch.
- Returns 200 if the Run is already terminal in CANCELLED state (idempotent).
- Calls `RunStateMachine.validate(currentStatus, CANCELLED)`; throws
  `IllegalStateException` (handled as 409) on illegal transition.
- Emits structured WARN log with MDC fields per the kernel above.

## Audit table

A durable `run_state_change` audit table is deferred to W2 per ADR-0020.
At W1 the audit trail lives in the application log stream (Logback JSON).

## Sub-clauses

- 24.c (active): cancel re-authorization, as above.
- 24.d (deferred to W2): resume + retry re-authorization. Stays in
  `docs/CLAUDE-deferred.md` until the W2 async orchestrator ships.
