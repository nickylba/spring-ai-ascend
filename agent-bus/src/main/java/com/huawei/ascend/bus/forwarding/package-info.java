/**
 * C3 forwarding runtime substrate — broker-agnostic, tenant-scoped, durable
 * outbox / inbox domain model, ports and state machine.
 *
 * <p>{@code com.huawei.ascend.bus.forwarding.spi} holds the pure-Java contract
 * surface (envelope, record models, ports — enqueue / mark / status, claim /
 * lease, delivery — and value types); {@code com.huawei.ascend.bus.forwarding.runtime}
 * holds the state-transition engine and the dispatcher worker skeleton. The
 * real persistent implementation (JDBC adapter / migration / polling / lease
 * store) is deferred — Stage 8 ships the record models, claim / lease port,
 * delivery port, worker skeleton, schema / migration draft and an in-memory
 * lease harness, but no production database dependency (decision §6.1).
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md};
 * {@code architecture/docs/L2/agent-bus/forwarding-persistence.md};
 * {@code ICD-Agent-Bus-Forwarding-Runtime}.
 */
package com.huawei.ascend.bus.forwarding;
