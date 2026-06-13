/**
 * Engine provider SPI surface.
 *
 * <p>This package is intentionally small and protocol-neutral: it must not
 * reference {@code org.a2aproject} types (enforced by
 * {@code RuntimePackageBoundaryTest}). {@code AgentRuntimeHandler} executes one
 * business Agent against the neutral {@code AgentExecutionContext} /
 * {@code RuntimeMessage} input model. {@code AgentCardProvider} supplies the
 * neutral {@code AgentCardDescriptor}; the A2A projection happens in
 * {@code engine.a2a.A2aAgentCardMapper}. A concrete handler may implement both
 * interfaces when that is the simplest shape, but normal execution code should
 * keep framework-specific decoration inside the framework adapter.
 * {@code MemoryProvider} is a reserved narrow SPI for frameworks that need
 * runtime-provided memory init/search/save integration. Frameworks with native
 * checkpointing can use their own checkpointer configuration without going through
 * these optional surfaces.
 */
package com.huawei.ascend.runtime.engine.spi;
