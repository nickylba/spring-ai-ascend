# 深读导引 R3 — Run 内核与幂等

> 对应风险登记册 `docs/governance/risk-register.md` R3。目标读者：需要达到 L3（实现级）
> 理解的工程师 — 能徒手修改本区、能独立评审 AI 给出的修复、能向审计员解释根因。
> 本导引压缩你的阅读路径，不替代读代码。预计投入：0.5–1 天专注阅读。
>
> 范围：`agent-runtime` 模块 `runtime/run/`（DFA、CAS）、`runtime/idempotency/`、
> `engine/a2a/IdempotentRequestHandler.java`、`A2aAgentExecutor` 的 Run 追踪方法。

## 1. 设计意图

这片区域是平台事务一致性的全部根基。平台**刻意没有**分布式事务管理器；它对外的一致性
承诺是几个小机制的代数组合，而这几个机制全部住在这里：

- **Run 是执行记录的唯一形态**（authority: ADR-0020，`docs/adr/locked/0020-runlifecycle-spi-and-runstatus-formal-dfa.md`）。
  `Run` 是不可变 record，每次状态变化产生新副本；`Run.withStatus(...)` 是**唯一**的状态
  变更入口，内部强制调用 `RunStateMachine.validate(from, to)`。任何跳出 DFA 边集的状态
  变化都是数据损坏，不是"另一种情况"——直接抛 `IllegalStateException`。
- **DFA 边集**（与 `architecture/docs/L0/ARCHITECTURE.md` §4 #20 逐字一致）：
  `PENDING → RUNNING | CANCELLED`；`RUNNING → SUSPENDED | SUCCEEDED | FAILED | CANCELLED`；
  `SUSPENDED → RUNNING | EXPIRED | FAILED | CANCELLED`；`FAILED → RUNNING`（重试，
  `attemptId` +1）；`SUCCEEDED / CANCELLED / EXPIRED` 终态。自环非法——自环会掩盖丢失更新，
  幂等语义（如重复 cancel 返回 200）必须由调用方处理，不进 DFA。
- **乐观锁先行**（CAS 两阶段迁移，代码注释引 ADR-0106；注意：该 ADR 文件本体已随设计语料
  清理下线，其规范内容现存于 `ARCHITECTURE.md` §4 #20——核对引用时以 §4 #20 为准）。
  `Run.version` 默认 0，由仓储在 `save` 时 +1；`InMemoryRunRepository` 在开发层就实现了与
  Postgres 层相同的 stale-save 拒绝语义（`RunRepository.OptimisticLockException`），让所有
  上层代码提前活在 CAS 世界里，Postgres 落地时无行为迁移。
- **幂等以 `(tenantId, messageId)` 为键**（authority: ADR-0027，
  `docs/adr/locked/0027-idempotency-scope-w0-header-validation.md`——该 ADR 当年裁定 W0 只做
  header 校验、去重推迟；如今的 `IdempotentRequestHandler` 即是其"推迟到 W1"承诺在 A2A 表面
  的兑现，claim/replay 语义沿用该 ADR 的定义）。装饰器模式：`IdempotentRequestHandler`
  包裹 SDK 的 `DefaultRequestHandler`，只拦截 `onMessageSend`，其余方法纯委托。
- **Run 追踪是尽力而为，不许遮蔽线上结果**。`A2aAgentExecutor.transitionRun` 把所有
  `RuntimeException` 吃掉只打日志——Run 记录滞后可以接受，让客户端拿不到响应不可接受。
  这是一个明确的优先级裁决，读代码时不要把它"修复"成向上抛。

接线位置：`boot/RuntimeAutoConfiguration.java` —— `runRepository()`（`@ConditionalOnMissingBean(RunRepository.class)`，
Postgres 层经同一接口替换）、`idempotencyStore()`、`a2aRequestHandler(...)`（在 bean 工厂里完成装饰）。

## 2. 攻击面 / 失效面

