package com.huawei.ascend.runtime.engine.spi;

/**
 * Protocol-neutral descriptor for one agent-card signature entry.
 *
 * <p>Mirrors the fields of the A2A {@code AgentCardSignature} type without
 * importing {@code org.a2aproject}. The mapper in {@code engine.a2a} converts
 * this to the wire type.
 */
public record SignatureDescriptor(
        String protectedHeader,
        String signature,
        String header) {
}
