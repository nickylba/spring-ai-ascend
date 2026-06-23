---
artifact_type: delivery_projection
version: agent-bus-stage16-review-and-stage17-plan
status: stage-17-completed
source_commit: 7d2ea5f1
stage17_completed: 2026-06-23
stage17_tests_green: 182
source_stage16_plan: docs/architecture/l0/10-governance/delivery-projections/agent-bus-stage15-review-and-stage16-plan.md
source_decision: docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-decision.md
source_icd_runtime: docs/architecture/l0/05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md
source_l2: architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md
target_module: agent-bus (+ cross-module agent-runtime, test-only)
---

# agent-bus Stage 16 评审与 Stage 17 计划（真实端到端集成验证：首次打通 agent-bus↔agent-runtime 跨模块集成）

## 0. 结论

提交 `7d2ea5f1` 可以作为 Stage 16 的阶段性成果接受：`ForwardingCircuitBreaker` 生产化补全 —— 端口加 `recordOutcome` 反馈 + 真实实现 `RouteCircuitBreaker`（CLOSED→OPEN→HALF_OPEN 三态机，纯 JDK）+ worker 7 参构造器 + `runOnce` 三处接入（顺序保证 `probeInFlight` 单探测不泄漏）。**179 tests green**，ArchUnit 纯度 green，§6.2 不变。断路器端口本身 transport-agnostic（只消费 `ForwardingRouteHandle`/`ForwardingDeliveryResult`，不碰物理端点 HD4、不碰 broker），**不裁决** Stage 13 的 push/pull/MQ 最终模型。**接受**。

Stage 17 = 用户在 Stage 16 收尾后选定的**真实端到端集成验证**：用真实、以编程方式启动的 `LocalA2aRuntimeHost`（agent-runtime 的 Spring Boot A2A 服务器）替换 Stage 15 的 MockWebServer，验证完整链路闭环：**C3 转发（outbox enqueue）→ dispatch worker tick → `A2aForwardingDeliveryPort` 投递 → 真实 `/a2a` 端点 → agent-runtime 处理 → `COMPLETED` → worker `ACKED`**。这把 Stage 15「对接可行性 PoC」从「测一个协议字节对称的 mock」提升为「测真实 agent-runtime server 全栈」。

**核心发现**：Stage 17 的真正难点**不在端到端 IT 代码本身**（`LocalA2aRuntimeHost.port(0)` + `RuntimeApp.create(StubHandler)` API 完整、StubHandler 范式现成、Stage 12/15/10 基础设施全部可复用），而在**首次打通 agent-bus↔agent-runtime 跨模块集成的前置工程**。agent-bus 与 agent-runtime 当前是完全隔离构建的兄弟模块（本地 m2 里两者都**从未 install**、互不引用），Stage 17 是首次建立 agent-bus→agent-runtime 依赖（test-only）。这是 agent-bus 项目自 Stage 1 以来的**首个跨模块集成里程碑**，工作量与风险大于任意单模块 Stage。

**前置可行性已实际验证**（见 §3）：
- ✅ parent 0.2.0 可 `mvn -N install` 进 m2。
- ✅ **agent-bus parent 0.1.0→0.2.0 后 179 tests 全 green（7.9s，零下载）** —— parent 统一核心前提确认，无 dependencyManagement 差异连锁风险。
- ⚠️ agent-runtime pom 存在 pre-existing build 损坏（`a2a-java-sdk-client-transport-jsonrpc`/`http-client` version missing，`034da8f7` 引入）—— agent-bus 自己 pom 已显式写 version 规避，agent-runtime 未修。修复方案明确（加 `${a2a-sdk.version}`，已 apply 验证 pom 可解析），但属**改同事维护模块**，需知会。
- ⚠️ agent-runtime 完整 build green 未验证（依赖下载慢：`pulsar-client`/`agent-core-java`/`bouncycastle` 首次拉取经代理超时，30+ 分钟仍在下载），但 version missing 是**唯一** pom 解析错误。

