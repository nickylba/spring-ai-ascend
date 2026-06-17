---
artifact_type: delivery_projection
version: agent-bus-stage13-review-and-stage14-plan
status: draft
source_commit: 0aad3702
source_stage13_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage12-review-and-stage13-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_transport_candidates: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
target_module: agent-bus
---

# agent-bus Stage 13 评审与 Stage 14 计划

## 0. 结论

最新提交 `0aad3702` 可以作为 Stage 13 的阶段性成果接受：transport 投递模型候选评审 review packet 完整产出（4 候选 T1-T4 × 8 维度评分矩阵 + push / pull / MQ 反压根本分析 + 非裁决推荐 + rejection criteria + deliver 重投策略子项），**无生产代码**（裁决阶段，性质同 Stage 5 / Stage 6），**153 tests green**，§6.2 始终不得项不变，最终 push / pull / MQ 裁决明确留给 H2/H3。Stage 13 把 Stage 12 暴露的 transport 议题（MI13-T）做了一次结构化的候选评审，核心结论清晰：**人类「基于 MQ 获反压」的诉求，内核 = 消费方控速 / 速率解耦，而 MQ 只是满足该内核的载体之一**——T3 consumer-pull over DB 是唯一同时满足「强反压」+「不破 §6.2」+「低复杂度」的候选，作为非裁决推荐进入 H2/H3。

但诚实评审暴露了 Stage 13 范围之外、由「最终裁决待 H2/H3」明确 deferred 的若干未决项——它们都不是 Stage 13 的 bug（Stage 13 是裁决阶段，范围是评审不是实现），而是 C3 从「可持久化」走向「可真正投递」前仍待输入的项：

- **MI13-T 最终投递模型裁决未下**：Stage 13 只给非裁决推荐（T3 倾向），未下定论；真实 transport 实现（deliver 的真实绑定）仍 deferred，依赖 H2/H3 在 review packet 后裁决。
- **MI13-R receiver 端缺失**：`JdbcForwardingInbox` 的 `markConsumed` / `markRejected` 仍无生产调用方；补齐方式（T1 暴露端点 vs T3 内置 pull worker）取决于 MI13-T 裁决。
- **MI13-O 调度运维化**：`ForwardingDispatchLoop` 骨架在，无真实 scheduler / polling cadence / 并发 worker 分片。
- **MI13-D deliver 异常重投策略未实现**：Stage 13 把它拆为「可独立于投递模型先行」的子项（review packet §7.4），给了落点建议（retry policy 端口，与 `DispatchLeasePolicy` 同层），但**未写代码**。

简短判断：

- Stage 13 方向正确：评审完整、反压根本分析到位、不预设方向（非裁决推荐而非定论）、§6.2 边界守住（不自行解除引 MQ）、文档同步 8 处一致。
- 上述未决项中，**MI13-D（deliver 重投策略）是唯一不阻塞 H2/H3 投递裁决、不破 §6.2、可立即写最小纯 Java 生产代码的子项**——Stage 13 review packet §7.4 已明确「可独立先行」。其余（transport 实现 / receiver / 调度运维化）均依赖 H2/H3 裁决。
- Stage 14 主轴经人类确认为 **deliver 重投策略先行**：把退避计算从 `ForwardingDeliveryPort.deliver` 抽到独立的 `ForwardingRetryPolicy` 端口（关注点分离：投递动作 vs 重投治理），落地指数退避（base + jitter）+ attemptCount 上限（耗尽 → DLQ / EXPIRED）；熔断（per-routeHandle circuit break）因需 per-route 有状态且与投递模型耦合，**只留端口骨架 + no-op 默认，deferred**。纯 Java，不破 §6.2。

## 1. 本次提交审查

### 1.1 完成情况

本次提交（`0aad3702`，experimental，fast-forward `2f5cc328..0aad3702` 推送，7 files changed, 477 insertions, 10 deletions）完成：

