---
artifact_type: a2d_review_packet
version: "agent-bus-forwarding-runtime-candidates"
status: draft
source_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage4-review-and-stage5-plan.md"
source_icd: "docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md"
source_l1: "architecture/docs/L1/agent-bus/README.md"
target_module: agent-bus
---

# agent-bus 运行态转发候选方案评审（Stage 5）

## 0. 评审边界与禁止范围

本评审是 **Stage 5 候选方案评审**，回答「Stage 4 已稳定的 broker-agnostic 转发语义，由哪类运行态承载」。它比较运行态实现候选，形成 H2/H3 级别的选型输入，**不实现运行态、不绑定 broker / MQ 产品、不引入生产依赖、不新增 queue / DLQ / replay 运行态存储**。

评审建立在 Stage 4 已稳定的设计态契约之上：见 [`ICD-Agent-Bus-Forwarding`](../../05-contracts/human-readable/ICD-agent-bus-forwarding.md)（HD4）。本评审不得用任何产品能力反向定义架构语义；产品名（Kafka / NATS / RocketMQ / RabbitMQ 等）在本文中一律是 **示例产品**，仅用于刻画候选形态，不代表选型结论。具体产品选择 deferred 到 H2/H3。

权威输入：[`agent-bus 第四阶段评审与第五阶段计划`](../delivery-projections/agent-bus-stage4-review-and-stage5-plan.md) §3-§5。

## 1. 评审问题

第五阶段必须回答的五个核心问题：

1. 哪类运行态承载 Stage 4 的 forwarding semantics。
2. 哪些能力必须由 bus runtime 提供，哪些可以由底层 broker / 数据库 / runtime-local queue 提供。
3. route handle 如何绑定到实际投递目标。
4. DLQ / replay / ordering / backpressure / timeout 的最小实现边界。
5. tenant isolation、observability、audit、cost 和 capacity 如何落到候选方案。

本评审 **暴露 trade-off，不下选型定论**。下文 §2 给候选、§3 给维度、§4 给最小边界、§5 给评分矩阵、§6 给小结与 deferred 决策。

## 2. 候选方案

| 候选 | 形态 | 一句话定位 |
|---|---|---|
| C1 | in-memory dispatcher | 单进程/单实例早期实现，低复杂度，弱 durable。 |
| C2 | runtime-local queue | 每个 runtime 内部队列，适合同实例或近端协作，跨实例能力弱。 |
| C3 | database outbox / inbox | durable、易审计，吞吐/延迟和 ordering 需要评估。 |
| C4 | external broker | Kafka / NATS / RocketMQ / RabbitMQ 等示例产品，高吞吐解耦，运维复杂度高。 |
| C5 | hybrid | control event + durable outbox + broker dispatch 的组合。 |

> 产品名（Kafka / NATS / RocketMQ / RabbitMQ）仅为刻画 C4 形态的示例，不代表已接受事实；broker 选型 deferred。

### 2.1 C1：in-memory dispatcher

**形态：** 单进程内的内存分发器，forwarding envelope 在内存中从 sender 传到 receiver。

| 维度 | 评估 |
|---|---|
| Stage 4 envelope 承载 | 承载；envelope 是纯契约，内存分发器直接消费 envelope 对象。 |
| route handle 绑定 | dispatcher 内存解析 route handle → 本地 receiver 引用；单实例无需远程寻址。 |
| tenant isolation | 进程内逻辑校验（`tenantId` in-band），无物理/存储隔离。 |
| ack / retry / timeout | 同步 ack 即时；retry 内存重试（进程重启丢失）；timeout 本地计时器。 |
| DLQ / replay | 内存 DLQ 列表（进程重启丢失）；无持久 replay。 |
| ordering | 单线程 dispatcher 可保 per-tenant / per-correlation；并发 worker 难保全局。 |
| backpressure | 有界队列 → 拒绝 / 阻塞；易映射到 `backpressure_rejected`。 |
| durability | 弱（进程崩溃即丢）。 |
| observability / audit | 本地 metrics / log；trace 易；audit 弱（无持久轨迹）。 |
| local dev 复杂度 | 最低。 |
| production 运维 | 低，但不满足 HA / 持久。 |
| Spring Boot / Java 21 / modules | 无新依赖；单模块内。 |
| 新增 production dependency | 无。 |

### 2.2 C2：runtime-local queue

**形态：** 每个 runtime 持有本地队列（阻塞队列 / Disruptor / file-backed queue 等），适合同实例或近端协作。

