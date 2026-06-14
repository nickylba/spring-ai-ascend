---
artifact_type: a2d_delivery_projection
version: "agent-bus-stage3-close-review-and-stage4-plan"
status: draft
source_commit: "7e2fedfb agent-bus Stage 3 收口：MI3-001..005 文档一致性与验证稳定性修正"
source_previous_plan: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage3-review-and-followup-plan.md"
source_l1: "architecture/docs/L1/agent-bus/README.md"
target_module: agent-bus
---

# agent-bus 第三阶段收口评审与第四阶段计划

## 1. 评审结论

最新提交 `7e2fedfb` 可以接受。

它基本完成了上一份计划中的 MI3-001 到 MI3-005：

| 项目 | 完成情况 | 评审意见 |
|---|---|---|
| MI3-001 L0 新命名同步 | 已完成 | L1 与 ICD 已建立 `agent-runtime` / `agent-core` 到当前实现落点 `agent-service` / `agent-execution-engine` 的映射。 |
| MI3-002 S2C tenant 漂移收口 | 已完成 | 未再发现“需要增加 tenantId”“代码未实现”“迁移前通知”等过期语义；统一为契约层已迁移、runtime 绑定待后续波次。 |
| MI3-003 注册发现物理视图矛盾 | 已完成 | `physical.md` 已区分“设计态已裁决的 tenant/key/health/version”和“仍未裁决的运行态物理实现”。 |
| MI3-004 metadata key 缺失误绿 | 已完成 | `AgentBusModuleMetadataDriftTest` 已区分 key 缺失与空列表，缺失 key 会失败。 |
| MI3-005 ICD 测试名同步 | 已完成 | ICD 中的测试名已同步为 Java 方法名使用的 snake_case。 |

本次提交没有发现以下越界：

- 没有新增 runtime registry。
- 没有新增 broker / MQ runtime binding。
- 没有改变 Task lifecycle owner。
- 没有让 `agent-bus` 依赖 sibling module 的生产代码。
- 没有把 Task execution state 放进 discovery result。

因此第三阶段可以视为完成，后续应进入第四阶段设计，而不是继续扩大第三阶段文档。

## 2. 当前修改意见

### MI4-001：`ARCHITECTURE.md` 仍有一个阶段引用残留

位置：

- `architecture/docs/L1/agent-bus/ARCHITECTURE.md`

问题：

关键边界表里仍写：

```text
注册发现 ... 当前只记录为设计态，不进入 Stage 1 harness。
```

第三阶段已经完成注册发现 ICD 与设计级 harness，这里继续写“不进入 Stage 1 harness”会让读者误以为该能力仍停留在第一阶段前的计划语境。

建议改成：

```text
注册发现 ... 已在 Stage 3 形成设计态 ICD 与 harness；仍不进入 runtime 实现。
```

影响：

- 轻微文档漂移。
- 不阻塞接受本提交。
- 可并入第四阶段前置整理。

### MI4-002：README 的“后续工作”混合了历史索引和真正待办

位置：

- `architecture/docs/L1/agent-bus/README.md`

问题：

“后续工作”里同时列了已完成阶段的历史计划链接和仍待完成的工作项。作为索引可以理解，但作为“后续工作”会让读者误判 Stage 1/2/3 仍需执行。

建议：

- 拆成两个小节：
  - `阶段记录`：Stage 1/2/3 计划与评审链接。
  - `后续工作`：只保留真正待做项。

影响：

- 仅影响可读性和任务交接清晰度。
- 可并入第四阶段前置整理。

### MI4-003：部分标题仍使用 Service / Engine 泛称，建议在第四阶段前统一为 Runtime / Core 语义

位置：

- `architecture/docs/L1/agent-bus/ARCHITECTURE.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/features/README.md`

问题：

文档正文已大体切换到 `agent-runtime` / `agent-core`，但部分标题、关系名、能力名仍是 “Service 与 Engine”、“Service 到 Client”、“Service 到 Service”。这些词不一定错误，因为它们也可以表示通用服务角色；但在 L0 新命名刚收敛后，最好避免让读者把“service”误读成旧的 `agent-service` 逻辑模块名。

建议：

- 架构模块关系写 `Runtime 与 Core`、`Runtime 到 Client`、`Runtime 到 Runtime`。
- 如果要表达一般服务实例，写 `service instance` 或 “服务实例”，不要写成模块名。

影响：

- 不阻塞接受本提交。
- 第四阶段设计类 MQ 转发时建议先统一，否则 “service-to-service” 与 `agent-runtime` 关系容易混用。

## 3. 第四阶段目标

第四阶段建议聚焦：

`Gateway 分发与类消息队列转发语义`

第三阶段回答了“发给谁”：registry / discovery、tenant、health、version、route handle。

第四阶段要回答“怎么发”：

- gateway 如何将外部请求分发到 runtime。
- 真 bus 如何承载 runtime-to-runtime 的异步控制消息。
- ack、retry、timeout、DLQ、ordering、backpressure、correlation 如何表达。
- route handle 如何被转发语义消费。
- 哪些语义属于 broker 无关契约，哪些是未来运行态实现选择。

第四阶段仍应先做设计态和验证计划，不直接实现 MQ / broker runtime。

## 4. 第四阶段开发切片

### 切片 0：前置文档整理

范围：

- 修正 MI4-001。
- 修正 MI4-002。
- 修正 MI4-003。

验收：

```powershell
rg -n "Stage 1 harness|Service 与 Engine|Service 到 Client|Service 到 Service" architecture/docs/L1/agent-bus -S
```

剩余命中必须是历史阶段记录或明确的通用服务实例语义。

