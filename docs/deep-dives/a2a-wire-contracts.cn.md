# 深读导引 R4 — A2A wire 契约

> 对应风险区：`docs/governance/risk-register.md` R4。目标读者：需要达到 L3
> （实现级）理解的负责人 — 徒手改、独立评审 AI 修复、向审计员讲清根因。
> 本文压缩你的阅读路径，不替代读码。预计 0.5–1 天。

## 1. 设计意图

**协议表面钉死在 OSS SDK，平台绝不发明自己的 A2A 序列化。** 整个 reactor
钉同一个 `org.a2aproject.sdk` 版本（当前 `1.0.0.CR1`），facade 转发器是
字节级透传（A2A-NO-REWRITE，见 `docs/developer-handbook.cn.md` 承诺面一节）。
这个决定的代价是：SDK 的缺陷成为平台的缺陷，平台只能在自己的两端**绕行**
而不能改写协议 — 本区的全部复杂度都是这笔代价的利息。客户端 SDK 的存在
本身由 ADR-0162 裁决：原始协议客户端把"哪个事件结束一轮"的判断留给每个
调用方，平台必须收编这份语义。

**`A2aAgentExecutor` 是唯一的语义翻译缝。** 框架中立的
`AgentRuntimeHandler` 结果流（`OUTPUT`/`COMPLETED`/`FAILED`/`INTERRUPTED`）
在 `route(...)` 里一对一映射到 A2A 任务生命周期（`addArtifact`/
`complete`/`fail`/`requiresInput`），同时驱动 Run DFA
（`runStatusOf(...)`：`INTERRUPTED → SUSPENDED`）。租户从 call-context
state 取（`TENANT_STATE_KEY`），**压过**客户端自报的 metadata —
这是 ADR-0040 交叉校验在执行器侧的落点（深究见 R1 导引）。

**`runStatus` 消息元数据约定是双层方言。** 终态形式
（`runStatus: completed/failed/canceled/rejected`）不是平台发明的：它是
上游 OpenJiuwen 运行时方言的小写枚举名，e2e 直连时客户端必须认识
（注意 `canceled` 是单 l；平台自己的 `RunStatus` 枚举是双 l 的
`CANCELLED`，两个命名空间，别"统一"它们）。`input-required` 形式才是
平台自己的约定，为绕行 SDK 缺陷而生（§2 缺陷一）— 它刻意**镜像**终态
形式，让客户端用同一种读法处理两者。

**客户端的三谓词分类是本区的另一半契约。** `A2aEvents` 把事件分成
run-terminal（`isTerminal`）、awaiting-input（`isAwaitingInput`）、
两者之并 turn-ending（`isTurnEnding`）。`streamText` 在 turn-ending 上
完成而不是 terminal 上 — 因为挂起的 run 的 SSE 流被运行时**保持打开**，
等 terminal 等到的只有超时。`isFailureError(error, sawTurnEnd)` 是
post-terminal-cancellation 规则：SDK 在终态后正常取消订阅，turn-end 之后
的 `CancellationException` 是正常收尾，之前的是真传输失败。

## 2. 攻击面 / 失效面

先讲发现方法，因为它就是 L3 要学的东西。三个 SDK 缺陷的接口签名与文档
**全部合法**，评审（人与 AI）均未发现 — 所有 agent 继承同一错误框架。
击破它的是定位级联：**台账（哪个不变量红了）→ 裸线探针（curl/stub 直读
SSE 帧，看 wire 上实际有什么）→ 字节码（`javap`/常量池确认 SDK 内部
行为）**。复现第三步无需 IDE：
`unzip -p ~/.m2/.../a2a-java-sdk-server-common-1.0.0.CR1.jar org/a2aproject/sdk/server/tasks/AgentEmitter.class | strings`。
记录于 `docs/logs/plans/2026-06-11-ai-era-engineering-operating-model.cn.md` §9 案例 2/3。

