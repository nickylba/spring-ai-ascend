---
level: L1
view: governance
status: draft
---

# A2D 阶段输入与工具链

## 目的

本文补充 [a2d-human-checkpoints.md](a2d-human-checkpoints.md)，把每个阶段的人类输入、AI 产物、进入下一阶段使用的工具和退出条件具体化。

这份文档的目标不是解释理念，而是让一次 A2D 工作可以被真正启动、分派和验收。

## 总体规则

- 人类输入必须说明目标、边界、风险和验收口径；不能只给一句“实现某功能”。
- AI 进入下一阶段前必须说明使用哪些工具、读取哪些事实源、生成哪些产物。
- 工具只能投影、校验或生成交付素材；不能反向创造架构语义。
- OpenAPI/Swagger 和 graphify 是 H3 的后置投影检查工具，用来从更新后的代码生成接口文档和结构/依赖图，供人类审核是否符合 H1/H2 的预期。
- 未列入开发边界说明 allowed / writable / generated 的路径、依赖、契约变化和生成物默认禁止。
- 非 `decision-only` 阶段必须推进代码、harness、schema、contract test、probe、漂移检查或 adapter skeleton 中至少一项。

## 概念收敛

为了避免 A2D 变成“文档工厂”，本文只保留最小交付物集合。

**架构事实和 4+1 视图不是两个并列交付物。** 架构事实是被人类接受的设计内容，例如模块边界、状态 owner、跨模块契约、关键场景、不变量和部署约束；4+1 视图是这些事实的组织方式和评审界面。因此对人类和下游 AI 来说，统一称为**4+1 架构包**。4+1 架构包承载被接受的架构事实，不再要求另交一份“架构事实文档”。

**吸收预算**指人类在某一轮能真正理解、审核和承担风险的容量，不是钱，也不是模型 token。它用于限制 AI 并行推进的规模，避免模型一次提交太多高风险切片，导致人类只能形式化点头。吸收预算至少包含：

- 主 owner：谁必须理解并接受该切片。
- 备 reviewer：谁负责反向检查或补位。
- 理解深度：只需要理解行为承诺，还是必须理解契约，或必须读到实现级。
- 最大并行切片数：同一轮最多允许多少个高风险切片同时推进。
- 停止条件：review 积压、验证断裂、法律层失败、owner 无法吸收时停止。

例子：

```yaml
absorption_budget:
  risk_level: L3
  human_owner: agent-bus owner
  backup_reviewer: agent-runtime owner
  understanding_depth: contract-level
  max_parallel_slices: 1
  stop_when:
    - 出现未评审代码切片积压
    - contract test 或 wire probe 无法建立
    - 实现需要改变 runtime 调用语义
```

## 标准信息对象

为了让信息链路清楚，A2D 只使用下面这些标准对象。其他表格、矩阵、工具输出都应作为这些对象的章节、附录或证据，不应升级成新的并列主交付物。

| 标准对象 | 主要作用 | 包含哪些信息 | 创建阶段 | 下游消费 |
|---|---|---|---|---|
| 原始意图记录 | 保存人类最初表达，避免上下文丢失。 | 一句话需求、会议结论、背景、截图/链接、已知限制、提出人。 | Step 1 | 版本需求文档 |
| 版本需求文档 | 把原始意图整理成可分析需求。 | 目标、非目标、用户/调用方、关键场景、功能/非功能需求、must keep、风险预算、发布门槛、owner、参考材料。 | Step 2 | 开发边界说明、4+1 架构包 |
| 开发边界说明 | 定义本轮开发允许推进什么、禁止触碰什么、何时必须升级。 | allowed、forbidden、writable paths、状态/契约/依赖策略、第一批代码或 harness 入口、自动代码扫描证据、法律层计划、升级条件、后置投影期望。 | Step 3 | 4+1 架构包、交付计划、实现证据包 |
| 4+1 架构包 | 人类评审架构是否成立的主材料。 | 逻辑视图、开发视图、进程视图、物理视图、场景视图、关键决策、冲突、open issues、事实来源。 | Step 4 | 架构展开 / 切片化 |
| 架构展开 / 切片化 | 把 4+1 的粗粒度约束展开为可施工候选。 | 契约、状态、路径、harness、probe、测试、风险切片、依赖顺序、后置投影检查项。 | Step 5 | 交付计划 |
| 交付计划 | 把架构展开 / 切片化结果固化成可施工切片。 | 切片目标、可写路径、代码输出、harness/schema/contract/probe 输出、工具计划、DoD、吸收预算。 | Step 6 | 代码实现、法律层验证、实现证据包 |
| 实现证据包 | 证明代码确实在开发边界内完成，并且可验证。 | 代码 diff、测试 diff、命令结果、法律层证据、changed files 检查、漂移报告、实现反馈。 | Step 7 | 后置投影检查包、基线决策记录 |
| 后置投影检查包 | 让人类审核更新后的接口和结构是否符合预期。 | OpenAPI/Swagger 文档、接口差异、graphify 结构图/依赖图/调用关系、审核 finding。 | Step 8 | 基线决策记录 |
| 基线决策记录 | 决定哪些结果进入 accepted 基线，哪些延后。 | 验证汇总、发布风险、accepted facts、deferred 项、owner、后续入口。 | Step 9 | 基线说明、后续版本入口 |

## 信息流转链路

```text
原始意图记录
 -> 版本需求文档
 -> 开发边界说明
 -> 4+1 架构包
 -> 架构展开 / 切片化
 -> 交付计划
 -> 实现证据包
 -> 后置投影检查包
 -> 基线决策记录
```

1→3 流程仍然必须扫描现有代码、契约、依赖和测试，但扫描结果作为**开发边界说明**的自动证据附录保存，不作为人类主流程中的独立节点。

实现阶段发现的问题不直接改写上游对象，而是先进入**实现证据包**的实现反馈章节，再由人类决定回到开发边界说明、4+1 架构包或交付计划。

## 信息对象推进矩阵

A2D 的主线应跟随信息对象推进。H0/H1/H2/H3 是人类裁决点，不是另一套并列流程。每一步都要说明：当前对象如何变成下一个对象，哪些工作可以自动完成，哪些信息必须由人类补充或确认。

