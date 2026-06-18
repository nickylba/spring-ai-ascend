# Agent SDK 设计方案

## 1. 模块定位

`agent-sdk` 是一个面向客户侧配置的轻量装配 SDK。当前只支持 OpenJiuwen，职责收敛为：

1. 读取 `ascend-agent/v1` YAML。
2. 解析为内部 `AgentSpec`。
3. 按调用方选择，显式装配为 OpenJiuwen `ReActAgent` 或 `DeepAgent`。
4. 将 YAML 声明的 Java / HTTP tool 转为 OpenJiuwen `Tool`。
5. 将本地 filesystem skill 目录注册到 OpenJiuwen agent。

依赖边界：

| 依赖 | 版本 / 作用 |
|---|---|
| `com.openjiuwen:agent-core-java` | `0.1.12-jdk17`（JDK17 变体），提供 `ReActAgent`、`DeepAgent`、`DeepAgentConfig`、`HarnessFactory`、`Tool`、`ToolCard`、`LocalFunction`、`SkillUseRail` 等 OpenJiuwen API。 |
| `snakeyaml` | 读取 YAML。 |
| `jackson-databind` | 处理参数结构。 |

JDK 要求：SDK 继承 parent 的 `java.version=21`，以 JDK 21 编译；消费方需 JDK 21+。`agent-core-java` 取 JDK17 变体是为了兼容性，SDK 编译目标仍为 21。

## 2. 对外 API

SDK 只暴露两个 YAML 转 agent 的显式入口：

```java
public final class AgentFactory {
    public static ReActAgent toReactAgent(Path yamlPath);

    public static DeepAgent toDeepAgent(Path yamlPath);

    public static AgentFactoryBuilder builder();
}
```

Builder 保留扩展点：

```java
ReActAgent reactAgent = AgentFactory.builder()
    .toolResolver(customResolver)
    .toReactAgent(Path.of("openjiuwen/agent.yaml"));

DeepAgent deepAgent = AgentFactory.builder()
    .toolResolver(customResolver)
    .toDeepAgent(Path.of("openjiuwen/deepagent.yaml"));
```

调用方需要哪一种 agent，就显式调用哪一个方法；SDK 不提供返回 `Object` 的泛化入口。

## 3. 转换流程

ReActAgent：

```text
agent.yaml
  -> AgentYamlLoader
  -> AgentSpec
  -> AgentFactory.toReactAgent(...)
  -> OpenJiuwenReactAgentBuilder
  -> ReActAgent
```

DeepAgent：

```text
deepagent.yaml
  -> AgentYamlLoader
  -> AgentSpec
  -> AgentFactory.toDeepAgent(...)
  -> OpenJiuwenDeepAgentBuilder
  -> DeepAgent
```

## 4. YAML 设计

YAML 使用 `ascend-agent/v1`。它是客户侧 agent 装配格式，不是完整工作流 DSL。

| YAML 字段 | AgentSpec 字段 | 说明 |
|---|---|---|
| `schema` | `schema` | 当前为 `ascend-agent/v1`。 |
| `name` | `name` | 稳定标识符；映射为 OpenJiuwen `AgentCard.id`。生产环境建议全局唯一，因为 ReAct 路径会用它参与 OpenJiuwen 全局资源注册，见 §13.6。 |
| `displayName` | `displayName` | 映射为 OpenJiuwen `AgentCard.name`；为空时默认取 `name`。 |
| `description` | `description` | 映射为 OpenJiuwen `AgentCard.description`。 |
| `framework.type` | `frameworkType` | 当前只支持 `openjiuwen`。 |
| `framework.agent` | `agentType` | 当前支持 `react` 和 `deepagent`。 |
| `framework.options` | `frameworkOptions` | OpenJiuwen 特有选项，如 `maxIterations`、`sysOperationId`。 |
| `model` | `modelSpec` | provider、name、baseUrl、apiKey、sslVerify、headers。 |
| `prompt` | `promptSpec` | system prompt。支持 `agentMd`（外链 md 文件）和 `system`（内联字符串）并存，按 `agentMd → system` 顺序用空行拼接。 |
| `skills.sources` | `skillSources` | 本地 filesystem skill 目录。 |
| `tools` | `toolSpecs` | 工具声明列表。 |

环境变量解析规则：

1. YAML 中的 `${ENV_NAME}` 由 `AgentYamlEnvironmentResolver` 解析。
2. 环境变量不存在时抛出 `ValidationException`。
3. 示例 YAML 可以显式写 `apiKey` 便于真实调用；生产环境按客户自身安全规范管理密钥。

## 5. ReActAgent 装配

示例：

```yaml
schema: ascend-agent/v1
name: sdk-openjiuwen-example-agent
displayName: SDK OpenJiuwen Example Agent
description: Demonstrates YAML to OpenJiuwen ReActAgent.

framework:
  type: openjiuwen
  agent: react
  options:
    maxIterations: 6
    sysOperationId: sdk-openjiuwen-example-agent

model:
  provider: OpenAI
  name: deepseek-chat
  baseUrl: https://api.deepseek.com
  apiKey: ${DEEPSEEK_API_KEY}
  sslVerify: true

prompt:
  system: |
    你是一个订单助手。

skills:
  sources:
    - ../skills/order-analysis
    - ../skills/report-writing

tools:
  - name: queryOrder
    description: 查询本地示例订单状态。
    inputSchema:
      type: object
      properties:
        orderId:
          type: string
      required:
        - orderId
    ref:
      type: file
      class: QueryOrderTool
      method: query

rails:
  - name: orderAudit
    type: class
    class: com.example.agent.OrderAuditRail
    priority: 100
    options:
      redactFields:
        - apiKey
  - name: afterToolProof
    type: function
    event: afterToolCall
    class: com.example.agent.OrderRailHooks
    method: afterToolCall
```

落点：

| YAML 字段 | OpenJiuwen 落点 |
|---|---|
| `name`、`displayName`、`description` | `AgentCard`。 |
| `prompt.agentMd` + `prompt.system` | `ReActAgentConfig.configurePromptTemplate(...)`；两者按 `agentMd → system` 顺序拼接为单条 system message。可只填其一，也可并存。 |
| `model.*` | `ReActAgentConfig.configureModelClient(...)`。 |
| `framework.options.maxIterations` | `ReActAgentConfig.configureMaxIterations(...)`。 |
| `framework.options.sysOperationId` | `ReActAgentConfig.sysOperationId`；默认使用 `name`。 |
| `tools` | 转成 `Tool`，写入 `AbilityManager`，并通过 `Runner.resourceMgr().addTool(tool, agentId)` 注册。 |
| `rails` | P1 目标字段：实例化为 `AgentRail` 后调用 `BaseAgent.registerRail(...)`；当前代码待实现。 |
| `skills.sources` | 调用 `ReActAgent.registerSkill(skillDirectory)`。 |

## 6. DeepAgent 装配

示例：

```yaml
schema: ascend-agent/v1
name: sdk-openjiuwen-deepagent-example-agent
displayName: SDK OpenJiuwen DeepAgent Example Agent
description: Demonstrates YAML to OpenJiuwen DeepAgent.

framework:
  type: openjiuwen
  agent: deepagent
  options:
    maxIterations: 8

model:
  provider: OpenAI
  name: deepseek-chat
  baseUrl: https://api.deepseek.com
  apiKey: ${DEEPSEEK_API_KEY}
  sslVerify: true

prompt:
  system: |
    你是一个订单分析 DeepAgent。

skills:
  sources:
    - ../skills/order-analysis
    - ../skills/report-writing

tools:
  - name: calcDiscount
    description: 计算示例折扣。
    inputSchema:
      type: object
      properties:
        amount:
          type: number
      required:
        - amount
    ref:
      type: file
      class: CalcDiscountTool
      method: calculate

rails:
  - name: planningAudit
    type: class
    class: com.example.agent.PlanningAuditRail
    priority: 100
    options:
      recordToolCalls: true
  - name: beforeModelGuard
    type: function
    event: beforeModelCall
    class: com.example.agent.DeepAgentRailHooks
    method: beforeModelCall
```

落点：

| YAML 字段 | OpenJiuwen 0.1.12-jdk17 落点 |
|---|---|
| `name`、`displayName`、`description` | `AgentCard`。 |
| `prompt.agentMd` + `prompt.system` | `DeepAgentConfig.systemPrompt`；两者按 `agentMd → system` 顺序拼接。可只填其一，也可并存。 |
| `framework.options.maxIterations` | `DeepAgentConfig.maxIterations`。 |
| `model.name` | `DeepAgentConfig.model` map 中的 `model`。 |
| `model.provider`、`apiKey`、`baseUrl`、`sslVerify`、`headers` | `DeepAgentConfig.backend` map。 |
| `tools` | 转成 OpenJiuwen `Tool`，放入 `DeepAgentConfig.tools`。 |
| `rails` | P1 目标字段：实例化为 `AgentRail` / `DeepAgentRail` 后写入 `DeepAgentConfig.rails`；当前代码待实现。 |
| `skills.sources` | 归并为 skill 根目录，写入 `DeepAgentConfig.skillDirectories`；OpenJiuwen `SkillUseRail` 通过 `skillsRoot/skillName/SKILL.md` 读取。 |