| # | 怎么被绕过 / 怎么静默失败 | 防线在哪（file:behavior） |
|---|---|---|
| A1 | 客户端不带 `messageId` → 完全绕过去重，重发即重执行 | 设计如此（无键不可去重）：`IdempotentRequestHandler.onMessageSend` 对 null/blank 直接透传；客户端 SDK 侧 `SendSpec` 自动生成 messageId 兜底（见手册 §客户端） |
| A2 | 跨租户重放：tenant-A 用 tenant-B 的 messageId 拿到 B 的 task | `InMemoryIdempotencyStore`：键是 `(tenantId, key)` 复合 record；且 tenantId 取自 `ServerCallContext` 中传输层认证写入的 `A2aAgentExecutor.TENANT_STATE_KEY`，**压过**客户端自报的 params.tenant（`A2aAgentExecutor.metadata` 的优先级链） |
| A3 | claim 泄漏：delegate 抛异常但 claim 未释放 → 该 messageId 永久 IN_FLIGHT，合法重试被永久拒绝 | `IdempotentRequestHandler` 的 `catch (RuntimeException)` 统一 `store.release`；`A2AError` 虽出现在 throws 子句但继承 `RuntimeException`，逃不出这个 catch（见 §4 陷阱史） |
| A4 | replay 指向已消失的 task → 客户端拿到空响应 | `IdempotentRequestHandler.replayTask` 返回 null 时 `release` 后递归重入（重新 claim 为 ACQUIRED 并真正执行），不会用"无"作答 |
| A5 | 结果不是 `Task`（如直接回 `Message`）却被 complete → replay 无物可放 | `onMessageSend` 只对 `result instanceof Task` 调 `store.complete`，否则 `release`——代价是此类 send **不幂等**，重试会重执行（诚实的取舍，写在行为里） |
| A6 | 绕过 DFA 直接 `save(new Run(...))` 手搓状态 | `RunRepositorySaveGuardTest`：扫描所有 import 了 `RunRepository` 的生产文件，`.save(` 实参必须是 `Run.create(` 或 `*.withStatus(`；并带反空洞断言（见 §3） |
| A7 | 并发丢失更新：HTTP cancel 与执行线程的终态写互踩，后写者赢 | `InMemoryRunRepository.save` 的 `compute` 内版本比对：stale 一方抛 `OptimisticLockException`；执行线程侧由 `transitionRun` 捕获记日志，cancel 结果得以保留 |
| A8 | `message/stream` 不去重 | 明确不防：`onMessageSendStream` 纯委托。理由写在类 javadoc——A2A 已有 `SubscribeToTask` 供重连在跑的 task。评审 AI 修复时警惕有人"顺手"给流式也加去重 |
| A9 | Run 追踪静默漂移：`transitionRun` 吞掉异常后 Run 状态与 task 状态不一致 | 仅有日志（`[A2A] run transition failed runId=...`）；e2e 侧 `awaitRunStatus` 以限时轮询吸收窗口。这是已知最薄的防线——排障时先查这条日志 |
| A10 | `IN_FLIGHT` 拒绝复用 `A2AErrorCodes.INVALID_REQUEST`，客户端无专用错误码可分支 | 消息文本含 "duplicate message/send is still executing"；I5 场景断言依赖该文本。改文案会断链 |

## 3. 支撑不变量清单

以下测试类全部核实存在；坏一条对应抓住一类回归：

- `com.huawei.ascend.runtime.run.RunStateMachineTest`
  - `dfaIsExactlyTheSpecifiedEdgeSet` — 7×7 全笛卡尔积穷举：11 条合法边逐一放行，其余全部
    断言抛 `IllegalStateException`。**双向**钉死边集——多加一条边和少一条边都会红。
  - `exactlyThreeStatesAreTerminal` — 终态恰为 SUCCEEDED/CANCELLED/EXPIRED 三个。