- **新增 transport candidates review packet** [`agent-bus-forwarding-runtime-transport-candidates`](../review-packets/agent-bus-forwarding-runtime-transport-candidates.md)：§0 评审边界 + 禁止范围 / §1 五个评审问题 / §2 候选 T1-T4（每个带维度表）/ §3 八项评审维度 / §4 投递模型边界定义（dispatcher 归属 + 投递触发方）/ §5 评分矩阵（强 / 中 / 弱 × 8 维度，权重标注）/ §6 反压根本分析（复议 C3 dispatcher-push：push 无消费方控速 vs pull / broker 天然反压）/ §7 小结（trade-off + 非裁决推荐 + rejection criteria + deliver 重投策略子项 + 不下定论的 + 护栏）/ §8 后续。
- **核心结论**（§6 / §7）：反压诉求内核 = 消费方控速 / 速率解耦，MQ 只是载体之一；T3 consumer-pull over DB 是唯一不破 §6.2 的强反压候选（复用 Stage 12 claim / lease / `SKIP LOCKED` / RLS，代价是 dispatcher 归属 sender→receiver），作为非裁决推荐进入 H2/H3；T2 / T4（broker）反压强但需 H2/H3 解除 §6.2；T1（push RPC）反压弱。
- **deliver 重投策略子项**（§7.4）：指数退避（`nextAttemptAt = now + base * 2^attempt + jitter`，base + jitter 防惊群）、attemptCount 上限（retryable 耗尽 → DLQ / EXPIRED）、熔断（per-routeHandle 持续 `receiver_unavailable`）；落点 retry policy 端口（与 `DispatchLeasePolicy` 同层）；**可独立于投递模型先行**。
- **文档同步（8 处）**：[`decision §8`](../review-packets/agent-bus-forwarding-runtime-decision.md) transport 议题段（评审完成 + 推荐方向 + 待 H2/H3）+ 相关文档列表（补 Stage 12 / 13 计划 + transport candidates）；[`ICD`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md) §边界标题 + Stage 13 边界条目 + Open Issues transport 项；yaml `stage13_scope`（`nature: decision-review`，4 候选 × 8 维度）；L1 README 标题 + 阶段记录（补 Stage 10-13）+ 后续工作 Stage 13 条；physical.md 转发边界 + §5.2。
- Stage 13 计划 [`agent-bus-stage12-review-and-stage13-plan`](agent-bus-stage12-review-and-stage13-plan.md) 随附（含 Stage 12 回顾 §0-§2）。

### 1.2 验收判断

- review packet 覆盖 decision §8 全部 deferred（push vs pull / 是否引 MQ / C3+broker hybrid + 反压 + deliver 重投策略 + 续约耗时），4 候选 × 8 维度评分矩阵完整，暴露 trade-off 而**不预设方向**。
- **无 Java 改动**，**153 tests green**（Stage 12 基线保持，裁决阶段符合预期）。
- §6.2 始终不得项不变；ArchUnit 纯度 green（Spring/JDBC 仍限 `persistence.jdbc` 子包）。
- 文档相对路径全部用 reorganize 后的新路径（L1-High-Level-Design / L2-Low-Level-Design），无新死链。
- **但 transport 真实实现 / receiver 端 / 调度运维化是 Stage 13 范围外的 deferred scope**，Stage 13 评审不视为缺陷，而视为 Stage 14+ 的输入。

## 2. 当前修改意见

