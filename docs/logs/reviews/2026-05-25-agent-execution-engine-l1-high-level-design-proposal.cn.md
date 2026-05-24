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

#### 1.2.1 定位：基于开源基座增补的工具集引擎 (Open-Source Foundation & Core Engine)
根据 **L0 顶层架构定位**，`agent-execution-engine` 本身不参与具体垂直业务智能体的开发，而是作为智能体执行的“通用物理芯片”。
- **基于开源底座快速迭代**：本模块的发展并非闭门造车，而是**基于成熟开源项目 `openJiuwen/agent-core-java` 仓库作为起点与物理底座进行深度改造、增补与增量迭代**。我们将复用其已具备的底层执行器核心功能，并针对平台化需求进行分布式、无状态、响应式及 A2A 协议层面的改造。
- **工具非智能体**：本引擎仅提供基础执行器、通用组件及开发工具，具体的业务逻辑由使用工具的开发者（平台用户）自行构建。

#### 1.2.2 开发者体验 (DX) 演进路线（三阶段跃迁）
为了降低开发门槛、释放生产力，我们为引擎规划了清晰的开发者体验演进 roadmap：
1. **阶段一（当前 Wave 1 聚焦）：配置化开发 (Configuration-Driven Development)**
   - 彻底将智能体逻辑与代码解耦。无论是 Workflow 还是 ReAct 智能体，其核心运行拓扑、工具绑定关系、以及 Hook 配置全部支持**声明式标准 schema（JSON/YAML）定义**。支持配置一键解析、动态加载并在生产环境直接部署。
2. **阶段二：图形化开发 (Graphical Flow Canvas)**
   - 抽象并提供可视化的低代码拖拽画布（Flow Chart），支持开发者直观地对 Node、Tool、Hook 进行拓扑连接，并一键导出标准配置文件。
3. **阶段三（终极杀手锏）：自然语言式开发 (Natural Language-Driven Development)**
   - **对话式构建**：开发者用户在跟“元智能体（Meta Agent）”聊天的过程中，直接讨论和细化业务 SOP。
   - **交互式装配**：类似 openClaw 的交互体验，用户可通过自然语言指令“安装这个工具/注入那个钩子”，元智能体实时感知并翻译为引擎底层配置指令。
   - **自动生成与调试**：系统自动在后台合成、编排并进行运行时调试，最终一键导出符合生产部署要求的标准配置包。

### 1.3 设计原则与核心形态

#### 1.3.1 核心形态：双驱运行内核 (Dual-Engine Architecture)
1. **工作流引擎（Workflow / Graph-Mode Executor）**：
   - **拓扑执行**：支持有向无环（DAG）以及带有复杂环形/条件跳转的复杂图（Graph）拓扑调度。
   - **积木化节点（Standard Nodes）**：将常见计算步骤固化为原子级 Node 组件（如 LLM 推理节点、Prompt 渲染节点、条件决策路由节点、数据收集与映射节点等），开发者只需通过 DSL 或 Java API 编排节点关系。
2. **ReAct 引擎（ReAct / Loop-Mode Executor）**：
   - **循环推进（Reasoning-Action Loop）**：负责维护“思考(Thought) -> 动作(Action) -> 观察(Observation)”的自主推理闭环。
   - **组件级支撑（Tools & Hooks）**：提供标准的 **Tool（物理工具适配）** 与 **Hook（生命周期钩子）**。通过 Tool 屏蔽外部系统的物理调用；通过 Hook 支持在 Loop 运行的前置、中置、后置注入统一的安全审计、风控拦截和日志追踪。

#### 1.3.2 设计原则：无状态芯片与声明式/对话式生成 (Stateless & Generative Tenets)
1. **完全无状态（Stateless Compute Kernel）**：
   - 引擎内核坚守“纯计算芯片”原则，不直连数据库、不发起直接 A2A 网络寻址。
   - 每次执行均为 pure 状态映射过程。一切阻断或外部依赖通过向 Service 抛出强类型 **`InterruptSignal`** 解决，实现计算线程的零阻塞与极致并发。