构造路径：

```text
AgentSpec
  -> DeepAgentConfig
  -> Workspace
  -> HarnessFactory.createDeepAgent(card, config, workspace)
  -> DeepAgent
```

当前 SDK 显式构造 `Workspace.rootPath(".")` / `language("cn")` 后传给 `HarnessFactory.createDeepAgent(...)`。这是当前真实构造路径，但也是待解耦项：后续应让 `framework.options.workspacePath` / `language` 进入同一套解析路径，避免 `DeepAgentConfig` 与 `Workspace` 出现两套默认值，见 §13.7。

## 7. Tool、Rail 和 Skill

### 7.1 Tool

公开 YAML 的 Java tool 引用不写 source `path`，只写 class 和 method：

```yaml
ref:
  type: file
  class: QueryOrderTool
  method: query
```

当前 resolver：

| resolver | 说明 |
|---|---|
| `JavaFileToolResolver` | 将 classpath 上的静态 Java 方法包装成 OpenJiuwen `LocalFunction`。 |
| `HttpToolResolver` | 将 HTTP tool 声明包装为 SDK 的 HTTP execution handle。 |
| custom `ToolResolver` | 通过 `AgentFactory.builder().toolResolver(...)` 注入。 |

tool ref 支持两种 YAML 形式：

- **对象形式**：`ref: { type: <scheme>, ...属性 }`
- **字符串简写**：`ref: "<scheme>:<value>"`，由 `AgentYamlParser.shorthandAttributes` 展开为对象形式的标准属性键。当前支持：
  - `file:com.example.Class#method` → `{ type: file, class: com.example.Class, method: method }`
  - `http:https://api.example.com/orders` → `{ type: http, url: https://api.example.com/orders }`
  - 其它 scheme 的简写落到 `{ type: <scheme>, value: <rawValue> }`，需 custom resolver 自行解释。

`file` ref 属性：

| 属性 | 含义 | 取值 | 默认 |
|---|---|---|---|
| `class` | 全限定类名 | 非空字符串 | — |
| `method` | 静态方法名，签名须为 `Map<String,Object> -> Object` | 非空字符串 | — |
| `path` | **不支持**——`JavaFileToolResolver` 遇到 `path` 属性会抛 `UnsupportedToolRefException` | — | — |

`http` ref 属性：

| 属性 | 含义 | 取值 | 默认 |
|---|---|---|---|
| `url` | 请求 URL | 非空字符串（合法 URI） | — |
| `method` | HTTP 方法 | `GET` / `HEAD` / `DELETE` / `POST` / `PUT` / `PATCH` 等 | `POST` |
| `headers` | 请求头 | `Map<String,String>` | `{}` |
| `timeout` | 请求超时 | 裸数字（秒）/ `30s` / `500ms` / `2m` / ISO-8601 | `30s` |

> `http` ref 的 `inputSchema` 仍由 `tools[].inputSchema` 声明，与 `ref` 内属性正交——`inputSchema` 描述给 LLM 的参数结构，`ref` 描述 SDK 如何执行。

#### 7.1.1 HTTP tool 安全边界（当前行为）

`HttpToolExecutor` 是 customer SDK 暴露给 YAML 的 HTTP 调用面，其当前行为必须显式声明，避免后续实现者各自解释：

| 维度 | 当前行为 | 风险 / 后续方向 |
|---|---|---|
| **目标地址限制** | 无限制——`HttpToolResolver` 只做 URI 语法解析，不强制 `http/https`、绝对 URI、host allowlist 或内网拒访；YAML 可声明 `http://127.0.0.1`、`http://169.254.169.254`（云元数据）、内网 RFC1918 段。 | customer YAML 若来自不可信来源，存在 SSRF 风险；非 HTTP(S) 或相对 URI 会在 JDK `HttpRequest` 构造/执行阶段失败，错误不够前置。后续应加可选 allowlist / 内网拒访 policy，并在 resolver 阶段校验 scheme 与 absolute URI。 |
| **重定向跟随** | 默认 `HttpClient.Redirect.NORMAL`——自动跟随 JDK 默认允许的重定向；SDK 不控制跳数，也不复核重定向后的目标地址。 | 重定向可能将请求引向内网或非预期 host。后续应支持 YAML 配置 `followRedirects` 或固定 `NEVER`，并把最终目标纳入同一套 policy。 |
| **响应体上限** | **无上限**——`BodyHandlers.ofString()` 全量读入内存。 | 大响应可导致 OOM。后续应加默认上限（如 1 MB）并在超限时截断或抛错。 |
| **错误体进入 LLM 上下文** | 非 2xx 响应抛 `ToolExecutionException`，body 截前 500 字符作为异常消息——**该消息会进入 LLM 上下文**（agent 捕获 tool 异常后喂给模型）。 | 错误体可能含敏感信息（栈、内部地址）。当前 500 字符截断是基本保护，但未做脱敏。后续应支持 YAML 配置是否回显错误体。 |
| **请求超时** | 默认 30s，YAML 可覆盖（`timeout` 字段，支持 `30s`/`500ms`/ISO-8601）。 | 已有保护，无即时风险。 |
| **请求方法与 body** | GET/HEAD/DELETE 输入拼 query；其它方法（默认 POST）输入序列化为 JSON body。 | 行为确定，无歧义。 |
| **响应解码** | 若 `content-type` 包含 `json` 且 body 非空，`HttpToolExecutor.decode(...)` 会尝试解析为结构化对象；非 JSON 直接返回原文；声明为 JSON 但解析失败时也退回原文，不抛错。 | 该行为会影响 tool 结果进入 LLM 上下文的形态。后续若引入 schema validation 或响应截断，需要同时定义结构化对象与原文回退的处理规则。 |
| **TLS 校验** | 由 JDK `HttpClient` 默认行为决定，**不随 `model.sslVerify` 联动**——HTTP tool 的 SSL 校验独立于 LLM client。 | 文档需明确这一点；后续可加 `ref.sslVerify` 字段。 |

> 当前 SDK 对 HTTP tool 采取"开放调用 + 基础超时"策略，**未做 SSRF 防护、未做响应体限流、未做错误体脱敏**。作为 customer SDK，这三项是已知的安全缺口，应在后续版本通过 YAML policy 字段或 SDK 默认 policy 补齐。在补齐前，文档需警示：**YAML 中的 `http:` tool ref 等同于声明"该 agent 可向任意 URL 发请求"，仅应在可信 YAML 来源场景使用。**

### 7.2 Rail

Rail 是 agent 生命周期扩展点，权限高于普通 tool：它能在模型调用、工具调用、异常和 invoke 生命周期前后执行逻辑。P1 目标是像注册 tool 一样支持 classpath rail 注入，同时提供受限的函数绑定形式。

当前代码尚未实现 `rails[]` 解析和注入；代码修改点见 §12.5 #17。设计上固定两种 YAML 形态：

#### 7.2.1 class rail

```yaml
rails:
  - name: orderAudit
    type: class
    class: com.example.agent.OrderAuditRail
    priority: 100
    options:
      redactFields:
        - apiKey
```

字段约束：

| 字段 | 含义 | 约束 |
|---|---|---|
| `name` | rail 声明名 | 非空，用于错误定位和日志。 |
| `type` | rail 声明类型 | `class`；未填时可默认按 `class` 处理。 |
| `class` | rail 实现类 | 必须在 classpath 上；ReAct 路径要求实现 `AgentRail`，DeepAgent 路径允许 `AgentRail` / `DeepAgentRail`。 |
| `priority` | rail 优先级 | 可选；未填使用实现类默认值。 |
| `options` | 初始化参数 | `Map<String,Object>`；建议实现类提供受控 factory 或 `init` 读取，不做任意对象图反序列化。 |
| `method` | 不适用 | `type: class` 不指定绑定方法；rail 类通过重写 `beforeModelCall` / `afterToolCall` 等生命周期方法完成绑定。 |

落点：

| Agent | OpenJiuwen 落点 |
|---|---|
| ReAct | agent 构造并 `configure(...)` 后调用 `BaseAgent.registerRail(...)`。 |
| DeepAgent | 写入 `DeepAgentConfig.rails`，交给 `HarnessFactory.createDeepAgent(...)`。 |

#### 7.2.2 FunctionRailAdapter

函数绑定是 class rail 的次选形式，用于轻量 hook，不允许任意生命周期方法名：

```yaml
rails:
  - name: afterToolProof
    type: function
    event: afterToolCall
    class: com.example.agent.OrderRailHooks
    method: afterToolCall
```

字段约束：

| 字段 | 含义 | 约束 |
|---|---|---|
| `type` | rail 声明类型 | 固定 `function`。 |
| `event` | 绑定事件 | 初始白名单固定为 `beforeModelCall` / `afterModelCall` / `beforeToolCall` / `afterToolCall`；是否扩展到其它事件由 SDK 显式维护。 |
| `class` | 静态 hook 类 | 必须在 classpath 上。 |
| `method` | 静态 hook 方法 | 必填；方法签名固定为 `AgentCallbackContext -> void` 或 `Map<String,Object> -> Map<String,Object>`。前者用于审计、日志、计数等直接操作 callback context 的 hook；后者用于对可序列化上下文做轻量改写并返回更新后的 map，例如追加审计字段或 prompt 片段。 |

