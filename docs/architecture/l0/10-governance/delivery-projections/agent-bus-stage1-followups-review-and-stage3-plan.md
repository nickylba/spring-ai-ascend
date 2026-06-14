---
artifact_type: a2d_delivery_projection
version: "agent-bus-stage1-followups-review-and-stage3-plan"
status: draft
source_commit: "85f46ab6 test(agent-bus): MI-001..004 Stage 1 review follow-ups"
source_stage2_review: "docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage2-review-and-stage3-plan.md"
source_l1: "architecture/docs/L1/agent-bus/README.md"
target_module: agent-bus
---

# agent-bus 第一阶段后续修正评审与第三阶段执行计划

## 1. 评审结论

最新提交 `85f46ab6` 可以接受，但带两个小修意见。

这次提交主要补齐第一阶段评审中的 MI-001 到 MI-004：

| 项目 | 结果 | 评审意见 |
|---|---|---|
| MI-001 | 已补 | SPI 纯度验证增加 HTTP / 网络框架前缀覆盖，能更明确地阻止 ingress SPI 泄漏传输框架概念。 |
| MI-002 | 基本补齐 | 新增 POM 依赖漂移测试，能防止 `agent-bus` 在 production scope 依赖 sibling 模块，也能保护 test-only 依赖不进入生产依赖图。 |
| MI-003 | 已裁决 | `IngressResponse` 保持低上下文；`RUN_CREATE + ACCEPTED` 必须有 cursor 的规则下沉到 ingress gateway / handler 层。 |
| MI-004 | 已补 | SPI 纯度与依赖边界验证都增加了 import-liveness 断言，降低“空集合误绿”的风险。 |

本次提交的代码质量整体比第一阶段原始提交更稳：它没有急着进入生产运行态，而是把边界约束继续落在验证层；新增测试也尽量保持纯 JUnit / ArchUnit，不引入 Spring context 或运行态依赖。

## 2. 当前修改意见

### MI-FU-001：`AgentBusModuleMetadataDriftTest` 的名义覆盖大于实际覆盖

位置：

- `agent-bus/src/test/java/com/huawei/ascend/bus/architecture/AgentBusModuleMetadataDriftTest.java`

问题：

这个测试的类名和注释都宣称它是 POM / `module-metadata.yaml` 漂移验证，并且把权威来源写成 `agent-bus/module-metadata.yaml`。但当前实现只解析 `pom.xml`，没有读取 `agent-bus/module-metadata.yaml`。

因此它现在能验证：

- `pom.xml` 没有新增 production scope 的 `com.huawei.ascend:*` sibling dependency。
- `archunit-junit5` 和 `spring-boot-starter-test` 仍保持 test scope。

但它还不能验证：

- `module-metadata.yaml#allowed_dependencies` 是否真的仍为 `[]`。
- `module-metadata.yaml#forbidden_dependencies` 是否和测试断言同步。
- 后续如果 metadata 允许了某个依赖，测试是否应该随之改变。
- 后续如果 metadata 新增禁止项，测试是否能自动捕获。

处理建议：

| 优先级 | 建议 | 说明 |
|---|---|---|
| 推荐 | 让测试同时读取 `module-metadata.yaml` | 至少断言 `allowed_dependencies: []` 与 POM 生产依赖图一致。 |
| 可接受 | 将类名和注释降级为 POM 作用域验证 | 如果暂时不想引入 YAML 解析，就不要声称已经覆盖 module metadata drift。 |
| 后续增强 | 把 forbidden / allowed dependency 规则抽成可复用测试输入 | 便于其他模块复用同一类边界验证。 |

这个问题不阻塞接受最新提交，但应该作为下一轮前置小修。

### MI-FU-002：`IngressResponseTest` 的方法名仍保留“待 owner 裁决”的语义

位置：

- `agent-bus/src/test/java/com/huawei/ascend/bus/spi/ingress/IngressResponseTest.java`

问题：

测试注释已经写清楚 MI-003 采用方案 A：`RUN_CREATE + ACCEPTED` 的 cursor 规则由 ingress gateway / handler 层负责，`IngressResponse` 本身保持低上下文。但方法名仍是：

```java
accepted_currently_does_not_enforce_non_null_cursor_pending_owner_decision
```

这会让读者误以为该问题仍处于 pending owner decision 状态。

处理建议：

把方法名改为类似：

```java
accepted_allows_null_cursor_because_cursor_requirement_is_gateway_context_rule
```

这属于命名同步问题，不影响行为。

### MI-FU-003：L1 索引仍有第二阶段后的状态漂移

位置：

- `architecture/docs/L1/agent-bus/README.md`

问题：

第二阶段已经完成 S2C `tenantId` 迁移，但 L1 索引里仍写着“S2C envelope 需要增加 `tenantId`”和“把 S2C tenant 迁移通知记录转成独立 delivery projection”。这部分在上一份第二阶段评审里已经指出，本次最新提交没有覆盖。

处理建议：

第三阶段的切片 0 先做文档事实同步：

- 将“S2C envelope 需要增加 `tenantId`”改为“S2C envelope 已增加 `tenantId`，后续补 runtime-side construction binding / schema validation integration”。
- 将“待通知事项”改为“第二阶段后续同步事项”。
- 将后续工作从“迁移通知记录”改为“补齐迁移后的绑定、校验和 downstream 文档同步”。

## 3. 第三阶段目标

第三阶段建议正式进入：

`Agent 注册与发现设计 / 验证`

这个阶段回应 bus 的两大组成：

- Gateway：需要知道外部请求应该进入哪个内部 agent / service / route。
- 真 bus：需要知道 service 与 service 之间如何发现、选择、调用对端能力。

