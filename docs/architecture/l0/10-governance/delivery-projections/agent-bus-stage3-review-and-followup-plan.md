---
artifact_type: a2d_delivery_projection
version: "agent-bus-stage3-review-and-followup-plan"
status: draft
source_commit: "a23b5076 docs(agent-bus): Stage 3 注册发现 ICD + 设计级 harness；Stage 2 漂移修正"
source_stage3_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage1-followups-review-and-stage3-plan.md"
source_l0_commit: "544391d8 Refine L0 logical module boundaries"
target_module: agent-bus
---

# agent-bus 第三阶段评审与后续收口计划

## 1. 评审结论

最新提交 `a23b5076` 可以带修正接受。

本次提交完成了第三阶段主体目标：

| 目标 | 完成情况 | 评审意见 |
|---|---|---|
| 注册发现 ICD | 已完成 | 新增 `ICD-Agent-Registry-Discovery`，覆盖 registry key、tenant 隔离、route handle、health、version、lease、失败模式和第三阶段禁止范围。 |
| L1 视图同步 | 部分完成 | README、logical、process、physical、development、features、scenarios 都有更新，但仍有旧事实残留。 |
| 设计级验证 | 已完成 | 新增 `AgentBusRegistryDiscoveryDesignContractTest`，以文档断言和 ArchUnit trip-wire 保护第三阶段不越界实现运行态注册表。 |
| 第一阶段后续修正 | 基本完成 | `AgentBusModuleMetadataDriftTest` 已读取 `module-metadata.yaml`，`IngressResponseTest` 方法名已去掉 pending 语义。 |
| 第二阶段漂移修正 | 部分完成 | `s2c-callback.v1.yaml`、README、development、features、logical 已修，但 `ARCHITECTURE.md`、scenarios、physical 仍有漂移。 |

结论：第三阶段方向正确，没有发现 `agent-bus` 抢占 Task lifecycle、实现 runtime registry、引入 broker/MQ 绑定或跨 tenant fallback 的实质越界。但文档一致性还没有达到可以交给后续自动化继续扩展的状态。

## 2. 当前修改意见

### MI3-001：L1/ICD 没有完全跟随 L0 新逻辑模块命名

L0 最新逻辑模块已经收敛为：

| L0 逻辑模块 | 当前实现/兼容落点 |
|---|---|
| `agent-runtime` | `agent-service/`、历史 `agent-service` 文档和 Maven artifact |
| `agent-core` | `agent-execution-engine/`、历史 engine 文档和 Maven artifact |

当前第三阶段文档仍大量直接使用旧逻辑名：

- `docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md`
- `architecture/docs/L1/agent-bus/ARCHITECTURE.md`
- `architecture/docs/L1/agent-bus/logical.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/physical.md`
- `architecture/docs/L1/agent-bus/scenarios.md`
- `architecture/docs/L1/agent-bus/spi-appendix.md`

处理原则：

- 架构语义使用 `agent-runtime` / `agent-core`。
- 当前代码事实、Maven artifact、module metadata 和 forbidden dependencies 仍可保留 `agent-service` / `agent-execution-engine`。
- 第一次出现时写清映射，例如：`agent-runtime`（当前实现/兼容落点：`agent-service`）。
- 不要把 L0 逻辑命名升级误写成当前仓库目录已经重命名。

### MI3-002：S2C tenant 漂移修正未完整覆盖 L1 文档集

仍存在过期事实：

- `architecture/docs/L1/agent-bus/ARCHITECTURE.md` 仍写 `S2cCallbackEnvelope` 没有 `tenantId`，后续才增加。
- `architecture/docs/L1/agent-bus/scenarios.md` 的 SC-002 仍写 envelope 需要增加 `tenantId`，且迁移前要通知冲突方。
- `architecture/docs/L1/agent-bus/process.md` 的进程断言仍写 “S2C tenant 目标态必须进入 envelope”，语气像未完成目标态。
- `architecture/docs/L1/agent-bus/physical.md` 仍写该变更“必须分离为迁移切片”，需要改成“契约层已迁移，runtime 绑定待后续波次”。

处理原则：

