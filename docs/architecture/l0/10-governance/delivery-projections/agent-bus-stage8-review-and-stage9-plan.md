---
artifact_type: delivery_projection
version: agent-bus-stage8-review-and-stage9-plan
status: draft
source_commit: f94cffd9
source_stage8_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage7-review-and-stage8-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
target_module: agent-bus
---

# agent-bus Stage 8 评审与 Stage 9 计划

## 0. 结论

最新提交 `f94cffd9` 可以作为 Stage 8 的阶段性成果接受：C3 已从“待确认”推进为 `adopted-c3`，并且补齐了 outbox / inbox record 模型、claim / lease 端口、dispatcher worker skeleton、抽象 delivery port、schema / DDL 草案和 in-memory lease harness。这次提交已经明显进入工程实现轨道，不再是文档空转。

但 Stage 8 仍然不能直接进入生产持久化接入。当前最大风险集中在 **lease-safe 状态变更** 与 **record 条件不变量**：已经有 claim / lease，但 ACK / RETRY / DLQ / EXPIRE 这些后续状态变更还没有携带 lease owner 校验；record 模型也还没有把 runtime ICD 中的条件字段规则全部固化到 Java 构造器和 DDL check constraint。Stage 9 必须先补齐这些硬约束，再决定真实数据库 / migration 归属和 adapter 落地。

简短判断：

- Stage 8 的方向正确，范围也够大，完成度比前几轮更接近可施工代码。
- claim / lease 的入口已经出现，但状态变更仍需 lease-owner CAS 化，否则多实例下会有 stale worker 改状态的风险。
- Stage 9 应该继续大批次推进：lease-safe mutation、强 record invariants、DB/migration 决策、持久化 adapter 路径和 harness 一起做。

## 1. 本次提交审查

### 1.1 完成情况

本次提交完成了以下内容：

- C3 裁决状态更新为 `adopted-c3`，后续变更需 ADR / review packet。
- 新增 `ForwardingOutboxRecord`、`ForwardingInboxRecord`、`ForwardingLease`。
- 新增 `ForwardingOutboxClaimPort`，用 `claimDue` 取代裸 `findRetryable(now)`。
- 新增 `ForwardingDeliveryPort`、`ForwardingDeliveryResult`、`ForwardingDispatcherWorker`。
- `ForwardingDispatcher` 收敛为 accept / enqueue gateway 角色。
- in-memory outbox 支持 claim / lease、expired lease reclaim、terminal 不可 claim。
- 新增 `forwarding-persistence.md`，提供 Postgres DDL 草案、migration / rollback 说明。
- runtime schema 增加 lease、claim_lease、stage8_scope 和新增 contract tests。
- L1 文档同步 Stage 8 状态。

验收判断：

- MI8-001..005 基本收口。
- 生产代码仍保持纯 Java，没有引入 JDBC driver / Flyway / concrete broker / HTTP transport。
- Stage 8 的 DDL 明确标注为草案，未伪装成已执行 migration。
- 仍需 Stage 9 补齐并发安全和条件不变量，否则真实 DB adapter 很容易实现偏。

## 2. 当前修改意见

| 编号 | 问题 | 严重度 | 证据 | 修改意见 |
|---|---|---|---|---|
| MI9-001 | claim 带 lease owner，但 ACK / RETRY / DLQ / EXPIRE 状态变更不带 lease owner，无法防 stale worker 或非持有者改状态。 | 高 | `ForwardingDispatcherWorker` 调用 `markAcked(id, tenantId)` / `scheduleRetry(id, tenantId, ...)`；`ForwardingOutboxPort` 对应方法没有 `leaseOwner`。 | Stage 9 必须把状态变更升级为 lease-owner guarded mutation，真实实现用 `WHERE tenant_id=? AND message_id=? AND lease_owner=? AND lease_until>?` 或等价 CAS。 |
| MI9-002 | lease 生命周期收口不完整：terminal / retry 后是否释放 lease 没有强规则。 | 高 | `forwarding-persistence.md` 只写 `releaseLease`，但 worker 在 ACK / RETRY / DLQ / EXPIRE 后没有调用；in-memory mutate 也不清 lease。 | 明确策略：ACK / DLQ / EXPIRED 必须清 lease；RETRY_SCHEDULED 应清 lease 或写明下一次 retry 需等待 lease 过期，推荐清 lease。 |
| MI9-003 | record 构造器没有完整执行 runtime ICD 的条件不变量。 | 高 | `ForwardingOutboxRecord` 未校验 `tenantId == routeHandle.tenantScope`、`RETRY_SCHEDULED` 必须有 `nextAttemptAt`、`DLQ/EXPIRED/RETRY_SCHEDULED` 必须有 `lastFailureCode`；`ForwardingInboxRecord` 未强制 `DUPLICATE_SUPPRESSED/REJECTED` 有 failureCode、`CONSUMED` 有 consumedAt。 | Stage 9 将条件字段规则固化到 Java record、schema、DDL check constraint 和 contract test。 |
| MI9-004 | retry result 只要求 failureCode 非空，没有限制必须是 retryable failure code。 | 中 | `ForwardingDeliveryResult.retry(...)` 可接收 `ROUTE_NOT_FOUND`、`TENANT_MISMATCH`、`DUPLICATE_SUPPRESSED` 等非 retryable code。 | 增加 failure-code classification API，`retry(...)` 只接受 retryable，`dlq(...)` 接受 non-retryable 或 retry exhausted。 |
| MI9-005 | `targetServiceId` “从 opaque routeHandle 投影”表述仍不够严谨。 | 中 | `ForwardingOutboxRecord` javadoc 写 target 从 opaque routeHandle 投影；L2 也写 discovery 经 routeHandle 投影。opaque handle 本身不应被解析。 | 明确 targetServiceId 来自 discovery metadata / gateway audit context，而不是解包 routeHandle。 |
| MI9-006 | Postgres DDL 草案还缺少条件约束和产品决策落位。 | 中 | DDL 有状态 enum check，但没有 `attempt_count >= 0`、`lease_owner/lease_until` 成对、`RETRY_SCHEDULED => next_attempt_at not null`、terminal/failureCode 条件。 | Stage 9 若确认 Postgres，就把 DDL 从草案推进到可执行 migration 草案；否则保留设计态并补完整 check constraints。 |