**Stage 17 不裁决** Stage 13 的 push/pull/MQ 哲学张力（仍 H2/H3）—— 端到端 IT 用 Stage 15 已选的 T1（同步等完成）投递绑定 + Stage 10 测试驱动的 `TickSource`（无真实 scheduler，符合 §6.1「总线中无调度器/计时器」）。§6.2 不变（A2A 是 HTTP JSON-RPC，非 concrete broker/MQ；`transport.a2a` 子包隔离 Stage 15 已落地）。

## 1. Stage 16 评审回顾（commit `7d2ea5f1`）

Stage 16 把 Stage 14 落地时**故意 defer** 的 `ForwardingCircuitBreaker` 端口（当时 javadoc 写明「真实熔断需 per-route 失败率状态，且形态依赖 Stage 13 未裁决的 transport 模型 —— push 需主动短路、consumer-pull 天然自调速，接入前会 bake in transport 假设」）正式接进 `ForwardingDispatcherWorker`。阻塞由 Stage 15 解除：PoC 选 T1（push over sync RPC），push 模型 dispatcher 主动驱动投递，需要 breaker 在故障 route 主动短路。落地点（[`stage15-review-and-stage16-plan`](agent-bus-stage15-review-and-stage16-plan.md)）：

- **端口扩展 `recordOutcome`**：投递后反馈驱动状态机；`ALWAYS_CLOSED` 从单方法 lambda 改两方法匿名类。
- **`RouteCircuitBreaker` 三态机**（纯 JDK）：CLOSED→OPEN→HALF_OPEN；注入 `EpochClock`（MI11-001 同源）；per-route `ConcurrentHashMap<String,RouteState>` + `synchronized(RouteState)` + `probeInFlight` 单探测锁。
- **worker 7 参构造器 + `runOnce` 三处接入**：`allowsDelivery`（renew 后 deliver 前，OPEN 短路复用 skip 路径）/ `recordOutcome`（deliver 后 switch 前）/ catch 块补 `retry(RECEIVER_UNAVAILABLE)`。

**4 个优点：**

1. **transport-agnostic 边界守恒**：breaker 端口纯 JDK，`RouteCircuitBreaker` 依赖仅 `ForwardingDeliveryResult`/`ForwardingRouteHandle`/`EpochClock`/`java.util.concurrent`，不沾 spring/jdbc/jackson/kafka/nats/netty/a2a —— **无需新增 ArchUnit 豁免**，§6.2 文本扫描不触发。HD4 守恒（breaker 不碰物理端点，只消费 routeHandle 抽象）。
2. **`probeInFlight` 不变量设计严谨**：三处接入的顺序约束保证 HALF_OPEN 单探测锁在所有路径不泄漏 —— `allowsDelivery` 必在 lease renew 之后（renew 失败 skip 不碰 breaker）；`recordOutcome` 必在 switch 之前（switch 内 lease-guard 异常不影响反馈）；catch 块必补 `recordOutcome(failure)`（探测抛异常也清 `probeInFlight`）。由 `RouteCircuitBreakerTest`（11 tests）+ `AgentBusForwardingRuntimeContractTest` Stage 16 节（45 tests 含 4 新增）覆盖。
3. **触发分类封装在 breaker 内**：`recordOutcome` 接收完整 `ForwardingDeliveryResult`，分类（ACKED→成功 / RETRY_SCHEDULED→失败 / DLQ·EXPIRED→忽略）封装在 `RouteCircuitBreaker` 内，worker 只调一次不关心分类 —— worker 关注点（lease/状态机切换）与 breaker 关注点（route 健康）干净分离。
4. **`DispatchTickResult` 不变 + 自洽不变量保持**：OPEN 短路复用现有 skip 路径（`skipped++`），**无新计数器**，自洽不变量 `claimed == acked+retried+dlqd+expired+skipped` 自动保持，现有契约测试不破坏。

**2 个边界项（观察，非阻塞，deferred）：**

