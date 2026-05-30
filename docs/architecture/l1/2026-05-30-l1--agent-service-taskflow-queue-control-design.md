# Agent Service Taskflow 模块设计终稿

> 版本：v1.2 接口冻结稿
> 日期：2026-05-30
> 服务：`agent-service`
> 模块名：`taskflow`
> Java 包根路径：`agent-service/src/main/java/com/huawei/ascend/service/taskflow/`
> Java 包名：`com.huawei.ascend.service.taskflow`

## 1. 模块定位

`taskflow` 是 `agent-service` 内部的 L3/L4 设计模块：

```text
agent-service
  1. access-layer
  2. session
  3. taskflow.queue
  4. taskflow.control
  5. runtime / engine
```

本模块只冻结 L3/L4 的接口、模型和边界。

```text
access-layer
  -> taskflow.control
  -> runtime / engine

session
  -> taskflow.queue

runtime / engine
  -> taskflow.control
```

模块职责：

1. `queue` 定义通用队列接口；
2. `queue` 不理解队列内容物类型；
3. `control` 定义 Task 控制接口、Task 状态和状态回写接口；
4. `control` 对 L1 提供 `runTask` / `resumeInput` / `cancelTask`；
5. `control` 对 L5 runtime 提供 `markRunning` / `markWaiting` / `markSucceeded` / `markFailed` / `markCancelled`；
6. `control` 调用 L5 runtime 时只依赖本模块自己的 `TaskRuntimeDispatcher`；
7. PR #100 的 `EngineDispatchSpi` 由 L5 runtime adapter 适配，不由 L4 直接依赖。

## 2. 命名与目录约束

模块名统一收敛为：

```text
taskflow
```

目录约束：

```text
agent-service/src/main/java/com/huawei/ascend/service/taskflow/
```

包名约束：

```text
com.huawei.ascend.service.taskflow
```

子模块约束：

```text
queue/
control/
```

外部接口目录遵从项目风格使用：

```text
spi/
```

不使用：

```text
api/
```

当前阶段不固定具体实现类逻辑；只固定接口、模型、目录和跨层调用边界。

## 3. 总体目录结构

```text
agent-service/src/main/java/com/huawei/ascend/service/taskflow/
  queue/
    spi/
      Queue.java
      QueueFactory.java
      QueueManager.java
      QueuePublisher.java
      QueueConsumer.java
      QueueQueryPort.java
      QueueSubscriptionPort.java
      QueueListener.java
      RuntimeQueueGateway.java
    model candidates

  control/
    spi/
      TaskControlClient.java
      TaskRuntimeDispatcher.java
      TaskStore.java
      TaskStatePolicy.java
      TaskView.java
      RunTaskCommand.java
      RunTaskResult.java
      ResumeInputCommand.java
      ResumeInputResult.java
      CancelTaskCommand.java
      CancelTaskResult.java
      MarkRunningCommand.java
      MarkWaitingCommand.java
      MarkSucceededCommand.java
      MarkFailedCommand.java
      MarkCancelledCommand.java
      MarkTaskResult.java
    model candidates
```

说明：

1. `queue/spi` 是 L3 对外稳定面；
2. `control/spi` 是 L4 对外稳定面；
3. `model candidates` 是本模块内部模型候选目录，下一轮实现时再落具体类；
4. 本文不固定任何实现类命名；
5. 本文不定义 L5 runtime 包结构。

## 4. Queue SPI 定义

### 4.1 Queue

路径：

```text
taskflow/queue/spi/Queue.java
```

定义：

```java
public interface Queue extends QueuePublisher, QueueConsumer, QueueQueryPort, QueueSubscriptionPort {

    QueueId id();

    QueueInfo info();

    RuntimeQueueGateway newRuntimeQueueGateway(RuntimeGatewaySpec spec);
}
```

功能注释：

1. `Queue` 是通用队列抽象；
2. `Queue` 不约束内容物类型；
3. `Queue` 不持有 Task 状态；
4. `Queue` 不解释 Runtime 信号；
5. `RuntimeQueueGateway` 是受限视角。

### 4.2 QueuePublisher

路径：

```text
taskflow/queue/spi/QueuePublisher.java
```

定义：

```java
public interface QueuePublisher {

    QueueItemKey push(Object value);
}
```

功能注释：

