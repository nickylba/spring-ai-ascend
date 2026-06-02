package com.huawei.ascend.service.access.egress;

import com.huawei.ascend.service.access.model.EgressBinding;
import com.huawei.ascend.service.access.model.NotificationFrame;
import com.huawei.ascend.service.queue.QueueFactory;
import com.huawei.ascend.service.queue.InternalEventQueue;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory {@link EgressQueueRegistry} backed by the shared internal-event-queue
 * module. One {@link InternalEventQueue} of {@link NotificationFrame} is created
 * per active session, keyed by (tenant, session).
 */
public final class DefaultEgressQueueRegistry implements EgressQueueRegistry {

    private final ConcurrentHashMap<Key, InternalEventQueue<NotificationFrame>> queues = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Key, EgressBinding> bindings = new ConcurrentHashMap<>();

    @Override
    public InternalEventQueue<NotificationFrame> getOrCreate(EgressBinding binding) {
        Objects.requireNonNull(binding, "binding");
        Key key = Key.from(binding.tenantId(), binding.sessionId());
        bindings.put(key, binding);
        return queues.computeIfAbsent(key,
                ignored -> QueueFactory.inMemoryQueue(queueIdValue(binding)));
    }

    @Override
    public Optional<InternalEventQueue<NotificationFrame>> find(String tenantId, String sessionId) {
        return Optional.ofNullable(queues.get(Key.from(tenantId, sessionId)));
    }

    @Override
    public Optional<EgressBinding> findBinding(String tenantId, String sessionId) {
        return Optional.ofNullable(bindings.get(Key.from(tenantId, sessionId)));
    }

    @Override
    public void remove(String tenantId, String sessionId) {
        Key key = Key.from(tenantId, sessionId);
        queues.remove(key);
        bindings.remove(key);
    }

    private static String queueIdValue(EgressBinding binding) {
        return binding.tenantId() + ":" + binding.sessionId() + ":egress";
    }

    private record Key(String tenantId, String sessionId) {
        private Key {
            Objects.requireNonNull(tenantId, "tenantId");
            Objects.requireNonNull(sessionId, "sessionId");
        }

        static Key from(String tenantId, String sessionId) {
            return new Key(tenantId, sessionId);
        }
    }

}
