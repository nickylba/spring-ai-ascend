package com.huawei.ascend.middleware.dispatcher;

import com.huawei.ascend.bus.spi.s2c.S2cCallbackEnvelope;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackResponse;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Event Bus Implementation Stub for S2cCallbackTransport.
 * To be implemented when the physical event bus (Kafka/NATS) is ready.
 * 
 * Target Architecture:
 * 1. Serializes envelope and publishes to bus topics.
 * 2. Suspends CompletableFuture waiting for a correlation ID match on response topic.
 */
public class EventBusSandboxTransportStub implements S2cCallbackTransport {
    private static final Logger log = LoggerFactory.getLogger(EventBusSandboxTransportStub.class);

    @Override
    public CompletionStage<S2cCallbackResponse> dispatch(S2cCallbackEnvelope envelope) {
        log.warn("[EventBus Transport Stub] Physical bus not implemented. Rejecting TraceID: {}", envelope.traceId());
        return CompletableFuture.completedFuture(
            S2cCallbackResponse.error(
                envelope.callbackId(), 
                envelope.traceId(), 
                "BUS_NOT_IMPLEMENTED", 
                "The event bus transport is currently a stub."
            )
        );
    }
}
