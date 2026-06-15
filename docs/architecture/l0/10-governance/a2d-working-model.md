---
level: L1
view: governance
status: draft
---

# A2D 工作模型（内核）

## 目的

A2D（Architecture to Delivery）是 AI 将版本需求文档批量转化为可审阅架构和可交付实现的推理、编辑、验证与追踪模型。

A2D 不是目录结构，也不是一批静态文档，也不是逐需求人工审批流。目录只负责归档活动产物。

本文是 A2D 的精简内核，包含跨阶段共享的信息。每个阶段的具体操作步骤、输入输出和退出条件定义在对应的阶段契约中。

## 版本级自动化模型

A2D 默认以版本为单位运行。人类集中注入版本需求文档和开发边界说明，AI 在开发边界内批量完成分析、文档编辑、任务拆解、实现、验证和归档。

A2D 的主线跟随标准信息对象链推进：原始意图记录、版本需求文档、开发边界说明、4+1 架构包、架构展开 / 切片化、交付计划、实现证据包、后置投影检查包、基线决策记录。1→3 中的代码扫描结果作为开发边界说明的自动证据附录保留，不作为独立主流程节点。H0/H1/H2/H3 是人类裁决点，用来确认某个对象是否足以进入下一个对象，而不是另一套并列流程。

A2D 不要求最初的架构稿完整到可以一次性开发完成。更稳定的工作方式是：

1. 先形成**开发边界说明**：目标、非目标、模块边界、状态 owner、禁止依赖、第一批代码 / harness 入口、法律层映射和升级条件。
2. 在 4+1 之后先做**架构展开 / 切片化**：把粗粒度架构约束展开为契约、状态、路径、harness、probe、测试和风险切片。
3. 尽早形成**最小代码骨架 / harness / probe**：接口、record、状态机、contract test、schema、wire-level probe 或测试替身，让实现暴露真实约束。
4. 将实现暴露的问题**回灌**：更新 ICD、schema、L1/L2、评审包或下一阶段交付视图。
5. 每个关键边界尽量进入**法律层**：CI gate、ArchUnit、contract test、wire-level probe、schema check 或 drift check；无法机器检查的事项必须有人类 owner。
6. 只在触发边界、契约、状态、依赖或风险裁决时，要求人类重新确认。

## 两层约束模型

A2D 对 AI 的限制分为两层：

| 层级 | 人类参与密度 | 主要产物 | AI 可以做什么 | AI 不得做什么 |
|---|---|---|---|---|
| 架构协作层 | 高 | 版本需求文档、开发边界说明、必要的 4+1 架构包、模块责任、状态归属、跨模块契约、harness 入口、测试计划 | 整理输入、生成候选方案、发现冲突、建立自动推进边界、生成可审阅视图 | 自行裁决模块边界、状态 owner、跨模块控制权、兼容性破坏和发布风险 |
| 自动化交付层 | 中到低 | 交付视图、契约投影、代码骨架、实现代码、测试、漂移报告、验证证据、实现反馈 | 在已确认开发边界内使用 graphify、OpenAPI/Swagger、schema、stub、mock、contract test 或代码生成工具推进实现，并把实现发现的新约束回灌 | 把工具生成结果当成新的架构事实，或越过 H2 确认的开发边界改变模块/契约语义 |

Markdown 架构文档是人类审阅界面，不是无结构散文。进入自动化交付层前，架构层产物不需要完整覆盖所有实现细节，但必须能约束第一批施工：稳定 ID、模块、状态、契约、禁止范围、harness 入口、验证证据和升级条件必须清楚。后续实现发现的事实通过实现反馈回灌，而不是要求最初架构稿一次性完美。

架构文档属于意图层；真正限制模型的是法律层。A2D 默认 deny-by-default：开发边界未显式允许的路径、依赖、生成物和契约变化都视为禁止。每个关键 guard 必须证明自己非空虚，避免“什么都没检查也绿”的假安全。

graphify、OpenAPI/Swagger、schema 或代码生成工具是自动化交付层的事实投影工具：它们可以从已接受架构产物生成接口文档、代码骨架、mock/stub 或测试输入，也可以从代码反向抽取实际结构用于漂移检查；但它们不得替代人类确认的开发边界说明，也不得反向创造架构语义。