`FunctionRailAdapter` 由 SDK 负责把上述函数包装成 OpenJiuwen rail：ReAct 生成 `AgentRail` 后 `registerRail(...)`；DeepAgent 生成 rail 后写入 `DeepAgentConfig.rails`。安全策略、allowlist 包和更细权限控制后续再补，不作为是否支持 YAML class/function 注入的前置条件。

初始白名单不开放 `beforeInvoke` / `afterInvoke` / `onModelException` / `onToolException`。原因是前两者覆盖整个 agent 调用边界，容易被轻量函数 hook 变成全局控制逻辑；后两者涉及异常对象、重试和错误体处理，需等错误可见性、脱敏和重试策略明确后再设计。

不支持的形态：

- 不直接把任意 `List<Object>` 透传给 `DeepAgentConfig.rails`。
- 不从 YAML 反序列化任意 Java 对象图。
- 不允许函数绑定任意生命周期方法名；只能用 SDK 白名单事件。

### 7.3 Skill

当前只支持本地 filesystem skill：

```yaml
skills:
  sources:
    - ../skills/order-analysis
    - ../skills/report-writing
```

约束：

1. 每个 skill 目录必须包含 `SKILL.md`。
2. 路径相对 YAML 所在目录解析。
3. ReActAgent 调用 `registerSkill(...)`。
4. DeepAgent 写入 skill 根目录到 `DeepAgentConfig.skillDirectories`，由 OpenJiuwen `SkillUseRail` 注册 `list_skill` / `skill_tool` 并读取 `skillsRoot/skillName/SKILL.md`。

## 8. 包结构

```text
agent-sdk/
  pom.xml
  agent-sdk设计方案.md
  src/main/java/com/huawei/ascend/agentsdk/
    factory/
      AgentFactory.java
      AgentFactoryBuilder.java
    adapter/
      OpenJiuwenAgentSpecMapper.java
      OpenJiuwenSkillMapper.java
      OpenJiuwenToolMapper.java
      react/
        OpenJiuwenReactAgentBuilder.java
        OpenJiuwenReactOptions.java
      deepagent/
        OpenJiuwenDeepAgentBuilder.java
        OpenJiuwenDeepAgentOptions.java
    spec/
      AgentSpec.java
      yaml/
      model/
      prompt/
      skill/
      tool/
    support/
```

## 9. Example

示例项目：

```text
examples/agent-sdk-example/
  openjiuwen/
    agent.yaml
    deepagent.yaml
  skills/
    order-analysis/SKILL.md
    report-writing/SKILL.md
  src/main/java/com/huawei/ascend/agentsdk/example/
    OpenJiuwenReactAgentSdkExample.java
    OpenJiuwenDeepAgentSdkExample.java
    OpenJiuwenExampleSupport.java
    tools/
      ReadFileTool.java
      QueryOrderTool.java
      CalcDiscountTool.java
```

示例只展示：

```text
YAML -> ReActAgent
YAML -> DeepAgent
```

示例验证目标：

1. 通过 YAML 构造 `ReActAgent` 和 `DeepAgent`。
2. 使用真实大模型调用默认 DeepSeek OpenAI-compatible endpoint；ReAct 示例调用 `ReActAgent.invoke(...)`，DeepAgent 示例调用 `DeepAgent.run(...)` 驱动真实执行。
3. 自定义 Java tool `queryOrder`、`calcDiscount` 被真实调用，并返回 proof 字段。
4. ReAct 示例通过自定义 Java tool `readFile` 读取本地 `SKILL.md`，证明 skill 文件被真实使用；DeepAgent 示例通过 OpenJiuwen 原生 `skill_tool` 读取 `skills/<skillName>/SKILL.md`。
5. 本地 skill `order-analysis`、`report-writing` 被注入，并要求最终回答输出独有 skill proof 标记。
6. 示例 main 在运行结束时自动校验 tool invocation count、tool proof、skill proof；ReAct 额外校验 `readFile` 调用次数，校验失败则抛出异常。

## 10. 当前结论

1. `agent-sdk` 当前只负责 OpenJiuwen YAML 到 OpenJiuwen agent 实例的客户侧装配。
2. `agent-core-java` 依赖版本为 `0.1.12-jdk17`（JDK17 变体）；SDK 以 JDK 21 编译，消费方需 JDK 21+。
3. 对外 API 固定为 `AgentFactory.toReactAgent(Path)`、`AgentFactory.toDeepAgent(Path)` 和 builder。
4. ReActAgent 与 DeepAgent 使用同一套 YAML 主字段，差异由 `framework.agent` 和对应 OpenJiuwen 落点决定。
5. 公开 YAML 的 Java tool ref 使用 `type + class + method`，不使用 tool `path`。
6. `prompt.agentMd` 与 `prompt.system` 并存已实现，按 `agentMd → system` 顺序拼接；`agentMd` 原样读取不 trim，文件不存在 fail-fast。
7. 已识别的待修改代码点见 §12，按 P0/P1/P2 分级；HTTP tool 安全缺口见 §7.1.1 与 §11.4。

## 11. ReActAgent 与 DeepAgent 配置清单

本节只列 **SDK 当前已实现的参数** 和 **建议后续补充的参数**，不列 OpenJiuwen 全量字段。每个参数标明含义、取值范围、默认值、实现状态。

状态标记：✅ 已实现 / 📌 建议补充 / ⚠️ 已构造但被硬编码。

### 11.1 ReActAgent 配置

#### 11.1.1 顶层字段

| YAML 路径 | 含义 | 取值范围 | 默认值 | 状态 |
|---|---|---|---|---|
| `schema` | 配置格式版本 | 固定 `ascend-agent/v1` | — | ✅ |
| `name` | agent 稳定标识符，映射 `AgentCard.id` | 任意非空字符串 | — | ✅ |
| `displayName` | 展示名，映射 `AgentCard.name` | 字符串 | 取 `name` | ✅ |
| `description` | agent 描述 | 任意非空字符串 | — | ✅ |
| `framework.type` | 框架类型 | `openjiuwen` | — | ✅ |
| `framework.agent` | agent 类型 | `react` | — | ✅ |

#### 11.1.2 framework.options（已实现）

| YAML 路径 | 含义 | 取值范围 | 默认值 | 状态 |
|---|---|---|---|---|
| `framework.options.maxIterations` | ReAct 循环最大轮数 | 正整数 | `5` | ✅ |
| `framework.options.sysOperationId` | 系统操作 ID，用于 skill 注册隔离 | 任意字符串 | 取 `name` | ✅ |

#### 11.1.3 model（已实现）

| YAML 路径 | 含义 | 取值范围 | 默认值 | 状态 |
|---|---|---|---|---|
| `model.provider` | 模型 provider | `openai` / `openai-compatible` / 自定义 | `openai-compatible` | ✅ |
| `model.name` | 模型名 | 字符串 | — | ✅ |
| `model.baseUrl` | 推理 endpoint | URL | — | ✅ |
| `model.apiKey` | API Key，支持 `${ENV}` | 字符串 | — | ✅ |
| `model.sslVerify` | 是否校验 SSL | `true` / `false` | `true` | ✅ |
| `model.headers` | 附加请求头 | `Map<String,String>` | `{}` | ✅ |

#### 11.1.4 prompt / skills / tools（已实现）

| YAML 路径 | 含义 | 取值范围 | 默认值 | 状态 |
|---|---|---|---|---|
| `prompt.agentMd` | 外链 agent 身份 md 文件，相对 YAML 目录解析 | 路径字符串（文件必须存在） | — | ✅ |
| `prompt.system` | 内联 system prompt | 字符串 | `""` | ✅ |
| `skills.sources` | 本地 skill 目录列表 | 路径列表（相对 YAML 目录） | `[]` | ✅ |
| `tools[].name` | tool 名称，全局唯一 | 非空字符串 | — | ✅ |
| `tools[].description` | tool 描述 | 非空字符串 | — | ✅ |
| `tools[].inputSchema` | 输入 JSON Schema | JSON Schema object | `{}` | ✅ |
| `tools[].ref` | tool 引用，支持 `file:Class#method` 简写或 `{type, class, method}` / `{type, url, ...}` 对象 | `file` / `http` | — | ✅ |

> `prompt.agentMd` 与 `prompt.system` 可只填其一，也可并存；并存时按 `agentMd → system` 顺序用空行拼接为最终 system prompt。两者都留空则 system prompt 为空串。
>
> `agentMd` 读取规则：
> 1. 路径相对 YAML 所在目录解析（复用 `resolvePath`）。
> 2. 文件必须存在；不存在或读取失败抛 `ValidationException`（fail-fast）。
> 3. 文件内容**原样读取，不做 trim**——保留 md 文件自身的首尾空行与格式，避免破坏 markdown 语义。拼接时仅用 `\n\n` 分隔两段，不额外裁剪。

