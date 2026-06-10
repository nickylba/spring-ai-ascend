# Decision Material — agent-bus SPI: Align or Retire

- Date: 2026-06-11
- Status: awaiting owner decision
- Trigger: core-module review finding H6 — the entire `com.huawei.ascend.bus.spi.*`
  surface (engine + s2c + ingress, 21 files) has zero production implementations or
  callers anywhere in the repository, while parts of its documentation claimed
  shipped status (the false claims were corrected to design-only wording in the
  review-leftovers batch; the structural question remains).

## What agent-bus contains today

| Surface | Types | Contract status (YAML) | Consumers in repo |
|---|---|---|---|
| `bus.spi.engine` | EnginePort, Orchestrator, RunContext, ExecutionContext, SuspendSignal, Checkpointer, TraceContext, RunMode, ExecutorDefinition, AgentEvent, ExecuteRequest, DefinitionRef/Resolver, EngineDescriptor | `engine-port.v1.yaml: design_only` | 3 test files only |
| `bus.spi.s2c` | S2cCallbackTransport, S2cCallbackEnvelope, S2cCallbackResponse | `s2c-callback.v1.yaml` (status text overstated; code now says design-only) | none |
| `bus.spi.ingress` | referenced by agent-runtime/pom.xml comments | **does not exist** (no package) | — |

Meanwhile agent-runtime's REAL engine boundary is the parallel SPI
`com.huawei.ascend.runtime.engine.spi.AgentRuntimeHandler` + `AgentExecutionContext`
(+ StreamAdapter / AgentCardProvider / MemoryProvider), which now hosts three
adapter families (openJiuwen, AgentScope, LangGraph) and is exercised end-to-end.

The W1 Run kernel just landed in `com.huawei.ascend.runtime.run` (Run /
RunStatus / RunStateMachine / RunRepository) — note `RunStatus`+DFA overlaps
conceptually with what `bus.spi.engine` types assume, and `Checkpointer` /
`SuspendSignal` remain bus-side with no impl.

## Option A — Align (make agent-bus the real boundary)

Migrate agent-runtime's engine seam to consume `bus.spi.engine`: implement
`EnginePort` in front of `AgentRuntimeHandler`, move/bridge Run types to the bus
RunContext model, implement Checkpointer/Orchestrator per ADR-0158's
"engine as a real instance behind a port".

- Pros: honors ADR-0158/0159 architecture-of-record; transport-agnostic engine
  boundary enables the W2+ out-of-process engine plane; SPI investment preserved.
- Cons: large migration touching every adapter; duplicates the working
  `AgentRuntimeHandler` seam during transition; the bus SPI encodes design
  decisions never validated by an implementation (review M-11/M-13/M-15/M-17
  found real contract defects — runId carried twice in incompatible types,
  Serializable types holding lambdas, checkpointer leaked into the
  any-transport context); no current requirement forces cross-process engines.
- Cost estimate: multi-week; blocks on resolving the contract defects first.

## Option B — Retire (fold the bus SPI into design archive)

Move `bus.spi.engine`/`bus.spi.s2c` sources to a design-archive location (or
delete, keeping the contract YAMLs as the design record), update
ARCHITECTURE.md/workspace.dsl to mark the EnginePort boundary as
W2+ design-pending, and declare `runtime.engine.spi` the boundary of record
until a cross-process engine requirement actually arrives.

- Pros: one truth instead of two parallel SPIs; removes the standing trap of
  designing against vapor; zero migration risk; the design knowledge is not
  lost (YAML contracts + git history + this document).
- Cons: walks back ADR-0158's "neutral SPI owned by the bus plane" placement;
  re-introducing it later costs a fresh design pass (arguably a pro — the
  current one has known defects).

## Option C — Freeze (explicit middle path, lowest cost)

Keep agent-bus exactly as is, but stamp every SPI package-info with a
binding "design-frozen, do not implement or consume until ADR-XXXX re-opens"
marker, and add an ArchUnit test in agent-runtime asserting no production
import of `com.huawei.ascend.bus.spi..` (turning today's accident into an
enforced boundary).

- Pros: ~1 day; preserves both the investment and the honesty.
- Cons: carries dead weight in the reactor and the cognitive map indefinitely.

## Recommendation

**Option C now, Option A/B decided at the W2 planning gate.** The review-fixed
javadoc already states design-only status; C makes it structurally enforced for
near-zero cost, and defers the expensive strategic choice to the moment a real
cross-process engine requirement (or its definitive absence) is known. If the
W2 plan contains no out-of-process engine, take B; if it does, take A but fix
the M-11/M-13/M-15/M-17 contract defects before the first implementation.