1. **OPEN 路由记录空转**：route 在 breaker 内存 OPEN，但 outbox record 状态机独立、不知情。每 lease TTL 周期，DISPATCHING record 过期回 PENDING → 重新 claim → `allowsDelivery` 返回 false 短路 skip → 留 DISPATCHING → 循环。**无害**（不消耗 attemptCount、不 deliver），但浪费 lease 写 + claim 周期。低严重性（仅 route 持续故障 + 积压时可见）；deferred 到 breaker 状态持久化或 route 健康信号抑制 claim 时一并解决。
2. **`probeInFlight` 非泄漏隐性依赖有界交付**：HALF_OPEN 探测放行后 `probeInFlight=true` 靠 `recordOutcome` 清理。当前不泄漏是因 Stage 15 `streamTimeoutMillis`（deliver 有界 await）+ worker lease TTL（lease 过期 worker 放弃）+ catch 块三重保证。**隐性契约**：若未来换无界交付（如 T2 异步 broker push 无超时），`probeInFlight` 可能泄漏卡死 HALF_OPEN。低严重性（当前 T1 有界），值得在 L2 文档化为 breaker 的交付有界性前提。

Stage 16 DoD：179 tests green，ArchUnit 纯度 green，§6.2 不变。**接受**。

## 2. Stage 17 范围与设计

### 2.1 为什么（完成对接验证闭环）

Stage 15 用 MockWebServer 证明了**线上格式对称性**（wire-format symmetry）：harness 用 A2A SDK 自身序列化器（`JsonUtil.toJson(new SendStreamingMessageResponse(id, event))`）产出的 SSE 帧，与 agent-runtime `A2aJsonRpcController` 逐字节相同 —— 把 PoC 从「测一个 fake」提升为「测真实协议代码」。但 MockWebServer 终究只验证了**协议字节 + 客户端解析**，未验证**真实 server 栈**。

Stage 17 的增量价值 = **真实 Spring Boot MVC + 真实 A2A SDK server runtime + 真实 Task 状态机流转 + 真实 SSE 编码**：验证 agent-bus 的转发投递能真正驱动 agent-runtime 执行一个 Task 并拿到 `COMPLETED`，完成 agent-bus C3 转发 → agent-runtime 的对接验证闭环。用户选定**完整端到端**：outbox enqueue → dispatch worker tick → deliver → 真实 `/a2a` → agent-runtime 处理 → `COMPLETED` → worker `ACKED` 全链。

### 2.2 核心发现：首次跨模块集成

agent-bus 与 agent-runtime 是 root pom 下的平级兄弟模块，**当前完全隔离构建**：本地 m2 里 `agent-runtime` 与 `agent-bus` 都**从未 install**（`(none)`），互不引用。agent-bus 靠 `mvn -f agent-bus/pom.xml` 在隔离环境跑（用 m2 缓存的 0.1.0 parent），agent-runtime 同理。`physical §2` 明文「agent-bus 不依赖 agent-runtime 的生产代码」至今对 main scope 成立。

Stage 17 是 agent-bus 项目自 Stage 1 以来**首次建立 agent-bus→agent-runtime 依赖**（test-only）。这意味着端到端 IT 的难点**不在测试代码**（API/基础设施都现成），而在跨模块集成的治理前置 —— 这是 §2.3 的对象。

### 2.3 前置工程（切片 0）—— Stage 17 的真正主体

| 子项 | 内容 | 状态/风险 |
|---|---|---|
| **0a** | 修 agent-runtime pom：`a2a-java-sdk-client-transport-jsonrpc` / `a2a-java-sdk-http-client` 加 `<version>${a2a-sdk.version}</version>`（pre-existing version missing，`034da8f7` 引入；agent-bus 自己 pom 已显式写 version 规避，见 agent-bus/pom.xml L70-85 注释） | 修复方案明确，已 apply 验证 pom 可解析。**改同事维护模块**，需知会 |
| **0b** | `mvn -N install`（root，install parent 0.2.0，已验证 BUILD SUCCESS）+ `mvn install`（agent-runtime，首次进 m2） | gate: agent-runtime build green。**风险**: 依赖下载慢（`pulsar-client`/`agent-core-java`/`bouncycastle` 首次经代理拉取超时），需离线包或多次重试 |
| **0c** | agent-bus/pom.xml parent `0.1.0-SNAPSHOT` → `0.2.0-SNAPSHOT`（与 root/agent-runtime 一致，消除历史 tech debt） | **已验证**: 升级后 179 tests green（7.9s，零下载），无 dependencyManagement 差异连锁 |
| **0d** | agent-bus/pom.xml 加 test-scope `agent-runtime` 依赖（首次跨模块）+ ArchUnit 确认 main 纯度规则不受影响 + physical §2 test-only 突破说明 + decision §8 Stage 17 许可段 | gate: agent-bus 全量 test green + ArchUnit green |