| # | 失效方式 | 防线位置（file:behavior） |
|---|---|---|
| 1 | **SDK 缺陷一：status-update builder 丢弃 isFinal。** spec 的 `TaskStatusUpdateEvent` 有 `isFinal` 字段，但 `AgentEmitter.updateStatus` 构建事件时只调 builder 的 `taskId`/`status`/`build`，**从未调 isFinal setter**（字节码常量池可证）— wire 上一切状态更新都非 final，流式客户端等 input-required 的 final 标志会挂到超时、丢掉提示词 | `A2aAgentExecutor.routeInterrupted`：提示词骑在 Message 上，带 `runStatus=input-required` metadata；客户端 `A2aEvents.isAwaitingInput` 同时认状态形式与消息形式 |
| 2 | **SDK 缺陷二：cancel 响应 proto-oneof 不对称。** 服务端用 SDK 的 `JsonUtil` 序列化 `CancelTaskResponse`，产出 oneof 包装 `{"result":{"task":{...}}}`；OSS 客户端 transport 的 cancel 解析器却期待裸 `Task`，直接拒绝 | `AscendA2aClient.cancelTask`：绕开 OSS transport，手发 JSON-RPC POST，**两端都用 `JsonUtil`** — 对称性由构造保证。这是方言对称原则的范本：序列化器是契约的一部分 |
| 3 | **SDK 缺陷三：`requiresInput(true)` 毒化 emitter 终态 CAS。** 修缺陷一最便宜的一行（把 input-required 标成 final）是双重失败：builder 照样丢弃标志（wire 不变、客户端照挂），且 `updateStatus(..., true)` 对 `terminalStateReached` 做 CAS — 此后同一执行内任何状态更新抛 `IllegalStateException("Cannot update task status - terminal state already reached")`，INTERRUPTED 后还有结果的合法流变成搁浅任务。本仓第一刀真的这么改过且失败 | `A2aAgentExecutor.routeInterrupted`：调用**无参** `emitter.requiresInput()`（非 final）+ 消息约定 |
| 4 | 处理器流干涸却无终态结果（上游零事件应答）→ 任务永远 WORKING，轮询客户端挂死 | `A2aAgentExecutor.drainResults`：`!terminalRouted` 时强制 `emitter.complete()` + Run→SUCCEEDED |
| 5 | `startWork` 之后异常逃逸（上下文构造对 wire 可控输入抛错）→ 任务搁浅 WORKING | `A2aAgentExecutor.execute`：try/catch 全包，`failTask` 带 `RuntimeErrorCode.classify` 的结构化错误（TextPart + DataPart + metadata 三载体） |
| 6 | 流中途失败只断传输 → 客户端分不清 agent 失败与断网 | `A2aJsonRpcController.handleStream`：`onErrorResume` 以 JSON-RPC error 帧收尾；同理畸形 body→`JSON_PARSE`、未知方法→`METHOD_NOT_FOUND`，绝不回 `200 {}` |
| 7 | "清理" `routeInterrupted` 的 `Map.of("runStatus", "input-required")` — 编译过、阻塞路径测试全绿 | 流式 HITL 客户端静默退化为挂到超时。防线只有测试：`AscendA2aClientStubServerTest.streamTextReturnsPromptPromptlyWhenAgentRequiresInput` + `DynamicPlanConsistencyScenarioTest.i3...` |
| 8 | "规范化" `A2aEvents.TERMINAL_RUN_STATUSES` 的拼写（删掉单 l `canceled` 或别名 `cancelled`） | 直连 OpenJiuwen 方言的客户端取消后永远等不到 turn-end。`A2aEventsTest.recognizesEveryRuntimeTerminalRunStatusIncludingCanceledSpelling` |
| 9 | accepted 入队回执混进用户可见答案 | `A2aEvents.textFrom`：跳过 `metadata.accepted=true` 的消息 |
| 10 | 终态 Task 快照触发 OSS 客户端自动退订，被误读为传输失败 | `A2aEvents.isTerminal` 把 final 状态的 `Task` 快照也判终态，配合 `isFailureError` 的 sawTurnEnd |

## 3. 支撑不变量清单

以下测试类全部在源码树核实存在。

**`A2aEventsTest`**（`springai-ascend-client/src/test/java/com/huawei/ascend/client/`）

| 测试方法 | 断了说明什么坏了 |
|---|---|
| `recognizesEveryRuntimeTerminalRunStatusIncludingCanceledSpelling` | 终态 `runStatus` 方言（含单 l 拼写）失认 — 上游直连客户端挂死 |
| `inputAndAuthRequiredEndTheTurnWithoutEndingTheRun` | turn-ending 与 run-terminal 的区分崩塌 — HITL 提示词丢失或挂起 run 被误判结束 |
| `recognizesFinalTaskSnapshotAsTerminal` / `recognizesFinalA2aTaskStatusUpdateAsTerminal` | 两种终态事件形状之一失认 → 退订被误判为失败 |
| `recognizesAwaitingInputOnTaskSnapshot` | 非流式路径（Task 快照应答）的 HITL 识别失效 |
| `cancellationIsNormalCompletionOnlyAfterTerminalEvent` | post-terminal-cancellation 规则破坏 — 要么吞掉真传输失败，要么把正常收尾当失败抛 |
| `excludesAcceptedMessageFromUserVisibleAnswer` / `extractsTextFromAllA2aStreamingEventShapes` / `extractsTextFromNonStreamingTaskResult` | 答案文本提取在某种事件形状上丢字或混入回执 |