- `com.huawei.ascend.runtime.run.RunTest`
  - `withStatusValidatesTheDfa` — `withStatus` 是唯一变更路径且过 DFA。
  - `retryIncrementsAttemptCounter` — FAILED→RUNNING 使 `attemptId` 2（该边的存在性钉子，见 §4）。
  - `tenantIsMandatoryOnEveryRun` — null/blank tenant 在构造期即炸。
  - `createCapturesMdcTraceIdAndCopiesPreserveIt` / `createWithoutMdcTraceIdLeavesItNull` — trace_id 来自 MDC 且随副本保留。
- `com.huawei.ascend.runtime.run.InMemoryRunRepositoryTest`
  - `staleSaveIsRejected` — CAS 契约：从旧版本发起的 save 抛 `OptimisticLockException`（A7 的钉子）。
  - `saveBumpsVersionAndFindReturnsPersistedState`、`findByTenantAndTaskIsTenantScoped`。
- `com.huawei.ascend.runtime.idempotency.InMemoryIdempotencyStoreTest`
  - `firstClaimAcquiresSecondIsInFlightCompletedReplays` — claim/in-flight/replay 三角的状态机。
  - `keysAreTenantScoped`（A2 的钉子）、`releaseMakesTheKeyClaimableAgain`（A3 的钉子）。
- `com.huawei.ascend.runtime.engine.a2a.IdempotentRequestHandlerTest`
  - `retriedSendReplaysTheCreatedTask` — 重试零执行，`verify(delegate, times(1))`。
  - `replayReflectsLatestTaskState` — 重放的是 task **当前**状态，不是发送时刻快照。
  - `dedupKeyIsTenantScoped`、`sendWithoutMessageIdIsNotDeduplicated`（A1 的诚实化）。
  - `failedSendStaysRetryable` — RuntimeException 失败后 claim 释放、重试真执行。
  - `a2aErrorFailureStaysRetryable` — **A2AError-retryable 钉子**：首调抛 `A2AError(-32603)`，
    重试必须成功。它存在的全部意义是钉死"A2AError 继承 RuntimeException、逃不过 release"
    这一类型层级事实（§4 陷阱二）。
- `com.huawei.ascend.runtime.architecture.RunRepositorySaveGuardTest`
  - `productionRunSavesGoThroughDfaValidatedConstructionOnly` — A6 的守卫，且带**反空洞**断言：
    扫到的 save 点必须包含 `A2aAgentExecutor.java`，守卫"什么都没扫到"本身就是失败。
- `com.huawei.ascend.runtime.engine.a2a.A2aAgentExecutorTest`
  - `runRecordFollowsHappyPathLifecycle` / `runRecordFailsWhenExecutionFails` / `cancelMarksTrackedRunCancelled`
    — 执行器侧 PENDING→RUNNING→{SUCCEEDED|FAILED|CANCELLED} 的接线正确性。
  - `transportTenantOutranksClientDeclaredTenant` — A2 优先级链。
- 端到端（`examples/agent-runtime-a2a-llm-e2e` …`scenarios/consistency/`）：
  `DynamicPlanConsistencyScenarioTest` 的 I1–I5（真实 boot + 真实 wire + `EffectLedger` 账本代数）：
  `i1IdempotentReplayReExecutesNothing`、`i2FailedStepCommitsNothingAndRetrySkipsCheckpointedSteps`、
  `i3CancelDuringSuspensionCommitsNothingPastTheBarrier`、`i4PlanRevisionAbandonsTheOriginalRemainderCoherently`、
  `i5ConcurrentDuplicateSendExecutesExactlyOnce`（CyclicBarrier 双线程同发，账本恰好一次执行）。
  设计文档：`docs/logs/plans/2026-06-11-e2e-scenarios-and-consistency-test-design.md`。

## 4. 已知陷阱史

