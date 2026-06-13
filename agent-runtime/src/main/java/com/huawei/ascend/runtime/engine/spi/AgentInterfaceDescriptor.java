package com.huawei.ascend.runtime.engine.spi;

/**
 * Protocol-neutral descriptor for one supported agent interface (transport binding + path).
 *
 * <p>Carries zero {@code org.a2aproject} imports. The mapper in
 * {@code engine.a2a} converts this to {@code AgentInterface}.
 */
public record AgentInterfaceDescriptor(
        String protocolBinding,
        String path,
        String protocolVersion) {

    /** Convenience factory when only binding and path are known (no explicit protocol version). */
    public static AgentInterfaceDescriptor of(String protocolBinding, String path) {
        return new AgentInterfaceDescriptor(protocolBinding, path, null);
    }
}