- 统一表述为：S2C `tenantId` 契约层已迁移完成；runtime-side construction binding / schema validation integration 仍待后续波次。
- 删除“迁移前通知冲突方”“代码未实现”“后续才增加”等已经过期的说法。
- 保留兼容姿态：这是 pre-GA 内部契约 breaking change，当前不升 v1.1/v2。

### MI3-003：注册发现物理视图有局部自相矛盾

`physical.md` 一方面说注册发现未选择租户隔离，一方面又说第三阶段已回答 tenant 隔离。

处理原则：

- tenant 隔离、registry key、health、contract version 语义已在 ICD 中设计态裁决。
- 仍未裁决的是运行态物理实现：持久化策略、写入者细节、健康检查推/拉模型、region 路由、broker/topic 绑定、一致性策略。

### MI3-004：module metadata 漂移验证仍有 key 缺失误绿风险

`AgentBusModuleMetadataDriftTest#parseListBlock(...)` 在 key 缺失时返回空列表。

这意味着如果 `allowed_dependencies` 从 `module-metadata.yaml` 中被删除，`metadata_allowed_dependencies_is_empty` 仍会通过。

处理原则：

- 对 `allowed_dependencies` 和 `forbidden_dependencies` 分别断言 key 必须存在。
- 缺失 key 应失败，而不是被解释为空列表。
- 可保留零依赖文本解析方式；不要求引入 YAML 库。

### MI3-005：ICD 中的测试名称和实际测试方法名不一致

`ICD-Agent-Registry-Discovery` 中列出的测试名使用 `tenantId` 驼峰：

- `registry_entry_requires_tenantId`
- `discovery_query_requires_tenantId`

实际 Java 方法使用 snake case：

- `registry_entry_requires_tenant_id`
- `discovery_query_requires_tenant_id`

处理原则：

- 优先同步 ICD 到实际方法名。
- 或者重命名测试方法，但不要两边长期不一致。

## 3. 后续阶段目标

下一轮建议定义为：

`Stage 3 收口：文档一致性与验证稳定性修正`

目标不是新增能力，而是让第三阶段结果成为后续自动化和第四阶段设计的稳定输入。

## 4. 开发切片

### 切片 0：建立 L0 逻辑名到当前实现名的映射说明

修改范围：

- `architecture/docs/L1/agent-bus/README.md`
- `architecture/docs/L1/agent-bus/ARCHITECTURE.md`
- 必要时新增一个短小的“命名说明”小节。

要求：

- 明确 L0 逻辑模块使用 `agent-runtime` / `agent-core`。
- 明确当前代码和 Maven artifact 仍是 `agent-service` / `agent-execution-engine`。
- 后续 agent-bus L1 文档引用生命周期 owner 时优先使用 `agent-runtime`。
- 后续引用当前禁止依赖或当前路径时可保留旧 artifact 名。

验收标准：

- 新读者能区分“逻辑模块名”和“当前实现目录名”。
- 文档不再暗示仓库已经存在 `agent-runtime/` 或 `agent-core/` 目录。

### 切片 1：全量同步 agent-bus L1 的 L0 新命名

修改范围：

- `architecture/docs/L1/agent-bus/ARCHITECTURE.md`
- `architecture/docs/L1/agent-bus/logical.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/physical.md`
- `architecture/docs/L1/agent-bus/scenarios.md`
- `architecture/docs/L1/agent-bus/spi-appendix.md`
- `architecture/docs/L1/agent-bus/features/README.md`

要求：

- 架构关系、状态所有权、参与者表述改用 `agent-runtime` / `agent-core`。
- 当前实现路径、module metadata、forbidden dependencies、Maven artifact 仍保留 `agent-service` / `agent-execution-engine`，但必须标注它们是当前实现/兼容落点。
- 不改代码目录，不改 Maven module，不改 module metadata。

验收标准：

```powershell
rg -n "agent-service|agent-execution-engine" architecture/docs/L1/agent-bus -S
```

剩余命中必须属于：

- 当前实现目录 / Maven artifact。
- compatibility note。
- module metadata / forbidden dependency。
- 历史来源引用。

### 切片 2：收干 S2C tenant 文档漂移

修改范围：

- `architecture/docs/L1/agent-bus/ARCHITECTURE.md`
- `architecture/docs/L1/agent-bus/scenarios.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/physical.md`

要求：