**`AscendA2aClientStubServerTest`**（同模块）— 帧级 wire 契约，stub 按真实
运行时的帧序回放：

| 测试方法 | 断了说明什么坏了 |
|---|---|
| `streamTextReturnsPromptPromptlyWhenAgentRequiresInput` | **HITL 核心案例**：非 final input-required + 流保持打开时，提示词没有及时（<5s，远小于超时）返回 — 缺陷一的绕行失效 |
| `streamTextCompletesOnTerminalEventAndExcludesAcceptedAck` | 终态完成或回执排除回归 |
| `streamTextDoesNotReportAwaitingInputForACompletedRun` | `awaitingInput()` 误报 — 调用方会对已完成的 run 发无意义的续答 |
| `sendTextSendsAuthAndTraceHeadersAndSurfacesTraceresponseAndText` / `agentCardIsFetchedWithAuthAndTraceHeaders` | 认证/追踪头没真正过线（只是被配置了） |

**`A2aAgentExecutorTest`**（`agent-runtime/.../engine/a2a/`）：
`malformedRequestContext_failsTaskInsteadOfStrandingWorking`、
`emptyResultStream_finalizesTask`、`outputOnlyStream_finalizesTask`（失效面 4/5）、
`submitPrecedesStartWork`、`streamingOutputFormsSingleAppendingArtifact`
（单一成长 artifact 而非碎片）、`failedResult_carriesErrorReasonToTheWire`、
`executionException_carriesStructuredErrorDataPart`、
`transportTenantOutranksClientDeclaredTenant`、`cancelMarksTrackedRunCancelled`、
`agentStateKeyFollowsContextIdAcrossTasks`（会话键跟 contextId 走，
checkpointer 恢复依赖它）。

**`A2aJsonRpcControllerTest`**（`agent-runtime/.../boot/`）：
`streamingResponseDataIsJsonRpcEventReadableByA2aSdkClient`（**方言对称的
正面证明** — SSE 帧必须能被 SDK 客户端解析器读回）、
`midStreamFailureEndsWithJsonRpcErrorFrame`、
`malformedRequestBodyReturnsJsonRpcParseErrorNotEmptyObject`、
`handlerA2aErrorAnswersJsonRpcErrorEchoingRequestId`、
`sseParseFailureAnswersSingleErrorFrame`、`tenantHeaderFlowsIntoServerCallContext`。

**端到端**：`ClientTelemetryE2eTest.clientSpanAndServerTraceresponseShareOneTraceId`
（client span 与服务端 traceresponse 共享同一 trace-id，跨真实启动的运行时）；
`DynamicPlanConsistencyScenarioTest.i3CancelDuringSuspensionCommitsNothingPastTheBarrier`
（**整条 HITL + 对称 cancel 链路**走真实 wire：streamText 拿到提示 →
`awaitingInput()` → Run SUSPENDED → `cancelTask` 解析出 CANCELED 快照 →
Run CANCELLED，挂起点之后零副作用）。

## 4. 已知陷阱史（本分支可考）

- **HITL 三连击**（§2 缺陷 1/3 的学费单）：`bafa5ae4` — `streamText`
  只在 run-terminal 上放行 latch，input-required 阻塞整个超时并抛异常、
  丢掉提示词；`3c782d19` — 运行时侧立 `runStatus=input-required` 消息
  约定（提交信息记录了 builder 丢 isFinal 的根因）；`fac3d5c1` —
  客户端认下该约定 + cancel 改为两端 `JsonUtil` 对称（提交信息记录了
  proto-oneof 不对称根因）。中间被废弃的"最便宜一刀"即缺陷 3。
- `5ebe5e6f` — 处理器流无终态结果时任务永远 WORKING（失效面 4 的来历）。
- `77971d46` — A2A 失败曾应答 `200 {}`，客户端无从分辨；改为 JSON-RPC
  error 响应（失效面 6 的来历）。
- `e1235405` — 任务失败原因曾不上 wire（只有状态没有原因），审计不可解释。
- `21c46b00` — 曾先 WORKING 后 SUBMITTED（状态序错乱）；流式输出曾形成
  碎片 artifact 而非单一成长 artifact。
- `d0b1d725` — 框架会话键曾按 taskId（A2A 每次 send 开新 task），
  checkpointer 恢复永远不触发；改为按 contextId/session。
- 发现方法论的存档：`docs/logs/plans/2026-06-11-ai-era-engineering-operating-model.cn.md`
  §9 案例 2、3、6（案例 6 是字节码裁决的另一次应用：五分钟实锤一个评审误报）。

## 5. 深读路径（按序，约 0.5–1 天）