1. `push` 接收 `Object`；
2. Queue 不关心 `Object` 的业务类型；
3. 业务类型解释由调用方完成。

### 4.3 QueueConsumer

路径：

```text
taskflow/queue/spi/QueueConsumer.java
```

定义：

```java
public interface QueueConsumer {

    Optional<Object> poll(QueuePollRequest request);
}
```

功能注释：

1. `poll` 返回 `Object`；
2. Queue 不保证返回对象一定是 Task 事件；
3. Consumer 负责类型识别和过滤。

### 4.4 QueueFactory

路径：

```text
taskflow/queue/spi/QueueFactory.java
```

定义：

```java
public interface QueueFactory {

    Queue createQueue(QueueSpec spec);

    RuntimeQueueGateway createRuntimeQueueGateway(QueueId queueId, RuntimeGatewaySpec spec);

    void deleteQueue(QueueId queueId, QueueDeleteReason reason);
}
```

功能注释：

1. Session 创建 Queue 时调用 `createQueue`；
2. Runtime adapter 需要受限视角时调用 `createRuntimeQueueGateway`；
3. 删除 Queue 时调用 `deleteQueue`；
4. Factory 创建或删除 Queue 时通知 `QueueManager`。

### 4.5 QueueManager

路径：

```text
taskflow/queue/spi/QueueManager.java
```

定义：

```java
public interface QueueManager {

    void onQueueCreated(Queue queue);

    void onQueueDeleted(QueueId queueId, QueueDeleteReason reason);

    Optional<Queue> getQueue(QueueId queueId);

    Optional<Queue> findQueueBySession(String tenantId, String sessionId);

    Optional<QueueInfo> getQueueInfo(QueueId queueId);

    List<QueueInfo> listQueues(QueueQuery query);

    void registerListener(QueueId queueId, QueueListener listener);

    void log(QueueEvent event);

    void suspend(QueueId queueId, String reason);

    void resume(QueueId queueId);
}
```

功能注释：

1. `QueueManager` 是弱管理对象；
2. Queue 创建入口仍是 `QueueFactory`；
3. `QueueManager` 记录 Queue、查询 Queue、管理监听、记录日志；
4. `QueueManager` 支持按 `sessionId` 查找 Queue；
5. `Access` 不感知 `queueId`。

### 4.6 RuntimeQueueGateway

路径：

```text
taskflow/queue/spi/RuntimeQueueGateway.java
```

功能注释：

1. Runtime 侧只能看到受限 Queue Gateway；
2. Runtime 可以读取或写入授权范围内的队列数据；
3. Runtime 不通过 Gateway 改 Task 状态；
4. Task 状态回写必须走 L4 `TaskControlClient`。

## 5. Control SPI 定义

### 5.1 TaskControlClient

路径：

```text
taskflow/control/spi/TaskControlClient.java
```

定义：

```java
public interface TaskControlClient {

    CompletionStage<RunTaskResult> runTask(RunTaskCommand command);

    CompletionStage<ResumeInputResult> resumeInput(ResumeInputCommand command);

    CompletionStage<CancelTaskResult> cancelTask(CancelTaskCommand command);

    CompletionStage<MarkTaskResult> markRunning(MarkRunningCommand command);

    CompletionStage<MarkTaskResult> markWaiting(MarkWaitingCommand command);

    CompletionStage<MarkTaskResult> markSucceeded(MarkSucceededCommand command);

    CompletionStage<MarkTaskResult> markFailed(MarkFailedCommand command);

    CompletionStage<MarkTaskResult> markCancelled(MarkCancelledCommand command);
}
```

功能注释：

1. `TaskControlClient` 是 L4 唯一 Task 控制 SPI；
2. L1 调用 `runTask`、`resumeInput`、`cancelTask`；
3. L5 runtime adapter 调用 `mark*`；
4. Runtime / Engine 不再定义同职责 `TaskControlClient`；
5. Task 状态流转由 L4 内部实现。

### 5.2 TaskRuntimeDispatcher

路径：

```text
taskflow/control/spi/TaskRuntimeDispatcher.java
```

定义：

```java
public interface TaskRuntimeDispatcher {

    CompletionStage<TaskRuntimeDispatchResult> dispatchRun(TaskRuntimeDispatchRequest request);

    CompletionStage<TaskRuntimeDispatchResult> dispatchResume(TaskRuntimeDispatchRequest request);

    CompletionStage<TaskRuntimeDispatchResult> dispatchCancel(TaskRuntimeDispatchRequest request);
}
```