| 当前对象 | 推进到 | 必须完成的工作 | AI / 工具可自动完成 | 人类必须介入 | 通过条件 |
|---|---|---|---|---|---|
| 原始意图记录 | 版本需求文档 | 澄清目标、非目标、调用方、关键场景、must keep、发布门槛和风险预算。 | 归类原始描述、提出澄清问题、整理候选需求、标注歧义和缺口。 | 密集讨论。人类补充业务背景、组织约束、真实用户、不可变边界、验收口径。 | 人类确认需求文档足以支持影响分析；仍不清楚的问题必须进入 open questions。 |
| 版本需求文档 | 开发边界说明 | 分析可能改动的模块、契约、状态、依赖、路径、生成物和验证边界；1→3 中必须自动扫描当前代码、契约、依赖、测试和最近变更事实。 | `rg`、`git diff`、`git log`、构建文件扫描、包结构/依赖扫描、文档索引读取；半自动生成改动分析、影响矩阵、allowed / forbidden 草案、第一批切片候选、升级条件和 H3 后置投影期望。 | 裁决确认。人类接受、收紧或放宽开发边界；确认重点模块、可疑风险区、自动推进范围、禁止范围、owner 和吸收预算。 | 开发边界说明 accepted；自动代码扫描证据已作为附录保留；未列入 allowed / writable / generated 的变化默认禁止。 |
| 开发边界说明 | 4+1 架构包 | 把已接受边界组织成逻辑、开发、进程、物理和场景视图，并列出决策、冲突和 open issues。 | 生成 4+1 草案、事实引用、候选冲突、视图缺口和决策摘要。 | 架构评审。人类确认模块边界、状态 owner、跨模块契约、部署/进程假设和冲突处理。 | 关键架构事实被接受或明确 deferred；4+1 架构包不维护与开发边界说明冲突的第二套事实。 |
| 4+1 架构包 | 架构展开 / 切片化 | 把粗粒度架构约束展开为可施工对象：契约、状态、路径、harness、probe、测试和风险切片。 | 半自动生成切片候选、依赖顺序、验证计划、法律层计划、changed files gate 和后置投影检查项。 | 计划前审核。人类确认切片粒度、优先级、并行度、风险吸收能力和哪些切片可进入实现。 | 切片足以生成交付计划；4+1 不被直接当成代码任务来源。 |
| 架构展开 / 切片化 | 交付计划 | 固化可施工切片，定义可写路径、代码/harness/schema/contract/probe 输出、DoD 和工具 I/O。 | 生成交付计划草案、任务依赖、验收清单和工具输入输出。 | 计划审核。人类确认 Go / Partial-Go / No-Go。 | H2 Go / Partial-Go；每个非 decision-only 切片至少有一类可验证交付输出。 |
| 交付计划 | 实现证据包 | 在开发边界内实现代码、测试、harness、schema、contract test、probe 或 drift check，并记录证据。 | 编码、测试、生成物落地、命令执行、范围检查、法律层验证、失败整理。 | 例外介入。只有触发升级条件、越界、法律层断裂或发布风险变化时需要人类裁决。 | changed files 在允许范围内；测试和法律层结果有证据；失败和跳过项被记录。 |
| 实现证据包 | 后置投影检查包 | 基于更新后的代码生成接口和结构投影，检查是否符合 H1/H2 预期。 | OpenAPI/Swagger 生成接口文档和 diff；graphify 生成结构图、依赖图、调用关系；AI 汇总 mismatch。 | 后置审核。人类判断接口、字段、依赖方向、跨模块调用和调用方影响是否符合预期。 | 后置投影 finding 被接受、修复、结转或升级；不得把工具输出自动写成基线。 |
| 后置投影检查包 + 实现证据包 | 基线决策记录 | 汇总验证、漂移、例外、发布风险、accepted facts、deferred items 和后续入口。 | 汇总 CI、测试、投影、漂移和风险；生成基线决策记录草案和归档建议。 | 最终裁决。人类接受发布风险，决定 release / no-release / internal-only / archive-only。 | 只把已验证且被接受的事实写入 accepted 基线；deferred 项有 owner 和后续入口。 |

这张矩阵也定义了自动化边界：需求澄清是人类密集环节；自动代码扫描和改动影响分析是半自动环节，并沉淀为开发边界说明的证据附录；编码、测试和投影生成可以自动推进；开发边界说明、4+1 架构包、架构展开 / 切片化结果、交付计划和基线决策记录必须由人类在检查点确认。

| 信息对象 | 允许更新方式 | 不允许的更新方式 |
|---|---|---|
| 原始意图记录 | 追加来源、补充上下文。 | 改写为已接受需求。 |
| 版本需求文档 | H0/H1 经人类确认后更新目标、非目标和约束。 | AI 根据实现便利性自行改变需求。 |
| 自动代码扫描证据 | 1→3 中由工具根据代码扫描、git 信息和文档基线刷新，并挂在开发边界说明附录。 | 升级为独立主流程节点，或把当前代码状态当成目标架构。 |
| 开发边界说明 | H1 或触发升级后由人类确认更新。 | 实现阶段绕过 H1/H2 扩大 allowed 范围。 |
| 4+1 架构包 | H1/H2 根据人类裁决和事实来源更新。 | 维护一套与开发边界说明不一致的事实。 |
| 交付计划 | H2 根据 accepted 4+1 架构包和吸收预算更新。 | 安排开发边界外任务。 |
| 实现证据包 | H2/H3 根据代码、测试、CI 和法律层结果追加。 | 手工伪造验证通过。 |
| 后置投影检查包 | H3 根据更新后的代码生成，并由人类审核。 | H1 阶段提前生成并作为目标架构依据。 |
| 基线决策记录 | H3 根据验证结果和人类风险接受生成。 | 把未验证草案写成 accepted。 |

## 主产物要求与模板

### 0. 原始意图记录

作用：保留人类最初的表达和来源，避免后续整理需求时把语境、限制或提出人丢掉。

最低要求：

- 必须保留原始表述，不把它改写成已接受需求。
- 必须记录来源，例如会议、聊天、PR、issue、截图或外部材料。
- 必须记录提出人和时间，方便后续追问。
- 可以追加上下文，但不能覆盖原始内容。

```yaml
raw_intent_record:
  id:
  created_at:
  requested_by:
  source:
    type: meeting / chat / issue / PR / document / other
    refs: []
  raw_text:
    - 人类最初的原始表述
  known_context:
    - 背景、截图、链接、历史讨论或业务上下文
  known_constraints:
    - 人类一开始已经明确的限制
  initial_questions:
    - AI 需要追问的问题
```

### 1. 版本需求文档

作用：说明本轮为什么做、做什么、不做什么，以及人类已知的硬约束。

最低要求：

- 必须区分目标、非目标、约束和开放问题。
- 必须说明调用方或使用者，否则无法判断契约和场景。
- 必须说明发布门槛，否则无法判断 H3 是否完成。
- 不能直接把某个实现方案写成已接受架构边界。

```yaml
version_requirements_document:
  version_id:
  source_raw_intent_record:
  goal:
    - 本轮要达成的业务或工程目标
  non_goals:
    - 本轮明确不做的事情
  users_or_callers:
    - 外部用户 / 上层应用 / 内部 service / SDK / 测试系统
  scenarios:
    - name:
      happy_path:
      failure_path:
      boundary_cases:
  functional_requirements:
    - 必须提供的功能行为
  non_functional_requirements:
    - 性能 / 可用性 / 隔离 / 观测 / 安全 / 成本 / 兼容性
  must_keep:
    - 不能改变的模块边界、状态 owner、公开契约、兼容性或发布行为
  initial_allowed_scope_hint:
    modules: []
    paths: []
    tests: []
  initial_forbidden_scope_hint:
    modules: []
    contracts: []
    states: []
    dependencies: []
  risk_budget:
    acceptable:
      - 可探索风险
    must_escalate:
      - 必须升级给人类的风险
  release_bar:
    - 必须通过的验证、演示、兼容性或文档条件
  owners:
    architecture_owner:
    module_owners: []
    contract_owner:
  source_materials:
    - 需求文档、会议纪要、历史 ADR、PR、外部规范
  open_questions:
    - 会影响边界、契约、状态、风险或验收的问题
```

### 附录 A. 自动代码扫描证据

作用：在 1→3 流程中说明当前代码实际是什么样；它是开发边界说明的证据附录，不是独立主交付物，也不是架构裁决。

最低要求：

- 只在已有代码或已有契约时必需，并随开发边界说明一起被人类审核。
- 必须标明每个事实来自代码、文档、schema、测试还是工具输出。
- 必须把漂移列出来，而不是自动接受漂移。
- 0→1 流程若没有代码，只能写“无现有代码扫描证据”，不能臆造。

