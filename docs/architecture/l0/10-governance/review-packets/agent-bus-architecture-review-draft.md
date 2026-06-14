---
artifact_type: a2d_review_packet
version: "agent-bus-l1-draft-2026-06-14"
status: draft
human_review_gate: H2
version_intent: "待补充"
architecture_envelope: "待补充"
delivery_projection: "待补充"
source_baseline:
  current_branch: experimental
  comparison_snapshot: "C:/Users/tiany/code/spring-ai-ascend-main-snapshot-20260614-102511"
---

# agent-bus-l1-draft-2026-06-14 架构评审包

## 1. 评审摘要

### 目标

为 `agent-bus` 建立第一版 H2 架构评审包，使人类可以先判断它的 L1 架构边界，再推进正式 L1 文档、harness 断言、契约投影、接口桩或实现切片。

本评审包把 `agent-service` / agent runtime 和 `agent-execution-engine` 作为事实来源与边界约束，而不是把它们的职责迁移到 `agent-bus`。

### 非目标

- 不实现运行时 broker、mailbox、tick engine 或物理 bus。
- 不把 Task 生命周期、Task sleep 状态、Task 层级关系从 `agent-service` 迁移到 `agent-bus`。
- 本评审包不直接修改 `EnginePort`、S2C、ingress 或 federation 的签名；其中 S2C 增加 `tenantId` 已被接受为后续独立契约迁移切片。
- 不在缺少测试和实现证据时，把 `design_only` 契约描述成运行时已强制执行。
- 不把 main 分支中的 `architecture/docs/L1/agent-bus` 文档直接当作当前分支的权威事实；它只能作为结构参考。

### 评审结论

结论：`草案`

### 需要人类决策的事项

| 编号 | 决策点 | 可选方案 | AI 建议 | 负责人 | 是否阻塞自动推进 | 状态 |
|---|---|---|---|---|---|---|
| HD-001 | `agent-bus` 的正式 L1 文档位置 | 创建 `architecture/docs/L1/agent-bus/**` | 已接受：创建 `architecture/docs/L1/agent-bus/**`，并以本评审包作为生成正式 L1 的输入 | 架构负责人 | 否 | 已接受 |
| HD-002 | 首批接受的 SPI 范围 | 接受当前代码中已存在的 `ingress`、`s2c`、`federation`、`engine`；W2 workflow primitives 只保留设计态 | 已接受：首批 L1 范围覆盖当前代码中已存在的 `ingress`、`s2c`、`federation`、`engine`。W2 workflow primitives 只作为设计态事实，不进入自动实现范围 | 架构负责人、模块负责人 | 否 | 已接受 |
| HD-003 | 与 `agent-service` / runtime 的关系 | service 拥有 Task 生命周期；bus 划分为 gateway 与真 bus 两个逻辑子模块 | 已接受：`agent-service` 保持 Task 生命周期所有权；`agent-bus` 内部分为 gateway 和真 bus。gateway 负责外部到内部的转发与调度；真 bus 负责 service 与 service 之间的相互调用和跨服务治理 | 架构负责人、service 负责人、bus 模块负责人 | 否 | 已接受 |
| HD-004 | Workflow primitives 的成熟度 | 保持设计态，直到具体版本意图定义 mailbox、admission、backpressure、tick 语义 | 已接受：W2 workflow primitives 继续保持设计态，不能进入自动实现范围 | 架构负责人 | 否 | 已接受 |
| HD-005 | S2C 的租户边界 | `S2cCallbackEnvelope` 需要增加 `tenantId`；施工前通知冲突方并建立独立契约迁移切片 | 已接受：按照正确的跨边界 envelope 设计，S2C envelope 应直接携带 `tenantId`。本评审包只记录决策和冲突通知项，不在本次改动中直接修改代码契约 | 架构负责人、契约负责人 | 否 | 已接受 |
| HD-006 | main 快照中的 L1 文档如何处理 | 只作参考。当前分支的 SPI、代码和契约事实已经不同 | 已接受：main 快照只作为结构参考，不作为当前分支事实源 | 架构负责人 | 否 | 已接受 |

### 变更等级摘要

| 编号 | 变更 | 等级 | 原因 | 需要的批准 | 状态 |
|---|---|---|---|---|---|
| CH-001 | 增加本 H2 评审包 | Level 0 | 只增加治理文档，不改变运行行为 | 架构评审人 | 草案 |
| CH-002 | 基于已接受的评审包生成 `agent-bus` 正式 L1 文档 | Level 1 | 建立模块架构视图和后续自动化边界 | 架构负责人、模块负责人 | 已完成草案 |
| CH-003 | 为现有 ingress、federation、reflection SPI 增加 harness 测试 | Level 2 | 增加验证证据，不改变生产行为 | 模块负责人 | 提议中 |
| CH-004 | 实现运行时 bus primitives 或物理 broker 绑定 | Level 3 | 会改变执行语义、状态、排序、重试和部署行为 | 架构负责人、运维负责人、模块负责人 | 未批准 |

## 2. 事实来源

