package com.huawei.ascend.service.taskflow.queue;

import java.util.Objects;

public record QueueItemKey(QueueId queueId, String itemId) {

    public QueueItemKey {
        Objects.requireNonNull(queueId, "queueId");
        Objects.requireNonNull(itemId, "itemId");
        if (itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
    }
}
