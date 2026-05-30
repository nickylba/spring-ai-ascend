package com.huawei.ascend.service.taskflow.queue.spi;

import com.huawei.ascend.service.taskflow.queue.QueueDeleteReason;
import com.huawei.ascend.service.taskflow.queue.QueueEvent;
import com.huawei.ascend.service.taskflow.queue.QueueId;
import com.huawei.ascend.service.taskflow.queue.QueueInfo;
import com.huawei.ascend.service.taskflow.queue.QueueQuery;

import java.util.List;
import java.util.Optional;

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