本评审包是评审投影，不拥有架构事实。下面每个判断都必须能追溯到已经存在的文档、代码或契约。

| 事实类型 | 来源 | 锚点 | 状态 | 说明 |
|---|---|---|---|---|
| 模块职责 | `agent-bus/module-metadata.yaml` | `spi_packages`、`allowed_dependencies`、`forbidden_dependencies`、`deployment_plane: bus_state` | 草案/代码 | 声明 ingress、S2C、federation、engine SPI 包，并声明不依赖同级模块 |
| 模块职责 | `agent-bus/pom.xml` | description | 代码 | 说明 bus 目前是契约脚手架，运行时实现后续落地 |
| 契约 | `docs/contracts/contract-catalog.md` | `agent-bus` 相关行 | 草案 | 列出 bus SPI 契约以及运行态/设计态状态 |
| 契约 | `docs/contracts/ingress-envelope.v1.yaml` | request/response | 草案 | ingress envelope 当前注释中仍是设计态 |
| 契约 | `docs/contracts/s2c-callback.v1.yaml` | request/response | 草案/代码 | S2C 是当前已有 Java record 校验和测试的表面 |
| 契约 | `docs/contracts/engine-port.v1.yaml` | engine port | 草案/代码 | service 与 engine 之间的中立端口，被 execution engine 消费 |
| 契约 | `docs/contracts/federation-envelope.v1.yaml` | federation envelope | 草案/代码 | Federation SPI 已存在，运行时实现尚未存在 |
| 契约 | `docs/contracts/reflection-envelope.v1.yaml` | reflection envelope | 草案/代码 | Reflection router SPI 已存在，但 payload 仍是 map 形态 |
| 状态所有权 | `architecture/L0-Top-Level-Design/boundaries.md` | Task 状态矩阵 | 已接受/草案 | `agent-service` 拥有 Task 执行状态；bus 直接写入被禁止 |
| 不变量 | `architecture/L0-Top-Level-Design/boundaries.md` | bus 职责边界 | 已接受/草案 | bus 治理跨边界控制和引用 envelope；不拥有单个 service 内部的 Task sleep 状态 |
| 场景 | `architecture/L0-Top-Level-Design/views.md` | S2C、A2A/federation、rhythm signal 流程 | 已接受/草案 | bus 参与跨平面流程，但 service 仍然是生命周期所有者 |
| 场景 | `architecture/L1-High-Level-Design/agent-service/scenarios.md` | S2C callback、第三方 Agent 调用、审计 | 草案 | service/runtime 场景引用 bus 表面 |
| 能力 | `architecture/L1-High-Level-Design/agent-service/spi-appendix.md` | Bus SPI 附录 | 草案 | service 文档命名了中立 engine、S2C、ingress bus SPI |
| 开发视图 | `agent-bus/src/main/java/com/huawei/ascend/bus/spi/**` | Java SPI 包 | 代码 | 当前分支包含 ingress、S2C、federation、reflection、engine SPI |
| Harness | `agent-bus/src/test/java/**` | `S2cCallbackEnvelopeLibraryTest`、`SuspendSignalTest` | 代码 | S2C 和 engine 相关基础能力有部分测试；ingress、federation、reflection 仍有缺口 |
| 对照资料 | `C:/Users/tiany/code/spring-ai-ascend-main-snapshot-20260614-102511/architecture/docs/L1/agent-bus/**` | main 快照 L1 文档 | 仅参考 | 结构有参考价值，但不能作为当前分支事实源 |

## 3. 4+1 视图模型

### 3.1 视图元素

下表是后续人工评审、图生成和漂移检查的稳定元素清单。没有事实来源的元素不应进入这里。

