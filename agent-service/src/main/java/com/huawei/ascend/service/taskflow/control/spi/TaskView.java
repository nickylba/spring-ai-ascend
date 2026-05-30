package com.huawei.ascend.service.taskflow.control.spi;

import com.huawei.ascend.service.taskflow.control.Task;
import com.huawei.ascend.service.taskflow.control.TaskFailureCode;
import com.huawei.ascend.service.taskflow.control.TaskState;
import com.huawei.ascend.service.taskflow.control.WaitingReason;

public record TaskView(
        String tenantId,
        String sessionId,
        String taskId,
        String agentId,
        TaskState state,
        long revision,
        WaitingReason waitingReason,
        TaskFailureCode failureCode) {

    public static TaskView from(Task task) {
        return new TaskView(task.tenantId(), task.sessionId(), task.taskId(), task.agentId(),
                task.state(), task.revision(), task.waitingReason(), task.failureCode());
    }
}
