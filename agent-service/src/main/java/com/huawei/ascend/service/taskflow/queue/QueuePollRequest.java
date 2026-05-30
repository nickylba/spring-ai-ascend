package com.huawei.ascend.service.taskflow.queue;

public record QueuePollRequest() {

    public static QueuePollRequest one() {
        return new QueuePollRequest();
    }
}
