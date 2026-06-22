---
level: L1
view: cross-cutting
module: agent-runtime
status: draft
proposal_status: draft
updated: 2026-06-22
source_active_view:
  - architecture/L0-Top-Level-Design/overview.md
  - architecture/L0-Top-Level-Design/constraints.md
  - architecture/L0-Top-Level-Design/views.md
  - architecture/L1-High-Level-Design/agent-runtime/process.md
  - architecture/L1-High-Level-Design/agent-runtime/physical.md
---

# agent-runtime 运行时硬化草案

> Draft 状态文档。本文档承接当前 active L0/L1 尚未满足但仍然必要的设计点。除非后续 ADR、L1/L2 设计和代码事实完成提升，本文不得反向约束 active 架构或实现。

## 1. 背景

当前 active `agent-runtime` 已经具备 A2A Service Task API 实现形态、Task 生命周期桥接、框架中立执行 SPI、SSE 输出、Task 查询/订阅/取消、人工输入中断和远端 A2A Agent outbound 支撑。

当前 active 设计仍存在以下明确限制：

- 默认 TaskStore、QueueManager 和 EventBus 是宿主 JVM 内 InMemory 组件。
- 进程重启后 runtime Task、事件队列和订阅状态不保证恢复。
- Agent 执行线程池为无界 cached pool，runtime 内不主动提供执行背压。
- 租户 header 当前只做传播，不做认证或权限判定。
- 远端 A2A outbound 不等同于跨实例、跨部门、跨数据边界的 agent-bus 治理。

## 2. 目标

后续运行时硬化应补齐以下能力：

- 持久化 Task 状态、事件和恢复证据。
- 以 checkpoint、cursor、next-wake 或等价机制支持跨重启恢复。
- 为 Agent 执行、SSE 订阅、远端调用和中间件调用提供有界资源模型。
- 将资源压力表达为准入拒绝、背压信号、排队、让出、挂起或跨边界交接。
- 在严格安全态势下对租户身份缺失、租户不匹配和未认证租户来源 fail closed。
- 将跨实例、跨部门、跨数据边界 A2A 交互纳入 agent-bus 治理或显式拒绝。

## 3. 非目标

本草案不直接规定：

- Redis、JDBC、Kafka、Pulsar 或云服务产品选型。
- 完整的 agent-bus L1/L2 设计。
- 客户业务权限模型的所有权迁移。
- 业务 Agent checkpoint 的内部格式。
- 具体 SLA、容量数值和线程池参数。

## 4. 持久化恢复方向

### 4.1 Task 状态持久化

候选设计应提供可替换 TaskStore：

- Task 创建使用幂等键保护重复请求。
- Task 状态迁移使用版本号或 CAS 防止并发 writer。
- Task message、artifact 和 metadata 需要裁剪、归档和 TTL 策略。
- 查询、订阅、取消和继续消息必须能定位 Task owner 或可接管实例。

### 4.2 恢复证据

进入长等待或外部输入等待前，runtime 至少需要保留：

- Task ID、tenant、agent、session/context、trace。
- 当前 Task 状态和等待原因。
- 可恢复位置：cursor、agentStateKey、remote invocation reference 或 next wake signal。
- 已交付给外部系统的副作用证据和幂等键。

### 4.3 恢复语义

恢复路径必须先定义语义，再选择技术实现：

| 场景 | 需要明确的语义 |
|---|---|
| 宿主正常关闭 | drain、暂停接入、等待完成、持久化挂起或标记可恢复。 |
| 宿主异常退出 | Task 是恢复、失败、超时还是人工介入。 |
| 客户端重连 | 从哪个 event offset 继续接收。 |
| 远端调用未完成 | 如何查询远端状态、重放、取消或补偿。 |
| 重复继续消息 | 如何幂等处理，避免重复副作用。 |

## 5. 背压与容量方向

后续设计应把无界资源替换为可治理资源：

- 有界 Agent execution executor，暴露队列长度、拒绝策略和容量指标。
- 按 tenant、agent、Task tree、远端 Agent 和工具类型设置并发预算。
- SSE 慢消费者需要独立缓冲、裁剪或断开策略，不得反向阻塞 Task 控制通道。
- 远端 A2A 调用需要 timeout、circuit breaker、bulkhead 和重试预算。
- 中间件能力调用需要 provider 级背压，不能阻塞 Task lifecycle writer path。

资源压力建议表达为：

| 压力来源 | 候选表达 |
|---|---|
| 接入过载 | 准入拒绝、retry-after、排队或降级。 |
| 执行过载 | Task 保持 submitted/pending、延迟派发或拒绝。 |
| 外部 I/O 长等待 | suspend/resume、cursor 或异步 handover。 |
| SSE 慢消费 | consumer cursor、事件裁剪、断开重连。 |
| 远端调用过载 | bulkhead 拒绝、timeout、fallback 或人工介入。 |

## 6. 租户与安全态势方向

当前 `X-Tenant-Id` 只做传播。后续切面设计应补齐：

- trusted header 来源：只能由前置网关或受信 host 注入。
- default tenant 使用限制：开发态可以默认，生产/严格态势必须显式配置或 fail closed。
- TaskStore、event store、trace、audit、cost 和 replay fixture 全部携带 tenant。
- 查询、订阅、取消、继续消息和 replay 必须校验请求 tenant 与 Task tenant 一致。
- 远端 A2A Agent、工具、memory 和 sandbox 调用必须带上租户作用域和授权引用。

## 7. agent-bus 与远端 A2A 边界方向

当前 runtime outbound 能力可以直连远端 A2A Agent，但它不是完整 bus 治理。后续应明确：

| 调用形态 | 候选处理 |
|---|---|
| 同 JVM / 同 host 内部调用 | 可保持 runtime 内部闭合。 |
| 同租户、同信任域远端 A2A | 可由 runtime outbound 支撑，但必须记录显式信任边界。 |
| 跨实例、跨部门、跨部署或跨数据边界 | 应进入 agent-bus 治理或被策略拒绝。 |
| 大载荷或敏感数据 | 控制消息只携带引用信封，数据走授权数据路径。 |
| rhythm / wakeup / timeout | 可由 bus 治理信号，但 Task sleep state 仍归 runtime owner。 |

## 8. 验证期望

提升为 active 前，至少需要以下验证：

- TaskStore 状态机并发写测试。
- 重启恢复和异常退出故障注入测试。
- SSE 重连和慢消费者测试。
- bounded executor 过载和拒绝策略测试。
- tenant mismatch fail-closed 测试。
- 远端 A2A 超时、取消、重复回灌和幂等测试。
- agent-bus 边界人工评审或契约测试。

## 9. 与 active 文档的关系

当前 active 文档应继续描述：

- A2A 是 Service Task API 当前实现形态。
- Runtime 默认 InMemory，单实例状态岛，不保证跨重启恢复。
- 当前库内不主动提供执行背压。
- 租户 header 当前只传播不认证。
- 远端 A2A outbound 不替代 agent-bus。

本文只保存未来设计方向，不覆盖 `architecture/` 下当前事实。