#### 11.1.5 建议补充（ReActAgent）

必要性标记：**MUST** = 必要能力，应进入 P1 必要范围 / **DEFER** = 后续专题再补 / **NO** = 不进 YAML，走代码扩展点或待确认。这里的 MUST 不等于 P0；P0 仍只覆盖低风险硬编码修复。

| YAML 路径 | 含义 | 必要性 | 结论 |
|---|---|---|---|
| `model.request.temperature` / `topP` / `maxTokens` / `stop` / `seed` | 模型生成参数 | MUST | 属于 model 核心能力；tool-calling agent 需要可控采样与 token 上限。 |
| `model.request.extra` 或 `model.request.*` | provider 特有透传字段 | DEFER | 需要设计命名空间和冲突规则；首版先支持常用强类型字段。 |
| `model.timeout` / `model.maxRetries` | LLM 调用超时与重试 | MUST | 属于 model 可靠性最小能力；不应只依赖 OpenJiuwen 默认值。 |
| `model.sslCert` | 自签 CA / 私有证书 | DEFER | 企业场景有价值，但当前需求未确认；先不进首版 YAML。 |
| `framework.options.promptMode` | OpenJiuwen prompt 模板模式 | NO | 不是 model/tool/rail 的必要能力；除非出现明确场景，否则不进首版。 |
| `framework.options.contextEngine.*` | ReAct 上下文窗口控制 | DEFER | 有价值但不是最小 SDK 契约；建议后续作为 model/context 专题设计。 |
| `framework.options.memScopeId` | ReAct 长期记忆 scope | DEFER | 涉及多 agent 记忆隔离策略，后续再做。 |
| `AgentFactoryBuilder.rail(...)` / `railResolver(...)` | 代码级自定义 rail 注入 | MUST | ReAct 继承 `BaseAgent.registerRail(AgentRail)` 能力；SDK 应支持 classpath rail 注入。 |
| `rails[]` | 自定义 rail 声明 | MUST | YAML class 注入后由 SDK 实例化为 `AgentRail` 并在构造完成后调用 `agent.registerRail(...)`，不是写入 `ReActAgentConfig` 字段。 |

> ReAct 的 rail 落点不同于 DeepAgent：ReAct 侧没有 `ReActAgentConfig.rails` 字段，应通过 `BaseAgent.registerRail(AgentRail)` 注册；DeepAgent 侧可写入 `DeepAgentConfig.rails`。两条路径应复用同一套 YAML `rails[]` 解析和 class/function adapter，但 adapter 最终落点不同。

### 11.2 DeepAgent 配置

#### 11.2.1 顶层字段

| YAML 路径 | 含义 | 取值范围 | 默认值 | 状态 |
|---|---|---|---|---|
| `schema` | 配置格式版本 | 固定 `ascend-agent/v1` | — | ✅ |
| `name` / `displayName` / `description` | agent 元信息 | 非空字符串 | — | ✅ |
| `framework.type` | 框架类型 | `openjiuwen` | — | ✅ |
| `framework.agent` | agent 类型 | `deepagent` | — | ✅ |

#### 11.2.2 framework.options（已实现 / 硬编码）

| YAML 路径 | 含义 | 取值范围 | 默认值 | 状态 |
|---|---|---|---|---|
| `framework.options.maxIterations` | DeepAgent 最大迭代数 | 正整数 | `15` | ✅ |
| `framework.options.skillMode` | skill 注入模式 | `"all"` / `"none"` | ⚠️ 硬编码 `"all"` | ⚠️ |
| `framework.options.language` | 语言，影响 prompt 与 general-purpose agent 描述 | `"cn"` / `"en"` 等 | ⚠️ 硬编码 `"cn"` | ⚠️ |
| `framework.options.workspacePath` | 工作区根目录 | 任意路径字符串 | ⚠️ 硬编码 `"."`（相对进程 CWD） | ⚠️ |

> 上述 ⚠️ 字段解硬编码时的默认值与非法值行为约定（供后续实现遵循）：
>
> - **`skillMode`**：默认 `"all"`。`SkillUseRail.normalizeMode` 会小写化并 trim；非 `"all"` / `"none"` 的值当前不抛错，由 OpenJiuwen 按"非 none 即启用"处理——SDK 侧**不额外校验枚举**，原样透传，让 OpenJiuwen 升级新枚举值时 SDK 不用改。空串/null 视为 `"all"`（`normalizeMode` 兜底）。
> - **`language`**：默认 `"cn"`。空串/null 走 `"cn"`（`HarnessFactory.resolveLanguage` 兜底）。SDK 不校验合法语言码——OpenJiuwen 内部按 `cn`/`en` 选 prompt 片段，未覆盖语言退化为默认描述，不抛错。
> - **`workspacePath`**：默认 `"./"`（OpenJiuwen `Workspace` 默认），**不是** `"."`——当前 SDK 硬编码 `"."` 是历史遗留，解硬编码时应改为让 `DeepAgentConfig.workspacePath` 接管，由 `HarnessFactory.resolveWorkspace` 解析。SDK 不校验路径存在性（OpenJiuwen 在 `SysOperation` 装配时自行处理），但建议 YAML 文档提示客户用绝对路径避免 CWD 漂移。

#### 11.2.3 model / backend（已实现）

DeepAgent 走 `model` map + `backend` map。

| YAML 路径 | 含义 | 取值范围 | 默认值 | 状态 |
|---|---|---|---|---|
| `model.provider` | provider，写入 `backend.provider` | 字符串 | — | ✅ |
| `model.name` | 模型名，写入 `model.model` | 字符串 | — | ✅ |
| `model.baseUrl` | endpoint，写入 `backend.baseUrl` | URL | — | ✅ |
| `model.apiKey` | API Key | 字符串 | — | ✅ |
| `model.sslVerify` | SSL 校验，写入 `backend.verifySsl` | `true` / `false` | — | ✅ |
| `model.headers` | 附加请求头，写入 `backend.headers` | `Map<String,String>` | `{}` | ✅ |

#### 11.2.4 prompt / skills / tools（已实现）

| YAML 路径 | 含义 | 取值范围 | 默认值 | 状态 |
|---|---|---|---|---|
| `prompt.agentMd` | 外链 agent 身份 md 文件，相对 YAML 目录解析 | 路径字符串（文件必须存在） | — | ✅ |
| `prompt.system` | 内联 system prompt，写入 `systemPrompt` | 字符串 | `""` | ✅ |
| `skills.sources` | skill 目录，归并为 root 写入 `skillDirectories` | 路径列表 | `[]` | ✅ |
| `tools[]` | tool 声明，写入 `tools` | 同 ReAct | `[]` | ✅ |

> `prompt.agentMd` 与 `prompt.system` 并存规则同 ReAct：按 `agentMd → system` 顺序用空行拼接。

#### 11.2.5 建议补充（DeepAgent）

本节按“model / tool / rail / MCP 是必要能力，其它默认不进首版”重新分级。这里的“MCP”按 tool-provider 能力处理；rail 作为一等扩展点，应支持 YAML class 注入和受限函数绑定，安全治理后续再补。分级含义同 §11.1.5：MUST 表示进入 P1 必要范围，不等于 P0。

| YAML 路径 | 含义 | 必要性 | 结论 |
|---|---|---|---|
| `model.request.temperature` / `topP` / `maxTokens` / `stop` / `seed` | 模型生成参数，写入 `model` map | MUST | 属于 model 核心能力；与 ReAct 保持同一 YAML 语义，adapter 负责字段名映射。 |
| `model.request.extra` 或 `model.request.*` | provider 特有透传字段 | DEFER | 需要设计命名空间和冲突规则；首版先支持常用强类型字段。 |
| `backend.timeout` / `backend.maxRetries` | LLM 调用超时与重试 | MUST | 属于 model 可靠性最小能力；可统一为顶层 `model.timeout` / `model.maxRetries`，adapter 映射到 DeepAgent backend。 |
| `backend.sslCert` / `model.sslCert` | 自签 CA / 私有证书 | DEFER | 企业场景有价值，但当前需求未确认；先不进首版 YAML。 |
| `framework.options.skillMode` | skill rail 模式 | MUST | 当前已硬编码，且直接控制 `SkillUseRail`；应作为 rail 能力暴露。 |
| `framework.options.enableTaskLoop` | task completion rail 开关 | MUST | rail 核心能力；DeepAgent 是否启用任务完成判断不应只能靠代码。 |
| `framework.options.enableTaskPlanning` | task planning rail 开关 | MUST | rail 核心能力；计划/进度管理是 DeepAgent 关键行为。 |
| `framework.options.completionTimeout` | task loop 整体超时 | MUST | task completion rail 的最小安全边界；启用 task loop 时必须可控。 |
| `framework.options.taskPlanning.listToolCallInterval` | task planning rail 工具调用间隔 | DEFER | 属于 rail 调参，不是首版必须；先随 OpenJiuwen 默认。 |
| `framework.options.taskPlanning.enableProgressRepeat` | task planning rail 重复进度提示 | DEFER | 属于 rail 调参，不是首版必须；先随 OpenJiuwen 默认。 |
| `framework.options.permissions` | permission/security rail 策略输入 | DEFER | 安全策略需要单独 schema；不要先用宽泛 `Map<String,Object>` 承诺公共契约。 |
| `AgentFactoryBuilder.rail(...)` / `railResolver(...)` | 代码级自定义 rail 注入 | MUST | rail 是必要扩展点；应支持客户像注册 tool 一样把 classpath rail 接入生命周期。 |
| `rails[]` | 自定义 rail 声明 | MUST | 直接支持 YAML class 注入，写入 `DeepAgentConfig.rails`；对 class 类型、构造方式、函数签名和事件白名单做约束，安全治理后续再补。 |
| `framework.options.workspacePath` | 工作区根目录 | MUST | 虽不是 model/tool/rail，但当前硬编码 `"."` 会导致部署 CWD 漂移；属于必须修正的运行边界。 |
| `framework.options.language` | DeepAgent 语言 | MUST | 当前硬编码 `"cn"` 会污染非中文场景；属于必须修正的行为边界。 |
| `framework.options.defaultMode` | 启动模式 | DEFER | 不属于首版必要能力；等 task planning / loop 落地后再判断。 |
| `framework.options.restrictToWorkDir` | 工作区限制开关 | DEFER | 安全敏感但需要与 permissions/workspace policy 一起设计；不应孤立暴露。 |
| `mcps[]` | MCP server 列表 | MUST | MCP 是 tool 生态的必要组成，属于 tool-provider 能力；应进入 P1 必要范围。 |
| `prompt.extraSections` | 追加 prompt 段落 | NO | 不属于 model/tool/rail 必要能力；现有 `prompt.agentMd + system` 已覆盖首版 prompt 需求。 |
| `framework.options.enableGeneralPurposeAgent` | 通用子 agent | NO | 属于 subagent 高阶能力；不进首版 YAML，后续走代码扩展或单独设计。 |

