package com.huawei.ascend.service.taskflow.queue.spi;

import com.huawei.ascend.service.taskflow.queue.QueuePollRequest;

import java.util.Optional;

public interface QueueConsumer {

    Optional<Object> poll(QueuePollRequest request);
}
