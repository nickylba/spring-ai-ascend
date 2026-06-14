---
level: L1
module: agent-bus
view: process
status: draft
---

# agent-bus 进程视图

## 1. 进程视图原则

`agent-bus` 的进程视图只描述跨边界流量如何流动，不把 Task 生命周期执行权放入 bus。

进程视图中的所有流程都遵守两条规则：

- 进入或跨越边界的请求必须带有可追踪的 envelope。
- 状态机最终决策仍回到对应 owner，例如 Task 状态回到 `agent-service`。

## 2. C2S ingress 流程

| 步骤 | 参与者 | 动作 |
|---|---|---|
| 1 | `agent-client` | 构造 `IngressEnvelope`，包含 `requestId`、`tenantId`、`idempotencyKey`、`requestType`、`payload`、`traceId`。 |
| 2 | Gateway / `IngressGateway` | 校验 envelope 的基础字段、trace、幂等和入口规则。 |
| 3 | Gateway | 将请求路由到 `agent-service`。 |
| 4 | `agent-service` | 按 Task 生命周期规则创建、查询、取消或恢复 Task。 |
| 5 | Gateway | 返回 `IngressResponse`，表达 `ACCEPTED`、`REJECTED` 或 `DEFERRED`。 |

关键约束：

- Gateway 可以返回 cursor / ack / rejection。
- Gateway 不直接写 Task execution state。
- 运行结果通过后续查询、SSE、webhook 或 callback 观察，不阻塞 ingress 调用。

## 3. S2C callback 流程

| 步骤 | 参与者 | 动作 |
|---|---|---|
| 1 | `agent-service` / runtime | 某个 Run 需要客户端能力，进入等待客户端 callback 的流程。 |
| 2 | runtime | 构造 `S2cCallbackEnvelope`，通过 `SuspendSignal.forClientCallback(...)` 承载。 |
| 3 | `S2cCallbackTransport` | 将请求派发到 client/edge。 |
| 4 | `agent-client` | 执行本地 capability，返回 `S2cCallbackResponse`。 |
| 5 | `agent-service` | 校验 response schema，并决定 Run 恢复、失败或超时。 |

当前事实：

- `S2cCallbackEnvelope` 当前没有 `tenantId`。
- H2 已接受目标态：后续迁移中必须给 `S2cCallbackEnvelope` 增加 `tenantId`。
- 迁移前不能自动修改契约和代码，必须先通知冲突方。

失败路径：

- client 超时：service 将 Run 转入失败或对应终态。
- response schema invalid：service 拒绝恢复，并记录失败原因。
- transport failure：应通过 returned stage 异常完成，而不是在 transport 中同步抛出。

## 4. Service 与 Engine 流程

| 步骤 | 参与者 | 动作 |
|---|---|---|
| 1 | `agent-service` | 通过 `EnginePort.execute(...)` 驱动执行。 |
| 2 | `agent-execution-engine` | 实现 `EnginePort` 并返回 `AgentEvent` stream。 |
| 3 | `EnginePort` | 要求最后发出唯一 terminal event。 |
| 4 | `agent-service` | 根据 event 更新 Task/Run 状态。 |

`agent-bus` 在这里提供中立 SPI home。它不因此成为 execution owner，也不拥有 Run aggregate。

## 5. Federation / reflection 流程

Federation 和 reflection 属于真 bus 范围：

- `FederationGateway` 表达跨网络、跨部署的 ingress 转发。
- `ReflectionEnvelopeRouter` 表达从云侧 Slow Track 到 edge Fast Track 的 reflection route。

当前状态：

- SPI 已存在。
- broker、credential、routing policy、delivery guarantee 仍未落地。
- 运行时实现需要单独 H2/H3 决策。

## 6. Future workflow primitives

Mailbox、admission、backpressure、sleep、wakeup、tick 当前只保留设计态。它们会影响运行时进程语义，因此不能由自动化直接生成实现。

进入实现前必须先明确：

- 谁拥有队列状态。
- 谁做 admission decision。
- backpressure 如何传播。
- tick/rhythm 的时间源是谁。
- 失败、重试、DLQ 的 owner 是谁。

## 7. 进程断言

| 断言 | 证据 |
|---|---|
| C2S 必须经过 `IngressGateway`。 | client 模块依赖规则和 ingress SPI。 |
| S2C 必须经过 `S2cCallbackTransport`。 | service L1 场景和 S2C SPI。 |
| Task 状态只由 service 生命周期层更新。 | L0 boundaries 状态矩阵。 |
| Engine terminal event 必须唯一且最后发出。 | `EnginePort` 契约和后续 harness。 |
| S2C tenant 目标态必须进入 envelope。 | H2 决策和冲突通知记录。 |
