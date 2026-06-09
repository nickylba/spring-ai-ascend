# SkillSandbox 运行时挂起与隔离架构 (基于 Native SchemaOnlyTool)

## 1. 核心架构理念 (Core Philosophy)
本架构解决大模型框架（如 AgentScope）执行物理技能（Skill/Tool）时的“单体阻塞”与“黑盒执行”危险。
**架构转向说明**：在深入剖析 AgentScope-Java 源码后，我们废弃了之前“通过自定义 Exception 和流劫持”的侵入性 Hack 方案。转而**完全拥抱框架原生的 `SchemaOnlyTool` 和 `ToolSuspendException` 机制**。这意味着我们利用框架自带的能力，实现了零侵入的动态挂载和状态中断。

## 2. 物理映射与原生对接机制 (Native Mechanisms)

### 2.1 基因渲染与注册 (`SchemaOnlyTool` 动态绑定)
AgentScope 提供了 `io.agentscope.core.tool.SchemaOnlyTool`。它允许我们通过传入纯数据的 `ToolSchema`（而不必写任何具体的 Java 方法和 `@Tool` 注解）来动态生成工具实例。
- **流程**：系统启动或运行时，`agent-sdk` 从 YAML 中读取技能描述。
- **装载**：直接调用 `new SchemaOnlyTool(schema)` 将其装载给底层的 `ReActAgent`。
- **效果**：大模型能收到完美的 JSON Schema Prompt，符合《基因-工具下发一致性公理》。

### 2.2 挂起机制 (`ToolSuspendException` 原生中断)
当大模型发起工具调用命中 `SchemaOnlyTool` 时，其底层的 `callAsync()` 会原生返回一个 `Mono.error(new ToolSuspendException())`。
- **接管**：AgentScope 的核心状态机会自动捕获这个内置异常，终止当前的推理流，并吐出一个挂起状态的消息（含 Pending ToolCallBlock）。
- **转换**：我们的 `AgentScopeStreamAdapter` 直接识别这个挂起消息，将其转换为引擎标准的 `REQUIRE_ACTION`（或 `TOOL_CALL`）抛出给物理层。

### 2.3 防腐隔离 (`agent-middleware` 的沙箱执行)
挂起后的 Payload 由 SpringAI Ascend 总线接管，送入 `agent-middleware` 中的 `SkillSandbox` 进行物理测算。
单进程内由 `LocalSandboxExecutorImpl` 的隔离线程池承载，微服务架构下则直接对接到 AgentBus 远端。

## 3. 运行流转时序 (State Machine)
1. **[Bind]** 解析 YAML -> 创建原生 `SchemaOnlyTool` 塞入 AgentScope。
2. **[Prompt]** LLM 接收到原生 Schema。
3. **[Action]** LLM 生成工具调用参数。
4. **[Native-Yield]** `SchemaOnlyTool` 触发原生的 `ToolSuspendException`。
5. **[Suspend]** AgentScope 内部机制捕获异常，输出挂起的 `Message`。
6. **[Adapter]** StreamAdapter 将其翻译为引擎 `REQUIRE_ACTION` 抛给 Dispatcher。
7. **[Sandbox]** Sandbox 在隔离线程池中执行。
8. **[Resume]** 包装结果再次投递给 AgentScope 恢复执行。

## 4. 优势总结
1. **零侵入**：不需要用 ByteBuddy 写字节码，也不需要强行 Wrapper 拦截流，完全复用生态。
2. **MCP 兼容**：此设计直接契合 AgentScope 内部的 `McpTool` 架构。未来演进可平滑切入真正的 MCP 协议。
