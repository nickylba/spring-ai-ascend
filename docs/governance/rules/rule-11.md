---
rule_id: 11
title: "Contract Spine Completeness"
level: L1
view: development
principle_ref: P-J
authority_refs: [ADR-0079, ADR-0078]
enforcer_refs: [E105]
status: active
kernel_cap: 12
kernel: |
  **Every persistent record class committed under `agent-runtime-core/src/main/java/ascend/springai/service/runtime/**/*.java` (or its successor module) MUST declare a `String tenantId` component validated by `Objects.requireNonNull(tenantId, "tenantId is required")` in its compact constructor. Process-internal value objects exempt themselves with a `// scope: process-internal` reason comment. Activated 2026-05-18 (Wave 4 Track B) — trigger met by `Run` and `IdempotencyRecord` carrying tenantId.**
---

# Rule 11 — Contract Spine Completeness

## Motivation

Every persistent record that crosses a JVM boundary or lands in storage carries
tenant identity at the value-type level. The original deferred draft of Rule 11
listed `tenant_id` plus the relevant subset of `{user_id, session_id, run_id,
parent_run_id, attempt_id, capability_name}` per record type. The active
post-2026-05-18 form narrows the enforcement to the single non-negotiable
field — `tenant_id` — and codifies it as a record-class compact-constructor
invariant.

## Enforcer

Gate rule `contract_spine_tenant_id_required` (E105) scans
`agent-runtime-core/src/main/java/ascend/springai/service/runtime/**/*.java`
for `record` declarations and asserts every persistent record class declares
a `String tenantId` component. Process-internal value objects opt out with
a `// scope: process-internal` reason comment on the same record line.

## Trigger

Activated 2026-05-18 by Wave 4 Track B per `D:\.claude\plans\spicy-mixing-galaxy.md`.
Run record at `agent-runtime-core/src/main/java/ascend/springai/service/runtime/runs/Run.java`
and IdempotencyRecord at `agent-runtime-core/src/main/java/ascend/springai/service/runtime/idempotency/IdempotencyRecord.java`
already carry the mandatory tenantId component.