1. **W0 的"纸面幂等"**（ADR-0027，2026-05-12）。架构文档曾宣称 `IdempotencyHeaderFilter`
   做去重/缓存重放/409，而代码只校验 UUID 形状；`IdempotencyStore` 是从未被注入的孤儿
   `@Component`，且 filter 错误地套在 GET/HEAD/OPTIONS 上。裁决：文档收窄到实情、去重推迟。
   教训沉淀为本仓库"架构文本只描述已交付物"的纪律；真正的去重后来以
   `IdempotentRequestHandler` 落在 A2A 表面（commit `8847653b`）。
2. **A2AError claim 泄漏——评审误报**（PR-179，2026-06-11，commit `3f275bdb`）。外部评审断言：
   delegate 抛 `A2AError`（它出现在 throws 子句里，长得像 checked exception）时 catch
   `RuntimeException` 接不住，claim 泄漏、messageId 永久 IN_FLIGHT。**不可复现**：A2A SDK 的
   `A2AError` 实际 `extends RuntimeException`。处置不是改代码，而是把类型层级事实钉成测试
   `a2aErrorFailureStaysRetryable` + 源码注释——下一个评审者（人或 AI）再报这条时，红/绿一目了然。
   这是"用钉子终结争论"的范例：评审 AI 修复时若见它要删这个"看似冗余"的测试，拒绝。
3. **FAILED→RUNNING 边今日从线上不可达——已上报的设计缺口**（I2 场景注释，2026-06-11）。
   DFA 里 `FAILED → RUNNING` 带 `attemptId` 自增是为重试设计的（ADR-0020），单元层由
   `retryIncrementsAttemptCounter` 钉住。但当前 A2A 通路上**每次** `message/send` 开新 task，
   `A2aAgentExecutor.startRun` 总是 `Run.create(...)`（attempt 1 的新 Run）——生产代码没有任何
   调用点对 FAILED 的 Run 执行 `withStatus(RUNNING)`。所以线上重试呈现为"旧 Run 停在 FAILED +
   新 Run attempt 1"，而非"同一 Run attempt 2"。I2 的末段断言诚实记录了这个形状
   （`succeeded.attemptId()==1`，首 task 的 Run 永驻 FAILED）。该边是给未来 `RunLifecycle`
   SPI（cancel/resume/retry 端点，ADR-0020 设计、尚未落地）预留的。审计员问"attemptId 为什么
   总是 1"时，这就是标准答案；也是 Postgres 层落地时最容易被误删的"看似死代码"。
4. **ADR-0106 引用悬空**。`Run.java` / `RunRepository.java` javadoc 引 ADR-0106（version 字段
   两阶段迁移），但该 ADR 文件已随设计月语料清理不在 `docs/adr/` 中；规范内容由
   `ARCHITECTURE.md` §4 #20 承载（含 W0 已知局限与 `RunCancelDuringResumeRaceIT` 的 W2 安排）。
   核对契约时以 §4 #20 为准，勿因找不到文件而判定注释错误。

## 5. 深读路径

按序读，括号内是你要从每个文件带走的一件事：

1. `agent-runtime/src/main/java/com/huawei/ascend/runtime/run/RunStatus.java` +
   `RunStateMachine.java`（11 条边背下来；`validate` 抛而不是返回 false；自环为何非法）。
2. `run/Run.java`（不可变 record；`withStatus` 是唯一变更口且做 attempt 自增；`withVersion`
   是包私有的——只有仓储能碰 version；`create` 从 MDC 取 trace_id）。
3. `run/RunRepository.java` + `InMemoryRunRepository.java`（CAS 语义浓缩在 `compute` 闭包的
   版本比对里；`save` 返回带新版本的副本——调用方必须用返回值续作，这是 stale-save 的根因）。
4. `idempotency/IdempotencyStore.java` + `InMemoryIdempotencyStore.java`（ACQUIRED/IN_FLIGHT/REPLAY
   三角；`putIfAbsent` 的原子性 = Postgres 唯一约束的开发层等价物）。
5. `engine/a2a/IdempotentRequestHandler.java`（逐行读 `onMessageSend`：claim 三分支、
   task-vanished 的 release+递归、非 Task 结果的 release、catch 块的 release；tenant 的来源）。
