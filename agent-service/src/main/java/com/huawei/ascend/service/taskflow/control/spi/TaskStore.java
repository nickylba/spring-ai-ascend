package com.huawei.ascend.service.taskflow.control.spi;

import com.huawei.ascend.service.taskflow.control.Task;

import java.util.List;
import java.util.Optional;
import java.util.function.UnaryOperator;

public interface TaskStore {

    Task create(Task task);

    Optional<Task> find(String tenantId, String taskId);

    Optional<Task> findActiveTask(String tenantId, String sessionId);

    Optional<Task> compareAndUpdate(String tenantId, String taskId, long expectedRevision,
                                    UnaryOperator<Task> mutation);

    List<Task> listBySession(String tenantId, String sessionId);
}