功能注释：

1. `TaskRuntimeDispatcher` 是 L4 调用 L5 runtime adapter 的端口；
2. 它不是 PR #100 `EngineDispatchSpi` 的替代品；
3. PR #100 合入后，由 Runtime adapter 把该端口桥接到 `EngineDispatchSpi`；
4. L4 不直接 import `EngineDispatchSpi`。

### 5.3 TaskStore

路径：

```text
taskflow/control/spi/TaskStore.java
```

功能注释：

1. `TaskStore` 是 Task 状态持久化端口；
2. 当前设计只冻结端口，不冻结内存或数据库实现；
3. 下一轮实现时再选择本地后端或持久化后端；
4. `TaskStore` 必须支持按 `sessionId` 查找当前可接续 Task。

### 5.4 TaskStatePolicy

路径：

```text
taskflow/control/spi/TaskStatePolicy.java
```

功能注释：

1. `TaskStatePolicy` 是 Task 状态机策略端口；
2. 下一轮实现时再落默认策略；
3. 终态 Task 不允许再运行；
4. `CANCELLING` Task 不接收新的用户输入。

### 5.5 TaskView

路径：

```text
taskflow/control/spi/TaskView.java
```

定义：

```java
public record TaskView(
        String tenantId,
        String sessionId,
        String taskId,
        String agentId,
        TaskState state,
        long revision,
        WaitingReason waitingReason,
        TaskFailureCode failureCode) {
}
```

功能注释：

1. `TaskView` 是对 L1 / L5 暴露的只读 Task 视图；
2. `revision` 用于后续并发控制；
3. L5 runtime adapter 不直接修改 Task。

## 6. Command 定义

### 6.1 RunTaskCommand

路径：

```text
taskflow/control/spi/RunTaskCommand.java
```

定义：

```java
public record RunTaskCommand(
        String tenantId,
        String sessionId,
        String taskId,
        String agentId,
        Object input,
        String idempotencyKey,
        Map<String, Object> metadata) {
}
```

字段说明：

| 字段 | 说明 |
|---|---|
| `tenantId` | 租户标识 |
| `sessionId` | L2 Session 返回给 L1 的会话标识 |
| `taskId` | 可选；用户显式指定历史 Task 时使用 |
| `agentId` | 外部传入，L1 透传给 L4 / L5 |
| `input` | 用户输入或 L1 归一后的意图对象 |
| `idempotencyKey` | 幂等键 |
| `metadata` | 调用元数据 |

### 6.2 ResumeInputCommand

路径：

```text
taskflow/control/spi/ResumeInputCommand.java
```

功能注释：

1. 用于继续等待用户输入的 Task；
2. 默认挂接当前 Session 下最后一个可接续 Task；
3. 用户显式指定 `taskId` 时优先使用指定 Task；
4. 当前 Task 为 `CANCELLING` 时不挂接输入。

### 6.3 CancelTaskCommand

路径：

```text
taskflow/control/spi/CancelTaskCommand.java
```

功能注释：

1. 用于请求取消 Task；
2. L4 接收取消意图；
3. L5 runtime adapter 执行实际取消；
4. 完成后由 Runtime adapter 调用 `markCancelled`。

### 6.4 Mark Commands

路径：

```text
taskflow/control/spi/MarkRunningCommand.java
taskflow/control/spi/MarkWaitingCommand.java
taskflow/control/spi/MarkSucceededCommand.java
taskflow/control/spi/MarkFailedCommand.java
taskflow/control/spi/MarkCancelledCommand.java
```

功能注释：

1. `MarkRunningCommand` 表示 Runtime 已开始执行；
2. `MarkWaitingCommand` 表示 Runtime 等待用户、确认或依赖；
3. `MarkSucceededCommand` 表示 Runtime 成功完成；
4. `MarkFailedCommand` 表示 Runtime 失败；
5. `MarkCancelledCommand` 表示 Runtime 已取消。

## 7. Model 定义

### 7.1 TaskState

路径：

```text
taskflow/control/TaskState.java
```

定义：

```java
public enum TaskState {
    CREATED,
    RUNNING,
    WAITING,
    PAUSED,
    CANCELLING,
    COMPLETED,
    FAILED,
    CANCELLED
}
```

状态约束：