`mcps[]` 子字段：`serverId`（默认随机 UUID）/ `serverName` / `serverPath`（路径或 URL）/ `clientType`（`sse` / `stdio`，默认 `sse`）/ `params`（`Map`）/ `authHeaders` / `authQueryParams`。

`rails[]` 采用 classpath 注入，类似 Java tool 但更严格：

| 字段 | 含义 | 约束 |
|---|---|---|
| `name` | rail 声明名 | 非空，便于错误定位 |
| `class` | rail 实现类 | 必须在 classpath 上，且实现 `AgentRail` / `DeepAgentRail`，或由 SDK 提供 adapter 包装 |
| `priority` | rail 优先级 | 可选；未填使用实现类默认值 |
| `options` | 构造/初始化参数 | `Map<String,Object>`；优先要求实现类提供受控 factory，而不是任意反射构造 |
| `method` | 绑定方法 | `type: class` 不填；`type: function` 必填，并与 `event` 一起交给 `FunctionRailAdapter` 包装 |

两条 agent 路径的落点：

| Agent | rail 类型约束 | OpenJiuwen 落点 |
|---|---|---|
| ReAct | `AgentRail` 或 `FunctionRailAdapter` 生成的 `AgentRail` | agent 构造并 `configure(...)` 后调用 `BaseAgent.registerRail(...)` |
| DeepAgent | `AgentRail` / `DeepAgentRail` 或 `FunctionRailAdapter` 生成的 rail | `DeepAgentConfig.rails`，交给 `HarnessFactory.createDeepAgent(...)` |

不建议把 `rails[]` 设计成“随便绑定某个函数到某个生命周期事件”。rail 不是普通 tool：它能拦截 `beforeInvoke` / `beforeModelCall` / `beforeToolCall` / exception 等全链路事件，能改变 prompt、工具调用、终止条件和安全策略。更稳妥的抽象是：

1. **首选**：客户实现 `AgentRail` / `DeepAgentRail`，SDK 从 classpath 实例化并注入。
2. **次选但需要支持**：SDK 提供 `FunctionRailAdapter`，允许 YAML 绑定 `beforeModelCall` / `afterModelCall` / `beforeToolCall` / `afterToolCall` 四个初始白名单事件到静态 Java 方法；方法签名固定为 `AgentCallbackContext -> void` 或 `Map<String,Object> -> Map<String,Object>`。前者用于审计、日志、计数等直接操作 callback context 的 hook；后者用于对可序列化上下文做轻量改写并返回更新后的 map。函数绑定只开放有限事件，不允许任意生命周期方法名；`beforeInvoke` / `afterInvoke` / exception 类事件后续单独评估。
3. **默认**：公开稳定 rail 能力的专用 YAML 字段，如 `enableTaskLoop`、`enableTaskPlanning`；`permissions` 后续以受控 schema 表达。MCP 作为 tool-provider 单独设计，不混入 rail schema。

rail 暴露原则：

- **应该暴露核心 rail 能力的稳定开关和参数**，例如 `enableTaskLoop`、`enableTaskPlanning`、`skillMode`、task planning 参数；`permissions` 在安全 schema 明确后再暴露。这些是 DeepAgent 行为的一等能力，不应只能靠代码改。
- **应支持代码级自定义 rail 注入**。和 tool 一样，客户可以把实现类放在 classpath 上；SDK 应提供 `AgentFactoryBuilder.rail(...)` / `railResolver(...)` 显式接入。
- **应支持 YAML rail 注入**。`rails[]` class 注入与 `FunctionRailAdapter` 都需要做；必须有类型校验、签名校验、允许事件白名单和清晰错误信息。
- **不应开放任意 `rails[]` 对象列表透传**。`DeepAgentConfig.getRails()` 接受 `List<Object>`，但 SDK YAML 不应允许直接声明任意 Java 对象图；应只支持明确的 class 注入和受限函数绑定两种形态。

> 默认不暴露给 YAML：任意 Java 对象图透传、`subagents`（字段过多，建议走代码扩展点）、`sysOperation` / `permissionHost` / `factoryKwargs`（Java 对象或无消费点）。其中 `permissionHost` 可在确认安全策略需求后用受控 `permissions` schema 间接表达，而不是直接 YAML 注入 Java 对象。

### 11.3 已构造但被硬编码的修复项

| 位置 | 当前硬编码 | 应改为从 YAML 读取 | 默认值 / 非法值约定 |
|---|---|---|---|
| `OpenJiuwenReactAgentBuilder` 调 `configureModelClient` 的 `sslCert` 参 | `null` | 暂不纳入下一轮 P0；仅在确认企业自签 CA / 私有证书需求后，再设计 `model.sslCert` 或 builder policy | 默认 `null`；空串视为 `null` |
| `OpenJiuwenReactAgentBuilder` 未构造 `ModelRequestConfig` | — | `model.request.*` | 字段全部可选，未填走 OpenJiuwen 默认（temp=0.95 等） |
| `OpenJiuwenDeepAgentBuilder` 的 `Workspace` | `rootPath(".")` / `language("cn")` | `framework.options.workspacePath` / `language`，或填入 `DeepAgentConfig` 让 `HarnessFactory` 自行解析 | `workspacePath` 默认 `"./"`（对齐 OpenJiuwen `Workspace` 默认，非 `"."`）；`language` 默认 `"cn"`，空串兜底 `"cn"`，不校验合法码 |
| `OpenJiuwenDeepAgentBuilder` 的 `skillMode` | `"all"` | `framework.options.skillMode` | 默认 `"all"`；空串/null 兜底 `"all"`；非 `all`/`none` 原样透传，由 `SkillUseRail` 解释 |

### 11.4 HTTP tool 安全缺口（待补）

| 缺口 | 当前行为 | 后续方向 |
|---|---|---|
| SSRF 防护 | 无，YAML 可声明任意 URL | 加可选 allowlist / 内网拒访 policy |
| 重定向跟随 | 默认 `NORMAL`；SDK 不控制跳数，也不复核重定向后的目标地址 | 支持 YAML `followRedirects` 或固定 `NEVER`，并把最终目标纳入同一套 policy |
| 响应体上限 | 无上限，全量读入内存 | 加默认上限（如 1 MB），超限截断或抛错 |
| 错误体脱敏 | 非 2xx body 截前 500 字符进异常消息（会进 LLM 上下文） | 支持 YAML 配置是否回显错误体 |

> 详见 §7.1.1。在补齐前，HTTP tool 仅应在可信 YAML 来源场景使用。

## 12. 待修改代码清单

本节集中记录当前代码中**已识别、但本轮未动**的修改点。每条标明位置、问题、应改为、默认值约定、优先级。优先级：P0 = 低风险高价值，应优先；P1 = 覆盖主用例；P2 = 长尾。

### 12.1 DeepAgent 硬编码字段解耦（P0）