### 2.4 端到端 IT 设计（切片 1：`C3ForwardingEndToEndIntegrationTest`）

**复用现成基础设施（不新造轮子）：**
- Stage 12：embedded-postgres + `JdbcForwardingOutbox`（真实 Postgres 16.2 + Flyway V1 + RLS）。
- Stage 15：`A2aForwardingDeliveryPort` + `MapEndpointResolver`（endpoint 指向真实 host port）+ `A2aForwardingProperties`。
- Stage 10：`ForwardingDispatchLoop` + 测试 `TickSource`（**无真实 scheduler**，测试驱动 tick，符合 §6.1）。
- Stage 16：`RouteCircuitBreaker`/`ALWAYS_CLOSED`（健康 route 不触发，用 `ALWAYS_CLOSED` 或构造一个高阈值 breaker）。

**新增（IT 本身）：**
- 真实 `LocalA2aRuntimeHost.port(0)` + 内嵌 stub handler（照 `RuntimeAppTest.StubHandler`：实现 `AgentRuntimeHandler`，`execute` 返回 `Stream.of(Map.of(...))`，`resultAdapter` 映射为 `AgentExecutionResult.completed(...)`）。
- `try (RunningRuntime runtime = RuntimeApp.create(stubHandler).run(LocalA2aRuntimeHost.port(0)))` → `runtime.port()` 拿真实绑定端口 → `MapEndpointResolver` 指向 `http://localhost:<port>/a2a`。

**链路 + 断言：**
```
embedded-postgres ──► JdbcForwardingOutbox.enqueue(record)         [Stage 12]
LocalA2aRuntimeHost.port(0) + StubHandler ──► realPort              [Stage 17 新增]
MapEndpointResolver(route → http://localhost:realPort/a2a)          [Stage 15]
ForwardingDispatchLoop.run(tenant, limit, owner, leaseMs)           [Stage 10, 测试 TickSource]
  └─ worker.runOnce: deliver                                         [Stage 7-16]
        └─ A2aForwardingDeliveryPort → HTTP /a2a                     [Stage 15]
              └─ real Spring Boot MVC → A2aJsonRpcController
                    └─ A2A SDK server runtime → StubHandler → COMPLETED (SSE)
        └─ mapped → ACKED
assert: outbox record → ACKED, DispatchTickResult.acked == 1
        自洽不变量 claimed == acked+retried+dlqd+expired+skipped
        X-Tenant-Id header 到达真实 controller（R-C.c tenant continuity）
```

**命名 + 构建**：`C3ForwardingEndToEndIntegrationTest`（以 `Test` 结尾，surefire 跑，照 Stage 12 `ForwardingJdbcIntegrationTest` 范式，保持 `mvn -f agent-bus/pom.xml test`）。可选扩展场景：route 不可达（host 关闭）→ `RETRY/RECEIVER_UNAVAILABLE`；handler 返回 `FAILED` → `RETRY/RECEIVER_UNAVAILABLE`（保守，`REMOTE_TASK_FAILED` 仍 deferred）。

### 2.5 边界突破 + ArchUnit + governance