2. **配置与代码彻底分离（Configuration & Code Decoupling）**：
   - 智能体逻辑的承载是“配置”而非“编译后代码”。任何 Workflow 的图节点定义和 ReAct 的决策树均可声明化，使得运行拓扑可在运行时被修改、翻译和序列化。
3. **自然语言式开发友好（NL-Dev Friendly Design）**：
   - 引擎设计支持高度的**动态化反射与热插拔**（如动态热插拔 Tool、动态替换节点），底层逻辑必须提供自省（Introspection）接口。这为阶段三的“元智能体”在对话过程中动态解析 SOP、合成配置、并在运行时环境就地动态热加载进行调试提供了坚实的底层技术支撑。
4. **防腐与异构引擎兼容（Heterogeneous Engine Friendly）**：
   - 提供标准化的抽象层，能够以统一的 SPI 屏蔽不同大模型（LLMs）以及底层物理框架的异构差异。

## 2. 场景视图 (Scenarios View)

### 2.1 极速内聚运行场景 (共进程模式)
- **典型链路**：`agent-service` 解析到标准调用请求 -> 装配 `InjectedContext` -> 直接通过 JVM 内存调用本地共进程的 `agent-execution-engine` -> 执行核极速推进 Run 状态并返回 `StateDelta`，实现亚毫秒级的紧凑计算，不发生多进程网络调用。

### 2.2 声明式配置解析与动态热部署场景
- **典型链路**：开发者编写或更新智能体的 JSON/YAML 配置文件 -> Service 监听到变更或通过 API 触发 -> 引擎的 `ConfigCompiler` 进行编译与安全 Schema 校验 -> 从 `Component Registry` 中拉取对应的 Node/Tool/Hook 实现类 -> 生成全新的执行 DAG 图/状态机 -> 就地热替换（Hot-swap）原实例，实现运行态零停机更新。

### 2.3 自然语言对话式交互开发场景 (阶段三)
- **典型链路**：开发者与“元智能体 (Meta Agent)”对话沟通业务 SOP -> 元智能体通过组件自省（Introspection）接口查询引擎“工具箱”内当前可用的所有 Node、Tool 和 Hook 元数据 -> 元智能体动态合成标准配置 Schema -> 调用引擎的 `ConfigAssembler` 装配并在沙箱调试 -> 确认无误后导出生产环境标准的 YAML 配置文件。

### 2.4 中断挂起与状态脱水原则场景 (零阻塞设计)
- **典型链路**：执行核（Workflow/ReAct）在计算过程中遇到工具物理调用、用户输入、或者 A2A 子智能体协作 -> 执行内核及调用的组件严格遵守不直接进行 I/O 原则 -> 抛出强类型的 `InterruptSignal` 中断信号 -> 执行核捕获中断，立刻停止后续 Run 推进并保存当前内存快照 -> 封装成 `StateDelta` 向上抛回 Service -> 物理工作线程瞬间归还线程池，等待 Service 异步唤醒。

## 3. 逻辑视图 (Logical View)

### 3.1 统一 SPI 契约交互层 (Unified SPI Gateway)
- 引擎对外暴露的唯一物理边界。承载并实现底层的 `StatelessEngineExecutor` 契约，负责接收 Service 输入的 `TaskSpec` 与 `InjectedContext`，通过引擎分流机制调度具体执行核，并将执行成果包装为 `StateDelta` 输出。

### 3.2 声明式配置解析与装配编译器 (Config Compiler & Assembler)
- 负责将声明式的 JSON/YAML Schema 描述一键编译并装配为运行态实体的引擎。
- **配置编译器 (Compiler)**：进行标准 Schema 语意与安全校验，将非结构化配置编译为物理 DAG 关系或决策树状态机。
- **动态装配器 (Assembler)**：结合 `Component Registry`，对配置文件中指定的各种 Node、Tool、Hook 实体类进行实例化、依赖注入、以及连线装配，并支持运行时热插拔（Hot-swap）替换。