| # | 位置 | 当前问题 | 应改为 | 默认值 / 约定 |
|---|---|---|---|---|
| 1 | [OpenJiuwenDeepAgentBuilder.java:42](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java:42) `skillMode("all")` | YAML 无法关闭 skill 注入 | 从 `framework.options.skillMode` 读取，写入 `DeepAgentConfig.skillMode` | 默认 `"all"`；空串/null 兜底 `"all"`；非 `all`/`none` 原样透传，由 `SkillUseRail` 解释（SDK 不校验枚举） |
| 2 | [OpenJiuwenDeepAgentBuilder.java:47](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java:47) `rootPath(".")` | 相对进程 CWD 解析，部署时漂移，沙箱边界不稳 | 从 `framework.options.workspacePath` 读取；**优先**填入 `DeepAgentConfig.workspacePath` 让 `HarnessFactory.resolveWorkspace` 统一解析，而非单独构造 `Workspace` | 默认 `"./"`（对齐 OpenJiuwen `Workspace` 默认，纠正当前 `"."` 历史遗留）；SDK 不校验路径存在性 |
| 3 | [OpenJiuwenDeepAgentBuilder.java:48](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java:48) `language("cn")` | 海外场景 general-purpose agent 描述混入中文 | 从 `framework.options.language` 读取，写入 `DeepAgentConfig.language`（与 `Workspace.language` 二选一，由 `HarnessFactory.resolveLanguage` 统一解析） | 默认 `"cn"`；空串/null 兜底 `"cn"`；不校验合法语言码 |

> 这三项是同一处构造（`Workspace.builder()...` + `DeepAgentConfig.builder()...`），建议一次性改完，避免 `Workspace` 与 `DeepAgentConfig` 字段再次错位。

### 12.2 ReActAgent 模型参数补全（P1，`sslCert` 待确认）

| # | 位置 | 当前问题 | 应改为 | 默认值 / 约定 |
|---|---|---|---|---|
| 4 | [OpenJiuwenReactAgentBuilder.java:43](src/main/java/com/huawei/ascend/agentsdk/adapter/react/OpenJiuwenReactAgentBuilder.java:43) `configureModelClient(..., null, headers)` 第 6 参 `sslCert` 传 `null` | 企业自签 CA 无法配置，但当前未确认是否要把证书路径暴露进 YAML | 暂不纳入下一轮 P0；仅在确认企业 CA 需求后再设计 `model.sslCert` 或 builder policy | 默认 `null`；空串视为 `null` |
| 5 | [OpenJiuwenReactAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/react/OpenJiuwenReactAgentBuilder.java) 未构造 `ModelRequestConfig` | temperature/topP/maxTokens/stop/seed 全用 OpenJiuwen 默认（temp=0.95 偏高），且无法透传 `reasoning_effort` 等字段 | 从 `model.request.*` 构造 `ModelRequestConfig`，经 `configureModelClient` 时通过 `ReActAgentConfig.modelConfigObj` 注入；`extraFields` 走 `ModelRequestConfig.extraFields`（`@JsonAnySetter`） | 字段全部可选，未填走 OpenJiuwen 默认 |

### 12.3 HTTP tool 安全加固（P1）

| # | 位置 | 当前问题 | 应改为 | 约定 |
|---|---|---|---|---|
| 6 | [HttpToolExecutor.java:31](src/main/java/com/huawei/ascend/agentsdk/adapter/HttpToolExecutor.java:31) `followRedirects(Redirect.NORMAL)` | 自动跟随 JDK 默认允许的重定向；SDK 不控制跳数，也不复核重定向后的目标地址，可被引向内网 | 支持 YAML `ref.followRedirects`（默认 `false`）或固定 `NEVER`，并复用目标地址 policy 校验最终地址 | 默认改为不跟随——SSRF 防御优先于便利性，需跟随时显式声明 |
| 7 | [HttpToolExecutor.java:42](src/main/java/com/huawei/ascend/agentsdk/adapter/HttpToolExecutor.java:42) `BodyHandlers.ofString()` | 无界读取响应体，大响应可 OOM | 加默认上限（如 1 MB），超限截断或抛 `ToolExecutionException` | 上限值可通过 YAML `ref.maxResponseBytes` 覆盖 |
| 8 | [HttpToolExecutor.java:52](src/main/java/com/huawei/ascend/agentsdk/adapter/HttpToolExecutor.java:52) 非 2xx body 截前 500 字符进异常消息 | 错误体未脱敏直接进 LLM 上下文 | 支持 YAML `ref.exposeErrorBody`（默认 `false`），关闭时异常消息只含状态码与 URL | 默认不回显错误体 |
| 9 | [HttpToolResolver.java](src/main/java/com/huawei/ascend/agentsdk/spec/tool/HttpToolResolver.java) + `HttpToolExecutor` 无目标地址限制 | `HttpToolResolver` 只做 URI 语法解析，不强制 `http/https`、绝对 URI、host allowlist 或内网拒访；YAML 可声明内网/云元数据 URL，SSRF 风险 | 加可选 allowlist / 内网拒访 policy；resolver 阶段校验 scheme 与 absolute URI；至少文档警示 | 可信 YAML 来源才用 HTTP tool，见 §7.1.1 |

### 12.4 DeepAgent 模型参数补全（P1）

| # | 位置 | 当前问题 | 应改为 | 默认值 / 约定 |
|---|---|---|---|---|
| 10 | [OpenJiuwenDeepAgentBuilder.java:67](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java:67) `modelConfig` 只放 `model` 名 | temperature/topP/maxTokens 等无法配置 | `model` map 增加 `temperature`/`top_p`/`max_tokens`/`stop`/`seed` 及透传字段，从 `model.request.*` 读取 | 未填走 OpenJiuwen 默认 |
| 11 | [OpenJiuwenDeepAgentBuilder.java:72](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java:72) `backendConfig` 只放五字段 | timeout/maxRetries 无法配置；`ssl_cert` 是否暴露待确认 | 从统一 YAML `model.timeout` / `model.maxRetries` 映射到 DeepAgent backend 的 `timeout` / `max_retries`；证书类字段仅在确认企业 CA 需求后再设计 | 未填走 OpenJiuwen 默认（timeout=60, retries=3） |

### 12.5 其它建议补充项（P1-P2）

| # | 位置 | 当前问题 | 应改为 | 优先级 |
|---|---|---|---|---|
| 12 | [OpenJiuwenReactAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/react/OpenJiuwenReactAgentBuilder.java) 未注入 `ContextEngineConfig` | 长会话上下文上限不可调 | 后续作为 context 专题设计，不进首版 YAML | P2 |
| 13 | [OpenJiuwenReactAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/react/OpenJiuwenReactAgentBuilder.java) 未注入 `memScopeId` | 多 agent 同进程共享长期记忆 scope | 后续与 memory/scope 隔离策略一起设计，不进首版 YAML | P2 |
| 14 | [OpenJiuwenDeepAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java) 未注入 `restrictToWorkDir` | 沙箱开关不可配，安全敏感 | 后续与 `workspacePath` / `permissions` / security policy 一起设计，不孤立暴露 | P2 |
| 15 | [OpenJiuwenDeepAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java) 未注入 `enableTaskLoop`/`enableTaskPlanning`/`completionTimeout` | task rail 无法开启，核心规划/完成判断能力无法由 YAML 控制 | 从 `framework.options` 读取，默认 `false`；开启时 `HarnessFactory` 自动装对应 rail；首版只做开关和 timeout，task planning 细粒度参数后续再补 | P1 |
| 16 | [OpenJiuwenDeepAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java) 未注入 `mcps` | DeepAgent 无法接 MCP server | 作为 tool-provider 必要能力，从 `mcps[]` 构造 `List<McpServerConfig>`，配套最小 MCP 示例和测试 | P1 |
| 17 | `AgentFactoryBuilder` / [OpenJiuwenReactAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/react/OpenJiuwenReactAgentBuilder.java) / [OpenJiuwenDeepAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java) 未提供自定义 rail 注入 | 客户无法像 tool 一样把 classpath rail 或受限函数 rail 接入 agent 生命周期 | P1 支持代码级 `rail(...)` / `railResolver(...)`、YAML `rails[]` class 注入、`FunctionRailAdapter` 有限事件绑定；ReAct 走 `BaseAgent.registerRail(...)`，DeepAgent 走 `DeepAgentConfig.rails` | P1 |
| 18 | [OpenJiuwenDeepAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java) 未注入 `extraPromptSections` | prompt 无法模块化拼装 | 不进首版 YAML；现有 `prompt.agentMd + system` 已覆盖基础 prompt 需求 | P2/NO |
| 19 | [OpenJiuwenDeepAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java) 未注入 `enableGeneralPurposeAgent` | 通用子 agent 无法开启 | subagent 高阶能力，不进首版 YAML；后续走代码扩展或单独设计 | P2/NO |

### 12.6 代码组织（观察，非阻塞）

[AgentYamlParser.java](src/main/java/com/huawei/ascend/agentsdk/spec/yaml/AgentYamlParser.java) 当前 ~280 行，仍在可控范围。由于 MCP 与 rail 也进入 P1 必要能力，parser 很快会继续增长；建议在落 P1 model/tool/rail/MCP 字段时同步拆分为 `PromptParser` / `ModelParser` / `ToolParser` / `RailParser` / `McpParser` / `SkillParser`，主 parser 只负责字段分发。

## 13. 严格复审补充