```yaml
auto_code_scan_evidence:
  status: none / reference-only / baseline-generated
  source_refs:
    code:
      - path or package
    docs:
      - architecture or ADR path
    contracts:
      - ICD / schema path
    tests:
      - test or harness path
  code_structure:
    modules:
      - module:
        packages:
        key_classes:
        owner_if_known:
  dependency_report:
    tool: rg / build metadata / import scan / manual scan
    findings:
      - from:
        to:
        evidence:
  contract_report:
    interfaces:
      - name:
        provider:
        callers:
        schema_or_api_ref:
        compatibility_notes:
  state_report:
    states:
      - state:
        owner:
        writers:
        readers:
        evidence:
  test_report:
    existing_tests:
      - path:
        covers:
    gaps:
      - missing happy path / failure path / boundary path
  drift_findings:
    - expected:
      actual:
      evidence:
      severity:
```

### 3. 开发边界说明

作用：定义 AI 可以自动推进的施工边界，是 H1 的核心裁决产物。

最低要求：

- 必须同时包含 allowed、forbidden、verification、escalation。
- 必须使用默认拒绝：未列入 allowed / writable / generated 的变化默认禁止。
- 必须定义第一批代码、harness、schema、contract test、probe 或 adapter skeleton 入口。
- 必须定义关键边界的法律层计划；不能机器检查的，必须指定人工 owner。

```yaml
development_boundary:
  status: draft / accepted
  applies_to_version:
  source_intent:
  flow_type: 0-to-1 / 1-to-3
  accepted_goals: []
  accepted_non_goals: []
  working_assumptions:
    - assumption:
      validation_method:
      stop_when_false:
  allowed:
    modules:
      - module:
        writable_paths:
        purpose:
    contracts:
      - optional fields / compatible additions / draft schemas
    dependencies:
      - allowed dependency or framework
    generated_artifacts:
      - path:
        generator:
        source:
  forbidden:
    modules: []
    paths: []
    contracts:
      - required fields / error semantics / timeout owner / retry owner
    state_changes:
      - owner / writer / lifecycle / persistence ownership changes
    dependencies:
      - forbidden framework / DB / broker / cross-module dependency
  legal_layer_plan:
    - boundary:
      guard_type: ArchUnit / rg-gate / contract-test / schema-check / wire-probe / drift-check / manual-review
      input:
      expected_output:
      liveness_check:
      fail_closed: true
      owner:
  post_implementation_projection_expectations:
    - tool: OpenAPI/Swagger / graphify
      purpose: H3 审核更新后代码是否符合 4+1 架构包和交付计划预期
      expected_input_after_implementation:
        - 更新后的代码、接口、配置或构建图
      expected_review_focus:
        - 人类需要检查的接口、依赖、调用关系或模块边界
      owner:
  first_slices:
    - id:
      goal:
      writable_paths:
      code_output:
      harness_or_probe_output:
      documentation_sync:
      stop_when:
  escalation_conditions:
    - 触发人工裁决的条件
```

### 4. 4+1 架构包

作用：用 4+1 组织被接受的架构内容，让人类判断架构是否成立。4+1 架构包不是另一套事实源。

最低要求：

- 每条关键声明必须引用版本需求文档、开发边界说明、自动代码扫描证据、ADR、ICD、schema 或代码证据。
- 4+1 视图可以按需取舍，但缺失的视图必须说明为什么当前不需要。
- 必须列出冲突、开放问题和需要人类裁决的事项。
- 不能把工具输出直接当成架构裁决。

```yaml
four_plus_one_architecture_package:
  status: draft / reviewed / accepted
  source_development_boundary:
  fact_sources:
    - version_requirements_document
    - development_boundary.auto_code_scan_evidence
    - ADR / ICD / schema / code evidence
  logical_view:
    concepts:
      - name:
        responsibility:
        owner:
    module_boundaries:
      - module:
        responsibility:
        forbidden_responsibility:
  development_view:
    modules:
      - module:
        packages:
        dependencies:
        writable_or_readonly:
  process_view:
    runtime_flows:
      - scenario:
        participants:
        sync_or_async:
        failure_behavior:
  physical_view:
    deployment_units:
      - unit:
        runtime:
        external_dependencies:
        config_or_env:
  scenario_view:
    scenarios:
      - scenario:
        steps:
        validation:
  decisions:
    - id:
      decision:
      alternatives:
      rationale:
  conflicts_and_open_questions:
    - id:
      issue:
      owner:
      blocks_delivery: true / false
```

### 5. 交付计划

作用：把架构展开 / 切片化结果固化为可施工计划。4+1 架构包和开发边界说明是约束来源，不是代码任务来源。

最低要求：

- 每个非 `decision-only` 切片必须包含代码、harness、schema、contract test、probe、drift check 或 adapter skeleton 输出之一。
- 每个切片必须列出可写路径和禁止事项。
- 每个切片必须列出工具输入、工具输出和验证方式。
- 高风险切片必须包含吸收预算。

```yaml
delivery_plan:
  status: draft / accepted
  source_development_boundary:
  source_four_plus_one_architecture_package:
  phase_type: decision-only / probe / code-skeleton / implementation / verification / migration / archive
  slices:
    - id:
      goal:
      risk_level: L1 / L2 / L3
      owner:
      backup_reviewer:
      understanding_depth: behavior-commitment / contract-level / implementation-level
      writable_paths: []
      forbidden_paths: []
      code_outputs:
        - interface / record / adapter / state machine / service / test double
      harness_outputs:
        - unit / contract / integration / wire probe / fake / fixture
      schema_or_contract_outputs:
        - ICD / schema / DTO / fixture
      post_implementation_projection_outputs:
        - H3 需要生成并给人类审核的 OpenAPI/Swagger 文档或 graphify 结构图
      tool_io:
        - tool:
          input:
          output:
          maturity: planned / spec-first draft / post-check-generated / enforcement
      legal_layer_outputs:
        - guard / contract test / schema check / wire probe / drift check
      dod:
        - 可验收条件
      stop_when:
        - 触发停止或升级的条件
  absorption_budget:
    max_parallel_slices:
    stop_when:
      - review 积压、验证断裂、owner 无法吸收
```

### 6. 实现证据包

作用：证明实现确实在开发边界说明内完成，并且验证不是空转。

最低要求：

- 必须包含 changed files，并和 allowed / writable paths 对齐。
- 必须记录执行过的测试和工具命令；未执行必须说明原因。
- 法律层检查必须有 liveness 或 negative case。
- 必须记录实现反馈，尤其是代码暴露的新架构缺口。

```yaml
implementation_evidence_pack:
  source_delivery_plan:
  commit_or_branch:
  changed_files:
    - path:
      allowed_by:
      reason:
  code_outputs:
    - path:
      summary:
  test_outputs:
    - path:
      covers:
  tool_results:
    - tool:
      command_or_invocation:
      input:
      output:
      status: passed / failed / skipped
      evidence_path:
      skipped_reason:
  legal_layer_results:
    - boundary:
      guard_type:
      result:
      liveness_or_negative_case:
  drift_report:
    - expected:
      actual:
      severity:
      decision_needed:
  post_implementation_projections:
    - tool: OpenAPI/Swagger / graphify
      input:
      output:
      human_review_focus:
      matches_expectation: true / false / pending-human-review
  implementation_feedback:
    - finding:
      architecture_impact:
      suggested_action:
      return_to: H1 / H2 / continue
```