| 元素编号 | 视图 | 类型 | 名称 | 所有者 | 职责 | 事实来源 | 状态 |
|---|---|---|---|---|---|---|---|
| E-001 | 开发/逻辑/物理 | 模块 | `agent-bus` | bus 模块负责人 | 拥有跨平面 bus SPI 契约和 bus_state 治理表面 | `agent-bus/module-metadata.yaml` | 草案 |
| E-002 | 逻辑/场景 | 能力 | Ingress Gateway | bus 模块负责人 | 从 edge/client 进入 compute_control 的唯一 C2S 表面 | `IngressGateway`、`IngressEnvelope`、`ingress-envelope.v1.yaml` | 草案 |
| E-003 | 逻辑/场景 | 能力 | S2C Callback Transport | bus 模块负责人 | 服务端调用客户端能力的请求/响应表面 | `S2cCallbackTransport`、`S2cCallbackEnvelope`、`s2c-callback.v1.yaml` | 草案/代码 |
| E-004 | 逻辑/场景 | 能力 | Federation Gateway | bus 模块负责人 | Mode B 业务中心部署中的跨网络转发层 | `FederationGateway`、`federation-envelope.v1.yaml` | 草案 |
| E-005 | 逻辑/场景 | 能力 | Reflection Envelope Router | bus 模块负责人 | 从云侧 Slow Track 到边缘 Fast Track 的 reflection envelope 路由 | `ReflectionEnvelopeRouter`、`reflection-envelope.v1.yaml` | 草案 |
| E-006 | 逻辑/开发/进程 | 契约 | Engine Port SPI | bus 模块负责人、engine 负责人 | service 与 engine 之间的中立执行边界 | `EnginePort`、`engine-port.v1.yaml` | 草案/代码 |
| E-007 | 逻辑/进程 | 模块 | `agent-service` / runtime | service 负责人 | 拥有 Task 生命周期、Task 状态、层级关系、suspend/resume 和 service API | L0 boundaries、service L1 文档 | 已接受/草案 |
| E-008 | 逻辑/进程 | 模块 | `agent-execution-engine` | engine 负责人 | 实现 execution engine，消费中立 engine SPI | engine 模块元数据、engine 源码 import | 草案/代码 |
| E-009 | 逻辑/物理 | 模块 | `agent-client` | client 负责人 | edge 调用方；C2S 必须通过 ingress SPI | client 模块元数据、ArchUnit 测试引用 | 草案/代码 |
| E-010 | 物理/进程 | 部署平面 | `bus_state` | bus/运维负责人 | bus 排序、backpressure、callback correlation、mailbox fairness | deployment plane contract、bus DFX | 草案 |
| E-011 | 逻辑/进程 | 状态 | Task 执行状态 | service 负责人 | 持久化 Task 生命周期状态 | L0 boundaries 状态矩阵 | 已接受/草案 |
| E-012 | 逻辑/进程 | 能力 | W2 workflow primitives | bus 模块负责人 | 未来的 mailbox、admission、backpressure、sleep、wakeup、tick primitives | 模块元数据、POM 注释 | 延后 |
| E-013 | 逻辑/进程 | 逻辑子模块 | Gateway | bus 模块负责人 | 负责外部到内部的转发、入口治理和调度，包括 edge/client 到 compute_control 的 ingress，以及需要进入内部服务的外部请求 | HD-003 人类决策、Ingress SPI | 已接受 |
| E-014 | 逻辑/进程 | 逻辑子模块 | 真 bus | bus 模块负责人 | 负责 service 与 service 之间的相互调用、跨服务路由、federation/reflection 等跨服务治理能力 | HD-003 人类决策、Federation/Reflection SPI | 已接受 |

### 3.2 视图关系

下表是 4+1 图、graphify 输入和漂移检查的关系清单。

| 关系编号 | 视图 | 从 | 到 | 关系类型 | 方向 | 同步/异步 | 契约/状态 | 事实来源 |
|---|---|---|---|---|---|---|---|---|
| R-001 | 逻辑/场景 | `agent-client` | Ingress Gateway | 调用 | 单向 | 同步确认，异步结果 | `ingress-envelope.v1.yaml`、Task Cursor | `IngressGateway`、client 模块元数据 |
| R-002 | 逻辑/进程 | Ingress Gateway | `agent-service` | 路由到生命周期所有者 | 单向 | 同步确认，异步结果 | Task 状态仍归 service 所有 | L0 boundaries、ingress 注释 |
| R-003 | 逻辑/场景 | `agent-service` | S2C Callback Transport | 调用 | 单向 | 异步 `CompletionStage` | `s2c-callback.v1.yaml` | `S2cCallbackTransport`、service SPI 附录 |
| R-004 | 逻辑/场景 | S2C Callback Transport | `agent-client` | 派发 | 单向 | 异步 | callback id、server run id、trace id、idempotency key | `S2cCallbackEnvelope` |
| R-005 | 开发/进程 | `agent-service` | Engine Port SPI | 驱动 | 单向 | stream | `engine-port.v1.yaml`、`AgentEvent` terminal event | `EnginePort` |
| R-006 | 开发/进程 | `agent-execution-engine` | Engine Port SPI | 实现/消费 | 单向 | stream | `EnginePort` | engine 源码 import |
| R-007 | 逻辑/场景 | Federation Gateway | 远端 `agent-service` | 转发 | 单向 | 同步确认，异步结果 | `federation-envelope.v1.yaml`、ingress envelope | `FederationGateway`、L0 A2A/federation 视图 |
| R-008 | 逻辑/场景 | Reflection Envelope Router | `agent-client` / edge session | 路由 | 单向 | 异步/最终一致 | `reflection-envelope.v1.yaml` | `ReflectionEnvelopeRouter` |
| R-009 | 逻辑/进程 | `agent-bus` | Task 执行状态 | 禁止写入 | 单向禁止 | 无 | Task 状态所有者是 service | L0 boundaries 状态矩阵 |
| R-010 | 物理/进程 | `agent-bus` | `bus_state` | 部署/治理 | 单向 | 最终一致 | ordering/backpressure/correlation/fairness | deployment plane contract、DFX |
| R-011 | 逻辑/进程 | Gateway | `agent-service` | 转发/调度 | 单向 | 同步确认，异步结果 | ingress envelope、Task Cursor | HD-003、Ingress SPI |
| R-012 | 逻辑/进程 | 真 bus | `agent-service` | 跨服务调用治理 | 多方 | 异步/最终一致 | federation/reflection/control envelope | HD-003、Federation/Reflection SPI |

