package com.huawei.ascend.service.taskflow.queue.spi;

import com.huawei.ascend.service.taskflow.queue.QueueEvent;

public interface QueueListener {

    void notify(QueueEvent event);
}