### 7. 后置投影检查包

作用：在代码实现后，从更新后的代码生成接口和结构投影，让人类审核实现是否符合 H1/H2 的预期。

最低要求：

- 输入必须是更新后的代码、接口、schema、包结构或构建结果。
- 必须区分工具生成结果和人类审核结论。
- OpenAPI/Swagger 与 graphify 的结果不能自动进入基线，必须经过 H3 裁决。
- 发现不一致时必须进入实现证据包或基线决策记录，不得静默接受。

```yaml
post_projection_check_pack:
  source_implementation_evidence_pack:
  openapi_swagger:
    input:
      - updated code / interface / schema / build result
    output:
      - generated interface document / diff
    human_review_focus:
      - fields / errors / tenant-auth / caller impact / compatibility
    findings: []
  graphify:
    input:
      - updated code / package structure / build dependencies
    output:
      - structure graph / dependency graph / call relation
    human_review_focus:
      - module boundary / dependency direction / cross-module call
    findings: []
  summary:
    matches_h1_h2_expectation: true / false / pending
    required_actions: []
```

### 8. 基线决策记录

作用：决定哪些结果进入 accepted 基线，哪些作为 deferred 或 exception 结转。

最低要求：

- 只能记录已验证、已接受的事实。
- skipped 验证不能被写成通过。
- deferred 项必须有 owner、触发条件和后续入口。
- 发布风险必须有人类接受。

```yaml
baseline_decision_record:
  version_id:
  source_evidence:
    - implementation evidence pack path
  source_post_projection_check_pack:
  verification_summary:
    passed: []
    failed: []
    skipped:
      - item:
        reason:
        risk:
  accepted_facts:
    capabilities: []
    module_boundaries: []
    contracts: []
    state_ownership: []
    code_or_harness_facts: []
  accepted_exceptions:
    - exception:
      owner:
      expiry_or_trigger:
      mitigation:
  deferred_items:
    - item:
      owner:
      follow_up_entry:
      trigger:
  release_risk_decision:
    go_no_go: release / no-release / internal-only / archive-only
    accepted_by:
    reason:
  archive_updates:
    - baseline doc
    - review packet status
    - delivery plan status
```

## 两条流程

A2D 不应该强行把所有任务塞进同一条流水线。至少需要区分两类工作。

| 流程 | 适用场景 | 人类参与密度 | H1 的重点 | H2 的重点 | 工具特点 |
|---|---|---|---|---|---|
| 0→1 新建流程 | 新模块、新服务、新核心机制，代码还不存在或只有很少草稿。 | 高，H0/H1 前后可能多轮讨论。 | 与 AI 密集讨论候选架构，形成可施工约束：开发边界说明和 4+1 架构包。 | 先做架构展开 / 切片化，再进入代码骨架、harness、schema、probe。 | OpenAPI/Swagger 和 graphify 不做前置产出，只在 H3 基于更新后代码生成后置投影，供人类审核。 |
| 1→3 增量流程 | 已有代码和架构基线，要做功能增强、重构、迁移或修复。 | 中，通常从 H0 到 H3 完整走一遍。 | 确认允许修改范围、禁止范围、漂移检查和升级条件。 | 审核交付切片、法律层、修改范围和 H3 后置投影检查项。 | OpenAPI/Swagger 和 graphify 仍以后置检查为主，用更新后的代码生成文档和结构图，让人类确认结果是否符合预期。 |

0→1 流程可以把大量精力放在 H0/H1 的架构讨论中。只要开发边界说明、4+1 架构包、架构展开 / 切片化结果、第一批代码/harness/probe 入口和升级条件已经足够清楚，就不需要为了“从头到尾走完所有文档产物”而拖慢编码。

1→3 流程更像版本级变更治理。因为已有代码、契约和历史基线，AI 应该从一开始就使用工具抽取现状、比较漂移、限制修改范围，并在 H2/H3 形成验证证据。

## 检查点压缩规则

检查点是决策语义，不一定等于四次独立会议。

| 流程 | 可以压缩什么 | 不能省略什么 |
|---|---|---|
| 0→1 | H0/H1 可以是一段密集人机架构讨论；H1/H2 可以在同一次评审中完成，只要人类已经接受 4+1 架构包和第一批架构展开 / 切片化结果。 | 版本需求文档、开发边界说明、4+1 架构包、架构展开 / 切片化结果、第一批代码/harness/probe 入口、升级条件。 |
| 1→3 | 通常不建议压缩。已有代码意味着必须自动抽取现状、对比漂移、确认修改范围，再进入实现。 | 开发边界说明、自动代码扫描证据、修改范围白名单、漂移检查、法律层证据。 |

0→1 的最小可施工包可以是：

```text
版本需求文档
 -> 4+1 架构包草案
 -> 开发边界说明
 -> 架构展开 / 切片化
 -> 第一批代码/harness/probe 切片
 -> H2 Go 决策
 -> H2/H3 编码、验证、回灌
```

1→3 的最小可施工包通常是：

```text
版本需求文档
 -> 现有代码/契约/依赖 baseline
 -> 开发边界说明
 -> 4+1 变更投影
 -> 交付计划
 -> H2 Go 决策
 -> 编码、验证、漂移检查、回灌
```

## 工具产物成熟度

同一个工具在不同流程、不同阶段的产物成熟度不同。文档必须明确当前是哪一种。

| 状态 | 含义 | 可作为架构事实吗 | 例子 |
|---|---|---|---|
| reference-only | 只作为参考材料，不代表当前系统事实。 | 否。 | 0→1 中参考相邻模块命名习惯。 |
| planned | 只定义后续会如何使用工具。 | 否。 | H1 说明 H3 需要生成并审核哪些后置投影。 |
| spec-first draft | 基于已接受 ICD/schema/4+1 生成草案。 | 只能作为待验证草案。 | 0→1 中从 human-readable ICD 派生 schema 草案。 |
| baseline-generated | 从现有代码或已接受契约抽取现状。 | 可以作为开发边界说明的自动证据附录，但不能反向裁决架构。 | 1→3 中通过代码搜索和构建元数据整理当前模块关系。 |
| post-check-generated | 从更新后的代码生成后置投影。 | 否，必须经 H3 人类审核后才能进入基线。 | H3 中生成 OpenAPI 文档和 graphify 结构图。 |
| accepted-by-human | 已经被人类在 H1/H2 裁决接受。 | 是。 | H2 接受的 4+1 架构包条目。 |
| implementation | 代码、测试或生成物已经在允许范围内落地。 | 需要验证后才能进入基线。 | H2/H3 中实现出的 adapter skeleton。 |
| enforcement | 已进入 CI、ArchUnit、contract test、schema check、wire probe 或 drift check。 | 可以作为法律层证据。 | contract test 在 CI 中失败即阻塞合并。 |

H1 阶段通常只能产生 `planned` 或 `spec-first draft`。OpenAPI/Swagger 和 graphify 的实际输出属于 `post-check-generated`，只能在 H2 实现后、H3 审核前生成。`enforcement` 通常在 H2 后的实现和验证阶段形成。

## 流程工具链 I/O

### 0→1 新建流程工具链

0→1 的工具重点是帮助人类形成目标架构、契约草案和第一批可验证切片。没有更新后的代码时，OpenAPI/Swagger 和 graphify 不产生有效验收投影。