### 3.3 逻辑视图

摘要：

`agent-bus` 是跨平面契约和治理中心。它定义 ingress、S2C callback、federation/reflection、以及中立 service-engine 边界的稳定 SPI 表面。它不是业务 Task 生命周期状态的所有者；它治理跨边界流量如何进入、离开或引用这些状态。

在 H2 决策中，`agent-bus` 被进一步拆成两个逻辑子模块：Gateway 和真 bus。Gateway 面向“外部到内部”，负责入口治理、转发和调度；真 bus 面向“内部服务之间”，负责 service 与 service 之间的相互调用、跨服务路由和跨服务治理。这个拆分目前是逻辑架构拆分，不代表当前代码已经拆成两个 Maven module。

评审风险：

- 当前代码和契约文件比当前分支中的架构文档更完整，正式 L1 bus 视图缺失。
- 一些注释描述了未来运行时行为，但 POM 和 DFX 仍声明当前只是契约脚手架。
- `bus` 这个名字容易诱发职责漂移，导致 Task 生命周期、sleep 状态或 execution orchestration 被误放进 bus。

### 3.4 开发视图

摘要：

当前 `agent-bus` 生产代码是 `com.huawei.ascend.bus.spi.*` 下的纯 Java SPI。模块元数据声明没有允许的同级模块依赖，并禁止依赖 `agent-service`、`agent-execution-engine`、`agent-middleware`、`agent-client`、`agent-evolve`。

允许的依赖：

- Java 平台 API。
- `agent-bus` 内部 sibling SPI carrier types。
- 用于验证 SPI 行为的构建/测试依赖。

禁止的依赖：

- `agent-bus` 到 `agent-service` 的生产依赖。
- `agent-bus` 到 `agent-execution-engine` 的生产依赖。
- `agent-bus` 到 `agent-client`、`agent-middleware`、`agent-evolve` 的生产依赖。
- 除非后续 H2/H3 边界批准，否则 SPI 包中不应引入 Spring、Reactor、Jackson、HTTP framework、broker 或运行时实现依赖。

评审风险：

- 目前 `agent-service` 文档比 bus 自己的 L1 文档更能说明 bus 关系，这是结构性缺口。
- 生成代码不应写入 SPI 包，除非生成源契约已被接受，且生成文件可机械复现。

### 3.5 进程视图

摘要：

应接受的进程形态至少区分四类流程：

- Edge/client ingress 通过 `IngressGateway` 进入，获得同步 `IngressResponse`，并通过 cursor/callback 机制观察长任务结果。
- Runtime/service 使用 `S2cCallbackTransport` 请求客户端托管能力，并异步获得响应。
- Service 通过 `EnginePort` 驱动 execution；execution engine 实现该端口，并最后发出唯一 terminal event。
- Federation/reflection 路由跨边界 envelope，但它们的运行时 broker 技术和投递保证不在本切片实现。

失败、重试、取消路径：

- Ingress retry 由 `idempotencyKey` 标识；拒绝和延后由 `IngressResponse` 表示。
- S2C retry 由 `idempotencyKey` 标识；transport failure 应通过返回的 stage 异常完成，而不是同步抛出。
- Engine 的错误、完成和 interrupt request 是 terminal event，而不是跨边界抛出的异常。
- 取消语义由 ingress request type 命名，但完整生命周期所有权仍在 `agent-service`。

评审风险：

- Backpressure、DLQ、ordering、mailbox fairness、tick/rhythm 语义在周边架构中被提到，但在本模块还没有可执行实现。
- 尚不清楚哪些进程断言可以在 `agent-bus` 内单测，哪些必须依赖 service/runtime harness。

### 3.6 物理视图

摘要：

`agent-bus` 属于 `bus_state` 部署平面。当前分支包含 SPI 契约，不包含物理 broker 绑定。除非 H2 接受具体运行时实现切片，否则物理投影应保持为后续产物。

部署平面影响：

- Edge plane 的 `agent-client` 不应直接绑定 compute_control 内部；ingress 是声明的跨平面表面。
- Compute_control 的 `agent-service`、`agent-execution-engine` 消费中立 bus 契约，但保留自己的生命周期状态。
- Bus_state 只有在运行时实现被接受后，才拥有跨边界 ordering、correlation、backpressure 等运行时责任。

数据、租户、凭证和网络边界：

- `IngressEnvelope` 携带 `tenantId`，并校验其非空。
- `S2cCallbackEnvelope` 当前通过 callback/run registry 绑定解析租户范围，而不是直接携带 `tenantId` 字段；H2 已决策后续应给 envelope 增加 `tenantId`。
- 跨网络 federation 由 `FederationGateway` 表示，但具体 credential、broker、routing policy 仍延后。

评审风险：

- 当前 S2C 租户范围与现有代码兼容，但作为独立 wire envelope 比 ingress 更弱。后续迁移到直接携带 `tenantId` 时，需要通知受影响模块和文档所有者。
- Broker 技术和物理投递保证尚未选择。