| 维度 | 评估 |
|---|---|
| Stage 4 envelope 承载 | 承载。 |
| route handle 绑定 | 本地 queue 按 route handle 分流到同实例多 receiver。 |
| tenant isolation | 逻辑隔离（同进程内）。 |
| ack / retry / timeout | 本地 ack；retry 本地；timeout 本地。 |
| DLQ / replay | 本地 DLQ（弱持久，除非 file-backed）；replay 有限。 |
| ordering | per-queue ordering 可保；跨实例 / 跨进程难。 |
| backpressure | 有界队列。 |
| durability | 弱-中（file-backed 可选提升）。 |
| observability / audit | 中。 |
| local dev 复杂度 | 低。 |
| production 运维 | 中（跨实例能力弱，水平扩展受限）。 |
| Spring Boot / Java 21 / modules | 无新依赖，或轻量本地队列库。 |
| 新增 production dependency | 可选（高性能本地队列库）。 |

### 2.3 C3：database outbox / inbox

**形态：** 用数据库表承载 outbox（发送侧写入）与 inbox（接收侧消费），envelope 序列化入行，durable。

| 维度 | 评估 |
|---|---|
| Stage 4 envelope 承载 | 承载；envelope 序列化入 outbox 行。 |
| route handle 绑定 | route handle 存入 outbox 行；inbox consumer 据 route handle 路由。 |
| tenant isolation | DB 行级（`tenantId` 列 + 查询过滤 + 唯一索引）；强，且延续 registry key 的 tenant 维度。 |
| ack / retry / timeout | DB 事务保证 outbox 写入原子性；retry 重扫 outbox（状态机）；timeout 用状态字段。 |
| DLQ / replay | DLQ 表（同库）；replay 重放 outbox 行（幂等 + `idempotencyKey`）；durable。 |
| ordering | per-route / per-tenant ordering via index；并发 consumer 需 careful（避免乱序与重复处理竞争）。 |
| backpressure | 表大小 / tenant quota；polling rate 限速；DB 连接池上限。 |
| durability | 强（DB 事务 + 复制）。 |
| observability / audit | 强（DB 可审计 + metrics + trace 关联 `correlationId`）。 |
| local dev 复杂度 | 中（需 DB，但通常项目已有）。 |
| production 运维 | 中（DB 运维、polling 延迟、吞吐受 DB 上限）。 |
| Spring Boot / Java 21 / modules | 复用现有 DB / JDBC（若项目已有）；无新 broker 依赖。 |
| 新增 production dependency | 可能无（复用现有 DB）。 |

### 2.4 C4：external broker

**形态：** 外部 MQ / broker 承载消息，高吞吐、解耦。示例产品仅用于刻画形态：Kafka（log / partition）、NATS（streams / core）、RocketMQ（topic / DLQ）、RabbitMQ（exchange / queue / DLX）。**产品选择 deferred 到 H2/H3。**

| 维度 | 评估 |
|---|---|
| Stage 4 envelope 承载 | 承载；envelope 作 broker message payload。 |
| route handle 绑定 | route handle 映射到 topic / queue / consumer-group / partition key。 |
| tenant isolation | per-tenant topic / partition / namespace（取决于 broker）；中-强。 |
| ack / retry / timeout | broker ack（commit offset）；retry 重发 / DLQ topic；timeout broker / 客户端配置。 |
| DLQ / replay | broker 原生能力（log retention / streams / DLQ topic / DLX）；强。 |
| ordering | per-partition ordering（partition key）；强（分区维度）。 |
| backpressure | broker 流控（consumer lag / quota / credit）；强。 |
| durability | 强（broker 复制 / 持久化）。 |
| observability / audit | 强（broker metrics），但需集成到统一 trace / metrics 体系。 |
| local dev 复杂度 | 高（需 broker 实例，即使 testcontainer）。 |
| production 运维 | 高（集群运维、容量规划、监控、灾备）。 |
| Spring Boot / Java 21 / modules | 新增 broker client 依赖 + adapter（新 Maven module 或 agent-bus 子包）。 |
| 新增 production dependency | 是（broker client，例如 `kafka-clients` / `nats-java` / `rocketmq-client` / `amqp-client` 等示例）。 |

### 2.5 C5：hybrid

**形态：** 分层组合——控制事件（in-memory 或 broker）+ durable outbox + broker dispatch 大载荷。

