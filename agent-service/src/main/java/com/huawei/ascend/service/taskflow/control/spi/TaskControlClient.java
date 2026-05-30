package com.huawei.ascend.service.taskflow.control.spi;

import java.util.concurrent.CompletionStage;

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
