---
level: L1
view: [scenarios, logical, process, development, physical]
module: agent-execution-engine
affects_level: L0, L1
affects_view: [scenarios, logical, process, development, physical]
status: proposed
---

# 架构评审提案：agent-execution-engine L1 高级设计提案 (Wave 1.2)

> **日期:** 2026-05-25
> **作者:** LucioIT (核心架构师) & 急急 (智能体)
> **目标 Wave:** W0/W1 (立即执行)
> **关联军规:** Rule G-1.c (L1 深度与落地)

## 1. 背景与原则 (Background & Principles)

### 1.1 顶层设计背景 (L0 架构)

#### 1.1.1 六大核心模块
1. **智能体客户端 (agent-client)**：在 SaaS 应用与桌面应用中被集成，负责感知业务知识与状态，操作业务环境与工具，下发管理智能体配置，调用执行智能体服务。
2. **智能体服务端 (agent-service)**：负责把图模式执行 of workflow 智能体与循环模式执行 of ReAct 智能体封装成微服务。
3. **智能体执行引擎 (agent-execution-engine)**：**（本模块核心定界）** 负责提供两大类智能体的执行器，提供可供开发者使用的各种组件，如 workflow 会用到的 node、ReAct 会用到的 tool 和 hook。
4. **智能体总线 (agent-bus)**：负责连接南北向 of C/S 通信流量，连接东西向 of A2A 通信流量。
5. **智能体中间件 (agent-middleware)**：负责提供智能体需要的基础服务，如记忆服务、技能服务、知识服务、沙箱服务等。
6. **智能体演进平台 (agent-evolve)**：负责在线与离线的智能体自主演进。

#### 1.1.2 两种核心部署/集成模式
- **平台中心模式 (Platform-Centric Mode)**：业务侧仅集成 `agent-client`，其他所有模块均部署在平台端（集中托管与运行，降低业务集成心智负担）。
- **业务中心模式 (Business-Centric Mode)**：业务侧不仅集成 `agent-client`，还会在本地化（业务物理边界内）部署 `agent-service` 和 `agent-execution-engine`，实现就近计算；平台侧仅提供统一治理、互联互通及基础公共服务。

### 1.2 项目阶段背景与演进规划

#### 1.2.1 定位：平台级工具集与无状态引擎 (Framework & Core Engine)
根据 **L0 顶层架构定位**，`agent-execution-engine` 的核心使命是**作为智能体执行的“物理芯片（Runtime Engine）”**，提供高度抽象、通用、无状态的智能体底层驱动程序与组件生态。
- **工具非智能体**：本模块不应该且绝不耦合任何具体垂直业务领域的智能体（如具体的客服、代码助手等）。具体的业务智能体，应由下游开发者（平台使用者）基于本模块提供的核心引擎和积木组件进行组装与定制开发。
- **核心演进重心**：
  - **组件化沉淀**：本阶段聚焦于提供高内聚、低耦合的基础执行器（Executors）与开箱即用的标准组件（Nodes/Tools/Hooks），从而让开发者能通过搭积木的方式拼装出复杂的智能体。
  - **开发者体验（DX, Developer Experience）倍增**：提供流畅的编程 API（如 Java Fluent API 或 DSL）、热插拔加载机制以及运行时状态追踪能力，使智能体的开发、调试和测试过程极简化。

#### 1.2.2 演进 Roadmap（双阶段交付）
- **Wave 1 (当前聚焦 - 极简高能核心)**：
  - 完整交付两大基础执行器：面向确定性图拓扑的 **Workflow 图模式执行器**，与面向非确定性推理决策的 **ReAct 循环模式执行器**。
  - 抽象并落地标准组件模型（Node SPI、Tool SPI、Hook 机制）。
  - 提供本地化的共进程函数驱动（为 `agent-service` 的极速 Fast-Path 提供就地算力支撑）。
- **Wave 2 (未来扩充 - 开发者生态赋能)**：
  - 提供运行时热插拔组件加载器（Dynamic Class Loader），支持在不重启 JVM 的情况下动态发布和装载新的 Tool/Node 插件。
  - 研发并交付可视化低代码编辑器底座（Visual Flow Canvas SDK），支持配置一键编译为执行引擎原生支持的图拓扑 DAG。

### 1.3 设计原则与核心形态

#### 1.3.1 核心形态：双驱运行内核 (Dual-Engine Architecture)
1. **工作流引擎（Workflow / Graph-Mode Executor）**：
   - **拓扑执行**：支持有向无环（DAG）以及带有复杂环形/条件跳转的复杂图（Graph）拓扑调度。
   - **积木化节点（Standard Nodes）**：将常见计算步骤固化为原子级 Node 组件（如 LLM 推理节点、Prompt 渲染节点、条件决策路由节点、数据收集与映射节点等），开发者只需通过 DSL 或 Java API 编排节点关系。
2. **ReAct 引擎（ReAct / Loop-Mode Executor）**：
   - **循环推进（Reasoning-Action Loop）**：负责维护“思考(Thought) -> 动作(Action) -> 观察(Observation)”的自主推理闭环。
   - **组件级支撑（Tools & Hooks）**：提供标准的 **Tool（物理工具适配）** 与 **Hook（生命周期钩子）**。通过 Tool 屏蔽外部系统的物理调用；通过 Hook 支持在 Loop 运行的前置、中置、后置注入统一的安全审计、风控拦截和日志追踪。

#### 1.3.2 设计原则：无状态芯片与极简化编程 (Stateless & DX Tenets)
1. **完全无状态（Stateless Compute Kernel）**：
   - 引擎内核坚守“纯计算芯片”原则，**不直连任何持久化数据库，也不发起直接的 A2A 网络寻址**。
   - 每次执行均为一次纯粹的输入（InjectedContext）到输出（StateDelta）的映射过程。
   - 一切阻断或外部依赖（如需要调用工具或协同其他智能体）一律通过向上层 Service 抛出强类型的 **`InterruptSignal`（中断信号）** 来实现，彻底释放底层计算线程，实现极致的并发性能与分布式容灾。
2. **防腐与异构引擎兼容（Heterogeneous Engine Friendly）**：
   - 提供标准化的抽象层，能够以统一的 SPI 屏蔽不同大模型（LLMs）以及底层物理框架的异构差异。
   - 提供影子工具（Shadow Tools）与防腐上下文翻译器，使得外部存量智能体能轻松被引擎纳管。
3. **极简编程契约（Developer Ergonomics）**：
   - 保持最轻量的心智负担：工具注册仅需一个注解（如 `@Tool`），节点实现只需实现单接口（`NodeExecutor`）。
   - 提供强类型契约保障，编译期即可检测出编排错误，规避黑盒运行时的脆弱性。

## 2. 场景视图 (Scenarios View)

## 3. 逻辑视图 (Logical View)

## 4. 进程视图 (Process View)

## 5. 开发视图 (Development View)

## 6. 物理视图 (Physical View)

## 7. 附录：核心 SPI 接口 (Appendix: Core SPI Interfaces)
