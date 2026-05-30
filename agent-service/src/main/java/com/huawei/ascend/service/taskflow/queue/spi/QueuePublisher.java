package com.huawei.ascend.service.taskflow.queue.spi;

import com.huawei.ascend.service.taskflow.queue.QueueItemKey;

public interface QueuePublisher {

    QueueItemKey push(Object value);
}
