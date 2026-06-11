package com.huawei.ascend.bus.messaging;

/**
 * Handle for an active topic subscription. Closing it stops delivery promptly
 * (a message already handed to the handler may still complete); close is
 * idempotent and throws no checked exception.
 */
public interface Subscription extends AutoCloseable {

    /**
     * Number of messages dropped for this subscriber because its bounded
     * queue was full — drops are counted, never silent.
     */
    long droppedCount();

    @Override
    void close();
}