### 3.7 场景视图

| 场景编号 | 用户/参与者 | 流程摘要 | 支撑元素 | 契约 | Harness/测试 | 状态 |
|---|---|---|---|---|---|---|
| SC-001 | Edge client | Client 通过 bus ingress 提交 run/get/cancel/resume 请求，并收到 ack/cursor/rejection | `IngressGateway`、`IngressEnvelope`、`IngressResponse` | `ingress-envelope.v1.yaml` | 缺口：增加 ingress record/response 契约测试 | 草案 |
| SC-002 | Agent runtime/service | Service 请求 client 执行客户端托管能力 | `S2cCallbackTransport`、`S2cCallbackEnvelope`、`S2cCallbackResponse` | `s2c-callback.v1.yaml` | 已有 S2C envelope 测试；后续增加 transport failure 契约测试 | 草案/代码 |
| SC-003 | Service 与 execution engine | Service 通过中立 engine 边界执行，engine 返回事件流 | `EnginePort`、engine SPI records | `engine-port.v1.yaml` | 已有 engine 相关测试；terminal-event harness 需确认 | 草案/代码 |
| SC-004 | 业务中心部署 | 本地业务侧 bus shim 将符合条件的 ingress 转发到平台 Federation Hub | `FederationGateway` | `federation-envelope.v1.yaml` | 缺口：增加 federation contract tests/mocks | 草案 |
| SC-005 | Heaven-Earth reflection | 云侧 Slow Track judge 将 reflection updates 路由到 edge Fast Track session | `ReflectionEnvelopeRouter` | `reflection-envelope.v1.yaml` | 缺口：map payload 需要契约校验决策 | 草案 |
| SC-006 | 未来 workflow governance | Mailbox/admission/backpressure/sleep/wakeup/tick primitives 塑造 bus 运行时行为 | 延后的 W2 primitives | backpressure/control/work-item/access-intent contracts | 缺口：尚不能 codegen 或实现运行时 | 延后 |

## 4. 契约投影矩阵

OpenAPI/Swagger、schema、stub、mock、contract test 都只能作为已接受契约事实的投影，不能反向成为语义事实源。

| 契约编号 | 人类 ICD 来源 | 机器投影 | 投影类型 | 兼容规则 | 所有者 | 生成产物 | 验证 |
|---|---|---|---|---|---|---|---|
| C-001 | `docs/contracts/ingress-envelope.v1.yaml` | `bus.spi.ingress` 下的 Java records | YAML schema + Java SPI | 不删除 required field；request type enum 变更需要 H2 | bus 契约负责人 | 当前为手写；生成 stub 待定 | 增加 required fields、trace id、status rules 单测 |
| C-002 | `docs/contracts/s2c-callback.v1.yaml` | `bus.spi.s2c` 下的 Java records 和 transport SPI | YAML schema + Java SPI | H2 已接受增加 `tenantId`；迁移切片必须同步更新 YAML、Java record、构造点、测试和调用方 | bus/service 契约负责人 | 当前为手写 | 已有 envelope 测试；增加 `tenantId` required-field 测试和 transport failure harness |
| C-003 | `docs/contracts/engine-port.v1.yaml` | `bus.spi.engine` 下的 Java SPI | YAML/contract + Java SPI | terminal event 语义必须稳定 | bus/engine 契约负责人 | 当前为手写 | 增加 event-stream terminal 断言 |
| C-004 | `docs/contracts/federation-envelope.v1.yaml` | `FederationGateway` 和未来 mock/stub | YAML schema + Java SPI | 保持 broker-agnostic；routing policy 变更需要 H2 | bus/federation 负责人 | 尚未生成 | 增加 federation mock 和 contract tests |
| C-005 | `docs/contracts/reflection-envelope.v1.yaml` | `ReflectionEnvelopeRouter` map payload | YAML schema + Java SPI | payload 形状变更需要 schema 兼容检查 | bus/evolve/edge 负责人 | 尚未生成 | 决定 typed record 还是 map validator |
| C-006 | `docs/contracts/backpressure-request.v1.yaml` | 尚未接受 | YAML schema | W2 primitives 接受前不生成运行时代码 | 架构负责人 | 无 | 仅人工评审 |
| C-007 | `docs/contracts/control-event.v1.yaml` | 尚未接受 | YAML schema | 进程语义接受前不生成运行时代码 | 架构负责人 | 无 | 仅人工评审 |
| C-008 | `docs/contracts/work-item.v1.yaml` | 尚未接受 | YAML schema | mailbox/work queue 所有权接受前不生成运行时代码 | 架构负责人 | 无 | 仅人工评审 |
| C-009 | `docs/contracts/access-intent.v1.yaml` | 尚未接受 | YAML schema | authorization flow 接受前不生成运行时代码 | 架构/安全负责人 | 无 | 仅人工评审 |

## 5. Harness 断言与测试计划

Harness 断言把架构主张连接到可执行或可评审的证据。

