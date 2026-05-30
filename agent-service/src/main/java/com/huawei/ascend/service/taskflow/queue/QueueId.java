package com.huawei.ascend.service.taskflow.queue;

import java.util.Objects;
import java.util.UUID;

public record QueueId(String value) {

    public QueueId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("value must not be blank");
        }
    }

    public static QueueId random() {
        return new QueueId(UUID.randomUUID().toString());
    }
}