| 编号 | 问题 | 严重度 | 证据 | 修改意见 |
|---|---|---|---|---|
| MI14-D | deliver 异常重投策略未实现：`attemptCount` 递增已有，但退避（backoff）/ attemptCount 上限 / 熔断均缺失；当前 worker RETRY 分支透传 `deliver` 返回的 `nextAttemptAt`，无治理层退避 | 中 | `ForwardingDispatcherWorker.runOnce`（`runtime`，RETRY_SCHEDULED 分支直接 `scheduleRetry(..., result.nextAttemptAtMillisEpoch())`）；`ForwardingDeliveryResult.retry(code, nextAttemptAtMillisEpoch)`；模块内无任何 `RetryPolicy` / `Backoff` / `CircuitBreaker` 类 | **Stage 14 主轴**：新增 `ForwardingRetryPolicy` 端口（指数退避 base+jitter + `exhausted(attemptCount)` 上限判定），worker RETRY 分支接入（exhausted → DLQ；否则 policy 算 nextAttemptAt）；落点 `forwarding.runtime` 包，纯 Java |
| MI14-X | `ForwardingDeliveryResult.retry` 携带 `nextAttemptAtMillisEpoch`，但退避应归 retry policy（关注点分离）：deliver 是投递动作，不该承载重投治理 | 低-中 | `ForwardingDeliveryResult` record 第三个字段 `nextAttemptAtMillisEpoch`；worker 透传该字段 | Stage 14 内：退避归 policy 后，`retry` 简化为 `retry(ForwardingFailureCode)`（移除 `nextAttemptAtMillisEpoch` 参数 / 字段或废弃），in-memory fake + contract test 同步；这是 Stage 8 契约的小幅演进，由 harness 锁定一致性 |
| MI14-C | 熔断（per-routeHandle circuit break）未定：需 per-route 有状态（成功 / 失败计数、开关），且与投递模型耦合（T1 push 下意义大；T3 pull 下 receiver 不拉即天然熔断） | 低-中 | 无 `CircuitBreaker` 类；review packet §7.4 标注熔断为复杂子项 | Stage 14 只留 `ForwardingCircuitBreaker` 端口骨架 + no-op 默认实现，标注 deferred；**不接 worker 逻辑**（避免与未裁决的投递模型耦合），真实熔断 deferred H2/H3 裁决后 |
| MI13-T | transport 最终投递模型裁决（T1 / T2 / T3 / T4）未下 | 高 | [`transport-candidates`](../review-packets/agent-bus-forwarding-runtime-transport-candidates.md) 给非裁决推荐 T3；decision §8 标注待 H2/H3 | deferred H2/H3（不阻塞 Stage 14）；真实 transport 实现 earliest Stage 14 之后，依赖裁决 |
| MI13-R | receiver 端缺失：`markConsumed` / `markRejected` 无生产调用方 | 中 | `JdbcForwardingInbox` 方法无生产调用方 | deferred（补齐方式取决于 MI13-T：T1 暴露端点 vs T3 pull worker） |
| MI13-O | 调度运维化：无真实 scheduler / polling cadence / 并发 worker 分片 | 低-中 | `ForwardingDispatchLoop` 骨架无 scheduler / 线程 | deferred 生产化 / 运维化阶段 |

## 3. Stage 14 目标

Stage 14 的目标是落地 Stage 13 review packet §7.4 明确「可独立先行」的 **deliver 异常重投策略子项**——这是唯一不阻塞 H2/H3 投递裁决、不破 §6.2、可立即写最小纯 Java 生产代码的推进项。核心设计动作是**关注点分离**：

> 把退避计算从 `ForwardingDeliveryPort.deliver`（投递动作）抽到独立的 `ForwardingRetryPolicy` 端口（重投治理）。投递动作只负责「这次投递成功 / 失败（带 failureCode）」；下次重试的时机与是否耗尽，归治理策略。

Stage 14 主轴经人类确认为 **deliver 重投策略先行**，**有生产代码**（纯 Java，不破 §6.2）：

> 交付 `ForwardingRetryPolicy` 端口 + 默认实现（指数退避 `nextAttemptAt = now + min(cap, base * 2^attempt) + jitter` + `exhausted(attemptCount)` 上限判定）；`ForwardingDispatcherWorker` 的 RETRY 分支接入（exhausted → `moveToDlq`；否则 policy 算 nextAttemptAt → `scheduleRetry`）；`ForwardingDeliveryResult.retry` 简化为 `retry(code)`（退避归 policy）；熔断（`ForwardingCircuitBreaker`）只留端口骨架 + no-op 默认，deferred；harness + contract test 覆盖；L2 / ICD / yaml / decision / L1 同步。

**不触碰**：transport 真实实现（deliver 的 HTTP / gRPC / broker / DB-pull 绑定）、receiver 端代码、调度运维化（scheduler / polling cadence / worker 分片）、agent-runtime 集成——均依赖 H2/H3 投递裁决，deferred Stage 15+。

**与 §6.2 的关系**：retry policy 是横切治理逻辑，纯 Java（`java.lang.Math` / `java.util.function` 即可），不引 broker / MQ / Spring / JDBC，ArchUnit 纯度保持 green。退避 / 上限 / 熔断的实现不解除任何 §6.2 项。