| 断言编号 | 事实来源 | 必须成立的内容 | 测试类型 | Fixture/Mock | 是否覆盖失败路径 | 需要的证据 | 所有者 |
|---|---|---|---|---|---|---|---|
| HA-001 | `agent-bus/module-metadata.yaml` | `agent-bus` 生产代码不依赖 service、engine、client、middleware、evolve 模块 | 静态架构测试 | 模块依赖图 | 是 | ArchUnit 或构建图输出 | bus 模块负责人 |
| HA-002 | `IngressEnvelope` + `ingress-envelope.v1.yaml` | required fields、tenant scope、request type、idempotency key、lower-hex trace id 被强制校验 | 单元/契约测试 | Java record 构造 | 是 | 新增 ingress envelope 测试 | bus 模块负责人 |
| HA-003 | `IngressResponse` + `ingress-envelope.v1.yaml` | rejected response 必须有非空 reason；accepted/deferred 语义显式 | 单元/契约测试 | Java record 构造 | 是 | 新增 ingress response 测试 | bus 模块负责人 |
| HA-004 | `S2cCallbackEnvelope` + `s2c-callback.v1.yaml` | S2C mandatory fields、`tenantId` 和 lower-hex trace id 被强制校验 | 单元/契约测试 | Java record 构造 | 是 | 迁移切片中更新 S2C envelope 测试覆盖 | bus 模块负责人 |
| HA-005 | `S2cCallbackTransport` | transport failure 尽可能通过 returned stage 异常完成，而不是同步抛出 | 契约/人工 | fake transport | 是 | 后续 transport harness | service/bus 负责人 |
| HA-006 | `EnginePort` + `engine-port.v1.yaml` | Engine event stream 最后只能发出一个 terminal event | 集成/契约测试 | fake engine publisher | 是 | 后续 engine port contract test | engine/bus 负责人 |
| HA-007 | L0 状态矩阵 | Bus 永远不直接写 Task execution state | 静态/人工 | source diff + dependency graph | 是 | 变更文件评审和禁止依赖检查 | 架构负责人 |
| HA-008 | `FederationGateway` + federation contract | Federation 保持 broker-agnostic，并通过 ingress carrier types 路由 | 单元/契约/人工 | fake gateway | 是 | 后续 federation mock/test | bus/federation 负责人 |
| HA-009 | `ReflectionEnvelopeRouter` + reflection contract | Reflection envelope 要么按 schema 校验，要么明确接受 map-shaped 形态 | 契约/人工 | schema validator 或 typed record | 是 | H2 决策和后续测试 | bus/evolve/edge 负责人 |

## 6. 自动化边界

### 6.1 允许自动推进的范围

| 维度 | 允许范围 | 检查方式 | 证据 |
|---|---|---|---|
| 模块 | `agent-bus` 文档和测试；只读引用 service/engine/client 事实 | 模块 diff / 依赖图 | 变更文件列表 |
| 文件 | `architecture/docs/L1/agent-bus/**`、`docs/architecture/l0/10-governance/**`、`agent-bus/src/test/**`、窄范围文档修正 | changed-file path check | Git diff |
| 契约 | 从已接受 contract YAML 生成或更新 mock/test，不改变语义字段 | schema diff / contract test | 契约 diff + 测试输出 |
| 状态 | 不改变 Task 状态所有者/写入者 | state matrix diff | 对照 L0 boundaries 人工评审 |
| 生成产物 | H2 接受源契约和可写路径后，才能生成 Swagger/schema/stub/mock | projection plan check | 工具 manifest |
| 工具 | graphify / OpenAPI / Swagger / schema / stub / mock / codegen | 工具输出 manifest | 可复现命令和生成文件清单 |

### 6.2 禁止自动推进的范围

| 维度 | 禁止范围 | 升级对象 |
|---|---|---|
| 模块 | 修改 `agent-service`、`agent-execution-engine`、`agent-client`、`agent-middleware`、`agent-evolve` 的生产代码 | 架构负责人 / 模块负责人 |
| 模块 | 给 `agent-bus` 增加生产级同级模块依赖 | 架构负责人 / 模块负责人 |
| 契约 | 修改 required fields、enum 含义、trace/idempotency 语义或 callback tenant 模型 | 契约负责人 |
| 状态所有者/写入者 | 将 Task 生命周期、Task sleep、Task hierarchy 或 approval state 所有权移入 bus | 架构负责人 |
| 部署/路由 | 选择 Kafka/NATS/自研 broker，或增加物理路由实现 | 架构负责人 / 运维负责人 |
| 运行时语义 | 实现 mailbox、admission、backpressure、wakeup、tick、DLQ 或 ordering 行为 | 架构负责人 / 模块负责人 |

### 6.3 升级条件

- 变更触碰 `agent-service` 的 Task 生命周期、Task 状态持久化或 suspend/resume 语义。
- 变更给 `agent-bus` 增加任何非测试的同级模块依赖。
- 生成的 schema/stub 改变 required fields、enum 值或兼容性保证。
- 变更把 `design_only` 契约转成运行时行为。
- 变更引入 broker 技术、网络 credential 或物理部署假设。
- 变更把 `ReflectionEnvelopeRouter` 从 map-shaped payload routing 扩展成语义级 reflection processing。