| 阶段 | 工具或方法 | 输入 | 输出 | 成熟度 | 进入哪个产物 |
|---|---|---|---|---|---|
| H0/H1 | 人机架构讨论 | 版本需求文档、约束、参考材料、相邻系统经验 | 候选架构、关键取舍、开放问题、裁决记录 | planned | 开发边界说明、4+1 架构包 |
| H0/H1 | 文档索引读取 | `00-overview`、ADR、模块责任卡、状态矩阵、ICD、治理规则 | 可继承原则、禁止边界、术语和历史决策 | baseline-generated 或 reference-only | 版本需求文档、开发边界说明、4+1 架构包 |
| H0/H1 | 代码搜索 / `rg` | 相邻模块、历史实现、测试样例 | 命名习惯、可复用模式、冲突点 | reference-only | 4+1 架构包附录、交付计划 |
| H1 | 4+1 建模 | 版本需求文档、候选架构、人工裁决、参考事实 | 逻辑/开发/进程/物理/场景视图 | planned 或 accepted-by-human | 4+1 架构包 |
| H1 | ICD / schema 草案编写 | 场景视图、模块边界、调用方/提供方责任 | human-readable ICD、machine-readable schema draft | spec-first draft | 4+1 架构包、交付计划 |
| H1/H2 | harness / probe 设计 | 场景、ICD、状态 owner、不变量 | fake、fixture、contract test、wire probe 方案 | planned | 交付计划 |
| H2/H3 | 编码工具 | 交付计划、开发边界说明、schema/ICD 草案 | 代码骨架、接口、record、adapter、测试替身 | implementation | 实现证据包 |
| H2/H3 | 测试与法律层工具 | 代码、schema、契约、guard 规则 | 测试结果、contract/schema/wire probe/ArchUnit 证据 | enforcement | 实现证据包、基线决策记录 |
| H3 | OpenAPI/Swagger 后置投影 | 更新后的代码、接口、schema、构建结果 | 更新后的接口文档、接口差异和人类审核材料 | post-check-generated | 后置投影检查包、基线决策记录 |
| H3 | graphify 后置投影 | 更新后的代码、包结构、构建依赖 | 更新后的结构图、依赖图、调用关系和人类审核材料 | post-check-generated | 后置投影检查包、基线决策记录 |

0→1 的关键限制：

- graphify 和 OpenAPI/Swagger 在 H1 没有更新后代码，不能提供有效验收结论。
- 如果 H1 想约束后置检查，只能写 H3 需要审核哪些投影结果。
- 代码生成物进入实现前必须被开发边界说明或交付计划允许。

### 1→3 增量流程工具链

1→3 的工具重点是抽取基本现状、限制修改范围、生成法律层证据，并在 H3 通过 OpenAPI/Swagger 和 graphify 做后置投影检查。

| 阶段 | 工具或方法 | 输入 | 输出 | 成熟度 | 进入哪个产物 |
|---|---|---|---|---|---|
| H0/H1 | `git diff` / `git status` | 当前工作区、目标分支 | 已有改动、未提交文件、修改范围风险 | baseline-generated | 开发边界说明的自动代码扫描证据 |
| H0/H1 | `git log` / PR 历史 | 近期提交、相关 PR、ADR 变更 | 历史意图、回归风险、迁移线索 | baseline-generated | 开发边界说明 |
| H0/H1 | 代码搜索 / `rg` | 需求关键词、接口名、状态名、包名 | 受影响代码、测试、配置、命名冲突 | baseline-generated | 开发边界说明的自动代码扫描证据 |
| H0/H1 | 构建文件和包结构扫描 | pom、module、package、import、配置文件 | 受影响依赖和模块候选；不调用 graphify | baseline-generated | 开发边界说明 |
| H0/H1 | schema/contract 读取 | 现有 schema、DTO、record、fixture、ICD | 契约影响候选和兼容性风险 | baseline-generated | 开发边界说明、交付计划 |
| H1/H2 | 4+1 变更投影 | 版本需求文档、开发边界说明、自动代码扫描证据 | 本轮对逻辑/开发/进程/物理/场景视图的影响 | accepted-by-human | 4+1 架构包 |
| H1/H2 | 交付切片生成 | 开发边界说明、4+1 架构包、自动代码扫描证据 | 可写路径、代码输出、测试输出、DoD、工具 I/O | planned | 交付计划 |
| H2/H3 | 编码工具 | 交付计划、allowed paths、契约/schema | 代码变更、测试变更、生成物 | implementation | 实现证据包 |
| H2/H3 | ArchUnit / `rg` gate | 禁止依赖规则、源码 | 边界检查结果、liveness 证据 | enforcement | 实现证据包 |
| H2/H3 | contract test | ICD/schema、调用方 fixture、提供方实现 | 契约兼容结果、失败证据 | enforcement | 实现证据包 |
| H2/H3 | wire-level probe | 部署或测试拓扑、接口、tenant/auth fixture | 真实或近真实交互证据、失败路径证据 | enforcement | 实现证据包 |
| H2/H3 | drift check | 开发边界说明、自动扫描 baseline、实现后代码 | 漂移报告、越界修改、生成物不一致 | enforcement | 实现证据包、基线决策记录 |
| H3 | OpenAPI/Swagger 后置投影 | 更新后的代码、接口、schema、构建结果 | 更新后的接口文档、接口差异和人类审核材料 | post-check-generated | 后置投影检查包、基线决策记录 |
| H3 | graphify 后置投影 | 更新后的代码、包结构、构建依赖 | 更新后的结构图、依赖图、调用关系和人类审核材料 | post-check-generated | 后置投影检查包、基线决策记录 |
| H3 | CI / 发布检查 | 全部测试、gate、构建、release bar | 验证汇总、发布风险、跳过项 | enforcement | 基线决策记录 |

1→3 的关键限制：

- H0/H1 的扫描只用于识别影响候选，不作为 OpenAPI/graphify 的验收投影。
- baseline-generated 输出只能帮助人类裁决，不能自动改写架构边界。
- OpenAPI/Swagger 和 graphify 的正式审核发生在 H3，输入必须是更新后的代码。
- drift check 发现漂移后必须由 H2/H3 决定接受、修复、回滚或结转。

## 完整展开流程

下面按信息链列出每一步的人类输入、工具输入、工具输出和产物去向。H0/H1/H2/H3 仍是裁决点，但不再作为主流程的组织轴。这里的“工具”可以是 AI，也可以是代码搜索、测试、CI、OpenAPI/Swagger、graphify 等自动化工具。

