package com.huawei.ascend.bus.memory;

/**
 * SPI for the ADR-0051 business-fact emission path: S-side execution emits
 * candidate business facts for the C-side to consume.
 *
 * <p>Implementations transport — they MUST NOT persist facts as platform
 * memory. A production implementation bridges to the C-side (callback,
 * outbox, stream frame); the in-repo reference merely records for tests.
 */
public interface BusinessFactPublisher {

    /** Emit one fact toward the C-side. Implementations must not block the caller indefinitely. */
    void publish(BusinessFactEvent event);
}