- **physical §2**：main 代码**仍零依赖** agent-runtime（test-only 突破）。ArchUnit 的 forwarding 纯度规则（`AgentBusForwardingSpiPurityTest`）针对**生产源**（`readForwardingProductionSources`），test-scope 引用 agent-runtime 不触发；确认 `AgentBusDependencyBoundaryTest` 是否有全局「agent-bus 不依赖 agent-runtime」规则，若有则限 test scope 豁免。
- **§6.2 不变**：A2A 是 HTTP JSON-RPC，非 concrete broker/MQ（Kafka/RabbitMQ/NATS 仍禁止）；`transport.a2a` 子包隔离 Stage 15 已落地，IT 不新增 a2a 依赖到 main。
- **decision §8**：加 Stage 17 许可段。正向：首次跨模块 test-only 集成、完成对接验证闭环、parent 版本统一消除历史 tech debt、test-only 不影响 main 边界。反向：main 仍零依赖 agent-runtime、§6.2 不变、不裁决 push/pull/MQ（仍 H2/H3）。

### 2.6 验证策略

- 切片 0 gate：agent-runtime build green（0b）+ agent-bus 升 parent green（0c 已验证）+ 跨模块 test dep 后 agent-bus 全量 green（0d）。
- 切片 1 gate：`C3ForwardingEndToEndIntegrationTest` green（ACKED + outbox record 状态 + tick 自洽）。
- 构建：`mvn -f /mnt/nas/.../agent-bus/pom.xml test -s ~/.m2/settings.xml -B`（system mvn 3.6.3 + Red Hat JDK 21，见 memory `build-env-maven-via-settings-xml`；不用 `./mvnw` 会 hang；不用 `-pl agent-bus` 撞 agent-runtime 破损 pom）。

## 3. 关键发现（前置验证结果）

实际验证（2026-06-23）：

| # | 发现 | 验证方式 | 结论 |
|---|---|---|---|
| 1 | parent 0.2.0 可 install | `mvn -N install`（root）| ✅ BUILD SUCCESS，m2 现有 0.1.0 + 0.2.0 |
| 2 | agent-bus 升 0.2.0 parent → 179 green | 临时改 parent + `mvn -f agent-bus/pom.xml test` | ✅ **179 tests green，7.9s，零下载**（核心前提确认，无连锁）|
| 3 | agent-runtime pom version missing | `mvn install` agent-runtime | ⚠️ 唯一 pom 错误：两个 a2a client transport 缺 version（`034da8f7` pre-existing）；修复方案明确（加 `${a2a-sdk.version}`，agent-bus 已如此规避）|
| 4 | agent-runtime 完整 build green | 同上（下载阶段超时）| ⚠️ 未验证。依赖下载慢（`pulsar-client`/`agent-core-java`/`bouncycastle` 首次经代理拉取，30+ min 仍在下载）。version missing 是唯一 pom 错误，compile 是否 green 待 0b 切片验证 |
| 5 | `LocalA2aRuntimeHost` API 完整 | 读 `agent-runtime/.../app/` | ✅ `port(int)` 静态工厂 + `RuntimeApp.create(handler).run(host)` + `RunningRuntime.port()` + `AutoCloseable`；`RuntimeAppTest.StubHandler` 范式现成 |
| 6 | `/a2a` 端点契约对称 | 读 `A2aJsonRpcController` | ✅ `X-Tenant-Id` header + `SendStreamingMessage` + SSE `jsonrpc` 帧 + `isFinal`/`isInterrupted` 终态，与 Stage 15 `A2aForwardingDeliveryPort` 发送格式完全对称 |

### 3.1 实际执行结果（2026-06-23，Stage 17 完成）

切片 0-3 全部落地，**182 tests green**（Stage 16 的 179 + 2 端到端 IT），构建成功：

| # | 计划项 | 实际结果 |
|---|---|---|
| 0a | 修 agent-runtime pom version missing | ✅ 两个 a2a client transport 加 `${a2a-sdk.version}`，agent-runtime `mvn install` BUILD SUCCESS（17.7s，jar 进 m2） |
| 0b | parent 0.2.0 install + agent-runtime 进 m2 | ✅ parent `-N install` + agent-runtime install 双 BUILD SUCCESS（依赖下载经代理重试后完成） |
| 0c | agent-bus parent 0.1.0→0.2.0 | ✅ 已落地，179 green 不回归 |
| 0d | agent-bus 加 test-scope agent-runtime 依赖 + `AgentBusDependencyBoundaryTest` runtime 守卫 | ✅ 已落地，ArchUnit green |
| 1 | `C3ForwardingEndToEndIntegrationTest` | ✅ 双测试 green（happy path ACKED + tick 自洽 + 端到端租户隔离） |