| 步骤 | 人类输入 | 使用工具 | 工具输入 | 工具输出 | 进入哪个标准对象 |
|---|---|---|---|---|---|
| 1. 原始意图注入 | 一句话需求、会议结论、问题背景、业务目标、已知限制。 | AI 需求访谈 | 人类原始描述、已有任务记录、参考材料。 | 澄清问题、候选需求分类、需要人类确认的歧义点。 | 原始意图记录 |
| 2. 标准需求整理 | 回答 AI 的澄清问题，确认目标、非目标、调用方和发布门槛。 | AI 文档生成 | 原始意图记录、澄清回答、版本需求文档模板。 | 标准化需求：目标、非目标、场景、功能/非功能需求、must keep、风险预算、release bar。 | 版本需求文档 |
| 3. 开发边界说明 | 人类确认允许范围、禁止范围、风险底线和停下条件；1→3 中可指定重点模块、最近 PR、可疑区域。 | AI 影响分析 + 自动代码扫描 + 人类裁决 | 版本需求文档、当前代码、目标分支、变更 diff、ADR、模块责任卡、ICD、状态矩阵、治理规则、参考架构。 | allowed、forbidden、writable paths、状态/契约/依赖策略、自动代码扫描证据、第一批切片候选、升级条件、后置投影期望。 | 开发边界说明 |
| 4. 4+1 架构包 | 人类确认必须覆盖的逻辑、开发、进程、物理、场景视图。 | AI 架构建模 | 已接受开发边界说明、版本需求文档、自动代码扫描证据、ADR/ICD/schema。 | 粗粒度架构约束、4+1 视图、决策、冲突、open issues。 | 4+1 架构包 |
| 5. 架构展开 / 切片化 | 人类审核切片粒度、优先级、并行限制和吸收预算。 | AI 切片生成 | 4+1 架构包、开发边界说明、场景视图、模块边界、状态 owner、不变量。 | 契约、状态、路径、harness、probe、测试、风险切片和验证计划候选。 | 交付计划草案 |
| 6. 交付计划 | 人类确认 Go / Partial-Go / No-Go。 | AI 计划固化 | 切片化结果、开发边界说明、契约/harness 草案、吸收预算。 | 可写路径、代码输出、harness/schema/contract/probe 输出、DoD、工具 I/O、后置投影检查项。 | 交付计划 |
| 7. 实现证据包 | 人类通常只在触发升级条件时裁决。 | AI 编码、测试、法律层验证、CI | accepted 交付计划、开发边界说明、schema/ICD 草案、可写路径。 | 代码 diff、测试结果、adapter skeleton、fixture、schema/codegen 生成物、changed files 检查和漂移报告。 | 实现证据包 |
| 8. 后置投影检查包 | 人类审核接口、字段、依赖方向和跨模块调用是否符合预期。 | OpenAPI/Swagger、graphify | 更新后的代码、接口、schema、构建结果、包结构、依赖关系。 | OpenAPI 接口文档、接口差异、graphify 结构图/依赖图/调用关系、人类审核 finding。 | 后置投影检查包 |
| 9. 基线决策记录 | 人类接受发布风险，决定 accepted、deferred、exception。 | AI 风险汇总 + CI 汇总 + 文档归档 | 实现证据包、后置投影检查包、CI 结果、release bar、deferred 清单。 | release / no-release / internal-only / archive-only；accepted facts、exceptions、deferred items；baseline 文档。 | 基线决策记录 |

### 0→1 与 1→3 的展开差异

| 步骤 | 0→1 新建流程 | 1→3 增量流程 |
|---|---|---|
| 自动代码扫描 | 通常没有现有代码，只能读取相邻模块作为 reference-only，并作为开发边界说明的参考附录。 | 必须自动扫描已有接口、状态、依赖、测试和修改范围风险，并作为开发边界说明的证据附录。 |
| 架构讨论 | H0/H1 可以多轮密集讨论，重点是目标 4+1 和第一批可施工切片。 | 架构讨论应围绕现有基线的变更影响，不应重新发明系统。 |
| OpenAPI/graphify | 不做前置产出；H3 基于新写出的代码生成后置投影。 | 不做 H1 前置验收；H3 基于更新后的代码生成后置投影，与变更预期比较。 |
| H2 审核 | 可以和 H1 靠得很近，但必须已有开发边界说明、4+1 架构包和第一批交付计划。 | 不建议压缩，必须确认修改范围、法律层、后置投影检查项和吸收预算。 |
| H3 审核 | 重点看新代码是否符合目标架构，以及后置投影是否暴露架构偏差。 | 重点看变更是否越界、是否破坏既有契约、后置投影是否符合增量预期。 |

## 阶段总览

| 检查点 | 人类输入重点 | AI 下一阶段动作 | 关键工具 | 进入下一检查点的产物 |
|---|---|---|---|---|
| H0 | 版本需求文档和硬约束 | 归一化需求、分析影响、生成开发边界说明草案 | 文档索引、代码搜索、基础依赖扫描、变更分级规则 | 版本需求文档、开发边界说明草案（含自动代码扫描证据） |
| H1 | 自动推进边界、后置检查期望和升级条件 | 生成 4+1 架构包和交付计划 | 4+1 组织、schema/ICD 草案、harness 设计、后置投影检查期望 | 4+1 架构包、交付计划、法律层计划 |
| H2 | 4+1 架构包和交付计划裁决 | 在开发边界内实现、测试、生成证据、回灌事实 | 编码工具、代码生成、契约测试、ArchUnit、wire probe、CI、漂移检查 | 实现证据包、实现反馈 |
| H3 | 例外、发布风险、后置投影和基线确认 | 归档 accepted 事实，关闭或结转问题 | CI 汇总、OpenAPI/Swagger 后置投影、graphify 后置投影、发布检查、基线归档 | 基线说明、归档记录、后续入口 |

## H0：注入版本需求文档

### 人类必须输入什么

H0 的输入必须让 AI 判断“要做什么、不能动什么、做到什么程度算完成”。

```yaml
version_id: 本次工作或版本的稳定 ID
version_goal:
  - 本版本要达成的业务或工程目标
users_or_callers:
  - 谁会使用这个能力；是外部用户、上层应用、内部 service、SDK 还是测试系统
business_or_technical_scenarios:
  - 关键场景；最好包含 happy path、failure path、边界条件
functional_requirements:
  - 必须提供的功能行为
non_functional_requirements:
  - 性能、可用性、隔离、观测、安全、成本、兼容性等要求
must_keep:
  - 不能改变的模块边界、状态 owner、writer、公开契约、兼容性、发布行为
allowed_change_scope_hint:
  modules:
    - 人类预期允许修改的模块
  paths:
    - 人类预期允许修改的文件或目录
  tests:
    - 人类预期可以新增或修改的测试区域
forbidden_scope_hint:
  modules:
    - 明确禁止触碰的模块
  contracts:
    - 禁止改变的契约语义，例如 required 字段、error code、retry owner
  state:
    - 禁止新增、迁移或改 owner 的状态
known_constraints:
  - 已知技术约束、组织约束、历史包袱、迁移窗口
risk_budget:
  - 哪些风险允许探索，哪些风险必须升级给人类
release_bar:
  - 进入完成态必须通过的验证、测试、演示、兼容性要求
human_owners:
  architecture_owner: 架构裁决人
  module_owners:
    - 模块负责人
  contract_owner:
    - 契约或法律层 owner
source_materials:
  - 需求文档、会议纪要、设计草稿、相关 PR、历史 ADR、外部规范链接
```

### 不够格的 H0 输入

- “实现 agent-bus”。
- “参考一下现有代码，补文档和代码”。
- “按最佳实践设计”。
- “先写个大概，后面再看”。

这些输入缺少边界、验收口径和风险预算，AI 只能猜。

### AI 进入 H1 前使用什么工具

| 工具或方法 | 用途 | 产出 |
|---|---|---|
| `rg` / 代码搜索 | 找现有模块、接口、状态、测试、命名冲突 | 影响候选列表 |
| `git diff` / `git log` | 判断当前分支已有改动、历史设计意图和近期提交方向 | 变更上下文 |
| 文档索引读取 | 读取 `00-overview` 到 `10-governance` 中相关基线 | 基线事实引用 |
| 变更分级规则 | 判断本次是 Level 0/1/2/3 还是需要升级 | 变更级别 |
| 基础依赖扫描 | 通过构建文件、包结构、import 和代码搜索识别受影响模块；不在 H1 调用 graphify | 影响候选列表 |
| 契约和状态矩阵读取 | 查找受影响 ICD、schema、state owner、writer | 契约/状态影响 |

