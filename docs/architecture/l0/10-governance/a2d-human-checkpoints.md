---
level: L1
view: governance
status: draft
---

# A2D 人类检查点流程

## 目的

这份文件是给人类看的。它只说明：**你需要在什么时候介入，提供什么，确认什么，获得什么。**

AI 在检查点之间自动完成所有分析、文档生成、任务拆解、实现和验证。你看不到也不需要关心 AI 内部怎么拆步骤。

## 适用读者

业务负责人、架构负责人、模块负责人、评审者。

## 流程总览

```text
H0 你告诉 AI 本版本要做什么
 -> AI 自动分析、建模、生成边界草案
H1 你确认 AI 可以在哪些范围内自动推进
 -> AI 生成必要架构事实、4+1 评审包、harness/测试计划和交付计划
H2 你集中审核 AI 的架构方案
 -> AI 基于已确认开发边界说明尽早实现代码骨架、harness、schema、contract test、wire-level probe，并把实现发现的问题回灌
H3 你审核剩余例外和发布风险
 -> AI 自动归档
```

A2D 分为两层：

| 层级 | 你重点确认什么 | AI 自动完成什么 |
|---|---|---|
| 架构协作层 | 目标、非目标、模块边界、状态 owner、跨模块契约、第一批代码 / harness 入口和法律层映射是否成立。 | 把输入整理成开发边界说明、必要架构事实、4+1 评审视图和交付视图。 |
| 自动化交付层 | 实现是否仍在已确认开发边界内，法律层是否兜住关键边界，是否有越界、漂移或发布风险。 | 从开发边界和契约派生代码骨架、schema、mock/stub、contract tests、wire-level probe，完成实现、验证和实现反馈回灌。 |

Markdown 架构文档是人类审阅界面，但不是一次性完整蓝图。最初只需要形成可执行的开发边界说明：约束边界、禁止范围、第一批实现入口、法律层、harness 和升级条件。代码和测试暴露的新事实再回灌到 ICD、schema、L1/L2、评审包或下一阶段交付计划。

## 目录结构说明

所有架构文档按职责组织在 `docs/architecture/l0/` 下，编号即阅读顺序。向别人介绍时，可以按编号从 00 到 10 依次讲解：

| 目录 | 名称 | 一句话说明 | 核心内容 |
|---|---|---|---|
| `00-overview/` | 系统概览 | 整个系统的入口文档，回答"我们做什么、为什么、给谁用"。新成员从这里开始读。 | 系统目标与问题域（`architecture-overview.md`）、设计原则（`system-principles.md`）、术语表（`glossary.md`） |
| `01-capabilities/` | 能力地图 | 定义系统有哪些能力（不绑定具体模块），再映射到模块、场景、ADR 和验证方式。避免"按模块重复建设能力"。 | 能力注册表（`capability-map.md`），每行一个 CAP-ID，关联 owning module、参与模块、阶段、场景、ADR、验证方式 |
| `02-scenarios/` | 场景 | 用具体的端到端故事描述系统行为。分业务场景（面向利益相关者）和技术场景（面向实现者）。是架构设计的验收依据。 | 业务活动 BA-001~003（Agent 处理业务请求、人工审批、多 Agent 协作）、技术场景 S1~S6（创建 Run、执行步骤、构建上下文、工具调用、挂起恢复、子任务联邦） |
| `03-adrs/` | 架构决策记录 | 记录重要的架构决策及上下文（为什么这么选、考虑了什么替代方案）。ADR 编号稳定，一旦 accepted 不再删除。 | 按 `ADR-NNN` 编号的决策文件（如 Run 生命周期所有权、契约优先交互、上下文归属、工具治理、harness 优先等） |
| `04-modules/` | 模块设计包 | 每个模块的"身份证"和完整设计。包含责任卡（对外承诺）和子目录内的 L1 详细设计。 | 模块责任卡总表（`module-responsibility-cards.md`）、每个模块一个子目录（如 `agent-service/`）含逻辑视图、开发视图、场景等 |
| `05-contracts/` | 模块间契约 | 定义模块之间的接口协议（ICD），是人类可读文档和机器可读 schema 的双轨制。模块间交互必须引用这里的契约，不能私自发明。 | `human-readable/` 下的 ICD 文档、`machine-readable/` 下的 YAML schema，覆盖 Gateway-Workflow、Agent-Context、Tool-Gateway 等接口 |
| `06-state/` | 状态归属矩阵 | 明确每个核心状态（Task 状态、Context、Checkpoint 等）的唯一 owner、谁可以写、谁可以读、生命周期和持久化要求。防止多模块争抢写同一状态。 | 状态注册表（`state-ownership-matrix.md`），每行一个 STATE-ID |
| `07-invariants/` | 架构不变量 | 把原则和 ADR 转化为可检查的规则（如"平台代码不得包含业务定制""每个状态只有一个 owner"），供代码审查和 CI gate 使用。 | 不变量注册表（`architecture-invariants.md`），每条关联验证方式 |
| `08-harness/` | Harness 设计 | 每个模块的测试骨架设计。定义该模块需要什么样的测试环境、依赖替身、验证手段，确保"可测试"在实现之前就被设计好。 | 按模块拆分的 harness spec（agent-service、context-engine、tool-gateway、workflow、observability） |
| `09-verification/` | 验证矩阵 | 把 ADR、ICD、场景、不变量、harness 要求汇总到一张表，回答"每条设计决策用什么方式验证、当前验证到什么程度"。 | 验证矩阵（`verification-matrix.md`）、测试策略（`test-strategy.md`） |
| `10-governance/` | 治理流程 | 本文件所在的目录。管理 A2D 工作流、变更分级、评审流程、文档约束、版本归档等"怎么协作"的规则。 | A2D 工作模型和检查点（本文件）、变更分级规则（`change-governance.md`）、评审流程（`architecture-review-process.md`）、文档约束（`architecture-documentation-constraints.md`）、A2D 阶段详情（`a2d-phases/`） |

