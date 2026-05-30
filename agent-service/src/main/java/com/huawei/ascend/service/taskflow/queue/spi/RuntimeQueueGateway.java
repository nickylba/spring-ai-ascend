package com.huawei.ascend.service.taskflow.queue.spi;

import com.huawei.ascend.service.taskflow.queue.QueueItemKey;
import com.huawei.ascend.service.taskflow.queue.RuntimeQueueQuery;

import java.util.List;

public interface RuntimeQueueGateway {

    QueueItemKey publish(Object value);

    List<Object> query(RuntimeQueueQuery query);
}