1. 不定义 `QUEUED`；
2. 不定义 `WAITING_FOR_TOOL`；
3. 不定义 `EXPIRED`；
4. `COMPLETED`、`FAILED`、`CANCELLED` 是终态。

### 7.2 WaitingReason

路径：

```text
taskflow/control/WaitingReason.java
```

定义：

```java
public enum WaitingReason {
    USER_INPUT,
    USER_CONFIRMATION,
    DEPENDENCY
}
```

### 7.3 TaskFailureCode

路径：

```text
taskflow/control/TaskFailureCode.java
```

定义：

```java
public enum TaskFailureCode {
    AGENT_ID_INVALID,
    OUT_OF_DOMAIN,
    NOT_CURRENT_TASK,
    ENGINE_DISPATCH_REJECTED,
    RUNTIME_ERROR,
    CANCELLED_BY_RUNTIME
}
```

说明：

1. `AGENT_ID_INVALID` 由 L5 runtime 判断并回写；
2. `OUT_OF_DOMAIN` 表示 Runtime 判断输入不属于当前 Task；
3. `ENGINE_DISPATCH_REJECTED` 表示 Runtime adapter 未接受派发；
4. 失败码可在后续实现波次扩展。

### 7.4 Task

路径：

```text
taskflow/control/Task.java
```

建议字段：

| 字段 | 说明 |
|---|---|
| `tenantId` | 租户标识 |
| `sessionId` | 会话标识 |
| `taskId` | Task 标识 |
| `agentId` | Runtime 需要的 Agent 标识 |
| `state` | Task 当前状态 |
| `revision` | 状态版本 |
| `waitingReason` | 等待原因 |
| `failureCode` | 失败原因 |
| `detail` | 状态细节 |
| `createdAt` | 创建时间 |
| `updatedAt` | 更新时间 |

### 7.5 Queue Model

建议对象：

| 对象 | 说明 |
|---|---|
| `QueueId` | Queue 标识 |
| `QueueInfo` | Queue 元信息 |
| `QueueSpec` | Queue 创建参数 |
| `QueueEnvelope` | Queue 包装对象 |
| `QueueItemKey` | Queue 条目标识 |
| `QueuePollRequest` | 拉取请求 |
| `QueueQuery` | Queue 查询条件 |
| `QueueEvent` | Queue 管理事件 |
| `RuntimeGatewaySpec` | Runtime Gateway 创建参数 |

## 8. Runtime / Engine 衔接

PR #100 已定义 `engine.spi.EngineDispatchSpi`。本模块按以下方式衔接：

```text
taskflow.control.spi.TaskRuntimeDispatcher
  -> L5 runtime adapter
  -> engine.spi.EngineDispatchSpi
```

状态回写链路：

```text
runtime / engine
  -> L5 runtime adapter
  -> taskflow.control.spi.TaskControlClient.mark*
```

兼容原则：

1. `EngineDispatchSpi` 是 L5 runtime / engine 派发接口的实现参考；
2. `TaskRuntimeDispatcher` 只表达 L4 侧需要调用 Runtime 的最小端口；
3. 若 `TaskRuntimeDispatcher` 字段、状态语义或返回语义与 PR #100 冲突，下一轮刷新以 PR #100 的 `EngineDispatchSpi` 为准；
4. 字段差异由 L5 runtime adapter 吸收，不把 PR #100 的实现细节反向泄漏到 L4。

映射：

| taskflow 端口 | PR #100 engine 端口 |
|---|---|
| `dispatchRun` | `EngineDispatchSpi.enqueueExecution` |
| `dispatchResume` | `EngineDispatchSpi.enqueueResume` |
| `dispatchCancel` | `EngineDispatchSpi.enqueueCancel` |
| `markRunning` | Runtime adapter 回调 L4 |
| `markWaiting` | Runtime adapter 回调 L4 |
| `markSucceeded` | Runtime adapter 回调 L4 |
| `markFailed` | Runtime adapter 回调 L4 |
| `markCancelled` | Runtime adapter 回调 L4 |

约束：

1. L4 不直接 import `EngineDispatchSpi`；
2. L5 runtime adapter 才持有 `EngineDispatchSpi`；
3. L5 runtime adapter 不定义第二套 `TaskControlClient`；
4. L5 runtime adapter 只回调 L4 `TaskControlClient`；
5. Runtime 面向用户的输出仍返回给 Access。