## 3. Stage 9 目标

Stage 9 的目标是把 Stage 8 的“持久化准备”推进为“并发安全、约束完整、可接真实持久化 adapter 的 C3 底座”：

> 完成 lease-owner guarded mutation、lease 生命周期闭环、record / schema / DDL 条件不变量、failure-code classification、DB/migration 归属决策，并在允许范围内交付最小持久化 adapter skeleton 或等价 SQL contract harness。

Stage 9 仍应作为较大批次执行，但顺序必须清楚：先修正并发和不变量，再接真实 DB / migration。

## 4. Stage 9 开发切片

### 切片 1：lease-safe mutation API

修改 `ForwardingOutboxPort`，让所有从 `DISPATCHING` 出发的状态变更携带 lease owner：

```java
ForwardingStatus.Outbox markAcked(ForwardingMessageId id, String tenantId, String leaseOwner);

ForwardingStatus.Outbox scheduleRetry(
        ForwardingMessageId id,
        String tenantId,
        String leaseOwner,
        ForwardingFailureCode code,
        long nextAttemptAtMillisEpoch);

ForwardingStatus.Outbox moveToDlq(
        ForwardingMessageId id,
        String tenantId,
        String leaseOwner,
        ForwardingFailureCode code);

ForwardingStatus.Outbox markExpired(
        ForwardingMessageId id,
        String tenantId,
        String leaseOwner);
```

并明确失败语义：

- record 不存在：显式失败。
- lease 不存在：显式失败。
- lease owner 不匹配：显式失败。
- lease 已过期：显式失败或返回 stale-owner 结果，不能继续修改。
- terminal record：显式失败。

DoD：

- worker 所有状态变更都传入 `leaseOwner`。
- stale worker 不能 ACK / RETRY / DLQ / EXPIRE 已被其他 worker reclaim 的记录。
- harness 覆盖“worker-1 lease 过期后 worker-2 reclaim，worker-1 ACK 失败”。

### 切片 2：lease 生命周期闭环

明确并实现 lease 清理策略：

- `ACKED`：清空 lease。
- `DLQ`：清空 lease。
- `EXPIRED`：清空 lease。
- `RETRY_SCHEDULED`：推荐清空 lease，让下一次 claim 只受 `nextAttemptAt` 控制；如果不清 lease，必须明确 retry 还要等待 lease 过期，这会制造不必要延迟。
- `DISPATCHING`：必须持有有效 lease。

DoD：

- Java record / in-memory test double / L2 文档一致。
- contract test 覆盖 terminal 后 lease 为空、retry 后 lease 为空、active DISPATCHING 有 lease。

### 切片 3：record 条件不变量固化

把 runtime ICD 里的条件字段规则固化到 `ForwardingOutboxRecord` / `ForwardingInboxRecord`：

Outbox：

- `tenantId == routeHandle.tenantScope()`。
- `attemptCount >= 0`。
- `RETRY_SCHEDULED` 必须 `nextAttemptAtMillisEpoch > 0` 且 `lastFailureCode` 是 retryable。
- `DLQ` 必须有 `lastFailureCode`。
- `EXPIRED` 必须有 `DELIVERY_TIMEOUT` 或明确的 timeout failure。
- `ACKED` 不得有 `lastFailureCode`，且 lease 必须为空。
- terminal 状态 lease 必须为空。
- `DISPATCHING` 必须有非空 lease。

