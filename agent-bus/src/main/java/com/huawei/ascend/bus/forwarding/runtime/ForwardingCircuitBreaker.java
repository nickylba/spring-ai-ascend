package com.huawei.ascend.bus.forwarding.runtime;

import com.huawei.ascend.bus.forwarding.spi.ForwardingRouteHandle;

/**
 * Per-route circuit-breaker seam for the C3 forwarding dispatcher (Stage 14 —
 * <b>deferred, not wired</b>).
 *
 * <p>Stage 13 separated the deliver-retry subitem (timing of a retryable failure
 * — now owned by {@link ForwardingRetryPolicy}, shipped Stage 14) from
 * circuit-breaking (per-route failure-rate governance). Circuit-breaking is
 * intentionally NOT wired into {@link ForwardingDispatcherWorker} in Stage 14:
 * a real breaker needs per-{@link ForwardingRouteHandle} failure-rate state and
 * its shape depends on the transport model still undecided in Stage 13:
 * <ul>
 *   <li>a <b>push</b> model (T1 / T2) has the dispatcher driving delivery, so it
 *       needs the breaker to actively short-circuit a failing route;</li>
 *   <li>a <b>consumer-pull</b> model (T3) is inherently self-paced — the
 *       receiver simply stops claiming, which is its backpressure / break — so
 *       an explicit breaker is largely redundant.</li>
 * </ul>
 * Wiring the breaker before that decision would bake in a transport assumption.
 * Instead Stage 14 ships only this port + the {@link #ALWAYS_CLOSED} no-op so
 * the seam exists and the signature is stable; the real implementation lands
 * alongside the transport ruling (H2/H3).
 *
 * <p>Plain JDK-portable type — no Spring, JDBC, broker or scheduler dependency
 * (forwarding purity, decision §6.1).
 *
 * <p>Authority: {@code docs/architecture/l0/10-governance/review-packets/agent-bus-forwarding-runtime-transport-candidates.md}
 * (deliver-retry-policy-subitem; circuit-breaker deferred).
 */
public interface ForwardingCircuitBreaker {

    /**
     * Whether delivery to the given route is currently permitted. A real
     * implementation tracks per-{@link ForwardingRouteHandle} failure rate and
     * returns {@code false} to short-circuit (so the caller defers / retries the
     * record rather than delivering into a known-failing route).
     *
     * @param routeHandle the opaque route (never unwrapped to a physical endpoint)
     */
    boolean allowsDelivery(ForwardingRouteHandle routeHandle);

    /**
     * No-op breaker: the circuit is always closed, so delivery is never blocked.
     * The stand-in until a real per-route implementation lands with the
     * transport decision (H2/H3).
     */
    ForwardingCircuitBreaker ALWAYS_CLOSED = routeHandle -> true;
}