## 9. 状态与输出映射

| Runtime 结果 | Taskflow 状态入口 | Task 结果 |
|---|---|---|
| 接受执行 | `markRunning` | `RUNNING` |
| 等待用户输入 | `markWaiting(USER_INPUT)` | `WAITING` |
| 等待用户确认 | `markWaiting(USER_CONFIRMATION)` | `WAITING` |
| 等待依赖 | `markWaiting(DEPENDENCY)` | `WAITING` |
| 成功完成 | `markSucceeded` | `COMPLETED` |
| 取消完成 | `markCancelled` | `CANCELLED` |
| AgentId 无效 | `markFailed(AGENT_ID_INVALID)` | `FAILED` |
| 不属于当前 Task | `markFailed(OUT_OF_DOMAIN)` | `FAILED` |
| Runtime 错误 | `markFailed(RUNTIME_ERROR)` | `FAILED` |

## 10. 实现顺序

### W1：接口冻结

目标：

1. 固定 `queue.spi`；
2. 固定 `control.spi`；
3. 固定 Task 基础模型；
4. 固定 Runtime adapter 边界；
5. 不落复杂实现逻辑。

### W2：本地最小实现

目标：

1. 增加本地 Queue 实现；
2. 增加本地 TaskStore 实现；
3. 增加默认 Task 控制服务；
4. 增加最小单元测试；
5. 不接 PR #100 engine。

### W3：接 PR #100 Runtime / Engine

目标：

1. L5 runtime adapter 实现 `TaskRuntimeDispatcher`；
2. L5 runtime adapter 持有 `EngineDispatchSpi`；
3. Runtime 状态通过 `TaskControlClient.mark*` 回写；
4. Runtime 输出返回给 Access。

### W4：持久化与分布式

目标：

1. 替换 Queue 后端；
2. 替换 TaskStore 后端；
3. 增加幂等键、revision、fencing；
4. 增加跨实例恢复能力。

## 11. 过度设计删除项

以下设计不进入当前接口冻结稿：

1. `QueueAdminPort`；
2. `TaskQueue` 专用类型；
3. Queue 内置 Task 状态；
4. Runtime 直接消费 Session Queue；
5. Runtime / Engine 侧第二套 `TaskControlClient`；
6. L4 直接 import `EngineDispatchSpi`；
7. Session 持有 Task；
8. Access 感知 `queueId`；
9. 投影读取模型；
10. 三轨队列模型；
11. 单独 control-flow / data-flow 同步队列；
12. `QUEUED`、`WAITING_FOR_TOOL`、`EXPIRED` 状态。

## 12. 待确认风险

| 风险 | 当前处理 |
|---|---|
| Runtime 返回 `OUT_OF_DOMAIN` 后，是否立即由 L4 新建 Task 并重派发同一输入。 | 当前只冻结失败码；自动重派发留到下一轮实现确认。 |
| `TaskRuntimeDispatcher` 是否需要完全贴合 PR #100 request 字段。 | 当前保持 L4 自有 request；W3 adapter 再做字段映射。 |
| `TaskStore` 是否和 Queue 后端共用同一持久化介质。 | 当前只冻结端口；W4 再确定实现。 |
| 分布式 Queue 的 exact-once 语义。 | 当前不承诺；W4 用幂等键、revision、fencing 补齐。 |

## 13. 最终结论

最终模块名：

```text
taskflow
```

最终包路径：

```text
com.huawei.ascend.service.taskflow
```

最终稳定 SPI：

```text
taskflow.queue.spi.Queue
taskflow.queue.spi.QueueFactory
taskflow.queue.spi.QueueManager
taskflow.queue.spi.RuntimeQueueGateway

taskflow.control.spi.TaskControlClient
taskflow.control.spi.TaskRuntimeDispatcher
taskflow.control.spi.TaskStore
taskflow.control.spi.TaskStatePolicy
```

最终和 PR #100 的衔接方式：

```text
TaskRuntimeDispatcher
  -> L5 runtime adapter
  -> EngineDispatchSpi

runtime / engine
  -> L5 runtime adapter
  -> TaskControlClient.mark*
```

最终删除项：

```text
不实现 QueueAdminPort
不实现 engine.service.TaskControlClient
不让 Queue 持有 Task 状态
不让 Access 感知 queueId
不让 L4 直接 import EngineDispatchSpi
```