- 将所有“需要增加 tenantId”“代码未实现”“迁移前通知冲突方”等语句改为已完成契约层迁移。
- 将剩余工作统一表述为 runtime-side construction binding / schema validation integration / downstream template sync。
- 保持不改变 Task lifecycle 所有权。

验收标准：

```powershell
rg -n "需要增加 `tenantId`|没有 `tenantId`|代码未实现|迁移前|后续给 `S2cCallbackEnvelope` 增加" architecture/docs/L1/agent-bus docs/contracts -S
```

除历史计划文档外，不应再出现未完成迁移语义。

### 切片 3：修正注册发现物理视图表达

修改范围：

- `architecture/docs/L1/agent-bus/physical.md`

要求：

- 将“租户隔离未选择”改为“租户隔离已在设计态裁决”。
- 未决问题保留为运行态实现问题：持久化、写入者、健康检查模型、region、broker/topic、一致性。
- 不新增运行态注册表生产类。

验收标准：

- `physical.md` 内部不再同时说“tenant 隔离未选择”和“tenant 隔离已回答”。

### 切片 4：修正 module metadata 漂移验证的 key 缺失误绿

修改范围：

- `agent-bus/src/test/java/com/huawei/ascend/bus/architecture/AgentBusModuleMetadataDriftTest.java`

要求：

- `allowed_dependencies` key 缺失必须失败。
- `forbidden_dependencies` key 缺失必须失败。
- key 存在且 inline `[]` 才能解释为空列表。
- key 存在且 block list 时按现有逻辑解析。
- 不引入新的生产依赖。

建议实现：

- 新增一个小 record，例如 `MetadataList(boolean present, List<String> values)`。
- `parseListBlock` 返回 `MetadataList`。
- 测试中先断言 `present`，再断言 values。

验收标准：

- 删除或拼错 `allowed_dependencies` 时测试应失败。
- 删除或拼错 `forbidden_dependencies` 时测试应失败。

### 切片 5：同步 ICD 中的测试名称

修改范围：

- `docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md`

要求：

- 测试名称与 `AgentBusRegistryDiscoveryDesignContractTest` 实际方法名一致。
- 如果未来生成器依赖这些名称，应优先使用 Java 方法名。

验收标准：

- ICD 中不再出现和 Java 方法名不一致的测试名。

## 5. 禁止范围

本轮收口不得：

- 新增 runtime registry。
- 新增 service discovery API runtime。
- 引入 broker / MQ runtime binding。
- 修改 `agent-service` / `agent-execution-engine` Maven module 名。
- 修改 Task lifecycle owner。
- 让 `agent-bus` 依赖 sibling module 的生产代码。
- 把 agent 业务定义、Task execution state、Run 状态塞进 discovery result。
- 扩大 S2C tenant 迁移为跨模块 production runtime 改造。

## 6. 验证计划

验证由后续施工智能体或人工执行，本计划不要求计划制定者本地运行。

建议执行：

```powershell
.\mvnw.cmd -pl agent-bus test
```

建议补充静态检索：

```powershell
rg -n "agent-service|agent-execution-engine" architecture/docs/L1/agent-bus -S
rg -n "需要增加 `tenantId`|没有 `tenantId`|代码未实现|迁移前" architecture/docs/L1/agent-bus docs/contracts -S
rg -n "runtime registry|运行态注册表|broker / MQ" architecture/docs/L1/agent-bus docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md -S
```

通过标准：

- Maven 测试通过。
- 旧命名剩余命中均有兼容/实现落点说明。
- S2C tenant 不再以“待迁移”表述出现。
- 注册发现仍保持设计态，不出现运行态实现。

## 7. 后续阶段预告

完成本轮收口后，第四阶段才适合进入：

`Gateway 分发与类消息队列转发语义`

第四阶段可以讨论：

- gateway handler 如何强制 `RUN_CREATE + ACCEPTED` 必须有 cursor。
- broker / in-memory dispatcher / event loop 的候选方案。
- admission、ack、retry、timeout、DLQ、ordering、backpressure。
- route handle 与 registry/discovery 的运行态绑定方式。

第四阶段进入前必须先确认第三阶段文档不再漂移，否则类消息队列设计会建立在不稳定的事实源上。