## 7. 自动化投影计划

本节只描述 H2 之后可以投影的内容。具体实现任务应进入 delivery projection。

| 事实来源 | 工具 | 生成产物 | 可写路径 | 能否自动提交 | 验证 | 漂移检查 |
|---|---|---|---|---|---|---|
| 已接受的 4+1 元素/关系表 | graphify | L1 图 / graph model | `architecture/docs/L1/agent-bus/**` | H2 后可以 | 渲染并检查图节点和关系 | 对比 graph nodes 与评审包表格 |
| `ingress-envelope.v1.yaml` | schema / test generator | 契约测试或 fixtures | `agent-bus/src/test/**` | 不改语义契约时可以 | `agent-bus` Maven test | Schema hash + generated manifest |
| `s2c-callback.v1.yaml` | schema / test generator | 增加 `tenantId` 后的 fixtures / negative tests | `agent-bus/src/test/**` | 通知冲突方并建立迁移切片后可以 | `agent-bus` Maven test | Schema hash + generated manifest |
| `federation-envelope.v1.yaml` | schema / mock generator | broker-agnostic fake gateway fixture | `agent-bus/src/test/**` 或 `docs/architecture/.../harness/**` | owner 接受后可以 | Contract test / 人工评审 | 契约状态检查 |
| `reflection-envelope.v1.yaml` | schema validator 或 typed record generator | Validator/fixture，等待 H2 payload 决策 | 待定 | 否 | 先做 H2 决策 | 契约状态检查 |
| W2 workflow primitive contracts | 暂无 | 无 | 无 | 否 | 人工架构评审 | 版本意图接受前必须保持延后 |

## 8. 交付就绪度

| 项目 | 是否就绪 | 证据 | 缺口/后续 |
|---|---|---|---|
| 模块边界清晰 | 部分 | module metadata、L0 boundaries、SPI 包布局 | 需要正式 L1 bus 文档使其成为一等事实 |
| 契约投影清晰 | 部分 | contract catalog + YAML + Java SPI | 需要对 design-only 和 runtime-enforced 状态做显式对齐 |
| 状态所有权清晰 | 是 | L0 boundaries 状态矩阵 | 必须在 bus L1 中重复，防止职责漂移 |
| Harness 断言就绪 | 部分 | 已有 S2C 测试和 engine 相关测试 | 需要 ingress/federation/reflection/terminal-event 断言 |
| 测试计划就绪 | 否 | 本评审包提出了断言 | 需要 delivery projection 拆成具体任务 |
| 自动化边界检查已定义 | 部分 | 第 6 节 | 若开始自动改文件，需要脚本/检查机制 |
| 漂移检查已定义 | 部分 | 第 7 节 | 需要 graph/schema manifest |
| 能否生成 delivery projection | 是 | HD-005 已接受，但 S2C tenant 迁移需要独立切片和冲突通知 | 先生成 L1 文档投影；S2C 代码契约迁移另建任务 |

## 9. 未决问题与 H2 决策

### 未决问题

| 编号 | 问题 | 是否阻塞自动推进 | 负责人 | 解决路径 | 状态 |
|---|---|---|---|---|---|
| OI-001 | 当前分支缺少正式 `architecture/docs/L1/agent-bus/**`，但 module metadata 指向该路径 | 否 | 架构负责人 | 已创建 `architecture/docs/L1/agent-bus/**` 文档集，包括 4+1 视图、SPI 附录和 feature catalog | 已关闭 |
| OI-002 | main 快照有 L1 bus 文档，但当前分支代码/契约已经分叉 | 否 | 架构负责人 | main 只作为结构参考，不作为事实源 | 待定 |
| OI-003 | Ingress/federation/reflection SPI 缺少直接测试 | 是 | bus 模块负责人 | H2 接受 SPI 范围后补契约/单元测试 | 待定 |
| OI-004 | 部分 bus 契约仍是 `design_only`，文档不能暗示运行时已强制执行 | 是 | 契约负责人 | 在 L1 文档中为每个 contract 标注 projection status | 待定 |
| OI-005 | S2C envelope 没有直接 `tenantId`，租户通过 registry 绑定；H2 已决定后续增加 `tenantId` | 对 S2C 迁移是 | 契约负责人 | 施工前通知冲突方，建立独立契约迁移切片 | 已接受，待通知 |
| OI-009 | S2C tenant 迁移会影响代码、契约、测试、service/runtime 文档和生成模板 | 对 S2C 迁移是 | 架构负责人、契约负责人 | 使用“冲突通知记录”逐项通知并确认 owner | 待通知 |
| OI-006 | W2 workflow primitives 已被命名，但尚未准备实现 | 是 | 架构负责人 | 版本意图和进程/状态语义评审前保持延后 | 待定 |
| OI-007 | `ReflectionEnvelopeRouter` 接受 `Map<String,Object>` 而不是 typed envelope | 否 | bus/evolve/edge 负责人 | 决定 map + schema validator 或 typed record | 待定 |
| OI-008 | 物理 bus 技术和部署保证尚未决定 | 对运行时工作是 | 运维负责人 / 架构负责人 | 将 broker 选择延后到独立 H2/H3 评审 | 待定 |