### H0 到 H1 的产物

| 产物 | 最低要求 |
|---|---|
| 版本需求文档 | 目标、非目标、约束、风险预算、发布门槛、开放问题；需求归一化结果作为其中一节 |
| 开发边界说明草案 | allowed、forbidden、first slices、verification、escalation；1→3 必须包含自动代码扫描证据附录 |

### H1 进入条件

- 人类能看懂本版本要解决什么问题。
- AI 已列出影响面和未知点。
- 开发边界说明草案已经能表达“哪些地方允许自动推进，哪些地方禁止”。

## H1：确认开发边界说明

### 人类必须确认什么

H1 的本质是给 AI 一张可施工的许可证。确认后，AI 在开发边界内不再逐条请求审批。

```yaml
accepted_goals:
  - 本版本接受的目标
accepted_non_goals:
  - 本版本明确不做的事
module_boundaries:
  writable:
    - module:
      paths:
      purpose:
  read_only:
    - module:
      reason:
  forbidden:
    - module:
      reason:
state_policy:
  unchanged_owners:
    - state:
      owner:
      writer:
  allowed_new_state:
    - state:
      owner:
      writer:
  forbidden_state_changes:
    - 禁止改变 owner、writer、生命周期或持久化归属的状态
contract_policy:
  allowed:
    - optional field、兼容 schema、测试 fixture、mock/stub
  forbidden:
    - required field、error code 语义、timeout owner、retry owner、兼容性破坏
dependency_policy:
  allowed:
    - 允许使用的现有依赖或新增依赖
  forbidden:
    - 禁止新增的 framework、DB、broker、跨模块依赖
legal_layer_policy:
  required_guards:
    - boundary:
      guard_type: ArchUnit / contract-test / wire-probe / schema-check / drift-check / CI-gate / manual-review
      guard_location:
      liveness_check:
      fail_closed: true
post_implementation_projection_review:
  openapi_swagger:
    purpose:
      - H3 从更新后的代码生成接口文档，供人类审核接口是否符合 4+1 架构包和交付计划预期
    expected_review_focus:
      - 新增/修改接口、字段兼容性、错误码、tenant/auth 参数、调用方影响
    forbidden_usage:
      - H1 阶段凭空生成或审核更新后接口文档
      - 用生成结果反向改变已确认契约语义
  graphify:
    purpose:
      - H3 从更新后的代码生成结构图、依赖图或调用关系，供人类审核模块边界和依赖方向是否符合预期
    expected_review_focus:
      - 新增依赖、跨模块调用、包边界、runtime 与 bus/gateway 关系
    forbidden_usage:
      - H1 阶段生成正式结构图作为目标架构依据
      - 用生成图反向决定模块边界或 owner
schema_codegen_policy:
  allowed_usage:
    - 0→1：从已接受契约生成 draft DTO、record、fixture、schema test
    - 1→3：从现有 schema 或代码生成 diff、fixture、schema test
first_implementation_slices:
  - id:
    goal:
    writable_paths:
    code_outputs:
    harness_outputs:
    legal_layer_outputs:
    stop_when:
risk_and_absorption_policy:
  risk_level: L1 / L2 / L3
  human_owner:
  backup_reviewer:
  max_parallel_slices:
  stop_when:
escalation_conditions:
  - 需要修改 forbidden 模块或路径
  - 需要改变状态 owner、writer 或跨模块控制权
  - 需要破坏兼容契约
  - 法律层无法覆盖关键失败路径
  - 实现证明 H1 假设不成立
```

### H1 必须避免什么

- 只确认“方向没问题”，没有可写路径。
- 只列 forbidden 黑名单，没有 allowed 白名单。
- 只说“需要测试”，没有指定 guard、contract test、wire probe 或 drift check。
- 只说“后续完善架构”，没有第一批代码、harness、schema 或 probe 入口。
- 允许使用工具，但没有限制工具的用途和生成物落点。

### AI 进入 H2 前使用什么工具

| 工具或方法 | 用途 | 产出 |
|---|---|---|
| 4+1 投影 | 把已接受事实投影成逻辑、开发、进程、物理、场景视图 | 评审包 |
| schema / contract 草案 | 从已接受 4+1 架构包和场景生成 schema、DTO、fixture 或 contract test 输入 | spec-first draft |
| harness 设计 | 明确测试替身、fixture、失败路径、wire-level probe 方案 | harness 计划 |
| 文档生成和引用检查 | 更新能力、模块、状态、契约、不变量、验证矩阵 | 4+1 架构包内容 |
| 交付切片生成 | 把开发边界拆成可施工任务 | delivery projection |

### H1 到 H2 的产物

| 产物 | 最低要求 |
|---|---|
| 4+1 架构包 | 用 4+1 组织本版本需要稳定的模块、状态、契约、场景、不变量和部署约束；每条关键声明必须引用事实源，不另建事实源 |
| 交付计划 | 阶段类型、开发切片、代码输出、harness/schema/contract/probe 输出、DoD、工具计划、法律层计划和吸收预算 |

### H2 进入条件

- 人类能判断架构方案是否允许进入实现。
- 每个非裁决切片都有代码、harness、schema、contract test、probe 或漂移检查输出。
- 每个关键 forbidden / invariant 都有机器检查或人工 owner。

## H2：集中审核 4+1 架构包和交付计划

### 人类必须输入什么

H2 不是审美式文档评审，而是决定“是否允许 AI 开始施工”。

```yaml
architecture_review_decision:
  accepted:
    - 可以进入实现的 4+1 架构包条目
  rejected:
    - 不允许进入实现的设计或任务
  conditional:
    - 必须满足条件后才能实现的事项
findings:
  - id:
    severity: blocker / high / medium / low
    evidence:
    required_change:
    blocks_implementation: true / false
delivery_slice_decisions:
  - slice_id:
    decision: approve / revise / split / reject / decision-only
    reason:
legal_layer_decisions:
  - boundary:
    guard_decision: accept / add / strengthen / manual-only
    required_liveness_check:
tool_decisions:
  - tool:
    decision: allow / restrict / forbid
    allowed_inputs:
    allowed_outputs:
    writable_paths:
risk_decisions:
  - risk:
    accept / defer / escalate / remove_from_version
absorption_decisions:
  max_parallel_slices:
  human_owner:
  backup_reviewer:
  stop_when:
go_no_go:
  implementation: go / no-go / partial-go
```

### H2 必须避免什么

- 只说“架构再完善一下”，没有 finding 编号和阻塞原因。
- 同意进入实现，但没有批准具体切片。
- 同意进入实现，但没有规定 H3 需要审核哪些 OpenAPI/graphify 后置投影。
- 接受法律层计划，但没有 liveness / negative case。
- 允许多切片并行，但没有人类吸收预算。

### AI 进入 H3 前使用什么工具