6. `engine/a2a/A2aAgentExecutor.java`（只读 Run 相关：`startRun` 两次 save、`transitionRun`
   的吞异常裁决、`drainResults` 的强制终态、`cancelRuns` 的遍历+逐条 best-effort、
   `runStatusOf` 的 Type→RunStatus 映射）。
7. `boot/RuntimeAutoConfiguration.java` 的 `runRepository()` / `idempotencyStore()` /
   `a2aRequestHandler(...)`（替换点在哪、装饰发生在哪）。
8. §3 列出的全部测试类，按上面顺序对照（每读完一个生产文件立刻读它的钉子）。
9. `examples/agent-runtime-a2a-llm-e2e/src/test/java/.../scenarios/consistency/` 四个文件：
   `EffectLedger`（账本即真相：`(runId, attemptId, stepId, effectType, payloadHash)`）、
   `ScriptedPlanHandler`（FAIL_ONCE 在效果提交**前**抛——失败原子性的实现处；checkpoint 经
   `replaceAgentState` 走平台缝）、`PlanStep`、`DynamicPlanConsistencyScenarioTest`
   （I1–I5 如何把"事务一致"翻译成账本代数 + DFA 历史断言）。
10. 佐证文档：ADR-0020、ADR-0027、`ARCHITECTURE.md` §4 #20、
    `docs/logs/plans/2026-06-11-e2e-scenarios-and-consistency-test-design.md`、
    `docs/developer-handbook.cn.md` 的幂等/Run 小节。

### 自检：改这三处会发生什么（先答再看）

**Q1 — 把 `InMemoryRunRepository.save` 里的 `compute` 版本比对删掉，改成无条件 `put`？**
A：`staleSaveIsRejected` 立刻红。更隐蔽的是 A7 复活：cancel 线程与执行线程同时写同一 Run 时
回到 last-write-wins——已 CANCELLED 的 Run 可被执行线程改写成 SUCCEEDED；I3 的
`awaitRunStatus(..., CANCELLED)` 会间歇性超时（典型 flaky 形态，不是必现红）。同时
Postgres 层落地时开发层与生产层语义分叉，违反"同一接口同一契约"的迁移设计。

**Q2 — 删掉 `IdempotentRequestHandler` catch 块里的 `store.release`？**
A：`failedSendStaysRetryable` 与 `a2aErrorFailureStaysRetryable` 双红。生产后果：任何一次
瞬态失败（LLM 超时、上游 503）把该 `(tenant, messageId)` 永久钉死在 IN_FLIGHT，客户端按
`retryable=true` 指引重试时永远收到 "duplicate message/send is still executing"——
丢失执行且不可自愈，只能换 messageId（对客户端语义即"换了一笔请求"）。这正是金融场景里
最贵的失效形态。

**Q3 — 给 `RunStateMachine` 加一条 `SUCCEEDED → RUNNING` 边（"支持重跑成功的任务"）？**
A：`dfaIsExactlyTheSpecifiedEdgeSet` 红（穷举断言非法边必须抛）；`exactlyThreeStatesAreTerminal`
也红（SUCCEEDED 不再是终态，`Run.isTerminal` / `A2aAgentExecutor.transitionRun` 的终态短路、
`cancelRuns` 的 `!run.isTerminal()` 过滤全部连锁变义）。正确做法是新 Run 新 attempt
（今日线上形状）或走 ADR-0020 的 RunLifecycle 重试路径，永远不是松动 DFA。

**Q4（附加）— `ScriptedPlanHandler.FAIL_ONCE` 的 throw 挪到 `commit(...)` 之后？**
A：I2 红：账本里出现失败步骤的幻影记录（`ledger.stepIds()` 不再是 `["s1"]`），session memory
多一条幻影 turn——这正是"失败原子性 = 效果提交前抛"这一约定的反向证明。
