package com.huawei.ascend.service.taskflow.queue.spi;

import com.huawei.ascend.service.taskflow.queue.QueueDeleteReason;
import com.huawei.ascend.service.taskflow.queue.QueueId;
import com.huawei.ascend.service.taskflow.queue.QueueSpec;
import com.huawei.ascend.service.taskflow.queue.RuntimeGatewaySpec;

public interface QueueFactory {

    Queue createQueue(QueueSpec spec);

    RuntimeQueueGateway createRuntimeQueueGateway(QueueId queueId, RuntimeGatewaySpec spec);

    void deleteQueue(QueueId queueId, QueueDeleteReason reason);
}