**两个计划外发现（已纳入决策记录，见 decision §8 Stage 17 bullet + L2 §18.2）：**

1. **agent-runtime 对 JDBC-bearing 共享 classpath 敏感**（计划未预见）：`LocalA2aRuntimeHost` 的 A2A server Spring 上下文纯内存，但 agent-bus 的 jdbc starter + postgres driver + flyway（Stage 12）泄漏到共享测试 classpath 后触发 `DataSourceAutoConfiguration` / `FlywayAutoConfiguration` 对真实 host 上下文 fire，因缺 `spring.datasource.url` 失败。IT 用 `System.setProperty("spring.autoconfigure.exclude", …)` 排除（`port(int)` 工厂未暴露 property hook，system property 是唯一高于其 package-private defaultProperties 的属性源）。
2. **Spring Boot 4 autoconfigure 重打包**（计划未预见）：jdbc autoconfigure 从 `org.springframework.boot.autoconfigure.jdbc`（Spring Boot 3）移到 `org.springframework.boot.jdbc.autoconfigure`（Spring Boot 4），flyway 同理移到 `org.springframework.boot.flyway.autoconfigure`。**用 Spring Boot 3 旧包名排除静默无效**（首次排除失败、改新包名才成功）。

**意外强证据**：运行日志显示真实 `A2aAgentExecutor` 收到 `metadata.tenantId=tenant-loop` / `tenant-iso-a` —— 证明 `X-Tenant-Id` header 不只被发送（Stage 15 wire 断言），更被真实 controller 接收/解析/传给 handler（Stage 15 的 MockWebServer 无法验证此语义）。

**对 Stage 17 计划的含义**：核心前提（parent 统一）已确认成立，IT 技术上完全可行。剩余风险集中在 0a（改 agent-runtime 同事模块）+ 0b（agent-runtime build green 待验证 + 依赖下载环境）。

## 4. 切片 + MI 表

| MI | 切片 | 产出 | 状态 |
|---|---|---|---|
| MI17-001 | 0a 修 agent-runtime pom | `agent-runtime/pom.xml` 两个 a2a client transport 加 `<version>${a2a-sdk.version}</version>`（pre-existing `034da8f7` bugfix；agent-bus 已如此规避） | ✅ 完成（agent-runtime install BUILD SUCCESS）|
| MI17-002 | 0b install 前置 | `mvn -N install`（parent 0.2.0）+ `mvn install`（agent-runtime）| ✅ 完成（双 BUILD SUCCESS，依赖经代理重试后拉齐）|
| MI17-003 | 0c agent-bus parent 统一 | `agent-bus/pom.xml` parent `0.1.0-SNAPSHOT` → `0.2.0-SNAPSHOT`（消除 tech debt） | ✅ 完成（179 green 不回归）|
| MI17-004 | 0d 跨模块 test dep + 治理 | agent-bus/pom.xml 加 test-scope `agent-runtime` 依赖；`AgentBusDependencyBoundaryTest` 加 `runtime..` 守卫；decision §8 Stage 17 许可段；physical §2 test-only 说明 | ✅ 完成（ArchUnit green）|
| MI17-005 | 1 端到端 IT | `C3ForwardingEndToEndIntegrationTest`：embedded-postgres + `LocalA2aRuntimeHost.port(0)` + 内嵌 StubHandler + `MapEndpointResolver` 指向真实 port + `JdbcForwardingOutbox.enqueue` + `ForwardingDispatchLoop.run`（测试 TickSource）+ 断言 ACKED/tick 自洽/tenant header | ✅ 完成（2 IT green，182 全量 green）|
| MI17-006 | 2 文档同步 | decision §8 + ICD（边界条 + Open Issues）+ yaml `stage17_scope` + L2 `forwarding-persistence §18` + L1 README/physical + 本双语文档 | ✅ 完成（§3.1 实际结果 + §4 状态 + decision/yaml/L2 同步）|
| MI17-007 | 3 构建验证 + 提交 | `mvn -f agent-bus/pom.xml test` green（179 + IT）；ArchUnit green；commit + push（experimental；含 agent-runtime pom 修复需协调）| 进行中（182 green 已验证；commit/push 切片 3）|

