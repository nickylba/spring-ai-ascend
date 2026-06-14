---
level: L1
module: agent-bus
view: scenarios
status: draft
---

# agent-bus 场景视图

## SC-001：client 创建或操作 Task

| 项目 | 内容 |
|---|---|
| 参与者 | `agent-client`、Gateway、`agent-service` |
| 入口 | `IngressGateway.routeClientRequest(...)` |
| 契约 | `ingress-envelope.v1.yaml` |
| 流程 | client 构造 `IngressEnvelope`，Gateway 校验并路由到 service，service 处理 Task 生命周期，Gateway 返回 `IngressResponse`。 |
| 成功结果 | 返回 accepted cursor 或查询结果。 |
| 失败结果 | 返回 rejected reason 或 deferred。 |
| 不变量 | Gateway 不直接写 Task execution state。 |
| 缺口 | ingress 契约测试需要补齐。 |

## SC-002：service 请求客户端能力

| 项目 | 内容 |
|---|---|
| 参与者 | `agent-service`、`S2cCallbackTransport`、`agent-client` |
| 入口 | `S2cCallbackTransport.dispatch(...)` |
| 契约 | `s2c-callback.v1.yaml` |
| 流程 | service 构造 `S2cCallbackEnvelope`，通过 transport 发给 client，client 执行本地 capability 后返回 `S2cCallbackResponse`，service 校验并恢复或失败。 |
| 成功结果 | Run 恢复并继续执行。 |
| 失败结果 | timeout、schema invalid、transport failure 等导致 Run 进入失败或对应终态。 |
| 不变量 | service 仍拥有 suspend/resume 状态机。 |
| 缺口 | envelope 需要增加 `tenantId`，且迁移前要通知冲突方。 |

## SC-003：service 驱动 execution engine

| 项目 | 内容 |
|---|---|
| 参与者 | `agent-service`、`EnginePort`、`agent-execution-engine` |
| 入口 | `EnginePort.execute(...)` |
| 契约 | `engine-port.v1.yaml` |
| 流程 | service 通过中立端口发起执行，engine 返回 `AgentEvent` stream，service 消费事件并更新状态。 |
| 成功结果 | 收到唯一 terminal event，Task/Run 状态由 service 收敛。 |
| 失败结果 | failed 或 interrupt request 以 terminal event 表达。 |
| 不变量 | bus 提供 SPI home，但不是 engine runtime owner。 |
| 缺口 | terminal event harness 需要补齐。 |

## SC-004：跨部署 federation

| 项目 | 内容 |
|---|---|
| 参与者 | 本地 Gateway、真 bus、`FederationGateway`、远端 `agent-service` |
| 入口 | `FederationGateway.routeFederated(...)` |
| 契约 | `federation-envelope.v1.yaml` |
| 流程 | 本地 bus 判断请求需要跨部署转发，通过 federation gateway 发送到远端服务边界。 |
| 成功结果 | 远端 service 返回同步确认，后续结果异步观察。 |
| 失败结果 | routing rejected、network failure、policy denied。 |
| 不变量 | 远端 Task 生命周期仍由远端 service 拥有。 |
| 缺口 | broker、credential、routing policy 未决定。 |

## SC-005：reflection 更新路由

| 项目 | 内容 |
|---|---|
| 参与者 | cloud Slow Track、`ReflectionEnvelopeRouter`、edge Fast Track/session |
| 入口 | `ReflectionEnvelopeRouter.route(...)` |
| 契约 | `reflection-envelope.v1.yaml` |
| 流程 | 云侧产生 reflection envelope，真 bus 负责路由到目标 edge session。 |
| 成功结果 | edge session 接收到 reflection update。 |
| 失败结果 | target not found、schema invalid、delivery failure。 |
| 不变量 | router 只路由 envelope，不拥有 reflection 语义处理。 |
| 缺口 | 当前参数是 `Map<String,Object>`，需要决定 schema validator 或 typed record。 |

## SC-006：未来 workflow primitives

| 项目 | 内容 |
|---|---|
| 参与者 | 真 bus、service、future scheduler/runtime |
| 入口 | 未接受 |
| 契约 | backpressure、control-event、work-item、access-intent 等草案契约 |
| 流程 | 未来可能由 mailbox、admission、backpressure、tick 等机制治理跨服务运行节奏。 |
| 成功结果 | 尚未定义 |
| 失败结果 | 尚未定义 |
| 不变量 | 当前只保留设计态，不进入自动实现范围。 |
| 缺口 | 需要版本意图、状态 owner、失败语义和 harness。 |
