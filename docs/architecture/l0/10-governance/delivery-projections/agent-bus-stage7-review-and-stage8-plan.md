---
artifact_type: delivery_projection
version: agent-bus-stage7-review-and-stage8-plan
status: draft
source_commit: c35c2d09
source_stage7_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage6-review-and-stage7-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
target_module: agent-bus
---

# agent-bus Stage 7 评审与 Stage 8 计划

## 0. 结论

最新提交 `c35c2d09` 可以作为 Stage 7 的阶段性成果接受：它已经从纯文档推进到 `agent-bus` 的 forwarding SPI、状态机、in-memory 测试替身和契约 harness，方向符合上一阶段“C3 outbox / inbox 最小骨架”的目标。

但它还不能直接进入真实持久化实现。当前最大风险不是“代码太少”，而是**契约、L2 文档和代码骨架之间还有几处没有完全对齐**。Stage 8 应该继续保持大批次推进，但第一步必须先把这些不一致收口，再接入真实数据库 / migration / lease / polling。

简短判断：

- 这次提交以代码为主，节奏比前几轮正确。
- Stage 7 的边界控制较好：未引入 broker / MQ client，未引入 JDBC，未写 Task execution state。
- Stage 8 可以开始做真实持久化，但不能直接开写 JDBC；必须先补齐 outbox / inbox record 模型、claim / lease 端口和 dispatcher 职责边界。

## 1. 本次提交审查

### 1.1 完成情况

本次提交完成了以下内容：

- C3 裁决从“等待裁决”推进到“默认采用 C3，待 H2/H3 最终确认”。
- 新增 L2：`architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md`。
- 新增 runtime ICD 与 machine-readable schema。
- 新增 forwarding SPI：
  - `ForwardingEnvelope`
  - `ForwardingMessageId`
  - `ForwardingRouteHandle`
  - `ForwardingStatus`
  - `ForwardingFailureCode`
  - `ForwardingReceipt`
  - `ForwardingDispatcher`
  - `ForwardingOutboxPort`
  - `ForwardingInboxPort`
- 新增 `ForwardingStateMachine`。
- 新增 in-memory test double。
- 新增契约测试和 SPI purity 测试。
- 同步 L1 文档。

验收判断：

- Stage 7 不再只是 md，符合“开始写代码”的要求。
- 代码抽象整体保持了纯 Java 的 SPI / 状态机风格，没有过早引入 Spring、JDBC、broker、HTTP transport。
- 代码边界没有明显越过 Stage 4 / Stage 7 的禁止范围。
- 但 contract-to-code 投影还不完整，Stage 8 前必须修正。

## 2. 当前修改意见

| 编号 | 问题 | 严重度 | 证据 | 修改意见 |
|---|---|---|---|---|
| MI8-001 | L2 端口投影声明了 `findRetryable(now)`，但 `ForwardingOutboxPort` 没有对应方法。 | 高 | `forwarding-outbox-inbox.md` §8 列出 `findRetryable(now)`；`ForwardingOutboxPort` 只包含 enqueue / mark / status 方法。 | Stage 8 必须把 due-message 查询升级为 claim / lease 端口，而不是简单补一个裸 `findRetryable`。 |
| MI8-002 | runtime schema 把 `sourceServiceId`、`targetServiceId` 定义为 outbox record 必填字段，但当前代码没有显式 outbox record 模型承载它们。 | 高 | `ICD-agent-bus-forwarding-runtime.md` outbox 字段表要求两者必填；`ForwardingEnvelope` 只有 messageId / tenantId / trace / correlation / idempotency / routeHandle / capability / deadline / payloadPolicy / payloadRef。 | Stage 8 先新增 `ForwardingOutboxRecord` / `ForwardingInboxRecord`，或明确这些字段从 envelope / routeHandle 投影而来；不能让持久化实现自己猜。 |
| MI8-003 | `ForwardingDispatcher` 的注释说它驱动 enqueue → DISPATCHING → ACK / RETRY / DLQ / EXPIRED，但当前接口方法只做 synchronous enqueue ack。 | 中 | `ForwardingDispatcher.java` 类注释与 `dispatch(envelope, now)` 方法注释语义不完全一致。 | Stage 8 应拆清 `ForwardingGateway` 的 accept/enqueue 与 `ForwardingDispatcherWorker` 的 claim/deliver/ack/retry 职责。 |
| MI8-004 | L2 写到 `idempotencyKey` 可作为辅助去重触发，但 schema / in-memory inbox / contract test 实际只按 `(tenantId, messageId, consumerServiceId)` 去重。 | 中 | `forwarding-outbox-inbox.md` §5 写“可由任一命中触发”；schema dedup_key 没有 `idempotencyKey`。 | Stage 8 必须二选一：把 `idempotencyKey` 降级为审计字段，或把它纳入明确唯一约束 / 查询语义。 |
| MI8-005 | C3 状态仍是“待 H2/H3 最终确认”，但代码已经提交。 | 中 | decision frontmatter status 为 `adopted-c3-pending-final-confirmation`。 | Stage 8 第一项应由人类确认 C3；确认后改为 adopted，未确认前真实 DB 实现不能进入生产路径。 |

