package com.huawei.ascend.service.taskflow.queue;

public record RuntimeQueueQuery(int limit) {

    public RuntimeQueueQuery {
        if (limit < 1) {
            limit = 100;
        }
    }
}