| 维度 | 评估 |
|---|---|
| Stage 4 envelope 承载 | 承载（跨控制层 / 数据层）。 |
| route handle 绑定 | 分层绑定（控制 route in-memory / broker，大载荷 route broker / outbox）。 |
| tenant isolation | 分层策略；强但复杂（需保证跨层一致）。 |
| ack / retry / timeout | 分层 ack；复杂（协调多 store）。 |
| DLQ / replay | 分层 DLQ；强但复杂。 |
| ordering | 跨层难保证；中（需因果关系或全局序号）。 |
| backpressure | 分层；强。 |
| durability | 强（outbox + broker 组合）。 |
| observability / audit | 强但分散（需跨层关联 `traceId` / `correlationId`）。 |
| local dev 复杂度 | 最高（多组件）。 |
| production 运维 | 最高（多系统运维）。 |
| Spring Boot / Java 21 / modules | 多依赖，可能多 adapter。 |
| 新增 production dependency | 是（outbox store + broker client）。 |

## 3. 评审维度（13 项）

下列 13 维度是 Stage 5 计划 §4 切片 1 的评审口径，每个候选在 §2 已逐项评估：

1. 是否满足 Stage 4 forwarding envelope（broker-agnostic 契约可承载）。
2. route handle 绑定方式（如何从 opaque route handle 到达投递目标）。
3. tenant isolation（延续 registry key 的 tenant 维度）。
4. ack / retry / timeout（同步 ack 与异步完成的承载）。
5. DLQ / replay（持久与重放）。
6. ordering（局部 / 全局，route 维度）。
7. backpressure（拒绝 / 延迟 / 降速）。
8. durability（进程崩溃 / 重启后是否丢失）。
9. observability / audit（trace / metrics / audit 与 tenant、routeHandle、correlationId 关联）。
10. local dev 复杂度。
11. production 运维复杂度。
12. 对 Spring Boot / Java 21 / 当前 Maven modules 的影响。
13. 是否需要新增 production dependency。

## 4. 运行态最小边界定义

不选产品前，先定义最小运行态边界（Stage 5 计划 §4 切片 2）。这些边界是架构语义，不绑定具体产品；每个候选只是「由谁承载」的差异。

| 边界 | 必须定义 | 候选承载差异 |
|---|---|---|
| sender | 谁构造 forwarding envelope，谁负责 `idempotencyKey`、`tenantId`、`traceId`、`correlationId`、`routeHandle`、`deadline`（及条件必填的 `payloadRef`）。 | 所有候选一致：sender 由调用方 runtime 承担；envelope 字段构造与 Stage 4 契约绑定，与承载无关。 |
| dispatcher | 谁做 admission / routeHandle resolve / delivery ack。 | C1：内存分发器；C2：本地 queue worker；C3：outbox poller / inbox router；C4：broker producer + consumer-group；C5：分层 dispatcher。 |
| receiver | 谁消费 envelope，如何把 outcome 回到 runtime owner（不改变远端 Task lifecycle owner）。 | 所有候选一致语义：receiver 回传 outcome 给接收方 runtime，`agent-bus` 不写 Task execution state；承载差异仅在投递通道。 |
| store | 是否需要 durable outbox / inbox / DLQ；若需要，owner 是谁。 | C1/C2：弱/无 durable；C3：DB 表（owner = agent-bus outbox schema）；C4：broker（owner = broker）；C5：分层 store。store owner 落到 agent-bus 时，必须仍不写 Task execution state。 |
| observer | trace / audit / metrics / cost 如何关联 `tenantId`、`routeHandle`、`correlationId`。 | C1/C2：本地；C3：DB 可审计 + metrics；C4：broker metrics + 集成；C5：跨层关联。所有候选都需把 `correlationId`/`tenantId` 贯穿观测面。 |
| backpressure | 拒绝、延迟、降速分别如何表达。 | 所有候选都必须用 Stage 4 的显式 failure mode（`backpressure_rejected` / `receiver_unavailable` / `delivery_timeout`），不静默丢消息；承载差异仅在信号来源（队列水位 / DB quota / broker lag）。 |

边界小结：**sender / receiver / backpressure 的语义对所有候选一致**（由 Stage 4 契约固定），候选差异集中在 **dispatcher / store / observer 的承载**。这意味着选型不会动摇架构语义，只改变运行态承载物。

## 5. 候选方案评分矩阵

下表是 Stage 5 计划 §4 切片 3 的评分矩阵。评分用 **强 / 中 / 弱** 暴露 trade-off，**不定论**；权重标注计划给定的高 / 中。