## 3. Stage 8 目标

Stage 8 的目标是把 Stage 7 的“最小骨架”推进为“可落真实持久化的运行态底座”：

> 完成 C3 最终确认，补齐 outbox / inbox record 模型与 claim / lease 端口，提供数据库 migration 草案和一个最小持久化实现方案，并用 harness 锁住 tenant、状态机、幂等、重试、DLQ、禁止 Task state 等边界。

Stage 8 可以是一个较大批次，不再拆成只补文档的小任务。但它必须按顺序做：先收口契约和模型，再写持久化实现。

## 4. Stage 8 开发切片

### 切片 1：C3 最终确认与文档收口

修改 `agent-bus-forwarding-runtime-decision.md`：

- 若人类确认 C3，将状态从 `adopted-c3-pending-final-confirmation` 改为 `adopted-c3`。
- 删除“若 H2/H3 反对则撤回代码”的悬置表述，改为“后续变更需 ADR / review packet”。
- 明确 Stage 8 的代码许可范围：真实持久化 schema、migration、claim / lease、repository adapter、dispatcher worker skeleton。

DoD：

- C3 不再处于半确认状态。
- Stage 8 不再被治理状态阻塞。

### 切片 2：record 模型补齐

在 production code 中新增显式 record 模型：

- `ForwardingOutboxRecord`
- `ForwardingInboxRecord`
- 可选：`ForwardingLease`
- 可选：`ForwardingConsumer`

最低字段要求：

- outbox record 必须显式承载 runtime ICD 的必填字段：`tenantId`、`messageId`、`sourceServiceId`、`targetServiceId`、`routeHandle`、`status`、`attemptCount`、`createdAt`、`updatedAt`。
- inbox record 必须显式承载：`tenantId`、`messageId`、`consumerServiceId`、`status`、`receivedAt`。
- `payloadRef`、`nextAttemptAt`、`lastFailureCode`、`consumedAt`、`failureCode` 按条件字段处理。

关键决策：

- `sourceServiceId` 是否进入 `ForwardingEnvelope`，还是只在 `ForwardingOutboxRecord` 中由 gateway 写入。
- `targetServiceId` 是否可从 opaque `routeHandle` 外部元数据投影，或需要 discovery 返回额外 audit metadata。
- `idempotencyKey` 是审计字段还是去重字段。

DoD：

- runtime ICD、machine-readable schema、Java record 三者字段一致。
- Contract test 不只检查文档包含字段，也检查 Java record 字段。

### 切片 3：claim / lease 端口设计与实现骨架

不要简单实现 `findRetryable(now)`。真实持久化需要防止多实例重复抢同一条消息，所以 Stage 8 应引入 claim / lease 语义。

建议新增端口方法：

```java
List<ForwardingOutboxRecord> claimDue(
        String tenantId,
        long nowMillisEpoch,
        int limit,
        String leaseOwner,
        long leaseUntilMillisEpoch);

ForwardingStatus.Outbox markAcked(...);
ForwardingStatus.Outbox scheduleRetry(...);
ForwardingStatus.Outbox moveToDlq(...);
ForwardingStatus.Outbox markExpired(...);
```

需要明确：

- claim 必须 tenant-scoped。
- 同一条 outbox record 同一时刻只能被一个 lease owner 持有。
- lease 过期后可重新 claim。
- `nextAttemptAt <= now` 才能进入 claim。
- terminal 状态不可再 claim。
- claim / 状态迁移必须在同一事务或等价 CAS 中完成。

DoD：

- L2 不再只写 `findRetryable`，而是改为 claim / lease。
- 端口、状态机、schema 对 lease 字段一致。

### 切片 4：数据库 schema 与 migration 草案

新增 Stage 8 持久化设计文档，建议路径：

- `architecture/docs/L2/agent-bus/forwarding-persistence.md`