## 4. Stage 14 开发切片

### 切片 0：范围确认 + retry policy 设计

- 确认 Stage 14 = deliver 重投策略子项（退避 + attemptCount 上限），有生产代码（纯 Java），不破 §6.2。
- 设计 `ForwardingRetryPolicy` 端口形态（接口 + 默认实现 record，参照 [`DispatchLeasePolicy`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md) 的 record + 构造器注入 + 静态实例范式）：方法 `nextAttemptAt(ForwardingFailureCode code, int attemptCount, long nowMillisEpoch)` + `exhausted(int attemptCount)`；落点 `com.huawei.ascend.bus.forwarding.runtime`（与 `DispatchLeasePolicy` 同层，纯 Java）。
- 设计 `ForwardingDeliveryResult.retry` 简化（移除 `nextAttemptAtMillisEpoch` 参数 / 字段，退避归 policy）的契约演进路径 + 受影响方（in-memory fake、worker、contract test）。
- 指数退避不变量确认：防溢出（`base * 2^attempt` 用 `long` + `cap` 上限 + `Math.min`）、jitter 可测（注入 jitter 源而非裸 `Math.random`，便于 harness 断言范围）。
- 熔断端口形态确认：`ForwardingCircuitBreaker` 接口骨架 + no-op 默认（`alwaysClosed`），**不接 worker**。

DoD：retry policy 端口签名 + 落点 + `ForwardingDeliveryResult` 简化决策 + 退避不变量 + 熔断边界全部明确；本切片只设计 + 更新 decision §8 / ICD open issues 标注 Stage 14 进行中。

### 切片 1：`ForwardingRetryPolicy` 端口 + 默认实现

- 新增 `ForwardingRetryPolicy`（接口，`runtime` 包，纯 Java）：
  - `long nextAttemptAt(ForwardingFailureCode code, int attemptCount, long nowMillisEpoch)` —— 仅 retryable 码有意义（non-retryable / dedup 不应进入 RETRY 分支，由 Stage 9 `Classification` + worker switch 保证）。
  - `boolean exhausted(int attemptCount)` —— attemptCount 达上限返回 true（worker 据此 → DLQ / EXPIRED）。
- 新增默认实现（record，参数化 `baseMillis` / `capMillis` / `maxAttempts` / jitter 源）：
  - 指数退避：`delay = min(capMillis, baseMillis << attempt)`（用位移 + min 防溢出），`nextAttemptAt = nowMillisEpoch + delay + jitter`。
  - jitter：注入确定性源（如 `LongSupplier` 或 jitter 接口），范围 `[0, delay)` 或 `[-jitterRange, +jitterRange]`，**测试可固定**。
  - `exhausted(attempt)` = `attempt >= maxAttempts`。
- 静态实例参考 `DispatchLeasePolicy.DISABLED`：提供 `NO_BACKOFF`（base=0 / maxAttempts 大）或 `IMMEDIATE` 之类便于测试。

DoD：端口 + 默认实现纯 Java；防溢出 + jitter 可测；ArchUnit 纯度 green。

### 切片 2：worker RETRY 分支接入 + `ForwardingDeliveryResult` 简化

- `ForwardingDispatcherWorker` 构造器增注入 `ForwardingRetryPolicy`（参照 `DispatchLeasePolicy` 注入位）。
- RETRY_SCHEDULED 分支改写：`if (retryPolicy.exhausted(record.attemptCount())) → moveToDlq(...)`（retryable 耗尽走 DLQ，沿用 Stage 9 MI9-004 classification）；`else → scheduleRetry(..., result.failureCode(), retryPolicy.nextAttemptAt(result.failureCode(), record.attemptCount(), clockNow))`。
- `ForwardingDeliveryResult` 简化：`retry(ForwardingFailureCode code)`（移除 `nextAttemptAtMillisEpoch` 参数 / 字段）；in-memory fake `InMemoryForwardingDelivery` 同步（不再算 nextAttemptAt）；保留 `acked` / `dlq` / `expired` 不变。
- 确认 `scheduleRetry` 内部仍递增 attemptCount（Stage 9 不变量不变）；nextAttemptAt 现由 policy 提供。

