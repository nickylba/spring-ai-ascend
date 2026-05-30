package com.huawei.ascend.service.taskflow.queue.spi;

import com.huawei.ascend.service.taskflow.queue.QueueId;
import com.huawei.ascend.service.taskflow.queue.QueueInfo;
import com.huawei.ascend.service.taskflow.queue.RuntimeGatewaySpec;

public interface Queue extends QueuePublisher, QueueConsumer, QueueQueryPort, QueueSubscriptionPort {

    QueueId id();

    QueueInfo info();

    RuntimeQueueGateway newRuntimeQueueGateway(RuntimeGatewaySpec spec);
}