### 已接受的自动化边界

已接受。当前自动化边界如下：

- 自动化可以生成完整 `agent-bus` L1 架构文档、graph 投影，以及现有 SPI records/interfaces 的测试。
- 自动化不得直接执行 S2C `tenantId` 迁移；该迁移必须先完成冲突通知并进入独立契约迁移切片。
- 自动化不得实现运行时 bus 行为，不得改变 Task 所有权，不得在没有单独 delivery projection 的情况下修改 `agent-bus` 之外的生产代码。

### 残余风险

| 风险编号 | 风险 | 接受人 | 缓解方式 | 后续 |
|---|---|---|---|---|
| RK-001 | Bus 文档夸大未来运行时行为 | 待定 | 按 capability 标注契约状态，显式保留 design-only | L1 文档生成 |
| RK-002 | Runtime/service 与 bus 职责因为场景强相关而模糊 | 待定 | 在 bus L1 和 harness 断言中重复 service-owned Task lifecycle 不变量 | H2 评审 |
| RK-003 | 生成产物被误当成语义事实源 | 待定 | projection manifest 必须包含 source facts 和 schema hashes | 自动化投影 |
| RK-004 | S2C 租户范围当前仍是隐式绑定，和 H2 接受的目标设计不一致 | 待定 | 记录冲突方并建立 S2C tenant 契约迁移切片 | 契约评审 |

### 冲突通知记录

| 通知编号 | 冲突方 / 受影响范围 | 冲突内容 | 需要通知的原因 | 建议处理方式 | 状态 |
|---|---|---|---|---|---|
| CN-001 | `agent-bus` 契约与代码 | `docs/contracts/s2c-callback.v1.yaml` 和 `S2cCallbackEnvelope` 当前没有 `tenantId`，但 H2 决定后续必须增加 | 这是契约源和 Java record 的破坏性签名变更 | 建立独立迁移切片，同步更新 YAML、record compact constructor、测试和契约目录 | 待通知 |
| CN-002 | `agent-bus` S2C 测试 | `S2cCallbackEnvelopeLibraryTest` 当前按无 `tenantId` 构造 envelope | 增加 required field 后现有测试构造点会失败 | 迁移切片中增加缺失/空白 `tenantId` negative tests，并更新正向构造用例 | 待通知 |
| CN-003 | `agent-service` / runtime | service 文档和运行时 S2C 流程消费 `S2cCallbackTransport`，并负责 suspend/resume、response validation | service 是 S2C envelope 的主要生产/消费方，构造点和校验点必须同步 | 通知 service owner，确认构造点、schema validation、审计字段和 Run 上下文的 tenant 来源 | 待通知 |
| CN-004 | `agent-execution-engine` / engine boundary | `SuspendSignal.forClientCallback(parentNodeKey, envelope)` 以 Object 承载实际 `S2cCallbackEnvelope`，engine 测试也覆盖该路径 | 虽然签名不直接依赖具体 record，但 S2C envelope 形状变化会影响 callback suspension 语义和测试夹具 | 通知 engine owner，确认 `SuspendSignal` 注释、测试夹具和 orchestrator cast 路径是否需要同步 | 待通知 |
| CN-005 | `agent-client` / edge capability | S2C dispatch 目标是 client/edge；增加 `tenantId` 后客户端接收、审计、幂等、权限判断可能要使用该字段 | tenant scope 从隐式 registry 变成 envelope 显式字段，会影响边缘侧契约理解 | 通知 client owner，确认 client-side schema、callback handler、审计字段和兼容策略 | 待通知 |
| CN-006 | 既有 L1 文档与模板 | 多处 service L1 文档或模板已经声称 `S2cCallbackEnvelope.tenant_id` 存在，但当前 Java record 没有该字段 | 这是当前事实冲突：文档目标态和代码现状不一致 | 通知文档/模板 owner，迁移前标注为目标态或待迁移，迁移后统一改成已实现事实 | 待通知 |
| CN-007 | contract catalog / 生成模板 | `docs/governance/templates/contract-catalog.md.j2` 当前记录“preferred fix 是 add tenantId，但 deferred” | H2 已经把 preferred fix 提升为接受决策，模板状态需要更新 | 通知治理模板 owner，在迁移切片中更新 deferred 状态和生成输出 | 待通知 |

### 后续产物

- Delivery projection：`docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-harness.md`
- Verification matrix update：H2 后增加 `agent-bus` contract/harness 行
- 实现切片：
- 已生成：`architecture/docs/L1/agent-bus/**`
- 待生成：
  - 增加 ingress 契约测试
  - 增加 federation/reflection 契约 fixture 或 schema validation 决策
  - 增加模块依赖和 contract projection manifest 的漂移检查
