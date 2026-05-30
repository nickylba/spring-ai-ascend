---
# Frame Card frontmatter. The identity block is COPIED from the frame's DSL
# element in architecture/features/engineering-frames.dsl. Every value here MUST
# match the DSL; the gate fails a card whose frontmatter disagrees with the DSL
# element's saa.id / saa.owner / saa.status / saa.primaryPackage.
level: L1
view: development
status: shipped
authority: "ADR-0161 (Frame Card shape + Card-over-DSL); ADR-0157 (EngineeringFrame Ontology)"

# --- Identity block: COPIED from the DSL frame element (do not invent) ---
frame_id: EF-ENGINE-REGISTRY
dsl_element: efEngineRegistry
owner_module: agent-execution-engine
primary_package: "com.huawei.ascend.engine.runtime"
source_adr: ADR-0157

# --- fact_refs: every generated fact_id this card cites. Each MUST resolve in
#     architecture/facts/generated/*.json. The gate cross-checks these. ---
fact_refs:
  - code-symbol/com-huawei-ascend-engine-runtime-engineenvelope
  - code-symbol/com-huawei-ascend-engine-runtime-engineoutcomechannel
  - code-symbol/com-huawei-ascend-engine-runtime-engineregistry
  - code-symbol/com-huawei-ascend-engine-runtime-inprocessengineport
  - contract-yaml/engine-envelope
  - contract-yaml/engine-hooks
  - test/com-huawei-ascend-engine-runtime-engineregistryresolvetest
  - test/com-huawei-ascend-engine-runtime-enginematchingstrictnessit
  - test/com-huawei-ascend-engine-runtime-enginepayloaddispatchonlyviaregistrytest
  - test/com-huawei-ascend-engine-runtime-everyenginedeclareshooksurfacetest
---

# `EF-ENGINE-REGISTRY` — Engine Registry Frame

> Anchors the in-process engine contract surface: the registry that maps a typed engine
> envelope to a single registered `ExecutorAdapter`, the envelope value carried across that
> boundary, and the in-process realization of the bus `EnginePort`.

## 1. Identity

> COPIED from the DSL frame element. These fields MUST match the DSL byte-for-byte;
> the gate fails a card that disagrees.

| Field | Value | Source |
|---|---|---|
| Frame ID (`saa.id`) | `EF-ENGINE-REGISTRY` | DSL element |
| DSL element | `efEngineRegistry` | `architecture/features/engineering-frames.dsl` |
| Owner module (`saa.owner`) | `agent-execution-engine` | DSL element |
| Status (`saa.status`) | `shipped` | DSL element |
| Primary package (`saa.primaryPackage`) | `com.huawei.ascend.engine.runtime` | DSL element |
| Source ADR (`saa.sourceAdr`) | `ADR-0157` | DSL element |
| Card path (`saa.cardPath`) | `architecture/docs/L1/frames/EF-ENGINE-REGISTRY.md` | DSL element ↔ this file |

## 2. Capability Boundary

> AUTHORED prose. Package names are CITED (they must exist); the lists below are the
> human-readable boundary, not a second registry.

**Can do** — the responsibilities that live inside this frame:

- Hold the registration table of `ExecutorAdapter` instances keyed by engine type, and resolve
  a typed envelope to the one matching adapter (`code-symbol/com-huawei-ascend-engine-runtime-engineregistry`).
- Carry the engine identity and execution payload across the dispatch boundary as an immutable
  envelope value (`code-symbol/com-huawei-ascend-engine-runtime-engineenvelope`).
- Provide the in-process realization of the bus engine boundary, wiring the registry to the bus
  `EnginePort` SPI (`code-symbol/com-huawei-ascend-engine-runtime-inprocessengineport`).
- Reject a payload that matches no registered adapter, rather than silently reinterpreting it.

**Cannot do** — explicitly out of scope (handled by another frame or an L2 detail):

- Define the engine boundary SPI itself — `com.huawei.ascend.bus.spi.engine.EnginePort` and its
  request/descriptor types are owned by the agent-bus frame `EF-ENGINE-PORT`; this frame only
  realizes it.
