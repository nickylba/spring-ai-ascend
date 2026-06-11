/**
 * Observer SPI of the LLM egress gateway: listener seam, generation-span sink and
 * spend ledger. Pure-JDK types only — implementations may live anywhere (including
 * outside this module) without dragging in Spring or the gateway internals.
 *
 * <p>The listener positions deliberately mirror the two LLM hook positions of
 * {@code docs/contracts/engine-hooks.v1.yaml} ({@code before_llm_invocation} /
 * {@code after_llm_invocation}) so a future Hook-SPI resurrection can adopt these
 * call sites verbatim; no {@code HookPoint} enum and no dispatcher exist here.
 */
package com.huawei.ascend.runtime.llm.gateway.spi;
