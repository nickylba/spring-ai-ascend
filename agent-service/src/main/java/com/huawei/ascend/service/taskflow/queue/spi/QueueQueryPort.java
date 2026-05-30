package com.huawei.ascend.service.taskflow.queue.spi;

import com.huawei.ascend.service.taskflow.queue.QueueItemKey;
import com.huawei.ascend.service.taskflow.queue.QueueQuery;

import java.util.List;
import java.util.Optional;

public interface QueueQueryPort {

    Optional<Object> getItem(QueueItemKey key);

    List<Object> query(QueueQuery query);
}