Inbox：

- `CONSUMED` 必须有 `consumedAtMillisEpoch > 0` 且不得有 failureCode。
- `DUPLICATE_SUPPRESSED` 必须有 `DUPLICATE_SUPPRESSED` failureCode。
- `REJECTED` 必须有 non-null failureCode。
- `RECEIVED` 不得有 failureCode，`consumedAtMillisEpoch` 应为空语义（当前 long 可用 0 表达，但要文档化）。

DoD：

- Java 构造器、machine-readable schema、DDL check constraint、contract test 一致。
- 新增“非法 record 构造失败”的测试。

### 切片 4：failure-code classification

给 `ForwardingFailureCode` 增加分类能力：

```java
boolean retryable();
boolean nonRetryable();
boolean dedup();
```

或等价 enum metadata。

规则：

- retryable：`DELIVERY_TIMEOUT`、`RECEIVER_UNAVAILABLE`、`BACKPRESSURE_REJECTED`。
- non-retryable：`ROUTE_NOT_FOUND`、`TENANT_MISMATCH`、`PAYLOAD_REF_INVALID`。
- dedup：`DUPLICATE_SUPPRESSED`。

DoD：

- `ForwardingDeliveryResult.retry(...)` 只接受 retryable code。
- `scheduleRetry(...)` 只接受 retryable code。
- `moveToDlq(...)` 可接受 non-retryable 或 retry-exhausted retryable code，但必须在文档里说明。

### 切片 5：DB / migration 归属裁决

Stage 8 已给出 Postgres DDL 草案，但没有决定真实 migration 归属。Stage 9 必须由人类确认以下问题：

- 数据库产品：是否采用 Postgres 作为 C3 outbox / inbox 的第一实现。
- migration 归属：`agent-bus` 自有 Flyway，还是共享 schema 模块，还是 `agent-runtime` 受控路径。
- RLS：是否启用 Postgres RLS，还是先只依赖应用层 tenant filter + unique key。
- 测试方式：是否允许 Testcontainers / embedded DB / SQL contract test。

推荐默认：

- 若项目已有 Postgres / Flyway 治理路线，采用 Postgres + Flyway，但 production dependency 必须显式进入 `agent-bus` 的 module metadata / forbidden dependency review。
- 如果归属未确认，则不要引入 JDBC / Flyway，只交付更完整的 SQL contract 和 adapter interface。

DoD：

- 决策写入 `forwarding-persistence.md` 和 review packet。
- 若引入依赖，更新 `pom.xml`、module metadata、forbidden dependency / ArchUnit 规则和 L1 development view。

### 切片 6：持久化 adapter skeleton 或 SQL contract harness

按切片 5 的裁决分两种路径：

路径 A：允许真实 DB adapter。

- 新增 `JdbcForwardingOutboxRepository` 或等价 adapter。
- 使用受治理的 transaction / CAS 方式实现 claim。
- 不接真实 receiver transport。
- 不写 Task execution state。
- 不绕过 routeHandle。

路径 B：DB 归属未确认。

- 不引入 JDBC。
- 新增 SQL contract 文档和 harness，锁定 claim SQL、state update SQL、DDL check constraints。
- in-memory harness 继续作为行为替身。

DoD：

- 无论 A/B，lease-owner guarded mutation 都被测试覆盖。
- 不允许出现“只补文档、没有任何代码/测试变化”的 Stage 9。

### 切片 7：文档同步

同步以下文档：

- `architecture/docs/L1/agent-bus/README.md`
- `architecture/docs/L1/agent-bus/development.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/physical.md`
- `architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md`
- `architecture/docs/L2/agent-bus/forwarding-persistence.md`
- `docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md`
- `docs/architecture/l0/05-contracts/machine-readable/agent-bus-forwarding-runtime.v1.yaml`

同步重点：

- Stage 9 是 lease-safe / persistence-ready，不要宣称完整生产转发已落地。
- 若未引入真实 DB，文档必须继续标注 DDL / SQL 为 contract / draft。
- 若引入真实 DB，文档必须写清 migration 归属、事务边界、回滚策略和运行时开关。

## 5. Stage 9 可接受结果

可以接受：

- lease-owner guarded mutation 完成。
- stale worker 无法修改被 reclaim 的消息。
- terminal / retry lease 生命周期闭环。
- record 条件不变量进入 Java、schema、DDL、harness。
- failure code 分类明确。
- DB / migration 归属有明确裁决，或明确延期且有 SQL contract harness。

不能接受：