- Own the `ExecutorAdapter` SPI hierarchy — the adapter and orchestrator-facing contracts live in
  `com.huawei.ascend.engine.spi` (a sibling package in the same module, not this frame's boundary).
- Drive the run lifecycle or fire lifecycle hooks — orchestration and hook dispatch are the
  agent-service orchestrators and the agent-middleware frame `EF-HOOK-SURFACE`.
- The runtime mechanics of matching, the strict-mismatch failure path, and the over-the-wire
  envelope field semantics are L2 detail — delegated to the contract surface and the frame's L2
  sink, not restated in this card.

**Owned state** — the data/state this frame is the structural home for:

- The in-memory engine-type → `ExecutorAdapter` registration map and its resolution surface
  (`code-symbol/com-huawei-ascend-engine-runtime-engineregistry`).
- The engine envelope value type carried across dispatch
  (`code-symbol/com-huawei-ascend-engine-runtime-engineenvelope`).
- The in-process outcome hand-off structure used by the port realization
  (`code-symbol/com-huawei-ascend-engine-runtime-engineoutcomechannel`).

**External dependencies** — frames / modules this frame is allowed to depend on:

- `com.huawei.ascend.bus.spi.engine` (frame `EF-ENGINE-PORT`) — the engine boundary SPI this frame
  realizes (`EnginePort`, `ExecutorDefinition`, `ExecutionContext`, `ExecuteRequest`, `DefinitionResolver`).
- `com.huawei.ascend.engine.spi` — the `ExecutorAdapter` SPI hierarchy the registry resolves to.
- `com.huawei.ascend.middleware` / `com.huawei.ascend.middleware.spi` — the hook dispatcher and
  `RuntimeMiddleware` SPI the registry wires for hook delivery.
- `com.huawei.ascend.bus.spi.s2c` — the S2C callback transport the registry holds for outcome routing.

**Forbidden dependencies** — dependencies the boundary must never take (held by an
ArchUnit enforcer; cite it in section 7):

- Pattern-matching on `ExecutorDefinition` subtypes outside the registry — every orchestrator MUST
  dispatch through `EngineRegistry`, never by an out-of-registry `instanceof` ladder (enforcer `E74`).
- Any dependency on the agent-service web / persistence layers — the engine module is a
  compute_control plane and must not import the service edge.

**Included / excluded packages** (this frame is a single package root, not a cluster):

- Included: `com.huawei.ascend.engine.runtime`
- Excluded: `com.huawei.ascend.engine.spi` (the `ExecutorAdapter` SPI — a sibling package, surfaced
  as a consumed contract in section 5, not part of this frame's Type Inventory).

## 3. Type Inventory

> GENERATED — do not hand-edit between the markers. Rendered from
> `architecture/facts/generated/code-symbols.json`, filtered to the frame's in-boundary
> package(s). Every row cites its `code-symbol/<kebab-fqn>` fact ID. The Card generator owns
> this region and overwrites it on every re-render.

<!-- BEGIN GENERATED: type-inventory -->
| Type | Kind | Fact ID |
|---|---|---|
| `com.huawei.ascend.engine.runtime.EngineEnvelope` | record | `code-symbol/com-huawei-ascend-engine-runtime-engineenvelope` |
| `com.huawei.ascend.engine.runtime.EngineOutcomeChannel` | class | `code-symbol/com-huawei-ascend-engine-runtime-engineoutcomechannel` |
| `com.huawei.ascend.engine.runtime.EngineRegistry` | class | `code-symbol/com-huawei-ascend-engine-runtime-engineregistry` |
| `com.huawei.ascend.engine.runtime.InProcessEnginePort` | class | `code-symbol/com-huawei-ascend-engine-runtime-inprocessengineport` |
<!-- END GENERATED: type-inventory -->

## 4. Internal Collaboration

> GENERATED — do not hand-edit between the markers. Rendered from `code-symbols.json`:
> the structural relationships (implements / extends / references) among the in-boundary
> types listed in section 3. This is the *structural* collaboration only — runtime call
> sequences belong in the frame's L2 sink, not here.

<!-- BEGIN GENERATED: internal-collaboration -->
| From | Relationship | To |
|---|---|---|
| `com.huawei.ascend.engine.runtime.InProcessEnginePort` | references | `com.huawei.ascend.engine.runtime.EngineRegistry` |
| `com.huawei.ascend.engine.runtime.InProcessEnginePort` | references | `com.huawei.ascend.engine.runtime.EngineOutcomeChannel` |
| `com.huawei.ascend.engine.runtime.EngineRegistry` | references | `com.huawei.ascend.engine.runtime.EngineEnvelope` |
<!-- END GENERATED: internal-collaboration -->

## 5. Contracts

> AUTHORED prose. The communication contracts this frame exposes or consumes. Cite each
> contract operation by its fact ID and each SPI by its package identity.
> Wire-field and over-the-wire mechanics are L2 — link down, do not inline.

**Exposed SPI / public surface (boundary identity):**

- `com.huawei.ascend.engine.runtime.EngineRegistry`
  (`code-symbol/com-huawei-ascend-engine-runtime-engineregistry`) — the registration + resolution
  surface other frames in the module depend on.
- `com.huawei.ascend.engine.runtime.EngineEnvelope`
  (`code-symbol/com-huawei-ascend-engine-runtime-engineenvelope`) — the immutable envelope value
  passed into resolution.
- `com.huawei.ascend.engine.runtime.InProcessEnginePort`
  (`code-symbol/com-huawei-ascend-engine-runtime-inprocessengineport`) — the in-process realization
  of the bus `com.huawei.ascend.bus.spi.engine.EnginePort` SPI (the boundary identity is owned by
  `EF-ENGINE-PORT`; this type implements it).

**Contract surfaces (schema contracts):**

| Contract | Fact ID | Contract source |
|---|---|---|
| Engine envelope schema (envelope shape + `known_engines`) | `contract-yaml/engine-envelope` | `docs/contracts/engine-envelope.v1.yaml` |
| Engine hooks schema (canonical `HookPoint` set + ordering) | `contract-yaml/engine-hooks` | `docs/contracts/engine-hooks.v1.yaml` |

> These engine contracts are YAML *schema* surfaces (`fact_kind: schema`), not OpenAPI/AsyncAPI
> operations; the public HTTP `contract-op/*` operations belong to the agent-service edge frames,
> not to this frame.

**Consumed contracts** (surfaces this frame depends on, owned by another frame):

- `com.huawei.ascend.bus.spi.engine` SPI (frame `EF-ENGINE-PORT`) — the `EnginePort` /
  `ExecutorDefinition` / `ExecutionContext` / `ExecuteRequest` / `DefinitionResolver` types the
  registry and the in-process port realize and route.
- `com.huawei.ascend.engine.spi.ExecutorAdapter` — the adapter SPI a resolved engine type is bound
  to (sibling package in the same module).

## 6. FunctionPoint Mapping

> AUTHORED prose over the frame's DSL `anchors` edges. Lists ONLY FunctionPoints the DSL
> anchors to this frame (`efEngineRegistry -> fpEngineDispatch` in `engineering-frames.dsl`).
> The gate fails a card that lists a FunctionPoint with no backing `anchors` edge.

This frame anchors exactly one FunctionPoint.

### `FP-ENGINE-DISPATCH` — resolve a typed engine envelope to its registered `ExecutorAdapter`

| Anchor | Fact ID |
|---|---|
| Entry class | `code-symbol/com-huawei-ascend-engine-runtime-engineregistry` |
| Entry method | `code-symbol/com-huawei-ascend-engine-runtime-engineregistry` :: `resolve(Lcom/huawei/ascend/engine/runtime/EngineEnvelope;)Lcom/huawei/ascend/engine/spi/ExecutorAdapter;` |
| Envelope value | `code-symbol/com-huawei-ascend-engine-runtime-engineenvelope` |
| Contract surface | `contract-yaml/engine-envelope` |
| Test (typed dispatch + mismatch) | `test/com-huawei-ascend-engine-runtime-engineregistryresolvetest` |

> The `instanceof`-free dispatch invariant and the strict no-fallback mismatch behaviour are
> verified by the enforcers in section 7; the runtime sequence and the mismatch failure path are
> L2 detail, not restated here.

## 7. Verification

> AUTHORED prose. The constraints, ArchUnit enforcers, and gate rules that hold this
> boundary. Cite each enforcer / rule as a structural identifier (not version metadata).

**Constraints / enforcers holding the boundary:**

- Enforcer `E74` (`EnginePayloadDispatchOnlyViaRegistryTest#every_orchestrator_implementation_depends_on_engine_registry`)
  — asserts every concrete orchestrator dispatches through `EngineRegistry`; out-of-registry
  pattern-matching on `ExecutorDefinition` subtypes is the prohibited shape.
- Enforcer `E75` (`EngineMatchingStrictnessIT#payload_with_no_registered_adapter_raises_engine_mismatch`)
  — asserts a payload with no registered adapter raises `EngineMatchingException` (`engine_mismatch`):
  no silent reinterpretation, no fallback.
- Enforcer `E76` (`gate/check_architecture_sync.sh#engine_envelope_yaml_present_and_wellformed`)
  — asserts `docs/contracts/engine-envelope.v1.yaml` exists and is well-formed (the single
  envelope-shape authority is gate-validated).
- Enforcer `E77` (`gate/check_architecture_sync.sh#engine_registry_covers_all_known_engines`)
  — asserts bidirectional consistency between `known_engines[].id` in the envelope contract and the
  engine-type constants in production code.

**Tests anchoring the behaviour** (fact-cited):

- `test/com-huawei-ascend-engine-runtime-engineregistryresolvetest` — typed dispatch by engine type
  and `EngineMatchingException` on an unknown engine type / payload.
- `test/com-huawei-ascend-engine-runtime-enginematchingstrictnessit` — a payload with no registered
  adapter raises an engine mismatch; registered adapters dispatch normally.
- `test/com-huawei-ascend-engine-runtime-enginepayloaddispatchonlyviaregistrytest` — every
  orchestrator implementation depends on `EngineRegistry`.
- `test/com-huawei-ascend-engine-runtime-everyenginedeclareshooksurfacetest` — every
  `ExecutorAdapter` implementation exposes a hook surface (structural pre-condition for hook delivery
  through the registry).

## Cross-references

- Frames directory + Card-over-DSL rules: [`README.md`](README.md).
- This frame's DSL element (authority): [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl).
- Generated facts cited above (authority over this prose): [`../../../facts/generated/`](../../../facts/generated/).
- Collective structural map: [`../engineering-frames.md`](../engineering-frames.md).
- The engine boundary SPI this frame realizes: frame `EF-ENGINE-PORT` (agent-bus, ADR-0158).
- This frame's L2 detail sink (runtime matching mechanics + mismatch failure path, if any): `architecture/docs/L2/engine-registry/`.