### 3.3 双驱物理执行内核 (Dual-Engine Execution Cores)
- 继承自成熟开源项目 `openJiuwen/agent-core-java` 仓库并进行“无状态化”深度改写的核心驱动层。
- **工作流（图）执行内核 (Workflow Core)**：负责 Workflow 智能体在 DAG 图层面的拓扑节点排序、分支路由控制、条件循环与跳转。
- **自适应循环（ReAct）执行内核 (ReAct Core)**：负责 ReAct 自主智能体在 "Thought -> Action -> Observation" 循环中的自主推理状态变迁，包含 LLM 文本与结构化 Tool Call 命令的双向解析。

### 3.4 引擎组件注册表与元数据自省服务 (Component Registry & Introspection)
- 引擎内部的“乐高积木盒子”与元数据目录。
- **组件注册表 (Registry)**：作为 Class/Plugin 的核心内存 Registry，存储和维护引擎支持的所有可用基础组件（各种 NodeExecutor 类、ToolSPI 类、LifecycleHook 类）。支持使用动态类加载器（Dynamic Class Loader）动态加载外部 Jar 包以扩展积木库。
- **组件自省服务 (Introspection)**：提供公共查询接口，向外（如面向开发者、可视拖拽画布、或对话式元智能体）暴露当前注册表内所有可用积木组件的描述（Description）、属性、以及输入参数的 JSON Schema，为自然语言对话式构建提供元数据底层支撑。

### 3.5 异构配置迁移编译器 (Heterogeneous Config Transpiler)
- 用于降低迁移心智负担的非运行态编译工具。负责接收 LangChain、LlamaIndex 等第三方异构框架的 Agent 拓扑配置文件，静态翻译、映射并输出为本引擎标准的声明式 Schema 配置，不参与运行态任何物理拦截。

## 4. 进程视图 (Process View)

### 4.1 声明式配置解析与动态热加载时序
1. **配置检测**：Service 监听到智能体配置（如 JSON 文件）发生变更，调用引擎编译器接口。
2. **Schema 校验**：`ConfigCompiler` 拦截配置，进行 Schema 合法性与安全性校验。
3. **图装配**：`ConfigAssembler` 解析节点与连线，去 `ComponentRegistry` 中查找对应的 Node、Tool 和 Hook 物理类并反射实例化。
4. **内存热换 (Hot-swap)**：引擎以原子操作将该 Agent 对应的内存执行图引用指向全新的装配实体，后续该 Agent 的所有计算请求直接由新图驱动。

### 4.2 对话式开发中组件自省与动态装配时序
1. **获取积木清单**：元智能体 (Meta Agent) 启动，向引擎自省服务 `IntrospectionService` 发起查询请求。
2. **输出积木元数据**：自省服务返回当前 Registry 中注册的全部组件元数据（包含 Tool、Hook 的入参 Schema 细节）。
3. **对话互动装配**：用户对元智能体说：“我想给 Agent 加上发送邮件工具”。
4. **追加并重编译**：元智能体核对元数据，生成标准的 `EmailTool` 配置段追加到该 Agent 配置中，随后触发热加载时序（4.1）在沙箱进行热插拔安装并激活调试。

### 4.3 引擎中断挂起与状态脱水时序
1. **运行触界**：双驱执行内核在推进单步 Run 过程中（如 ReAct 推理出需要调用 Google 搜索物理工具，或 Workflow 走到人工审批 Node）。
2. **抛出信号**：目标物理组件（如 Tool）遵守开发原则，不进行任何同步阻塞与中间件交互，瞬间向执行内核抛出强类型 `InterruptSignal(TOOL_EXECUTION, GoogleSearch)` 中断。
3. **内核挂起**：执行内核捕获中断，立即冻结运行状态、中止单步计算，输出最新的状态快照及信号包并封装为 `StateDelta`。
4. **线程解耦**：SPI 契约接口将 `StateDelta` 返回给 `agent-service`。Service 进行状态序列化并异步脱水，执行引擎物理计算线程立刻彻底释放并归还至 JVM 线程池。

## 5. 开发视图 (Development View)