DoD：worker 用 policy 算退避 + 判耗尽；`ForwardingDeliveryResult` 契约简化一致；in-memory fake + 现有 worker 测试同步通过。

### 切片 3：harness + contract test

- contract test（`AgentBusForwardingRuntimeContractTest` 镜像 ICD）新增 / 强化：
  - 退避单调递增且受 cap 上限（`nextAttemptAt` 随 attempt 递增，达 cap 后不再增长）。
  - `exhausted(maxAttempts)` 触发 DLQ（worker RETRY 分支耗尽 → `moveToDlq`，不再 `scheduleRetry`）。
  - jitter 落在合法范围（注入固定 jitter 源断言）。
  - 仅 retryable 码进入退避路径（non-retryable / dedup 不经 RETRY）。
  - attemptCount 每次 RETRY 递增（Stage 9 不变量回归）。
- harness 方法名逐字镜像 ICD，防漂移（同 Stage 4 约定）。

DoD：retry policy 行为被 contract test 锁定；现有 153 tests 不退化（简化 `ForwardingDeliveryResult` 后受影响测试同步更新）。

### 切片 4：熔断端口预留（deferred）

- 新增 `ForwardingCircuitBreaker`（接口骨架，`runtime` 包，纯 Java）：形态如 `boolean shouldAttempt(routeHandle / targetServiceId)` + 状态更新钩子（`recordSuccess` / `recordFailure`）——具体签名在切片 0 定。
- no-op 默认实现 `alwaysClosed`（恒返回 shouldAttempt=true，不记账）。
- **不接 worker 逻辑**：worker 不调用 circuit breaker（避免与未裁决的投递模型耦合）；标注 deferred 待 H2/H3 投递裁决后接入。
- 文档明确：熔断真实实现（per-route 状态、阈值、半开恢复）deferred，本阶段只占端口位。

DoD：端口骨架 + no-op 默认存在；不接 worker；deferred 标注清楚。

### 切片 5：文档同步

- [`L2 forwarding-persistence`](../../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)：新增 retry policy 段（端口 + 默认实现 + 退避公式 + exhausted → DLQ + 熔断端口预留 deferred）。
- [`ICD`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)：§边界加 Stage 14 条目；Open Issues 的 deliver 重投策略项更新（退避 + 上限已落地，熔断 deferred）；`ForwardingDeliveryResult.retry` 简化同步字段说明。
- yaml `agent-bus-forwarding-runtime.v1.yaml`：`stage14_scope`（retry-policy + worker 接入 + result 简化 + circuit-breaker-port-deferred）。
- [`decision §8`](../review-packets/agent-bus-forwarding-runtime-decision.md)：transport 议题段补「deliver 重投策略子项 Stage 14 落地（退避 + 上限），熔断 deferred」。
- L1（README / physical）：§5.2 / 后续工作加 Stage 14 条。

DoD：文档与代码一致；不宣称 transport / 熔断已落地（仍是 deferred）。

### 切片 6：构建验证 + 提交

- `mvn -f /mnt/nas/tianye/openclaw/workspace/spring-ai-ascend/agent-bus/pom.xml test -s ~/.m2/settings.xml`：Stage 14 新增 retry policy 测试，总数应 > 153；ArchUnit 纯度 green。
- 提交（retry policy 端口 + 实现 + worker 接入 + result 简化 + circuit breaker 端口 + harness + 文档同步 + 本 plan）。

DoD：tests green（> 153）；ArchUnit 纯度 green；commit；§6.2 不变。

## 5. Stage 14 可接受结果

可以接受：

- `ForwardingRetryPolicy` 端口 + 默认实现（指数退避 base + jitter + cap 防溢出 + exhausted 上限）落地，纯 Java，落点 `forwarding.runtime`。
- `ForwardingDispatcherWorker` RETRY 分支接入 policy（exhausted → DLQ；否则 policy 算 nextAttemptAt）；`ForwardingDeliveryResult.retry` 简化为 `retry(code)`，契约演进由 harness 锁定一致。
- contract test 覆盖退避单调 / cap / exhausted→DLQ / jitter 范围 / 仅 retryable 退避 / attemptCount 递增；tests green（> 153）。
- `ForwardingCircuitBreaker` 端口骨架 + no-op 默认存在，**不接 worker**，deferred 标注清楚。
- ArchUnit 纯度 green（retry policy / circuit breaker 仍纯 Java，不引 Spring / JDBC / broker）。
- §6.2 始终不得项不变；L2 / ICD / yaml / decision / L1 同步。

