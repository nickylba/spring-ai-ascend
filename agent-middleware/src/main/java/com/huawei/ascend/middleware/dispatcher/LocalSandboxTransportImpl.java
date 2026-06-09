package com.huawei.ascend.middleware.dispatcher;

import com.huawei.ascend.bus.spi.s2c.S2cCallbackEnvelope;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackResponse;
import com.huawei.ascend.bus.spi.s2c.S2cCallbackTransport;
import com.huawei.ascend.middleware.skill.spi.SkillSandbox;
import com.huawei.ascend.middleware.skill.spi.ToolCallPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Local JVM Implementation of the S2cCallbackTransport.
 * Keeps the existing single-process architecture fully functional while 
 * perfectly adhering to the upstream architectural SPI.
 */
public class LocalSandboxTransportImpl implements S2cCallbackTransport {
    private static final Logger log = LoggerFactory.getLogger(LocalSandboxTransportImpl.class);
    
    private final SkillSandbox localSandbox;

    public LocalSandboxTransportImpl(SkillSandbox localSandbox) {
        this.localSandbox = localSandbox;
    }

    @Override
    public CompletionStage<S2cCallbackResponse> dispatch(S2cCallbackEnvelope envelope) {
        log.info("[Local S2C Transport] Routing request in-JVM for TraceID: {}", envelope.traceId());
        
        Object rawPayload = envelope.requestPayload();
        
        // Defensive type checking (Avoid ClassCastException if upstream serializes to Map)
        if (!(rawPayload instanceof ToolCallPayload)) {
             log.error("[Local S2C Transport] Invalid payload type: {}. Expected ToolCallPayload.", 
                 (rawPayload != null ? rawPayload.getClass().getName() : "null"));
             return CompletableFuture.completedFuture(
                 S2cCallbackResponse.error(
                     envelope.callbackId(), 
                     envelope.traceId(), 
                     "INVALID_PAYLOAD", 
                     "Expected ToolCallPayload but got " + (rawPayload != null ? rawPayload.getClass().getName() : "null")
                 )
             );
        }
        
        ToolCallPayload typedPayload = (ToolCallPayload) rawPayload;

        // Execute physically in the local JVM sandbox
        return localSandbox.submit(typedPayload)
            .thenApply(result -> {
                log.info("[Local S2C Transport] Execution complete for TraceID: {}", envelope.traceId());
                return S2cCallbackResponse.ok(envelope.callbackId(), envelope.traceId(), result);
            })
            .exceptionally(ex -> {
                log.error("[Local S2C Transport] Execution failed for TraceID: {}", envelope.traceId(), ex);
                return S2cCallbackResponse.error(
                    envelope.callbackId(), 
                    envelope.traceId(), 
                    "SANDBOX_ERR", 
                    ex.getMessage()
                );
            });
    }
}