因此第三阶段不应该只写一个“类消息队列的转发计划”，而应把“转发 + 注册发现 + tenant 隔离 + 健康/version/route key”一起定义成设计态契约。

## 4. 第三阶段范围

### 必须包含

| 主题 | 内容 |
|---|---|
| 注册对象 | 一个 agent / service 对外暴露哪些可路由能力。 |
| 注册字段 | `tenantId`、`agentId` / `serviceId`、capability、route key、version、health、endpoint / logical target、lease / ttl。 |
| 发现查询 | 调用方如何按 tenant、capability、version、health 查询候选目标。 |
| 发现结果 | 返回什么字段，是否允许多个候选，排序和选择责任属于谁。 |
| 租户隔离 | tenant 是查询和注册的强制维度，禁止跨 tenant fallback。 |
| 健康与版本 | unhealthy target 是否可见，version mismatch 如何表达。 |
| 所有权边界 | `agent-bus` 只拥有 route / discovery 视图，不拥有 Task 生命周期和 Task execution state。 |
| 验证断言 | 用契约级 / 设计级测试证明以上边界，不实现运行态注册表。 |

### 暂不包含

| 主题 | 原因 |
|---|---|
| 运行态注册表实现 | 第三阶段先定契约和验证，不选择内存、持久化或外部发现系统。 |
| MQ broker 绑定 | 转发语义需要先有 route / discovery 契约，再选择 broker 或 in-process dispatch。 |
| mailbox / admission / backpressure / tick | 仍属于 W2 workflow primitives，当前保持设计态。 |
| Task 状态持久化 | 仍归 agent runtime / service。 |
| 跨模块生产代码改造 | 第三阶段先不修改 `agent-service`、`agent-client` 等模块生产代码。 |

## 5. 第三阶段开发切片

### 切片 0：前置小修和文档同步

完成：

- MI-FU-001：修正 `AgentBusModuleMetadataDriftTest` 的 metadata 覆盖不一致问题。
- MI-FU-002：重命名 `IngressResponseTest` 中已经裁决的 characterisation test。
- MI-FU-003：同步 L1 索引里的第二阶段状态漂移。

验证：

```powershell
.\mvnw.cmd -pl agent-bus test
```

### 切片 1：注册与发现 ICD

新增设计态契约文档，建议路径：

```text
docs/architecture/l0/05-contracts/human-readable/ICD-agent-registry-discovery.md
```

最少定义：

- registry entry。
- discovery query。
- discovery result。
- version / health / tenant 规则。
- failure modes。
- 与 gateway、真 bus、agent runtime 的边界。

### 切片 2：L1 视图同步

更新：

- `architecture/docs/L1/agent-bus/logical.md`
- `architecture/docs/L1/agent-bus/process.md`
- `architecture/docs/L1/agent-bus/physical.md`
- `architecture/docs/L1/agent-bus/development.md`
- `architecture/docs/L1/agent-bus/scenarios.md`
- `architecture/docs/L1/agent-bus/features/README.md`

目标：

- 在逻辑视图中显式出现 registry / discovery。
- 在进程视图中说明 register / heartbeat / discover / route 的流程。
- 在物理视图中说明 registry 的部署选择仍未定，不绑定具体运行态。
- 在开发视图中说明当前只允许设计、契约和验证变化。
- 在场景视图中补齐 agent 注册、服务发现、目标不可用、版本不匹配、跨 tenant 查询被拒绝。

### 切片 3：设计态验证

新增测试或文档校验，优先保证：

| 断言 | 目的 |
|---|---|
| registry ICD 存在并被 L1 README 链接 | 防止设计文档游离。 |
| registry entry 强制包含 `tenantId` | 防止注册能力绕过租户边界。 |
| discovery query 强制包含 `tenantId` | 防止查询能力绕过租户边界。 |
| discovery result 不携带 Task execution state | 防止 bus 吞掉 runtime 所有权。 |
| unhealthy / version mismatch 有明确表达 | 防止调用方只能靠异常猜测状态。 |
| 第三阶段不新增运行态注册表生产类 | 防止本阶段越界实现。 |

### 切片 4：下一阶段预告

第三阶段完成后，再进入第四阶段：

`Gateway 分发 / 类消息队列转发语义`

第四阶段才讨论：

- broker / in-memory dispatcher / event loop 的选择。
- request admission。
- queue depth / backpressure。
- retry / timeout。
- ordering。
- dead-letter / rejection。
- gateway handler 如何强制 `RUN_CREATE + ACCEPTED` 必须有 cursor。

## 6. 本次本地验证状态

已尝试：

```powershell
.\mvnw.cmd -pl agent-bus test
```

当前工作站失败原因：

```text
The JAVA_HOME environment variable is not defined correctly,
this environment variable is needed to run this program.
```

最新提交信息中记录的远端/他处验证结果为：

```text
mvn -pl agent-bus -am test -B
JDK Red Hat 21.0.10 aarch64
Maven 3.6.3
Tests run: 67, Failures: 0, Errors: 0
```

因此本地只完成了静态审查和文档审查，未完成本机 Maven 复验。

## 7. 对下一位施工智能体的约束

下一位智能体执行第三阶段时必须遵守：

- 先完成切片 0，再进入 registry / discovery ICD。
- 不新增运行态注册表。
- 不新增 MQ / broker 绑定。
- 不修改 Task 生命周期所有权。
- 不跨 tenant 做 fallback。
- 不把 `agent-service` / runtime 的 Task state 字段复制进 discovery result。
- 所有设计态新增内容必须能回链到 L1 README 或对应验证。
