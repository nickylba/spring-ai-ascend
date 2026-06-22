---
level: L0-TLD
TAG:
  - constraints
  - invariants
  - tenant
  - posture
  - telemetry
  - architecture-fact
status: active
dependency:
  - README.md
  - overview.md
  - boundaries.md
  - governance.md
  - glossary.md
---

# L0 架构约束

## 目的

本文档定义 L0 顶层架构必须长期遵守的约束与不变量，用于约束后续 L1/L2 架构设计、运行时路径、模块边界、代码实现和验证材料。

L0 约束回答“系统无论如何实现都不能违反什么”。它不定义具体数据库表、字段、消息 topic、方法签名、超时时间、重试次数、线程池参数、API schema 或 SPI 细节；这些内容由 L1/L2 架构、契约、代码和运维材料承载。

本文档不重复 `boundaries.md` 中的模块边界、系统范围、数据对象归属和运行协作边界。涉及六大逻辑模块职责、数据/状态对象 owner、跨模块行为界面的内容，以 `boundaries.md` 为准；本文档只保留跨边界长期生效的架构红线和切面约束。

## 核心不变量

| 不变量 | 约束 |
|---|---|
| 业务与平台解耦 | 业务代码通过声明式配置、扩展机制或适配方式接入，不修改平台核心内部逻辑来满足单个业务定制。 |
| 单一生命周期 writer | 服务端执行生命周期状态必须只有一个语义 owner 和一个受认可写入路径。 |
| 工具调用受治理 | 工具/技能调用必须经过授权、容量、幂等、审计和可观测性边界。 |
| 中断受治理 | 用户、智能体、审批、取消和方向变更等中断必须通过服务侧拥有的挂起/恢复、回调或控制命令路径进入。 |
| 上下文经由上下文边界 | Context package 必须通过服务/中间件上下文、记忆和检索表面生成，不得隐藏在执行组件内部逻辑中。 |
| 业务状态外部化 | 业务系统拥有业务事实；平台只记录引用、轨迹、审计证据和受控结果。 |
| 挂起而非占用 | 当前 active 架构要求长等待和中断以显式 Task 状态、事件或回调入口表达；完整 suspend/resume、cursor 和跨重启恢复能力属于 draft 设计。 |
| Trace 上下文传播 | 跨模块执行传播 tenant、trace、Task identity、runtime identity 和必要的 client invocation reference。 |
| 副作用安全 | 不可逆副作用必须具备幂等或重复保护，并留下审计证据。 |
| 子工作可见 | 子执行必须关联到父 Task tree，或通过显式跨 workflow / 跨实例交接记录关系。 |
| 控制/数据/流分离 | 当前 active 架构要求 Service Task API、服务实时流和协议桥边界不混写生命周期状态；Platform Gateway、`agent-bus` 治理、窄事件/控制通道和对象引用数据路径的完整分离属于 draft 设计。 |

## 切面约束

### 租户纵向约束

租户身份当前必须从入口边界传播到 runtime 执行上下文。网络传输、持久化边界和平台级租户权限检查尚未展开为 active L1 设计。

未来生产运行时应避免在受控边界之外依赖 HTTP 边缘的 ThreadLocal 租户状态；跨模块调用、跨边界协作和重放验证的 fail-closed 租户检查作为 draft 提案推进。

### 安全态势纵向约束

`APP_POSTURE` 在启动阶段读取，用于控制开发、研究和生产等不同安全态势下的默认行为。更严格态势下缺失必要配置必须 fail closed。

态势差异必须显式表达，不能通过隐式默认值、环境偶然行为或未记录的调试开关改变运行时安全边界。

不可信生成代码和未验证第三方工具必须先经过沙箱治理和容量约束，严格态势下不得被默认视为允许执行。

### 遥测纵向约束

遥测是一等横切能力。Trace、span、LLM call、runtime event、audit 和 cost 证据必须通过受认可的上下文载体或运行时 hook 表面产生。

Provider adapter 不得成为独立且唯一的遥测写入源。跨模块执行必须传播 tenant、trace、Task identity、runtime identity 和必要的 client invocation reference。

每个核心场景都应定义期望产生的 trace、event 和 audit 证据。当运行时绑定存在时，LLM generation span 必须携带模型、token、cost 和 latency 证据。

平台 cost attribution 覆盖 LLM 使用、模型路由和平台运行时成本证据。客户内部工具成本和业务系统成本仍属于客户/业务关注点，除非单独 active contract 委托平台上报。

重放表面需要具备租户作用域，并在租户不匹配时 fail closed；该约束当前作为安全与租户切面 draft 提案推进。MCP-only replay 是当前 L0 遥测重放方向，除非 active ADR 改变该方向。

严格态势下，原始 prompt、completion、tool input 或 tool output 不得作为 span attribute 直接记录。

### 审计与策略纵向约束

安全决策、不可逆工具调用、审批决策、跨边界交接和生命周期迁移都必须留下审计证据。

策略拒绝必须可观测，并且不得产生隐藏副作用。任何涉及外部系统调用、状态修改、资源扣减或权限敏感行为的路径，都必须具备审计和重复保护预期。

### 容量与背压纵向约束

当前 active `agent-runtime` 记录并暴露其无界 cached pool 和 InMemory 队列约束，不声明已经具备系统级背压。长周期工作通过有界运行时资源声明进入系统、资源压力表达为准入决策/背压信号/挂起/让出/跨边界交接，作为后续 draft 设计推进。

可能等待大模型生成、向量检索、沙箱执行或第三方服务的外部 I/O，后续应通过 reactive、virtual-thread、suspend/resume 或等价的非占用执行模式释放稀缺计算资源。具体库、线程模型、超时值和强制策略属于后续 L1/L2 或实现层设计。

### 开发者生命周期纵向约束

开发者体验是架构关注点，不只是文档工作。核心运行时行为必须暴露足够的 trace、debug timeline、harness fixture、运行证据和失败解释，使外部 Spring 开发者与模块贡献者能够独立集成和排障，而不依赖平台团队人工介入。

脚手架、mock、stub、断言、场景证据和可重放材料应服务于架构约束验证，不应只作为实现阶段的临时材料。

## 架构状态和验证期望

### 架构状态约束

- `draft`、`proposal`、`active` 和 `archive` 必须按当前存活状态使用。
- 状态用于描述材料在当前架构体系中的存活状态，而不是记录材料经历过的历史阶段。
- `proposal` 契约不得描述成当前 `active` 架构事实或运行时已强制执行的行为。
- 历史文档可以提供决策证据，但不得覆盖当前架构事实权威。
- `docs/architecture/l0/05-contracts/` 下的草案契约 YAML 在提升到 active contract system 前，不得驱动生产行为。

### 验证期望

每条 L0 约束最终应至少映射到以下一种验证形态：

- 静态架构检查。
- 状态机测试。
- 契约测试。
- 场景测试。
- golden trace 测试。
- 故障注入测试。
- 安全评审。
- 人工架构评审。

尚未验证的约束必须在 `governance.md` 中登记为缺失验证或待提升事项。
