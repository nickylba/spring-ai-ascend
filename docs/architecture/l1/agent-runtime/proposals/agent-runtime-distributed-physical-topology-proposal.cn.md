---
level: L1
view: physical
module: agent-runtime
status: proposal
proposal_status: proposed
updated: 2026-06-16
source_active_view: architecture/L1-High-Level-Design/agent-runtime/physical.md
---

# agent-runtime 分布式物理拓扑提案

> 提案状态文档。本文档描述 `agent-runtime` 面向分布式 Task 状态、事件队列、事件总线和执行节点拆分的候选物理拓扑。在被 ADR 或对应版本设计接受并实现前，不作为 active 架构事实。

## 1. 背景

当前 active `agent-runtime` 是嵌入宿主 JVM 的 Java library，默认使用 A2A SDK 的 InMemory TaskStore、QueueManager 和 MainEventBus。该形态适合单实例运行、业务服务内嵌和独立宿主进程部署，但存在以下物理约束：

- Task 和事件状态绑定单个 JVM 进程。
- 进程重启后 InMemory Task、事件队列和订阅状态丢失。
- 同一 Task 的订阅、取消、恢复需要回到拥有该 Task 内存状态的实例。
- 多实例部署时，各实例默认是独立状态岛。

本提案用于归档分布式物理拓扑候选方案，供后续版本进入架构评审、ADR 决策和实现拆分。

## 2. 目标

候选分布式物理拓扑希望支持：

- 多个 runtime 实例共享 Task 状态。
- 多个 runtime 实例共享或转发 Task 事件。
- 客户端可以在不同实例上查询、订阅、取消或恢复同一 Task。
- Runtime 进程重启后具备受控的 Task 恢复或终止语义。
- Access 接入层与 Agent 执行层在需要时可以独立伸缩。

## 3. 非目标

本提案不直接规定：

- 具体 Redis、JDBC、MQ 或云服务产品选型。
- 平台级 Run record 的所有权迁移。
- Agent 框架内部 checkpoint 的持久化格式。
- 跨租户、跨区域、跨数据边界的 agent-bus 治理策略。
- 生产容量指标和 SLO，相关内容需要独立压测和 DFX 设计支撑。

## 4. 候选拓扑

### 4.1 共享状态单元拓扑

```text
               +--------------+
               | A2A Client   |
               +------+-------+
                      |
                      v
        +-------------+-------------+
        | Load Balancer / Gateway   |
        +------+------+-------------+
               |      |
               v      v
   +----------------+  +----------------+
   | runtime inst A |  | runtime inst B |
   | access+engine  |  | access+engine  |
   +--------+-------+  +--------+-------+
            |                   |
            +---------+---------+
                      |
                      v
       +------------------------------+
       | Distributed runtime state    |
       | - TaskStore                  |
       | - Queue / stream             |
       | - Event dispatch             |
       +------------------------------+
```

该拓扑保留每个实例同时具备接入和执行能力，通过共享 runtime 状态后端消除单实例状态岛。

### 4.2 Access / Engine 分层拓扑

```text
               +--------------+
               | A2A Client   |
               +------+-------+
                      |
                      v
        +-------------+-------------+
        | runtime access instances  |
        | /a2a + Agent Card         |
        +-------------+-------------+
                      |
                      v
       +------------------------------+
       | Distributed runtime state    |
       | TaskStore + Event stream     |
       +-------------+----------------+
                     |
                     v
        +------------+-------------+
        | runtime engine instances |
        | AgentRuntimeHandler      |
        +--------------------------+
```

该拓扑把 HTTP 接入、Task 状态协调和 Agent 执行资源拆开，适合执行耗时较长、模型或工具调用资源隔离要求更高的部署场景。

## 5. 状态后端候选

### 5.1 TaskStore

候选方向：

- Redis Hash / JSON：适合低延迟 Task 状态读写。
- JDBC：适合持久化、审计和强查询诉求。
- 混合模式：Redis 承担在线状态，JDBC 承担最终归档。

需要明确的设计点：

- Task ID 到状态记录的幂等写入语义。
- Task 状态迁移的 CAS 或版本控制。
- Task 消息体大小和历史裁剪策略。
- 过期 Task 的 TTL、归档和删除策略。

### 5.2 QueueManager

候选方向：

- Redis Stream：适合 Task 事件流、消费者组和重放。
- Kafka / Pulsar：适合高吞吐事件流和跨服务消费。
- 数据库事件表：适合低吞吐但强审计场景。

需要明确的设计点：

- SSE 订阅从哪个 offset 开始。
- 客户端重连时如何恢复事件位置。
- 事件投递至少一次、至多一次或准精确一次语义。
- 慢消费者积压、事件 TTL 和 backpressure 策略。

### 5.3 EventBus

候选方向：

- Redis Pub/Sub：适合简单广播，但弱持久化。
- Redis Stream / MQ：适合持久化事件和跨实例消费。
- 进程内 EventBus + 外部分发桥：适合保留本地性能并扩展跨实例同步。

需要明确的设计点：

- 本地事件和远端事件如何去重。
- Task 状态写入与事件发布的一致性边界。
- 执行实例竞争同一 Task 的锁或 lease 机制。
- 节点异常退出后的事件补偿。

## 6. 跨实例语义

分布式物理拓扑至少需要定义以下语义：

| 场景 | 待决策问题 |
|---|---|
| `GetTask` | 任意实例是否都能读取最新 Task 状态。 |
| `SubscribeToTask` | 订阅实例是否需要拥有 Task 执行权，或只需读取事件流。 |
| `CancelTask` | 取消请求如何路由到正在执行的 engine 实例。 |
| Resume | 继续消息如何与上一次中断的 Agent checkpoint 关联。 |
| 执行节点崩溃 | Task 是恢复、失败、超时还是重新派发。 |
| 重复投递 | Agent 执行是否允许幂等重入，如何避免重复副作用。 |

## 7. 与 active 物理视图的关系

当前 active `physical.md` 只承认以下事实：

- Runtime 是宿主 JVM 内的 library。
- 默认 TaskStore、QueueManager 和 EventBus 是进程内 InMemory 组件。
- 多实例部署时，各实例默认是独立状态岛。
- 存储替换接缝存在，但具体分布式实现不是 active 事实。

本提案中的 Redis、JDBC、MQ、access/engine 分层、跨实例 Task 语义和重启恢复语义，在正式设计接受前不得反向写入 `architecture/L1-High-Level-Design/agent-runtime/physical.md`。

## 8. 后续治理入口

进入 active 架构前，需要完成：

- ADR：确认是否引入分布式 runtime 状态后端。
- L1 更新：更新 physical、process、logical 和 development 视图。
- L2 设计：定义 TaskStore、QueueManager、EventBus 替换实现和一致性语义。
- DFX：补充容量、恢复、故障注入、SSE 重连、数据清理和观测指标。
- 验证：增加跨实例查询、订阅、取消、恢复和节点故障测试。
