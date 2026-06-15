/**
 * Runtime machinery for the C3 forwarding substrate: the pure state-transition
 * engine ({@code ForwardingStateMachine}) and the dispatcher worker skeleton
 * ({@code ForwardingDispatcherWorker}) that drives claimed outbox records to a
 * terminal state through an abstract delivery port.
 *
 * <p>This package MUST NOT depend on a concrete broker / MQ client or a JDBC
 * driver (forwarding boundary, decision §6.1). The worker consumes the claim /
 * lease port and the delivery port; a real polling cadence, threading,
 * backpressure and a concrete delivery binding are deferred to a later stage.
 *
 * <p>Authority: {@code architecture/docs/L2/agent-bus/forwarding-outbox-inbox.md §4};
 * {@code architecture/docs/L2/agent-bus/forwarding-persistence.md §5}.
 */
package com.huawei.ascend.bus.forwarding.runtime;
