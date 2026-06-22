---
level: L1
view: scenarios
module: agent-runtime
status: draft
proposal_status: draft
updated: 2026-06-22
source_active_view:
  - architecture/L0-Top-Level-Design/views.md
  - architecture/L1-High-Level-Design/agent-runtime/scenarios.md
---

# agent-runtime 与 L0 S1-S6 场景缺口草案

> Draft 状态文档。本文从当前 `agent-runtime` L1 TS 场景视角，对 L0 S1-S6 进行查漏补缺。完全落在当前 active L1 的部分可作为后续映射基础；未完全覆盖的部分先保留为 draft 设计缺口。

## 1. 当前 L1 TS 场景

当前 active `agent-runtime` L1 场景包括：

| TS | 当前证明点 |
|---|---|
| TS-01 A2A 客户端调用本地 Agent | A2A Service Task API 创建/推进 Task，并通过中立 SPI 调用本地 handler。 |
| TS-02 异构 Agent 框架接入 | 框架适配器依赖中立 SPI，不污染 A2A 协议桥。 |
| TS-03 Task/Session 与业务 Agent 状态分离 | Runtime Task/Session 与业务 Agent checkpoint 不混写。 |
| TS-04 S2C 输出模式统一回传 | 同步、流式、查询路径统一折叠到 Task 状态和中立结果。 |
| TS-05 嵌入式 runtime 启动 | runtime 作为 library artifact 被 host 嵌入。 |
| TS-06 Task 查询、订阅与取消 | 围绕 Task ID 的 query、subscribe、cancel 保持 Task owner 清晰。 |
| TS-07 人工输入中断与继续执行 | `INPUT_REQUIRED` 和继续消息表达当前 active 中断恢复路径。 |
| TS-08 远端 A2A Agent 工具化调用 | 远端 Agent 工具规格中立化，且不抢占远端生命周期 owner。 |

## 2. L0 S1-S6 覆盖情况

| L0 场景 | 当前覆盖 | 已覆盖 TS | 未覆盖部分 |
|---|---|---|---|
| S1 创建 Task | 部分覆盖 | TS-01, TS-05, TS-06 | actor、idempotency、posture、生产级 tenant 准入和 gateway 前置治理未覆盖。 |
| S2 执行智能体步骤 | 基本覆盖 | TS-01, TS-02, TS-04 | `agent-core` 官方边界与异构框架边界的长期归属仍需随 agent-core L1 展开校准。 |
| S3 构建上下文包 | 部分覆盖 | TS-03, TS-07 | Context Engine、memory/retrieval/prompt/advisor 组装和中间件治理未覆盖。 |
| S4 带治理的工具调用 | 部分覆盖 | TS-02, TS-08 | Tool/Skill/Sandbox 授权、容量、审计、幂等和不可逆副作用保护未覆盖。 |
| S5 挂起 / 恢复 | 部分覆盖 | TS-07, TS-06 | 跨重启 checkpoint/cursor、next-wake、timeout、rhythm signal 和资源让出未覆盖。 |
| S6 子 Task / 联邦协作 | 部分覆盖 | TS-08 | 同实例 Task tree、跨实例 federation、agent-bus 治理和 join/failure propagation 未覆盖。 |

## 3. Draft 缺口条目

### GAP-01 S1 生产级准入

需要补充：

- actor / subject 绑定。
- idempotency key 和重复请求保护。
- posture 驱动的 fail-open / fail-closed 差异。
- Platform Gateway 或 host 前置治理与 Service Task API 的职责切分。

候选归属：`agent-runtime-runtime-hardening-draft.cn.md` 与未来 agent-bus / gateway L1。

### GAP-02 S3 Context Engine

需要补充：

- Session shell、context projection、memory、retrieval、prompt、advisor 的组装顺序。
- Runtime 与 middleware 的状态 owner 切分。
- Context package 的 tenant、trace、Task identity 传播。
- replay fixture 和 golden trace。

候选归属：未来 agent-middleware L1/L2 或专门 Context Engine draft。

### GAP-03 S4 Tool / Skill / Sandbox 治理

需要补充：

- Tool intent 从执行组件进入 runtime/middleware 治理面的契约。
- 授权、容量、审计、幂等和副作用重复保护。
- sandbox 隔离和不可信代码策略。
- 工具结果回写 Task 和 trace/audit 的证据模型。

候选归属：未来 agent-middleware L1/L2 或 Tool Gateway draft。

### GAP-04 S5 持久化挂起/恢复

需要补充：

- 持久化 TaskStore、EventStore、QueueManager。
- checkpoint/cursor/next-wake 语义。
- 重启恢复、异常退出和超时补偿。
- 慢消费者和 SSE 重连 cursor。

候选归属：`agent-runtime-distributed-physical-topology-proposal.cn.md` 与 `agent-runtime-runtime-hardening-draft.cn.md`。

### GAP-05 S6 Task tree / Federation / Bus

需要补充：

- 同实例 child Task 与 parent Task tree 的关系记录。
- 远端 Task reference、join、failure propagation 和 cost attribution。
- 跨实例、跨部门、跨信任边界进入 agent-bus 的准入规则。
- 数据引用信封和大载荷路径。

候选归属：未来 agent-bus L1/L2 与 runtime outbound/bus contract draft。

## 4. 后续映射建议

在上述 TS 场景和 draft 缺口稳定后，再回到 active 文档中做 L0 S1-S6 到 L1 TS 的正式映射。映射时应区分：

- active 覆盖：已有代码事实、测试或架构边界测试。
- active 但未自动验证：需要人工评审或显式未验证状态。
- draft 覆盖：只存在未来设计方向，不得描述为当前运行时权威。

## 5. 与 active 文档的关系

本文不修改当前 L0 S1-S6 的 active 文案，也不声明缺口已实现。它只保存下一步补齐与映射工作的输入。
