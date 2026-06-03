package com.huawei.ascend.service.access.protocol.a2a.egress;

import com.huawei.ascend.service.access.api.NotificationPort;
import com.huawei.ascend.service.access.model.AgentNotification;
import java.util.Objects;

public final class DefaultNotificationPort implements NotificationPort {

    private final A2aOutputMapper outputMapper;
    private final A2aOutputRegistry outputRegistry;

    public DefaultNotificationPort(A2aOutputMapper outputMapper, A2aOutputRegistry outputRegistry) {
        this.outputMapper = Objects.requireNonNull(outputMapper, "outputMapper");
        this.outputRegistry = Objects.requireNonNull(outputRegistry, "outputRegistry");
    }

    @Override
    public void notify(AgentNotification notification) {
        Objects.requireNonNull(notification, "notification");
        A2aOutputHandle handle = new A2aOutputHandle(notification.tenantId(), notification.sessionId());
        outputRegistry.append(handle, outputMapper.toA2aOutput(notification));
    }
}