## 5. deferred + 风险（明示边界）

**风险（需用户/同事关注）：**

- **改 agent-runtime 同事模块**（MI17-001）：a2a client transport version missing 是 pre-existing build bug，修复明确正确，但 agent-runtime 由同事维护。建议：① 作为独立 bugfix 提交（commit message 说明 pre-existing + 照 agent-bus 范式），知会同事；或 ② 在 root pom dependencyManagement 补这两个 client transport 的管理（更彻底，但改 root 影响全仓）。Stage 17 取最小修复（agent-runtime pom 显式 version）。
- **agent-runtime 完整 build green 未验证**：version missing 是唯一 pom 错误，但 compile/test 是否 green 待 0b 切片验证（依赖下载慢是环境因素）。若 agent-runtime 自身有 compile 问题，Stage 17 前置范围扩大。
- **parent 全仓一致性**：本次只升 agent-bus（0.1.0→0.2.0）。其他模块（agent-client/agent-middleware/agent-execution-engine/agent-evolve）的 parent version 需确认；若仍 0.1.0，reactor 全量构建可能有版本错配。Stage 17 范围只动 agent-bus + agent-runtime pom，其他模块 parent 一致性作为独立 tech debt 项。
- **依赖下载环境**：agent-runtime 首次 build 需拉取 `pulsar-client`/`agent-core-java`/`bouncycastle` 等大依赖，经 `~/.m2/settings.xml` 认证代理可能超时。需离线包预置或多次重试。

**deferred（明示边界，不在 Stage 17 范围）：**

- **不裁决 T1 vs C3 异步哲学张力**：Stage 17 IT 用 T1 投递绑定（Stage 15 已选）+ 测试 TickSource（无真实 scheduler，§6.1），**不裁决** Stage 13 push/pull/MQ 最终模型（仍 H2/H3）。
- **`REMOTE_TASK_FAILED` non-retryable 码**：handler 返回 `FAILED`/`CANCELED`/`REJECTED` 仍保守 `RETRY/RECEIVER_UNAVAILABLE`（沿用 Stage 15 tradeoff），精确 non-retryable 码 deferred。
- **registry 集成的 resolver 生产实现**：IT 用 `MapEndpointResolver` 硬编码真实 port，生产 resolver 由 registry 集成实现（Stage 3 设计态）。
- **真实 scheduler / polling / 多 worker**：IT 用测试 `TickSource` 驱动单 worker tick，真实调度/并发分片 deferred（§6.1）。
- **push/pull/MQ 最终裁决**：仍 H2/H3。
- **§6.2 守恒**：IT 不引入 concrete broker/MQ、不写 payload 正文/token 流/Task execution state 进 record、不跨租户回退。

## 相关文档

- Stage 16 计划：[`stage15-review-and-stage16-plan`](agent-bus-stage15-review-and-stage16-plan.md)（断路器接入 worker）。
- C3 裁决：[`agent-bus-forwarding-runtime-decision`](../review-packets/agent-bus-forwarding-runtime-decision.md)（`adopted-c3`；§8 Stage 17 许可段待加）。
- runtime 契约：[`ICD-Agent-Bus-Forwarding-Runtime`](../../05-contracts/human-readable/ICD-agent-bus-forwarding-runtime.md)（Stage 17 边界条 + Open Issues）。
- 持久化 L2：[`forwarding-persistence`](../../../../architecture/L2-Low-Level-Design/agent-bus/forwarding-persistence.md)（Stage 17 决策：端到端集成验证，§18 待加）。
- agent-runtime 启动 API：`agent-runtime/src/main/java/com/huawei/ascend/runtime/app/{RuntimeApp,RuntimeHost,RunningRuntime,LocalA2aRuntimeHost}.java`；范式 `agent-runtime/src/test/java/.../app/RuntimeAppTest.java`（StubHandler + 编程式启动）。