```text
H0 人类注入版本需求文档
 -> AI-1 需求归一化与分流        [阶段契约: a2d-phases/phase-intake.md]
 -> AI-2 架构影响分析            [阶段契约: a2d-phases/phase-impact.md]
 -> AI-3 生成版本开发边界说明        [阶段契约: a2d-phases/phase-envelope.md]
H1 人类确认版本开发边界说明
 -> AI-4 批量生成 A2D 产物       [阶段契约: a2d-phases/phase-produce.md]
 -> AI-5 生成 4+1 评审视图       [阶段契约: a2d-phases/phase-review-view.md]
 -> AI-6 生成交付视图            [阶段契约: a2d-phases/phase-delivery-view.md]
H2 人类集中审核 4+1 架构包
 -> AI-7 自动实现               [阶段契约: a2d-phases/phase-implement.md]
 -> AI-8 集成验证与漂移检查      [阶段契约: a2d-phases/phase-verify.md]
H3 人类审核例外和发布风险
 -> AI-9 版本归档               [阶段契约: a2d-phases/phase-archive.md]
```

人类视角的完整流程见 [a2d-human-checkpoints.md](a2d-human-checkpoints.md)。

## 产物状态

| 状态 | 含义 | 可用于开发吗 | 需要谁确认 |
|---|---|---|---|
| raw_input | 原始输入，可能不完整、冲突或未经分类。 | 否。 | 输入提供者确认背景。 |
| draft | 已整理但未裁决的草稿。 | 只能用于讨论。 | 相关负责人确认后才能升级。 |
| reviewed | 已评审，未必成为当前基线。 | 可用于探索性实现或 harness 草案。 | 评审者和相关负责人确认。 |
| accepted | 当前交付基线。 | 是。 | 架构负责人和模块负责人确认。 |
| superseded | 已被新版本替代。 | 否，除非明确用于迁移。 | 架构负责人确认替代关系。 |

## 归档位置约定

| 产物类型 | 默认归档位置 |
|---|---|
| 版本需求文档 | `10-governance/version-intents/<version>.md` |
| 版本开发边界说明 | `10-governance/architecture-envelopes/<version>.md` |
| 4+1 架构包 | `10-governance/review-packets/<version>.md` |
| 交付视图 | `10-governance/delivery-projections/<version>.md` |
| A2D Intake Record | `10-governance/a2d-intake/<id>.md` |
| Admission Decision | `10-governance/admission-decisions/<id>.md` |
| 基线说明 | `10-governance/baselines/<version>.md` |
| 原始需求记录 | [task.md](../task.md) |
| Conflict / Open Issue | [constraint-and-design-inventory.md](../constraint-and-design-inventory.md) |
| BA 场景 | `02-scenarios/BA-xxx-*.md` |
| Technical 场景 | `02-scenarios/technical/Sx-*.md` |
| 能力图 | [capability-map.md](../01-capabilities/capability-map.md) |
| 模块责任卡 | [module-responsibility-cards.md](../04-modules/module-responsibility-cards.md) |
| 模块设计包 | `04-modules/<module>/` |
| 状态归属矩阵 | [state-ownership-matrix.md](../06-state/state-ownership-matrix.md) |
| Human-readable ICD | `05-contracts/human-readable/*.md` |
| Machine-readable 草案 | `05-contracts/machine-readable/*.yaml` |
| Harness 设计 | `08-harness/` 或 `04-modules/<module>/harness-design.md` |
| 验证矩阵 | [verification-matrix.md](../09-verification/verification-matrix.md) |

## 交付物模板

各阶段交付物的最小内容和验收口径统一定义在 [a2d-artifact-templates.md](a2d-artifact-templates.md)。阶段契约可以引用模板并补充本阶段规则，但不得降低模板中的 allowed / forbidden / verification / escalation 要求。

## 相关文件

| 文件 | 内容 |
|---|---|
| [a2d-human-checkpoints.md](a2d-human-checkpoints.md) | 人类视角的检查点流程 |
| [a2d-stage-inputs-and-tools.md](a2d-stage-inputs-and-tools.md) | 每个阶段的人类输入、AI 产物和进入下一阶段的工具链 |
| [a2d-roles.md](a2d-roles.md) | 角色与协作边界 |
| [a2d-views.md](a2d-views.md) | 双视图模型、验收口径和最小工作包 |
| [a2d-artifact-templates.md](a2d-artifact-templates.md) | 各阶段交付物要求和模板 |
| [a2d-phases/](a2d-phases/) | AI 阶段契约（按阶段加载） |
| [change-governance.md](change-governance.md) | 变更分级规则 |
| [layer-update-protocol.md](layer-update-protocol.md) | 多层更新协议（L0/L1/L2 联动工作顺序） |
| [architecture-review-process.md](architecture-review-process.md) | 评审流程和 Finding 格式 |
| [document-artifact-catalog.md](document-artifact-catalog.md) | 目录职责索引 |

## 维护规则

- 阶段契约变更时，更新本文的阶段索引
- 新增产物类型时，更新归档位置约定
- 本文保持精简；详细内容放进阶段契约或相关文件
