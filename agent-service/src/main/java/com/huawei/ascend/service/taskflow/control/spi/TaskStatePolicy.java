package com.huawei.ascend.service.taskflow.control.spi;

import com.huawei.ascend.service.taskflow.control.TaskState;

public interface TaskStatePolicy {

    boolean canTransition(TaskState from, TaskState to);
}
