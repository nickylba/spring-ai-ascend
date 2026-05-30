package com.huawei.ascend.service.taskflow.control.spi;

import java.util.concurrent.CompletionStage;

public interface TaskRuntimeDispatcher {

    CompletionStage<TaskRuntimeDispatchResult> dispatchRun(TaskRuntimeDispatchRequest request);

    CompletionStage<TaskRuntimeDispatchResult> dispatchResume(TaskRuntimeDispatchRequest request);

    CompletionStage<TaskRuntimeDispatchResult> dispatchCancel(TaskRuntimeDispatchRequest request);
}