## 多层更新规则

当变更同时影响多层文档时，遵循 [layer-update-protocol.md](layer-update-protocol.md)。核心规则：

1. **自顶向下更新 + 影响传导**：先改 L0，再改 L1，最后改 L2。上层完成变更后**必须产出层间调整需求**（包含受影响模块、受影响契约、约束条件、待确认问题），作为下层的变更输入。下层不需要自己去猜上层改了什么。
2. **下层确认、细化、执行、传导**：下层收到调整需求后，确认理解 → 细化本层影响 → 执行变更 → 如有更下层影响则继续传导。
3. **自底向上裁决**：下层发现与上层矛盾时，停止工作，逐层上报裁决。
4. **层间审批**：L0 变更由架构负责人审批，L1 由模块负责人审批，L2 由契约 owner 审批。跨层变更逐层审批，不能跳层。

## AI 在每个阶段读写哪些目录

下面按检查点分段说明。人类只需关注"返回给你什么"和"改了哪些文件"。如果某个目录被改了但你没看到预期变化，可以追问 AI。

### H0 → H1 之间：AI 分析需求并生成边界草案

| 行为 | 涉及目录 |
|---|---|
| **读取**（AI 了解现状） | `00-overview/`（系统概览）、`01-capabilities/`（能力图）、`04-modules/`（模块责任卡）、`06-state/`（状态归属）、`05-contracts/`（现有契约）、`07-invariants/`（不变量）、`change-governance.md`（变更分级规则） |
| **新增或更新** | `10-governance/version-intents/<version>.md`（版本需求文档，含需求分类表和架构影响矩阵）、`10-governance/architecture-envelopes/<version>.md`（版本开发边界说明草案） |
| **不动** | `02-scenarios/`、`08-harness/`、`09-verification/`、源码 |

### H1 → H2 之间：AI 生成必要架构事实、评审包和交付计划

| 行为 | 涉及目录 |
|---|---|
| **读取** | `00-overview/` ~ `09-verification/`（全部现有架构基线）、`architecture-documentation-constraints.md`（文档写作约束） |
| **新增或更新** | `01-capabilities/`（能力图更新）、`04-modules/`（模块责任卡和模块设计包更新）、`06-state/`（状态归属更新）、`05-contracts/`（ICD 和契约草案）、`02-scenarios/`（场景新增或更新）、`07-invariants/`（不变量更新）、`08-harness/`（harness 设计）、`09-verification/`（验证矩阵）、`10-governance/review-packets/<version>.md`（4+1 架构包）、`10-governance/delivery-projections/<version>.md`（交付计划） |
| **不动** | 源码 |

本阶段不要求补完整架构稿。只生成本版本进入开发所需的架构事实；交付计划必须说明第一批代码 / harness / schema / contract test 如何推进。

### H2 → H3 之间：AI 自动实现、测试和验证

| 行为 | 涉及目录 |
|---|---|
| **读取** | `10-governance/delivery-projections/<version>.md`（交付计划）、`10-governance/architecture-envelopes/<version>.md`（版本开发边界说明）、`04-modules/<module>/`（模块设计包）、`08-harness/`（harness 设计）、`09-verification/`（验证矩阵） |
| **新增或更新** | **源码**（实现代码和测试）、`08-harness/`（harness 补充）、`09-verification/`（验证矩阵增量）、A2D 文档（如果实现改变了设计事实，回写对应文档）、`10-governance/review-packets/<version>.md`（追加验证汇总、漂移报告、升级项）、`10-governance/delivery-projections/<version>.md`（下一阶段实现反馈和修改意见）、`10-governance/baselines/<version>.md`（基线说明草案） |
| **不动** | `00-overview/`、`01-capabilities/`（除非实现改变了架构事实） |

