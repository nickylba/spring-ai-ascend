package com.huawei.ascend.service.taskflow.queue;

import java.util.Objects;

public record RuntimeGatewaySpec(String runtimeId) {

    public RuntimeGatewaySpec {
        Objects.requireNonNull(runtimeId, "runtimeId");
        if (runtimeId.isBlank()) {
            throw new IllegalArgumentException("runtimeId must not be blank");
        }
    }
}