本节基于当前工作区源码、`agent-sdk/pom.xml`、`examples/agent-sdk-example`、`architecture/facts/generated/*` 和本机 `com.openjiuwen:agent-core-java:0.1.12-jdk17` jar API 复核。目标是把“当前真实行为”“设计上合理但未实现”“设计上不应继续扩大”的边界写清楚。本文档可刷新；本轮不修改 Java 代码。

### 13.1 模块身份与治理边界

当前 `agent-sdk` 是一个**独立 Maven module 目录**，但不是根 reactor 的一部分：

- 根 `pom.xml` 的 `<modules>` 不包含 `agent-sdk`。
- `architecture/facts/generated/module-build.json` 只包含 `agent-bus`、`agent-runtime`、`agent-service`、`spring-ai-ascend-dependencies`，没有 `build-module/agent-sdk`。
- `architecture/facts/generated/code-symbols.json` / `tests.json` 当前也没有 `com.huawei.ascend.agentsdk` 事实。

因此：

1. `./mvnw clean verify` 或 root reactor verify 不能证明 `agent-sdk` 通过。
2. 对 `agent-sdk` 的事实性结论必须来自 `agent-sdk` 自身源码、测试和独立 Maven 命令，不能引用 generated facts 作为覆盖证明。
3. 如果 `agent-sdk` 要作为正式 shipped customer SDK，应补齐 root reactor/module metadata/facts/CI 覆盖；如果保持 standalone/experimental，应在 README/设计文档中明确其独立验证命令和成熟度。

建议当前阶段先保持 standalone，但把验证入口分成“单元/安装验证”和“example 真实运行验证”：

```bash
mvn -f agent-sdk/pom.xml test
mvn -f agent-sdk/pom.xml -DskipTests install
mvn -f examples/agent-sdk-example/pom.xml compile exec:java "-Dexample.mainClass=com.huawei.ascend.agentsdk.example.OpenJiuwenReactAgentSdkExample"
mvn -f examples/agent-sdk-example/pom.xml compile exec:java "-Dexample.mainClass=com.huawei.ascend.agentsdk.example.OpenJiuwenDeepAgentSdkExample"
```

其中 example 依赖 `com.huawei.ascend:agent-sdk:0.2.0-SNAPSHOT`，所以运行 example 前必须先 install SDK。`exec:java` 会走真实模型调用，需要 `DEEPSEEK_API_KEY` 和可访问的模型 endpoint；如果只想做 CI 级别编译验证，可退化为 `mvn -f examples/agent-sdk-example/pom.xml compile`，但这不能证明 example 可运行。

### 13.2 对外 API 的真实语义

`AgentFactory` 的返回类型是 OpenJiuwen 原生类型：

- `toReactAgent(Path)` 返回 `com.openjiuwen.core.singleagent.ReActAgent`。
- `toDeepAgent(Path)` 返回 `com.openjiuwen.harness.deep_agent.DeepAgent`。

这意味着 `agent-sdk` 不是 framework-neutral SDK，也不是 runtime hosting abstraction。它是 **YAML 到 OpenJiuwen agent instance 的装配器**。后续文档和 PR 描述不应把它表述为可直接替代 `agent-runtime` 的运行时适配层。

`AgentFactoryBuilder.toolResolver(...)` 当前语义：

- custom resolver 先于内置 resolver 注册，因此可以覆盖 `file` / `http` scheme。
- ReAct 和 DeepAgent 的 resolver 调用点都是 `toolResolvers.stream().filter(...).findFirst().resolve(...)`，即 **first-match-wins**；命中第一个 resolver 后不会继续尝试后续 resolver。
- 内置 resolver 顺序为 `JavaFileToolResolver`、`HttpToolResolver`。
- 当前未拒绝 `null` resolver；后续应补 `Objects.requireNonNull`，否则 build 时可能在 stream filter 阶段 NPE。

### 13.3 YAML schema 应补充的通用约束

当前 `ascend-agent/v1` 仍然是宽松 YAML schema，建议补充以下约束，避免实现者各自解释：

| 主题 | 当前行为 | 需要在设计中固定 |
|---|---|---|
| 未知顶层字段 | parser 当前忽略 | 是否继续忽略，还是进入 strict mode；建议当前忽略，后续加 lint-only warning |
| `framework.options` 未知字段 | record 原样保存，具体 builder 只读子集 | 保持透传存储，但未消费字段不应声称生效 |
| `model.headers` | 非 null 值转 string，null 被跳过 | 固定为 `Map<String,String>`，null header value 不进入请求 |
| `prompt.agentMd` | 原样读取文件，不 trim | 保留原样读取；如果用户希望去空白，应在 md 文件自身处理 |
| `tools[].inputSchema` | 任意 YAML object，未做 JSON Schema 校验 | 明确仅作为 LLM tool card 描述，不做 schema validator |
| `tools[].ref` unknown scheme | shorthand 会变成 `{ value: raw }`，对象形式原样保留 | 必须由 custom resolver 解释；无 resolver 时 build 阶段失败 |

不建议当前引入强 schema validator。原因是 SDK 仍在 standalone 阶段，过早严格化会破坏客户侧 YAML 演进；更合适的是先补明确错误信息和 lint-only 检查。

### 13.4 Skill source 语义需写得更精确

`SkillSourceLoader` 当前支持两种 filesystem source 形态：

1. source 目录自身包含 `SKILL.md`：该目录就是一个 skill。
2. source 目录不含直接 `SKILL.md`，但子目录中包含 `SKILL.md`：每个子目录是一个 skill，按目录名排序。

如果 source 目录同时包含直接 `SKILL.md` 和子 skill 目录，当前会 fail-fast。这个设计是合理的，避免一个 source 同时表示“单 skill”和“skill root”。

需要补充的边界：

- `skills.sources` 只支持 `filesystem`，不支持 GitHub/HTTP/remote skill。
- ReAct 路径注册的是每个 skill 目录：`agent.registerSkill(skillDirectory)`。
- DeepAgent 路径写入的是 skill root directory：多个 sibling skill 会归并到同一个 parent root。
- 当前没有 skill 名冲突检测；两个不同 source 下同名 skill 可能在 DeepAgent native skill path 中产生歧义。后续应优先在 `SkillSourceLoader.load()` 汇总所有 source 后做重复 skill name 校验，让冲突在 YAML load 阶段 fail-fast，而不是拖到 `OpenJiuwenSkillMapper` / agent build 阶段。

### 13.5 Tool 执行模型的风险与重构边界

`file` tool 当前并不是“从文件加载 Java 源码”，而是“classpath 上的静态 Java 方法”：

- YAML 不支持 source `path`。
- method 必须是 public static，参数为 `Map`。
- 当前 `OpenJiuwenToolMapper.invokeJava(...)` 用 `Class.forName` + `getMethod(methodName, Map.class)`，方法签名不匹配会在执行时失败，而不是 YAML load 阶段失败。

这个选择是合理的：客户 SDK 不承担编译 Java 源码的职责。但设计文档应避免“file tool”被误解为“读取本地 Java 文件执行”。后续可以考虑把 scheme 名从 `file` 迁移到 `java`，保留 `file` 作为兼容别名；短期不建议改，以免破坏现有 YAML。

HTTP tool 当前的安全缺口已在 §7.1.1 / §11.4 / §12.3 记录。补充两点：

- `HttpExecutionHandle` 当前只有 `url/method/headers/timeout`，所以 `followRedirects/maxResponseBytes/exposeErrorBody/allowlist` 不是“只改 executor”能完成，需要扩展 handle、resolver、测试和文档。
- `HttpClient` 是 `OpenJiuwenToolMapper` 默认构造出来的内部对象，SDK 对外没有 policy 注入点。后续若要做安全治理，建议新增 `HttpToolPolicy` 或允许 `AgentFactoryBuilder` 注入 `HttpToolExecutor` / policy，而不是把所有安全字段塞进 YAML。

### 13.6 ReActAgent 路径的全局副作用

ReAct tool 注册路径会写两个位置：

1. `agent.getAbilityManager().add(tool.getCard())`
2. `Runner.resourceMgr().addTool(tool, agentId)`

这说明 ReActAgent 构造不是纯对象转换；它会修改 OpenJiuwen 全局 `Runner.resourceMgr()`。当前测试用 UUID suffix 避免了 tool name 冲突，但真实客户进程中如果复用 agent name/tool name，可能出现全局资源覆盖或残留。

该副作用只在 ReAct 路径出现。DeepAgent 路径当前把 tools 放入 `DeepAgentConfig.tools` 后交给 `HarnessFactory.createDeepAgent(...)`，SDK 代码没有直接调用 `Runner.resourceMgr().addTool(...)`。

后续应补充：

- 明确 `name` 是 OpenJiuwen resource 隔离的重要输入，不只是展示 ID。
- 建议生产 YAML 使用稳定且全局唯一的 `name`。
- 如果 OpenJiuwen 提供 remove/unregister API，SDK 可增加 cleanup 或 builder option；当前不实现。

### 13.7 DeepAgent 路径的构造建议需要收敛

当前代码同时构造 `DeepAgentConfig` 和 `Workspace`，并把 `Workspace.rootPath(".")` / `language("cn")` 写死。根据 `DeepAgentConfig` API，它本身已有 `workspacePath`、`language` 字段；因此后续实现建议统一为：