### 5.1 依赖开源与自研边界定界
本模块采用**“继承开源，专注增量，改造内核”**的原则，与 `openJiuwen/agent-core-java` 仓库保持明确的分工边界：
- **复用开源基座**：直接继承其已被验证过的底层 DAG 拓扑图排序算法、ReAct 基础推理循环逻辑。
- **自研与深度改造部分（100% 自研/重写）**：
  - **无状态化改造**：彻底重构其执行核堆栈，剔除所有同步等待与物理 I/O，全面重构为“中断信号（InterruptSignal）- 状态脱水”的纯计算运行态。
  - **声明式配置编译器（ConfigCompiler & Assembler）**：研发一整套 JSON/YAML Schema 解析与内存图/状态机热加载器。
  - **自省自知能力（Introspection）**：研发组件注册表与 Schema 级元数据自省服务，专为对话式构建提供支撑。
  - **异构迁移工具（Transpiler）**：研发第三方配置的静态翻译迁移编译器。

### 5.2 自研代码包目录映射与依赖集成
```text
agent-execution-engine/src/main/java/com/huawei/ascend/agent/engine/
├── spi/                        # 统一 SPI 契约边界
│   └── StatelessEngineExecutor.java # 承载 Service 与 Engine 唯一的无状态交互接口
├── compiler/                   # 声明式配置编译器与装配引擎
│   ├── ConfigCompiler.java     # 配置文件解析与安全/格式校验
│   ├── ConfigAssembler.java    # 节点、工具、拦截钩子物理组装与内存热替换
│   └── schema/                 # 声明式 JSON/YAML 标准 Schema 验证文件
├── core/                       # 双驱无状态物理执行内核 (基于 openJiuwen 深度重构)
│   ├── workflow/               # 改造后的无状态 Workflow/DAG 计算推动器
│   └── react/                  # 改造后的无状态 ReAct 思考循环计算推动器
├── registry/                   # 组件注册表与自省服务 (乐高积木管理)
│   ├── ComponentRegistry.java  # Node、Tool、Hook 物理类加载与注册
│   ├── IntrospectionService.java # 组件描述与输入 Schema 元数据查询暴露服务
│   └── loader/                 # 外部 Jar 动态 ClassLoader 热插拔加载器
└── transpiler/                 # 异构配置迁移编译器 (纯静态翻译)
    ├── LangChainTranspiler.java # LangChain 配置翻译器
    └── LlamaIndexTranspiler.java # LlamaIndex 配置翻译器
```

## 6. 物理视图 (Physical View)

### 6.1 共进程内聚部署拓扑 (Embedded Deployment)
- `agent-execution-engine.jar` 作为一个轻量级计算芯片，直接以 Maven 依赖形式打包在 `agent-service.jar` 进程中（如 JVM 同一堆进程）。
- **进程内直连**：配置编译器、组件注册表、以及物理执行核都在同一 JVM 堆中运行。所有的装配、热加载（Hot-swap）、自省服务调用均为毫秒级的直接内存函数调用，极大榨干计算性能。

### 6.2 动态扩展 Jar 包的热部署拓扑 (Dynamic Extension Deployment)
- 当开发者通过自然语言对话元智能体动态新装或更新某个自定义 Tool/Hook 插件时，Service 层在物理节点将该插件 Jar 包下载存盘。
- Engine 侧的 `Dynamic Class Loader` 物理扫盘，并将该 Class 动态载入当前 JVM ClassPath 并注册至组件注册表（Registry），使积木盒在不重启物理容器的前提下，具备动态水平无限扩展的能力。

## 7. 附录：核心 SPI 接口 (Appendix: Core SPI Interfaces)