### 切片 1：类 MQ 转发语义 ICD

建议新增：

```text
docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md
```

最少定义：

| 主题 | 必须回答 |
|---|---|
| forwarding envelope | 必填字段：`tenantId`、`traceId`、`correlationId`、`idempotencyKey`、`routeHandle`、`capability`、`payloadRef`、`deadline`。 |
| route handle | 来自 Stage 3 discovery result；第四阶段不直接暴露物理 endpoint。 |
| delivery model | 同步 ack 与异步完成如何区分。 |
| retry | 谁允许重试，重试依据是什么，幂等键如何参与。 |
| timeout | request deadline、delivery timeout、processing timeout 是否区分。 |
| DLQ / replay | 哪些失败进入 DLQ；replay 是否保留 tenant、trace、payloadRef。 |
| ordering | 是否支持 per tenant / per route / per correlation ordering；默认无全局 ordering。 |
| backpressure | 接收方不可用、队列压力、tenant quota 如何表达。 |
| failure modes | `route_not_found`、`tenant_mismatch`、`delivery_timeout`、`receiver_unavailable`、`backpressure_rejected`、`duplicate_suppressed`。 |
| 禁止内容 | 不携带大对象正文、不携带 token stream、不携带 Task execution state。 |

### 切片 2：L1 视图同步

更新：

- `architecture/docs/L1/agent-bus/ARCHITECTURE.md`
- `architecture/docs/L1/agent-bus/logical.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/physical.md`
- `architecture/docs/L1/agent-bus/scenarios.md`
- `architecture/docs/L1/agent-bus/features/README.md`
- `architecture/docs/L1/agent-bus/README.md`

要求：

- 类 MQ 转发继续保持设计态。
- 明确它消费 Stage 3 的 route handle。
- 明确 broker / MQ 产品选择 deferred。
- 明确 runtime-to-runtime 消息不改变远端 Task lifecycle owner。
- 明确大载荷走 data reference path，不进 event/control channel。

### 切片 3：设计级 harness

建议新增测试：

```text
agent-bus/src/test/java/com/huawei/ascend/bus/architecture/AgentBusForwardingDesignContractTest.java
```

建议断言：

| 断言 | 目的 |
|---|---|
| ICD 存在且 L1 README 回链 | 防止契约游离。 |
| forwarding envelope 必须包含 `tenantId` | 延续租户强制维度。 |
| forwarding envelope 必须包含 `routeHandle` | 确保第四阶段消费第三阶段发现结果。 |
| forwarding envelope 只携带 `payloadRef`，不携带 payload body | 保持控制/数据分离。 |
| failure modes 包含 backpressure / timeout / tenant mismatch | 形成可验证失败语义。 |
| 第四阶段不新增 broker runtime package | 防止自动化越界实现。 |
| discovery result 与 forwarding envelope 通过 route handle 关联 | 防止无目标 broker 设计。 |

### 切片 4：Ingress Gateway 上下文级 cursor 规则

上一阶段已经裁决：

- `IngressResponse` 保持低上下文。
- `RUN_CREATE + ACCEPTED` 必须有 cursor 的规则在 gateway / handler 层校验。

第四阶段可以先定义设计态 harness，不必改 production code：

| 规则 | 建议表达 |
|---|---|
| `RUN_CREATE + ACCEPTED` | 必须产生 cursor。 |
| `RUN_GET` / `RUN_CANCEL` / `RUN_RESUME` | cursor 规则按具体 request type 定义。 |
| `REJECTED` | 必须有 rejection reason，不要求 cursor。 |
| `DEFERRED` | 不要求 cursor，但必须有后续观察路径定义。 |

## 5. 禁止范围

第四阶段不得：

- 引入 Kafka / NATS / RocketMQ / 自研 broker 的生产绑定。
- 新增 mailbox / queue / DLQ / replay 的运行态存储。
- 修改 Task lifecycle owner。
- 让 `agent-bus` 写 Task execution state。
- 让 event/control channel 承载大对象正文或 token stream。
- 绕过 Stage 3 route handle 直接使用物理 endpoint。
- 实现 service discovery API runtime。
- 改 Maven module 名或目录名。

## 6. 验证计划

验证由后续施工智能体或人工执行，本计划制定者不主动本地运行。

建议执行：

```powershell
.\mvnw.cmd -pl agent-bus test
```

建议静态检查：

```powershell
rg -n "Stage 1 harness|需要增加 `tenantId`|没有 `tenantId`|代码未实现|迁移前" architecture/docs/L1/agent-bus docs/contracts -S
rg -n "Kafka|NATS|RocketMQ|broker runtime|运行态注册表|Task execution state" architecture/docs/L1/agent-bus docs/architecture/l0/05-contracts/human-readable -S
rg -n "routeHandle|payloadRef|backpressure|DLQ|retry|timeout|ordering" docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding.md architecture/docs/L1/agent-bus -S
```

通过标准：

- Maven 测试通过。
- 第四阶段 ICD 与 L1 README 双向可发现。
- 类 MQ 转发语义保持 broker-agnostic。
- 转发 envelope 不承载大对象正文、token stream 或 Task state。
- 转发语义明确消费 Stage 3 的 `routeHandle`。

## 7. 下一阶段之后

第四阶段完成后，才适合讨论第五阶段：

`运行态候选方案评审`

第五阶段可以比较：

- in-memory dispatcher。
- Kafka / NATS / RocketMQ 等 broker。
- 数据库 outbox / inbox。
- runtime-local queue。
- 混合方案。

但第五阶段必须建立在第四阶段已经稳定的 broker-agnostic 语义上，不能用某个产品能力反向定义架构语义。