| 工具或方法 | 用途 | 产出 |
|---|---|---|
| 编码工具 | 在批准的 writable paths 内实现代码、测试、adapter skeleton | 代码变更 |
| schema/codegen | 生成 DTO、record、fixture、schema 校验 | 契约生成物 |
| 单元测试 / 集成测试 | 验证局部行为和跨模块行为 | 测试结果 |
| contract test | 验证模块间契约兼容性 | 契约证据 |
| ArchUnit / 依赖 gate | 验证包依赖、模块边界、禁止 import | 边界证据 |
| wire-level probe | 验证真实或近真实跨 service 调用、tenant 传递、失败路径 | 交互证据 |
| drift check | 比较实现结构与开发边界说明、ICD、schema、依赖图是否漂移 | 漂移报告 |
| `git diff` / changed files check | 验证修改范围是否仍在 H2 批准范围内 | 范围证据 |
| CI | 汇总测试、gate、静态检查和构建结果 | 验证汇总 |
| OpenAPI/Swagger 后置投影 | 从更新后的代码生成接口文档和接口差异，给人类审核是否符合预期 | 后置接口文档 |
| graphify 后置投影 | 从更新后的代码生成结构图、依赖图或调用关系，给人类审核是否符合预期 | 后置结构图 |

### H2 到 H3 的产物

| 产物 | 最低要求 |
|---|---|
| 实现证据包 | 代码 diff、测试 diff、guard、contract test、wire probe、schema check、drift check、changed files 检查和命令结果 |
| 实现反馈 | 代码或测试暴露的新事实、影响位置、处理建议、是否需要回到 H1/H2 |

### H3 进入条件

- 已有可审查的代码/测试/证据，而不只是文档。
- 所有执行过的工具都有结果记录；未执行的工具必须说明原因。
- 任何越界、漂移、法律层缺口都已经升级或写入例外。

## H3：审核例外和发布风险

### 人类必须输入什么

H3 决定本版本是否可以接受、归档或发布。

```yaml
verification_review:
  passed:
    - 已通过验证
  failed:
    - 失败验证和处理结论
  skipped:
    - 未执行验证、原因、风险
post_implementation_projection_review:
  openapi_swagger:
    output:
    human_review_result: match / mismatch / needs-follow-up
    findings:
      - 接口、字段、错误码、tenant/auth 或调用方影响是否符合预期
  graphify:
    output:
    human_review_result: match / mismatch / needs-follow-up
    findings:
      - 模块边界、依赖方向、调用关系是否符合预期
drift_review:
  accepted_drift:
    - 允许保留的漂移，必须有 owner 和后续入口
  rejected_drift:
    - 必须修复或回滚的漂移
exception_decisions:
  - exception:
    decision: accept / fix_now / defer / remove_from_version
    owner:
    expiry_or_trigger:
release_risk_decision:
  go_no_go: release / no-release / internal-only / archive-only
baseline_decision_record:
  accepted_facts:
    - 可写入基线的能力、模块边界、契约、状态、代码/harness 事实
  deferred_facts:
    - 不能写成 accepted 的草案或待验证事实
follow_up_entries:
  - 后续版本、issue、ADR、迁移计划或重构入口
```

### AI 归档前使用什么工具

| 工具或方法 | 用途 | 产出 |
|---|---|---|
| CI 汇总 | 汇总构建、测试、gate、contract、drift 结果 | 验证汇总 |
| OpenAPI/Swagger 后置投影 | 从更新后的代码生成接口文档，供人类审核接口是否符合预期 | 接口文档和审核 finding |
| graphify 后置投影 | 从更新后的代码生成结构图、依赖图或调用关系，供人类审核结构是否符合预期 | 结构图、依赖图和审核 finding |
| 发布检查 | 检查 release bar、兼容性、风险接受记录 | 发布风险说明 |
| 文档状态更新 | 将 accepted / superseded / deferred 状态写回文档 | 状态更新 |
| 基线归档 | 生成 `baselines/<version>.md` | 基线说明 |
| 问题追踪 | 将 deferred、exception、follow-up 转入后续入口 | 后续清单 |

### H3 到归档的产物

| 产物 | 最低要求 |
|---|---|
| 基线决策记录 | 验证汇总、发布风险、accepted facts、deferred 项、owner、后续入口；只记录 accepted 事实，不把草案写成基线 |

## 工具使用边界

| 工具 | 可以做什么 | 不可以做什么 |
|---|---|---|
| graphify / 依赖图 | H3 从更新后的代码生成结构图、依赖图或调用关系，供人类后置审核 | H1 阶段生成正式目标架构图；反向决定模块边界或 owner |
| OpenAPI/Swagger | H3 从更新后的代码生成接口文档，供人类后置审核 | H1 阶段凭空生成更新后接口文档；自行改变契约语义 |
| schema/codegen | 从已确认契约、schema 或已有代码生成 DTO、record、fixture、schema test | 绕过 H1/H2 增加 required 字段或兼容性破坏 |
| ArchUnit / `rg` gate | 检查禁止依赖、包边界、import 纯度 | 替代架构裁决 |
| contract test | 验证调用方和提供方的契约兼容 | 证明业务语义完整性 |
| wire-level probe | 验证真实交互、tenant 传递、失败路径 | 替代完整压测或生产验收 |
| drift check | 对比代码、契约、文档是否偏离开发边界 | 自动接受漂移 |
| CI | 聚合自动检查结果 | 替代 H3 风险接受 |

## 一个具体例子：agent-bus 第一阶段

```yaml
H0_input:
  version_goal:
    - 定义 agent-bus 的 gateway 转发能力和 service-to-service bus 能力的最小可施工边界
  users_or_callers:
    - agent-runtime
    - 上层业务应用
    - 内部 service
  functional_requirements:
    - gateway 接收外部请求并转发到内部 bus
    - bus 支持 service-to-service 调用
    - agent 注册和发现能力进入设计范围
  must_keep:
    - 当前已存在 ingress、s2c、federation、engine 包命名
    - W2 workflow primitives 暂时只保留设计态
  forbidden_scope_hint:
    - 不在本阶段决定 mailbox、admission、backpressure、tick 语义
  release_bar:
    - 至少形成可验证的 envelope、S2C envelope tenantId 冲突记录、第一批 harness/probe 计划

H1_envelope_decision:
  writable:
    - docs/architecture/L1/agent-bus/**
    - agent-bus 中已批准的接口/record/test skeleton 路径
  forbidden:
    - 未确认的生产 broker 引入
    - 改变 runtime 对 bus 的调用语义
  legal_layer:
    - S2C envelope 必须包含 tenantId 的 contract/schema check
    - 禁止 runtime 直接依赖 bus 内部实现的 ArchUnit 或 import gate
  post_implementation_projection_review:
    graphify:
      - H3 从更新后的代码生成 bus/runtime 结构和依赖投影，审核是否符合 gateway/bus 边界预期
    openapi_swagger:
      - H3 从更新后的接口代码生成接口文档，审核 gateway ingress 和 service-to-service 契约是否符合预期
    schema:
      - 生成 S2C envelope schema test
    wire_probe:
      - 验证 gateway -> bus -> service 的最小调用链

H2_delivery_decision:
  approved_slices:
    - S1: S2C envelope record + tenantId schema/contract test
    - S2: gateway ingress adapter skeleton + routing probe
    - S3: service registry/discovery port + fake registry harness
  stop_when:
    - 需要选择真实 MQ、DB、broker 或 backpressure 语义
    - runtime 需要修改调用语义
```

这个例子的重点是：第一阶段不是“继续完善 agent-bus 架构文档”，而是把 gateway、bus、registry/discovery、tenantId、runtime 边界变成可验证切片。