### 7.1 StatelessEngineExecutor 引擎唯一核心契约接口定义
```java
package com.huawei.ascend.agent.engine.spi;

import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Map;

/**
 * 智能体执行引擎核心无状态计算接口 (Engine SPI Contract)
 */
public interface StatelessEngineExecutor {
    /**
     * 无状态执行入口：输入任务定义与投影上下文，输出执行 Delta 增量与可能的中断信号
     */
    Mono<StateDelta> execute(TaskSpec task, InjectedContext ctx);
}

public class TaskSpec {
    private String taskId;
    private String agentId;                   // 指向要执行的智能体配置 ID
    private String taskType;                  // WORKFLOW 或是 REACT
    private Map<String, Object> parameters;   // 启动控制性参数
    // Getters and Setters...
}

public class InjectedContext {
    private String sessionId;
    private List<Message> messageHistory;      // 投影装配好的历史交互消息列表
    private List<Map<String, Object>> tools;   // 本轮可用物理工具集定义 (元数据 Schema)
    private Map<String, Object> sessionVars;   // 运行时会话变量快照
    // Getters and Setters...
}

public class StateDelta {
    private List<Message> newMessages;         // 本轮计算产生的新消息
    private Map<String, Object> updatedVars;   // 产生变化的会话变量增量
    private InterruptSignal interruptSignal;   // 触发挂起时携带的强类型控制中断包，完成则为 null
    // Getters and Setters...
}

public class Message {
    private String messageId;
    private String role;                      // USER, ASSISTANT, SYSTEM
    private String content;                   // 自然语言载荷
    private long timestamp;
    // Getters and Setters...
}
```

### 7.2 强类型中断信号 (InterruptSignal) 基础规范
```java
package com.huawei.ascend.agent.engine.spi;

import java.util.Map;

/**
 * 引擎中断信号类型定义
 */
public enum InterruptType {
    INPUT_REQUIRED,   // 索要用户输入或人工审批
    TOOL_EXECUTION,   // 引擎遭遇物理工具执行（如物理 API 或沙箱代码）
    SUB_TASK_AWAIT    // 引擎在 A2A 协议中拆分出子任务，需要外接协作
}

/**
 * 强类型中断原语信号：任何组件遇阻时必须立即实例化并 Throw
 */
public interface InterruptSignal {
    String getTaskId();
    InterruptType getType();
    Map<String, Object> getPayload(); // 中断所需的强类型参数，例如 ToolName 和入参数据
}
```

### 7.3 组件自省与注册表 (Component Registry & Introspection) 接口定义
```java
package com.huawei.ascend.agent.engine.registry;

import java.util.List;
import java.util.Map;

/**
 * 引擎基础组件物理注册表接口
 */
public interface ComponentRegistry {
    /**
     * 动态/静态注册积木类
     */
    void registerNode(String nodeType, Class<?> nodeClass);
    void registerTool(String toolName, Class<?> toolClass);
    void registerHook(String hookName, Class<?> hookClass);

    /**
     * 自省接口：暴露当前积木盒中注册的所有积木元数据
     */
    List<ComponentMetadata> listAvailableComponents();
}

/**
 * 积木元数据自省定义 (支撑自然语言对话式构建)
 */
public class ComponentMetadata {
    private String name;                       // 组件唯一标识 (如 DatabaseQueryNode, EmailTool)
    private String category;                   // NODE, TOOL, HOOK
    private String description;                // 自然语言功能描述 (供元智能体理解)
    private String inputSchema;                // JSON Schema 格式的输入参数约束 (供元智能体进行参数生成校验)
    private Map<String, Object> extraAttrs;    // 其他扩展元属性
    // Getters and Setters...
}
```

### 7.4 声明式配置编译器 (ConfigCompiler) 接口定义
```java
package com.huawei.ascend.agent.engine.compiler;

/**
 * 声明式配置解析、安全编译与运行时热加载接口
 */
public interface ConfigCompiler {
    /**
     * 对外部输入的声明式 Agent 拓扑配置（JSON/YAML）进行 Schema 安全与规范格式编译
     * @param configContent 配置文件内容文本
     * @return 编译是否通过
     * @throws IllegalArgumentException 编译不通过时抛出详细语法与拼装错误
     */
    boolean validateAndCompile(String configContent) throws IllegalArgumentException;

    /**
     * 运行时热部署：将某一具体智能体的内存执行图（DAG/状态机）瞬时无损热替换
     * @param agentId 智能体 ID
     * @param newConfigContent 替换的全新配置文件内容
     */
    void hotSwapAgent(String agentId, String newConfigContent);
}
```