- 直接写 JDBC adapter，但状态变更不校验 lease owner。
- 继续让 terminal / retry 记录残留 active lease。
- 继续只靠文档描述条件字段，不在 Java / DDL / harness 里约束。
- retry 接受 non-retryable failure code。
- 把 routeHandle 解包成物理 endpoint。
- 让 `agent-bus` 写 Task execution state。

## 6. 给施工智能体的提示

这轮任务应继续是大批次，但不要把“接真实 DB”放在并发语义之前。推荐一次提交包含：

1. lease-owner guarded mutation API。
2. lease 生命周期闭环。
3. record 条件不变量。
4. failure-code classification。
5. DB / migration 归属裁决。
6. adapter skeleton 或 SQL contract harness。
7. L1 / L2 / ICD / schema 同步。

如果 DB / migration 归属没有得到人类确认，则 Stage 9 仍然必须完成 1-4 和 SQL contract harness，不应回退成纯文档阶段。

## 7. 执行完成记录（Stage 9 已完成）

Stage 9 全部切片 1-7 已完成。验证：`mvn -pl agent-bus test` **129 tests green，BUILD SUCCESS**（`AgentBusForwardingRuntimeContractTest` 29 个，含 7 个新增 Stage 9 行为测试）。工作树未提交（待人类决定 commit / 开分支）。

逐切片 DoD 落地：

| 切片 | 状态 | 落地证据 |
|---|---|---|
| 1 lease-safe mutation | ✓ | `ForwardingOutboxPort` 四方法（`markAcked`/`scheduleRetry`/`moveToDlq`/`markExpired`）带 `leaseOwner`；`markDispatching` 移除（DISPATCHING 只经 `claimDue`）；新增 `ForwardingLeaseException`（RECORD_NOT_FOUND/NO_LEASE/OWNER_MISMATCH/NOT_DISPATCHING）；`ForwardingDispatcherWorker` 传 `leaseOwner`；`InMemoryForwardingOutbox.leaseGuardedMutate`；harness `stale_worker_acks_after_lease_reclaimed_by_another_owner_fails` + `lease_guarded_mutation_reports_record_not_found_and_no_lease` |
| 2 lease 生命周期闭环 | ✓ | ACKED/DLQ/EXPIRED/RETRY_SCHEDULED 清 lease，仅 DISPATCHING 持 lease；harness `terminal_and_retry_states_clear_lease_only_dispatching_holds_it` |
| 3 record 条件不变量 | ✓ | `ForwardingOutboxRecord.validateStatusInvariants` / `ForwardingInboxRecord.validateStatusInvariants`（Java 构造器）；DDL `ck_outbox_*` / `ck_inbox_*` CHECK；harness `outbox_record_rejects_invalid_status_invariants` + `inbox_record_rejects_invalid_status_invariants` |
| 4 failure-code classification | ✓ | `ForwardingFailureCode.Classification`（retryable/non-retryable/dedup）；`ForwardingDeliveryResult.retry(...)` 只接 retryable、`dlq(...)` 拒 dedup；harness `failure_code_classification_drives_retry_and_dlq_routing` |
| 5 DB/migration 归属裁决 | ✓ | **路径 B**（DB 归属未由人类确认，不引入 JDBC/Flyway）；决策写入 `forwarding-persistence.md §11` + `decision.md §8`；不引入依赖（pom 无改动，N/A） |
| 6 持久化 SQL contract harness | ✓ | `forwarding-persistence.md` §7 DDL 补完整 CHECK + §7.1 claim SQL（`FOR UPDATE SKIP LOCKED`）+ §7.2 lease-owner guarded state-update SQL；harness `forwarding_persistence_ddl_enforces_record_invariants`；非纯文档（生产代码 + 测试大量变更） |
| 7 文档同步 | ✓ | L1 README/development/process/physical、L2 forwarding-outbox-inbox/forwarding-persistence、ICD、yaml、review packet（decision.md）—— 共 9 文档标注 Stage 9 lease-safe/persistence-ready（路径 B），DDL/SQL 仍为 contract/draft |

护栏自检（§5「不能接受」均未发生）：未写 JDBC adapter；terminal/retry 不残留 active lease；条件字段进 Java + DDL + harness（非仅文档）；retry 拒 non-retryable；routeHandle 未解包成物理 endpoint；`agent-bus` 未写 Task execution state。

模块依赖未变（`agent-bus/pom.xml` 仍仅 `spring-boot-starter-test` + `archunit`）；`AgentBusForwardingSpiPurityTest` / `AgentBusDependencyBoundaryTest` 仍 green（生产代码无 `java.sql` / broker）。真实 JDBC adapter / Flyway migration / 真实投递绑定仍 deferred（DB 归属裁决后）。

