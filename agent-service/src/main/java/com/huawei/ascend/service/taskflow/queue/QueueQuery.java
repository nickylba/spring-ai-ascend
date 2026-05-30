package com.huawei.ascend.service.taskflow.queue;

import java.util.Objects;

public record QueueQuery(String tenantId, String sessionId, int limit) {

    private static final int DEFAULT_LIMIT = 100;

    public QueueQuery {
        if (limit < 1) {
            limit = DEFAULT_LIMIT;
        }
    }

    public static QueueQuery forSession(String tenantId, String sessionId) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sessionId, "sessionId");
        return new QueueQuery(tenantId, sessionId, DEFAULT_LIMIT);
    }
}