不能接受：

- 实现真实 transport（HTTP / gRPC / broker / DB-pull 绑定）或 receiver worker 或 scheduler——依赖 H2/H3 投递裁决，本阶段不做。
- 自行解除 §6.2 引入 concrete broker / MQ client 依赖。
- 熔断接 worker 逻辑（只留端口 + no-op；真实熔断 deferred）。
- 退避公式有溢出风险（`base * 2^attempt` 未用 long + cap + min 保护）或 jitter 不可测（裸 `Math.random` 无法 harness 断言）。
- 让 `agent-bus` 写 Task execution state；绕过 `routeHandle`；放 payload body / token stream（§6.2 始终不得）。

## 6. 给施工智能体的提示

- **范式参考**：`ForwardingRetryPolicy` 照 `DispatchLeasePolicy`（record + 构造器注入 + 静态实例 `DISABLED`）的注入范式；端口是接口（可插拔算法）+ 默认实现 record（参数化 base / cap / maxAttempts / jitter 源）。
- **退避防溢出**：`delay = min(capMillis, baseMillis << attempt)`——用位移 + `Math.min`，不要 `base * Math.pow(2, attempt)`（double 转 long 有精度 / 溢出风险）；`attempt` 是 int，位移到 63 以上要靠 cap 兜底。
- **jitter 可测**：注入 `LongSupplier` 或 jitter 接口（不要裸 `Math.random()`），harness 用固定源断言 `[0, delay)` 范围；防惊群是 jitter 的目的，不是随机性本身。
- **`ForwardingDeliveryResult` 简化是契约演进**：移除 `nextAttemptAtMillisEpoch` 参数 / 字段后，同步 `InMemoryForwardingDelivery` + 所有引用 `retry(...)` 的测试 + contract test；harness 锁定一致性（Stage 4 约定）。
- **熔断只占端口**：`ForwardingCircuitBreaker` 接口 + `alwaysClosed` no-op，**worker 不调用**——真实熔断需 per-route 状态且与投递模型耦合（T1 push 下需主动熔断，T3 pull 下 receiver 不拉即天然熔断），deferred H2/H3 裁决后。
- **ArchUnit 纯度**：retry policy / circuit breaker 在 `forwarding.runtime`（或 `runtime.spi`），纯 Java；`Math` / `java.util.function.*` 允许；不引 Spring / JDBC / broker（ArchUnit 仍全局禁 hikari / jackson / reactor / kafka / nats / servlet / netty，Spring/JDBC 限 `persistence.jdbc`）。
- **构建命令（cwd 跑偏教训）**：本会话 Bash cwd 曾跑偏到 `review-packets/` 子目录，相对 `agent-bus/pom.xml` 找不到；**用绝对路径** `mvn -f /mnt/nas/tianye/openclaw/workspace/spring-ai-ascend/agent-bus/pom.xml test -s ~/.m2/settings.xml`（system mvn 3.6.3 + Red Hat JDK 21，见 build-env-maven-via-settings-xml）；git 操作用 `git -C <repo-root>` 规避 cwd 偏移。
- **测试基线**：当前 153 green；Stage 14 加 retry policy 行为测试后应 > 153；`ForwardingDeliveryResult` 简化会改动少量现有测试，保持语义等价。
- **范围外（本阶段不动）**：真实 transport 实现、receiver 端代码、调度运维化（polling / scheduler / worker 分片）、agent-runtime 集成、熔断真实实现——均 deferred Stage 15+，依赖 H2/H3 投递裁决。

## 7. 执行记录

（计划阶段；Stage 14 执行后回填：切片 0-6 完成情况、retry policy 端口落位、worker 接入、`ForwardingDeliveryResult` 简化、circuit breaker 端口预留、tests green（> 153）、ArchUnit 纯度、commit。）