新增 migration 草案或 DDL 草案，具体路径由模块负责人决定。若进入生产代码，建议落在 `agent-bus/src/main/resources/db/migration/`；若当前模块尚未接 Flyway，则先放设计草案，不要伪装成已执行 migration。

最低 schema：

- `agent_bus_forwarding_outbox`
- `agent_bus_forwarding_inbox`

最低约束：

- outbox unique key：`tenant_id + message_id`。
- inbox unique key：`tenant_id + message_id + consumer_service_id`。
- 所有表必须有 `tenant_id`。
- 状态字段必须受 enum / check constraint 或等价约束保护。
- 如果采用 Postgres，必须说明 RLS 是否启用、何时启用、迁移顺序和回滚方式。

DoD：

- DDL / migration 草案与 runtime ICD 字段一致。
- 有 rollback / compatibility 说明。
- 不引入没有治理确认的数据库产品绑定。

### 切片 5：dispatcher worker skeleton

在不接真实 receiver transport 的前提下，新增最小 worker 编排：

1. claim due outbox records。
2. 标记 DISPATCHING。
3. 调用抽象 delivery port。
4. 根据结果 ACK / RETRY / DLQ / EXPIRED。

建议新增：

- `ForwardingDeliveryPort`
- `ForwardingDeliveryResult`
- `ForwardingDispatcherWorker`

边界：

- delivery port 只能消费 `routeHandle`，不能暴露物理 endpoint。
- worker 不写 Task execution state。
- worker 不拥有 registry，只消费已投影的 route handle。
- 真实 HTTP / gRPC / broker binding deferred 到后续阶段。

DoD：

- worker 可以用 fake delivery port 完成 ACK / RETRY / DLQ 测试。
- 不引入真实网络依赖。

### 切片 6：harness 增强

新增或增强测试：

- Java record 字段与 runtime ICD / schema 一致。
- `sourceServiceId`、`targetServiceId` 必填。
- claim due 只返回 tenant 内、非 terminal、到期、未被有效 lease 持有的消息。
- 同一消息不能被两个 lease owner 同时 claim。
- lease 过期后可重新 claim。
- terminal 状态不可 claim。
- retry 会更新 `attemptCount`、`nextAttemptAt`、`lastFailureCode`。
- DLQ / EXPIRED 为终态。
- persistence adapter 不写 Task execution state。
- production forwarding 仍不得引入 concrete broker / MQ client。

验证命令由施工智能体执行并记录结果：

```powershell
.\mvnw.cmd -pl agent-bus test
rg -n "TaskExecution|TaskStatus|payloadBody|payload_body|physicalEndpoint|Kafka|RabbitMQ|RocketMQ|NATS" agent-bus
```

DoD：

- 验证结果写入提交说明或阶段报告。
- 未执行验证必须说明原因。

### 切片 7：L1 / L2 同步

同步以下文档：

- `architecture/docs/L1/agent-bus/README.md`
- `architecture/docs/L1/agent-bus/development.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/physical.md`
- `architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md`

同步重点：

- Stage 8 从“最小骨架”进入“真实持久化准备 / 初始实现”。
- 明确 Gateway、Dispatcher Worker、Outbox、Inbox、Delivery Port 的边界。
- 明确真实 receiver transport 仍未落地，避免文档把能力写过头。

## 5. Stage 8 可接受结果

可以接受：

- C3 状态最终确认。
- record / schema / Java 类型字段一致。
- claim / lease 端口清楚。
- 有数据库 schema / migration 草案，或在治理允许下有最小 migration。
- 有 worker skeleton 和 fake delivery port 测试。
- 没有 concrete broker / MQ client。
- 没有 Task execution state 写入。

不能接受：

- 直接写 JDBC adapter，但没有 record / schema / migration 对齐。
- 继续只补文档，没有代码推进。
- 用 `findRetryable(now)` 代替 claim / lease，导致多实例重复投递风险。
- 让 routeHandle 解包为物理 endpoint 并暴露到 SPI。
- 让 `agent-bus` 写 Task 状态。

## 6. 给施工智能体的提示

这轮任务可以比 Stage 7 更大，但不要跳过模型对齐。推荐一次提交包含：

1. C3 最终确认。
2. outbox / inbox record 类型。
3. claim / lease 端口。
4. persistence L2 设计与 schema / migration 草案。
5. dispatcher worker skeleton。
6. harness 增强。
7. L1 / L2 同步。

如果数据库产品或 migration 归属无法确认，则停在 schema 草案 + repository port + in-memory lease harness，不要直接引入生产数据库依赖。