1. `framework.options.workspacePath` 写入 `DeepAgentConfig.workspacePath`。
2. `framework.options.language` 写入 `DeepAgentConfig.language`。
3. `HarnessFactory.createDeepAgent(...)` 的 `Workspace` 参数尽量使用 OpenJiuwen 默认或由 config 解析后的值，避免 SDK 与 HarnessFactory 各自解析一次。

如果 OpenJiuwen 当前 API 强制要求显式 `Workspace`，则 SDK 应用同一份 option 同时填 `DeepAgentConfig` 与 `Workspace`，并在测试中断言二者一致。不能继续让 `DeepAgentConfig` 和 `Workspace` 出现两套默认值。

### 13.8 模型参数补全不应一次性铺太宽

设计文档列出的候选字段需要按必要性收敛：首版只保 model / tool / rail / MCP 及当前硬编码边界修复，其它 OpenJiuwen 能力不应因为“能透传”就进入 YAML 公共契约。更稳妥的实现顺序：

1. P0：`DeepAgent skillMode/language/workspacePath`。
2. P1：model 必要参数：`model.request.temperature/topP/maxTokens/stop/seed`、`model.timeout`、`model.maxRetries`，并明确 React 与 DeepAgent 的字段名映射差异。
3. P1：tool 必要安全策略：HTTP tool redirect、响应体上限、错误体回显、目标地址 policy。
4. P1：rail 必要能力：DeepAgent `enableTaskLoop`、`enableTaskPlanning`、`completionTimeout`、代码级自定义 rail 注入、YAML `rails[]` class 注入、`FunctionRailAdapter` 有限事件绑定。
5. P1：MCP tool-provider：`mcps[]` 最小 schema、`McpServerConfig` 映射、最小 MCP 示例和测试。
6. P2/专题：context/memory、permissions/security、extra prompt sections、general-purpose/subagent、provider extra passthrough。

原因：

- React 使用 `com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig`，DeepAgent 使用普通 `model` / `backend` map，两条路径的映射不是同一 API。
- 一次性加入太多 YAML 字段会让 `AgentYamlParser` 快速膨胀，并增加错误默认值风险。
- MCP 已按 tool-provider 纳入必要范围；permissions、context/memory、subagent 仍是独立能力面，不是 model/tool/rail/MCP 最小契约的一部分，应有单独 schema、example 或集成测试证明。

### 13.9 文档与 example 的一致性

当前 example README 已声明：

- `agent-core-java:0.1.12-jdk17`
- `agent-sdk:0.2.0-SNAPSHOT`
- DeepAgent 示例调用 `DeepAgent.run(...)`

设计文档已经同步到 `0.1.12-jdk17`，但还应持续保持以下一致：

- 设计文档中的 example command 必须以 repo root 为工作目录。
- example 运行前必须 install SDK。
- 真模型调用需要 `DEEPSEEK_API_KEY`；无 key 时 example 不是可靠 CI 验证。
- 单元验证与真模型 example 验证应分开表述：`mvn -f agent-sdk/pom.xml test` 是单元层；example exec 是人工/环境依赖层。

### 13.10 当前不建议修改的点

以下点当前看起来“不完美”，但不建议马上重构：

| 点 | 不建议现在改的原因 |
|---|---|
| `AgentSpec` 使用扁平字段而不是嵌套 `FrameworkSpec` | 即使 P1 增加 `rails[]` / `mcps[]`，它们也是顶层能力面，不必因此先拆 `FrameworkSpec`；等 `framework.options` 自身复杂化后再拆 |
| `PromptSpec` 只保留最终 `system` | 目前两个 builder 都只消费最终 system prompt；保留 `agentMdPath` 会增加 public model surface |
| `JavaFileToolResolver` scheme 名叫 `file` | 命名不理想，但 §7.1 已把 `file` scheme 写入 YAML；短期改名会破坏兼容，若后续迁移到 `java` scheme，也必须保留 `file` 作为兼容别名 |
| `AgentYamlParser` 暂不拆类 | 当前仍可读；等 model/request/mcps 落地前再拆更合适 |
| 不把 `agent-sdk` 立刻纳入 root reactor | 这是治理/发布身份决策，不应混在 SDK 内部设计刷新里 |

### 13.11 下一轮代码修改建议排序

建议下一轮按以下 commit 粒度推进：

1. **P0 DeepAgent 构造字段解硬编码**：DeepAgent `skillMode/language/workspacePath`，配套单测。
2. **P1 Model 必要参数**：新增 `ModelRequestSpec`，支持 `temperature/topP/maxTokens/stop/seed/timeout/maxRetries`；React 映射到 `ModelRequestConfig`，DeepAgent 映射到 `model/backend` map，配套单测。
3. **P1 Tool 必要安全策略**：扩展 `HttpExecutionHandle` 与 resolver，默认关闭 redirect、增加响应上限、默认不回显错误体、增加目标地址 policy，配套本地 HTTP server 测试。
4. **P1 Rail 必要能力**：DeepAgent `enableTaskLoop/enableTaskPlanning/completionTimeout`、`AgentFactoryBuilder.rail(...)` / `railResolver(...)`、YAML `rails[]` class 注入、`FunctionRailAdapter` 有限事件绑定。
5. **P1 MCP tool-provider**：新增 `mcps[]` 最小 schema，映射到 `McpServerConfig`，配套最小 MCP 示例和测试。
6. **P1 skill 名冲突检测**：同名 skill fail-fast。
7. **P1 parser 拆分**：落 P1 model/tool/rail/MCP 字段时同步拆分 parser，避免主 parser 继续膨胀。
8. **P2 专题能力**：context/memory、permissions/security、subagent/general-purpose、extra prompt sections、provider extra passthrough。

`ModelRequestConfig` 缺失会让 ReAct 继续使用 OpenJiuwen 默认采样参数，确实影响 tool-calling 稳定性；但它同时牵涉 React `ModelRequestConfig` 与 DeepAgent `model` map 两套映射。当前排序有意先修 DeepAgent 已存在硬编码，再以 P1 单独落 model 必要参数，避免在同一轮把 YAML schema、两套 adapter 映射和默认值策略一起扩大。

### 13.12 本节核实来源

本节结论依赖当前工作区文件与本机 OpenJiuwen jar API，后续代码漂移时应重新核实：

- 根 [pom.xml](../pom.xml)：确认 root reactor 当前不包含 `agent-sdk`，parent Java 版本为 21。
- [agent-sdk/pom.xml](pom.xml)：确认 SDK 继承 parent，依赖 `com.openjiuwen:agent-core-java:0.1.12-jdk17`。
- [AgentFactory.java](src/main/java/com/huawei/ascend/agentsdk/factory/AgentFactory.java)：确认对外返回 OpenJiuwen 原生 `ReActAgent` / `DeepAgent`。
- [AgentFactoryBuilder.java](src/main/java/com/huawei/ascend/agentsdk/factory/AgentFactoryBuilder.java)：确认 custom resolver 先注册、内置 resolver 顺序、未拒绝 null resolver。
- [OpenJiuwenReactAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/react/OpenJiuwenReactAgentBuilder.java)：确认 ReAct config 落点、resolver first-match-wins、`Runner.resourceMgr().addTool(...)` 全局注册副作用。
- [OpenJiuwenDeepAgentBuilder.java](src/main/java/com/huawei/ascend/agentsdk/adapter/deepagent/OpenJiuwenDeepAgentBuilder.java)：确认 DeepAgent config/backend/model 落点、`Workspace.rootPath(".")` / `language("cn")` 硬编码。
- [AgentYamlParser.java](src/main/java/com/huawei/ascend/agentsdk/spec/yaml/AgentYamlParser.java)：确认 YAML 字段解析、prompt 拼接、tool ref shorthand、header/null 行为。
- [SkillSourceLoader.java](src/main/java/com/huawei/ascend/agentsdk/spec/skill/SkillSourceLoader.java) 与 [OpenJiuwenSkillMapper.java](src/main/java/com/huawei/ascend/agentsdk/adapter/OpenJiuwenSkillMapper.java)：确认 filesystem skill source 形态、单 skill/skill root 互斥、DeepAgent root directory 归并。
- [HttpToolResolver.java](src/main/java/com/huawei/ascend/agentsdk/spec/tool/HttpToolResolver.java) 与 [HttpToolExecutor.java](src/main/java/com/huawei/ascend/agentsdk/adapter/HttpToolExecutor.java)：确认 HTTP tool URI 解析、timeout、redirect、响应体和错误体行为。
- 本机 OpenJiuwen jar API：`AgentRail`、`AgentCallbackContext`、`AgentCallbackManager`、`BaseAgent.registerRail(...)`、`DeepAgentRail`、`DeepAgentConfig.rails`、`McpServerConfig`，确认 rail/MCP 目标落点和 callback 生命周期。
- [examples/agent-sdk-example/README.md](../examples/agent-sdk-example/README.md)：确认 example 运行命令与 `DeepAgent.run(...)` 调用口径。