| 维度 | 权重 | C1 in-memory | C2 runtime-local queue | C3 DB outbox/inbox | C4 external broker | C5 hybrid |
|---|:---:|:---:|:---:|:---:|:---:|:---:|
| tenant isolation | 高 | 弱（进程内逻辑） | 弱（进程内逻辑） | 强（DB 行级 + tenantId 列） | 中-强（per-tenant topic/partition） | 强（分层） |
| durability | 高 | 弱（内存） | 弱-中（file-backed 可选） | 强（DB 事务） | 强（broker 复制） | 强（组合） |
| backpressure | 高 | 中（有界队列） | 中（有界队列） | 中（quota / 限速） | 强（broker 流控） | 强（分层） |
| local dev simplicity | 中 | 强 | 强 | 中（需 DB） | 弱（需 broker） | 弱（多组件） |
| production ops | 高 | 强（简单，但弱 HA） | 中 | 中（DB 运维 + polling） | 弱（集群运维） | 弱（最复杂） |
| ordering / replay | 中 | 弱（单线程局部） | 弱-中（per-queue） | 中（index，需 careful） | 强（per-partition + retention） | 中（跨层难） |

矩阵读法：

- **durability 与 production ops 成反比**：C1 最易运维但不 durable；C4/C5 最 durable 但运维最重。
- **tenant isolation 的两条路径**：C3 走 DB 行级（复用现有 DB，无新依赖），C4 走 broker per-tenant 分区（强但需 broker 运维 + 新依赖）。
- **ordering / replay 的最强承载是 C4**（partition + retention），C3 次之（需 index 与 careful 并发控制）。
- **local dev 友好度**：C1/C2 最佳，C4/C5 最重——这影响早期切片的可迭代性。

## 6. 评审小结与 deferred 决策

### 6.1 关键 trade-off

- **durability ↔ ops 复杂度**：从 C1（弱 durable / 低 ops）到 C4/C5（强 durable / 高 ops）是一条连续权衡线。
- **tenant isolation 的两条路径**：DB 行级（C3，复用现有 DB，无新依赖）vs broker per-tenant 分区（C4，强但新依赖 + 运维）。
- **ordering**：broker per-partition（C4）> DB index careful（C3）> 内存单线程（C1）。
- **是否新增 production dependency**：C3 可能复用现有 DB（无新依赖），C4/C5 必然新增 broker client + adapter。
- **早期可迭代性**：C1/C2 的 local dev 复杂度最低，适合作为初始运行态切片的起点；C5 复杂度最高，不宜作为初始切片。

### 6.2 本评审不下定论的决策（deferred 到 H2/H3）

- 具体 broker 产品选择（C4 内部的 Kafka / NATS / RocketMQ / RabbitMQ 之间）。
- outbox store 是否复用现有 DB，还是独立存储（C3 的 owner 落点）。
- hybrid（C5）的分层边界与跨层 ordering 策略。
- ordering 的 route 维度定义（ICD Open Issue，需在选定承载后再定）。
- backpressure 的具体状态编码（ICD Open Issue）。

### 6.3 边界护栏（无论选哪个候选都必须守住）

- forwarding envelope 始终通过 `routeHandle` 绑定投递目标，不绕过 Stage 3 discovery 直接用物理 endpoint。
- envelope 不携带 payload body / token stream / Task execution state（`payloadRef` 条件必填，MI5-003 方案 B）。
- `agent-bus` 不写 Task execution state，不改变远端 Task lifecycle owner。
- backpressure / timeout / tenant mismatch 用 Stage 4 的显式 failure mode 表达，不静默丢消息。
- tenant isolation 延续 registry key 的 `tenantId` 强制（R-C.c），禁止跨 tenant fallback。

## 7. 后续

本评审是 Stage 6「最小运行态切片设计」的输入。Stage 6 可围绕被选中的候选定义最小实现切片（例如最小 in-memory dispatcher、最小 outbox/inbox 表与状态机、最小 broker adapter SPI、最小 DLQ/replay harness），但 **必须先拿到 H2/H3 对候选方案的裁决，不能由实现反推架构**（Stage 5 计划 §7）。

相关文档：

- 设计态契约：[`ICD-Agent-Bus-Forwarding`](../../05-contracts/human-readable/ICD-agent-bus-forwarding.md)（HD4）。
- 第五阶段计划：[`agent-bus-stage4-review-and-stage5-plan`](../delivery-projections/agent-bus-stage4-review-and-stage5-plan.md)。
- L1 入口：[`agent-bus L1 README`](../../../../architecture/docs/L1/agent-bus/README.md)。