1. `docs/developer-handbook.cn.md` 的 A2A 承诺面与"原始 A2A 作为兜底"
   两节 — 先拿到对外承诺的边界。
2. `agent-runtime/src/main/java/com/huawei/ascend/runtime/engine/a2a/A2aAgentExecutor.java`
   — 全文精读。重点：`drainResults` 的强制收尾、`routeInterrupted` 的
   大段注释（缺陷一的官方记录）、`runStatusOf` 的 DFA 映射、`metadata()`
   的租户优先级。
3. `agent-runtime/src/main/java/com/huawei/ascend/runtime/boot/A2aJsonRpcController.java`
   — 同一路径两个 `@PostMapping` 按 produces 分流；每条 catch 分支对应
   哪个 `A2AErrorCodes`；`handleStream.onErrorResume` 的 error 帧。
4. `springai-ascend-client/src/main/java/com/huawei/ascend/client/A2aEvents.java`
   — 三谓词 + `isFailureError`；逐条读 javadoc，它们是 wire 行为的存档。
5. `springai-ascend-client/src/main/java/com/huawei/ascend/client/StreamTurnCollector.java`
   — latch 编排：事件/错误在 transport 线程到达，verdict 在 transport
   关闭后才下（保证 post-turn-end cancellation 已被分类）。
6. `springai-ascend-client/src/main/java/com/huawei/ascend/client/AscendA2aClient.java`
   — `cancelTask` 的"Deliberately NOT"注释（缺陷二的官方记录）；
   `close()` 用 `shutdownNow` 的理由。
7. `springai-ascend-client/src/test/java/com/huawei/ascend/client/StubA2aServer.java`
   — 真实帧序长什么样（accepted 回执 → 提示消息 → 非 final 状态 →
   流保持打开）。这是你的裸线探针参照物。
8. `A2aEventsTest` + `AscendA2aClientStubServerTest` — 把 §3 的表对着代码
   过一遍。
9. `examples/agent-runtime-a2a-llm-e2e/.../scenarios/consistency/DynamicPlanConsistencyScenarioTest.java`
   的 I3 — 全链路收束。
10. 动手复现字节码探针：对 `AgentEmitter.class` 跑 §2 开头的命令，亲眼
    确认 builder 调用序列里没有 isFinal setter、`terminalStateReached`
    与那条 IllegalStateException 文案存在。

### 自检：改这三处会发生什么（先答再看）

**Q1：把 `routeInterrupted` 里的 `emitter.requiresInput()` 改成带
final 标志的形式（`requiresInput(message, true)`），顺手删掉"多余的"
`runStatus` metadata？**
答：双重失败。wire 上依然没有 final 标志（builder 从未被告知，字节码
可证），而 metadata 没了 — 流式 HITL 客户端挂到超时、丢提示词；同时
`terminalStateReached` CAS 被置位，同一执行内任何后续状态更新（如
INTERRUPTED 之后还有终态结果的合法流，或异常路径的 `emitter.fail`）抛
`IllegalStateException`，任务搁浅。红测试：
`streamTextReturnsPromptPromptlyWhenAgentRequiresInput`、I3。

**Q2：把 `A2aJsonRpcController.streamingResponseJson` / `handleBlocking`
的 `JsonUtil` 换成 Jackson `ObjectMapper`（"项目其他地方都用它"）？**
答：方言对称性破裂。SDK 客户端解析器期待 `JsonUtil` 的 proto-JSON 形状
（oneof 包装、枚举/字段命名规则），Jackson 默认输出对不上 — 客户端
解析失败，且 `AscendA2aClient.cancelTask` 的"两端同一序列化器"前提同时
失效。红测试：`streamingResponseDataIsJsonRpcEventReadableByA2aSdkClient`，
以及 I3 的 cancel 段。

**Q3：把 `A2aEvents.TERMINAL_RUN_STATUSES` 里的 `"canceled"` 改成
`"cancelled"` 以"对齐"平台 `RunStatus.CANCELLED`？**
答：两个命名空间被错误合并。消息 metadata 上的终态值是上游 OpenJiuwen
方言的小写枚举名（单 l `canceled`）；平台枚举拼写与 wire 值无关。改后
直连上游方言的客户端在取消后收不到 turn-end，挂到超时。红测试：
`recognizesEveryRuntimeTerminalRunStatusIncludingCanceledSpelling`。

**Q4（附加）：删掉 `drainResults` 末尾的 `!terminalRouted` 强制
`complete()`（"处理器总会给终态的"）？**
答：上游零事件应答（真实发生过）让任务永远 WORKING，轮询与流式客户端
全部挂死，Run 永不终态。红测试：`emptyResultStream_finalizesTask`、
`outputOnlyStream_finalizesTask`。
