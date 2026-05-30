package com.huawei.ascend.service.taskflow.queue.spi;

public interface QueueSubscriptionPort {

    void register(QueueListener listener);
}