### H3 之后：AI 归档版本

| 行为 | 涉及目录 |
|---|---|
| **读取** | `version-intents/`、`architecture-envelopes/`、`review-packets/`、`delivery-projections/` |
| **新增或更新** | `10-governance/baselines/<version>.md`（从草案提升为 accepted）、各 A2D 文档的 frontmatter 状态标记、`README.md`（入口更新） |
| **不动** | 源码 |

## H0：注入版本需求文档

### 你提供什么

把下面这份模板填到你觉得够用的程度，不需要写完整设计：

```yaml
version_goal: 本版本要达成什么
functional_requirements:
  - 功能需求列表
non_functional_requirements:
  - 性能、可用性、隔离、观测、安全、成本等要求
must_keep:
  - 不能改变的架构边界、状态 owner、公开契约、兼容性要求
allowed_change_scope:
  - 允许 AI 自动修改的模块、文档、测试范围
risk_budget:
  - 哪些风险可接受，哪些必须升级
release_bar:
  - 本版本必须通过哪些验证
```

如果上面这些还填不清楚也没关系。你只需要告诉 AI：
- 要做什么系统 / 什么功能 / 什么变更
- 谁使用它
- 你已经确定不能变的是什么
- 你希望做到什么程度就停下来

### AI 返回什么

- 需求归一化结果：哪些是功能需求、非功能需求、治理约束、开放问题
- 架构影响分析：影响了哪些模块、能力、状态、契约
- 版本开发边界说明草案：AI 建议的自动化范围、禁止范围、第一批实现入口和升级条件
- 澄清问题：AI 需要你补充的信息

### 你做什么

- 补充 AI 提出的澄清问题（如果有的话）
- 确认需求分类是否正确
- 等待 H1

## H1：确认版本开发边界说明

### AI 返回什么

- 版本开发边界说明草案：
  - 本版本接受的目标和非目标
  - 允许 AI 自动调整的模块、文件、契约、测试和生成物范围
  - 禁止 AI 触碰的架构边界
  - 第一批应写出的代码、harness、schema 或 contract test
  - 关键禁止项 / 不变量对应的法律层检查：gate、ArchUnit、contract test、wire-level probe、schema check 或 drift check
  - 允许使用的自动化投影工具，例如 graphify、OpenAPI/Swagger、schema、stub/mock、contract test、codegen
  - 编码完成后的漂移检查口径
  - 实现反馈回灌规则：代码发现架构缺口时回写到哪里、何时升级
  - 高风险切片的人类 owner、备份 reviewer、理解深度和吸收预算
  - 升级条件：哪些情况 AI 必须停下来请你裁决

### 你做什么

逐项确认：

| 确认项 | 你的判断 |
|---|---|
| 目标和非目标是否正确？ | 接受 / 修改 / 追加 |
| 可写模块、只读模块、禁止模块是否清楚？ | 接受 / 收紧 / 放宽 |
| 可写文件目录、可生成文件、只读文件是否清楚？ | 接受 / 补充 |
| 可自动处理的契约变化是否只包含兼容增量？ | 接受 / 收紧 / 升级到人工裁决 |
| 状态 owner、writer、跨模块控制权是否保持不变？ | 接受 / 要求升级 |
| graphify、OpenAPI/Swagger、schema 或 codegen 的用途是否只是投影和校验？ | 接受 / 限制 |
| 自动实现后的漂移检查是否足够？ | 接受 / 补充 |
| 第一批代码 / harness 入口是否足够小且可验证？ | 接受 / 调整 |
| 关键边界是否有法律层检查，且守护测试是否有 liveness guard？ | 接受 / 补充 |
| 未列入 allowed 的路径、依赖、生成物是否默认禁止？ | 接受 / 收紧 |
| 高风险切片的 owner、备份 reviewer 和吸收预算是否清楚？ | 接受 / 补充 |
| 实现反馈回灌规则是否清楚？ | 接受 / 补充 |
| 禁止范围是否遗漏？ | 补充 |
| 升级条件是否足够？ | 补充 |

**确认后，AI 在该边界内自动推进，不再逐条请你审批。** 只有越界时才会打断你。

### 自动推进范围例子

```yaml
allowed:
  modules:
    - agent-service 的 tool-call adapter 和相关测试
  files:
    - agent-service/src/main/java/**/tool/**
    - agent-service/src/test/java/**/tool/**
    - docs/architecture/l0/05-contracts/**
    - docs/architecture/l0/09-verification/verification-matrix.md
  contracts:
    - 给既有响应增加 optional 字段
  automation:
    - 从 ICD 派生 OpenAPI/Swagger 文档
    - 生成 mock、stub 和 contract test
    - 从代码反向抽取依赖图做漂移检查
forbidden:
  modules:
    - agent-bus channel 语义
    - gateway 路由和鉴权
  contracts:
    - 新增 required 字段
    - 改变 error code、retry owner、timeout owner
  state:
    - 改变状态 owner 或 writer
escalate_when:
  - 实现需要修改 forbidden 模块
  - 生成契约和 ICD 语义不一致
  - 测试计划无法覆盖关键失败路径
```

### 0→1 和 N→N 的区别

| 场景 | H1 的重点 |
|---|---|
| 从零设计新架构（0→1） | 你可能需要和 AI 多轮讨论架构方向。AI 会给出多个候选方案和取舍对比，你选择方向后再确认边界。讨论过程记录在版本工作区，不会污染正式架构目录。 |
| 在已有基线上增量变更（N→N） | 影响面通常是明确的。你主要确认变更范围和升级条件，不需要讨论架构方向。 |

## H2：集中审核 4+1 架构包

### AI 返回什么

一份完整的架构审核包，包含：

| 内容 | 说明 |
|---|---|
| 4+1 视图 | 逻辑、开发、进程、物理、场景视图的当前状态 |
| 决策摘要 | 本版本做了哪些架构决策 |
| 变更级别汇总 | 哪些是 Level 0/1/2/3 变更 |
| 影响面 | 影响了哪些模块、状态、契约 |
| 开放问题 | 还没有结论的问题 |
| 交付计划 | 阶段类型、开发切片、任务草案、DoD、代码 / harness / probe 输出、法律层输出、测试计划、自动化投影计划、实现反馈回灌 |

### 你做什么

重点审核：

1. **开发边界说明是否成立**：模块边界、状态归属、跨模块契约、禁止范围是否合理
2. **交付计划是否可行**：任务拆解、DoD、验证方式、harness 和测试计划是否够用，是否有明确代码 / harness / probe 推进
3. **自动化投影是否安全**：graphify、OpenAPI/Swagger、schema、stub/mock、codegen 只是在已确认架构事实上生成交付素材，而不是反向改写架构语义
4. **法律层是否成立**：关键 forbidden / invariant 是否有机器检查或人工 owner，守护测试是否自证非空虚
5. **吸收预算是否现实**：高风险切片是否有人类 owner、备份 reviewer、理解深度和并行上限
6. **例外项**：哪些风险允许进入本版本，哪些必须排除
7. **需要裁决的问题**：AI 标记为"需要人类裁决"的项

不需要审核“架构稿是否已经完美”。你需要审核的是：开发边界能否约束第一批施工，交付计划能否通过代码和 harness 暴露并收敛剩余问题。

**审核通过后，AI 自动进入实现阶段。** 你不需要逐条审批实现任务。

## H3：审核例外和发布风险

### AI 返回什么

| 内容 | 说明 |
|---|---|
| 验证汇总 | 哪些验证通过、哪些失败、哪些跳过 |
| 漂移报告 | 实现是否偏离了 H2 确认的架构 |
| 升级项 | 触发了升级条件的事项 |
| 发布风险 | 残余风险和缓解措施 |
| 基线说明草案 | 本版本归档后的基线描述 |

### 你做什么

- 确认残余风险是否可接受
- 确认升级项的处理方式
- 确认基线说明是否准确

**审核通过后，AI 自动归档版本。**

## 介入密度参考

| 场景 | H0 | H1 | H2 | H3 |
|---|---|---|---|---|
| 从零设计新架构（0→1） | 重交互，可能多轮 | 重交互，候选方案对比 | 详细审核 | 视情况 |
| 增量功能变更（N→N） | 标准输入 | 快速确认 | 重点审核变更部分 | 快速确认 |
| 小范围修复 | 简要说明 | 可跳过（AI 自动判断边界） | 可跳过（变更在边界内） | 快速确认 |

## AI 什么时候会主动打断你

即使在你没有主动介入的阶段，以下情况 AI 会停下来请你裁决：

- 需要改变状态 owner、writer 或跨模块控制权
- 需要破坏已有契约的兼容性
- 需要绕过已确认的模块边界或依赖方向
- 需求与 `CLAUDE.md`、正式 ADR 或治理规则冲突
- AI 发现自己在版本开发边界说明外操作

如果上述情况都没有发生，你只会收到 H0 → H1 → H2 → H3 四次交互。
