---
level: L1
view: development
status: design_only
authority: "ADR-0161 (Frame Card shape + Card-over-DSL); ADR-0157 (EngineeringFrame Ontology)"

# --- Identity block: COPIED from the DSL frame element (do not invent) ---
frame_id: EF-GRAPHMEMORY-AUTOCONFIG
dsl_element: efGraphmemoryAutoconfig
owner_module: spring-ai-ascend-graphmemory-starter
primary_package: ""
source_adr: ADR-0157

# --- fact_refs: every generated fact_id this card cites. Each MUST resolve in
#     architecture/facts/generated/*.json. ---
fact_refs:
  - code-symbol/com-huawei-ascend-service-runtime-graphmemory-graphmemoryautoconfiguration
  - code-symbol/com-huawei-ascend-service-runtime-graphmemory-graphmemoryproperties
  - code-symbol/com-huawei-ascend-service-runtime-memory-spi-graphmemoryrepository
  - code-symbol/com-huawei-ascend-service-runtime-memory-spi-graphmemoryrepository-graphedge
  - code-symbol/com-huawei-ascend-service-runtime-memory-spi-graphmemoryrepository-graphmetadata
  - test/com-huawei-ascend-service-runtime-graphmemory-graphmemoryautoconfigurationtest
---

# `EF-GRAPHMEMORY-AUTOCONFIG` — Graph Memory Auto-Config Frame

> The Spring Boot starter slice that, in a consuming application, decides whether to
> contribute a `GraphMemoryRepository` adapter — binding the starter's configuration
> properties and gating on posture, without owning the SPI it wires.

## 1. Identity

> COPIED from the DSL frame element. These fields MUST match the DSL byte-for-byte;
> the gate fails a card that disagrees.

| Field | Value | Source |
|---|---|---|
| Frame ID (`saa.id`) | `EF-GRAPHMEMORY-AUTOCONFIG` | DSL element |
| DSL element | `efGraphmemoryAutoconfig` | `architecture/features/engineering-frames.dsl` |
| Owner module (`saa.owner`) | `spring-ai-ascend-graphmemory-starter` | DSL element |
| Status (`saa.status`) | `design_only` | DSL element |
| Primary package (`saa.primaryPackage`) | `—` (none declared) | DSL element |
| Source ADR (`saa.sourceAdr`) | `ADR-0157` | DSL element |
| Card path (`saa.cardPath`) | `architecture/docs/L1/frames/EF-GRAPHMEMORY-AUTOCONFIG.md` | DSL element ↔ this file |

## 2. Capability Boundary

> AUTHORED prose. Package names are CITED (they must exist); the lists below are the
> human-readable boundary, not a second registry.

**Can do** — the responsibilities that live inside this frame:

- Register a Spring Boot auto-configuration entry for the GraphMemory starter, contributed
  to a consuming application's context discovery
  (`code-symbol/com-huawei-ascend-service-runtime-graphmemory-graphmemoryautoconfiguration`).
- Bind the starter's namespaced configuration properties and expose the posture switch that
  gates whether a GraphMemory adapter participates at all
  (`code-symbol/com-huawei-ascend-service-runtime-graphmemory-graphmemoryproperties`).
- Gate adapter contribution on the presence of the wired SPI type and on an explicit
  opt-in property, defaulting to *off* so that importing the starter is inert until a
  consumer turns it on.

**Cannot do** — explicitly out of scope (handled by another frame or an L2 detail):

- Own or declare the `GraphMemoryRepository` SPI. That SPI is owned by `agent-service`
  (`code-symbol/com-huawei-ascend-service-runtime-memory-spi-graphmemoryrepository`); this
  frame is a downstream consumer that wires it, per the ownership topology fixed in
  ADR-0082. The starter declares no SPI package of its own.
- Ship a production `GraphMemoryRepository` implementation. The frame currently contributes
  no adapter bean (see section 7 — the auto-configuration is empty of `@Bean` methods, and
  its test asserts no bean is registered). The networked adapter is unimplemented.
- Decide the memory ownership semantics — whether a row is platform state or delegated
  business state. That boundary is owned upstream by the SPI contract and ADR-0051, not by
  the wiring frame.
- Persist or traverse graph data, expose any HTTP/AsyncAPI route, or run any tenant-scoped
  query. Those are the SPI implementation's runtime concerns, delegated to an adapter that
  this frame does not yet provide.

**Owned state** — the data/state this frame is the structural home for:

- The starter's bound configuration properties (the opt-in switch and the reserved adapter
  connection fields)
  (`code-symbol/com-huawei-ascend-service-runtime-graphmemory-graphmemoryproperties`).
- The auto-configuration registration decision itself — i.e. *under what posture an adapter
  is contributed* — but **not** any `GraphMemoryRepository` instance or graph data.

**External dependencies** — frames / modules this frame is allowed to depend on:

- `agent-service` — the starter consumes the `GraphMemoryRepository` SPI declared there
  (the sole `allowed_dependencies` entry in the starter's `module-metadata.yaml`, per
  ADR-0078 / ADR-0082).
- Spring Boot auto-configuration — the framework surface this frame plugs into (conditional
  registration, property binding).

**Forbidden dependencies** — dependencies the boundary must never take:

- The starter's `module-metadata.yaml` declares an empty `forbidden_dependencies` list and
  a single `allowed_dependencies` entry (`agent-service`); the module-build fact layer is
  the structural authority for that envelope. Concretely, the wiring frame must not reach
  into any sibling domain module beyond the `agent-service` SPI it consumes, and must not
  depend on a concrete graph-store driver from inside the starter — the adapter selection is
  a consumer concern.

**Included / excluded packages:**

- Included: `com.huawei.ascend.service.runtime.graphmemory` (the starter's own auto-config
  package — the home of the two production types listed under section 6's anchor).
- Excluded: `com.huawei.ascend.service.runtime.memory.spi` (the `GraphMemoryRepository` SPI
  and its carrier types — owned by `agent-service`, not by this frame).

## 3. Type Inventory

> GENERATED — do not hand-edit between the markers. Rendered from
> `architecture/facts/generated/code-symbols.json`, filtered to the frame's in-boundary
> package(s). The Card generator owns this region and overwrites it on every re-render.

<!-- BEGIN GENERATED: type-inventory -->
<!--
  This frame is `design_only` and declares no `saa.primaryPackage` in its DSL element, so
  no fact-cited Type Inventory is generated here (README §"Status-conditional rules"). The
  two production types that already exist in the starter's auto-config package are cited as
  authored prose in sections 2 and 6; this block stays empty (with stable markers) until the
  frame is promoted to `shipped` with a declared `primaryPackage`. See section 7 for the
  missing-proof statement that governs promotion.
-->
<!-- END GENERATED: type-inventory -->

## 4. Internal Collaboration

> GENERATED — do not hand-edit between the markers. Rendered from `code-symbols.json`:
> the structural relationships among the in-boundary types listed in section 3.

<!-- BEGIN GENERATED: internal-collaboration -->
<!--
  Empty for the same reason as section 3: `design_only`, no declared `primaryPackage`, so
  no generated structural relationship table. Stable markers retained for the gate's block
  anchor. The one structural edge that matters today — the auto-configuration's conditional
  reference to the `GraphMemoryRepository` SPI and its binding of the starter properties —
  is stated as authored prose in section 2.
-->
<!-- END GENERATED: internal-collaboration -->

## 5. Contracts

> AUTHORED prose. The communication contracts this frame exposes or consumes. Cite each
> contract operation by its `contract-op/<id>` fact ID and each SPI by its package identity.

**Exposed SPI / public surface (boundary identity):**

- This frame exposes **no SPI of its own** — the starter's `module-metadata.yaml` declares
  `spi_packages: []`. Its public surface is the Spring Boot auto-configuration contract: the
  auto-config class
  (`code-symbol/com-huawei-ascend-service-runtime-graphmemory-graphmemoryautoconfiguration`)
  and the bound properties type
  (`code-symbol/com-huawei-ascend-service-runtime-graphmemory-graphmemoryproperties`).

**Consumed SPI (the contract this frame wires):**

- `com.huawei.ascend.service.runtime.memory.spi.GraphMemoryRepository` — the consumed
  boundary identity, owned by `agent-service`
  (`code-symbol/com-huawei-ascend-service-runtime-memory-spi-graphmemoryrepository`),
  together with its carrier records
  `code-symbol/com-huawei-ascend-service-runtime-memory-spi-graphmemoryrepository-graphedge`
  and
  `code-symbol/com-huawei-ascend-service-runtime-memory-spi-graphmemoryrepository-graphmetadata`.

**Contract operations (OpenAPI / AsyncAPI):**

- None. This frame anchors an internal-channel FunctionPoint (see section 6) and exposes no
  wire operation; the generated `contract-surfaces.json` carries no `contract-op/*` for
  graph memory. The `memory-store.v1.yaml` contract document (`contract-yaml/memory-store`)
  is the prose taxonomy for the wider memory family; the on-the-wire mechanics of any future
  adapter are L2 detail, delegated to the SPI implementation, not restated here.

## 6. FunctionPoint Mapping

> AUTHORED prose over the frame's DSL `anchors` edges. List ONLY FunctionPoints the DSL
> anchors to this frame (`efGraphmemoryAutoconfig -> fpGraphMemoryStore` in
> `engineering-frames.dsl`).

### `FP-GRAPH-MEMORY-STORE` — tenant-scoped graph memory store, auto-wired by the starter

The DSL anchors exactly one FunctionPoint to this frame
(`efGraphmemoryAutoconfig -> fpGraphMemoryStore`, `saa.rel "anchors"`). The FunctionPoint's
*behaviour* (tenant-scoped CRUD + semantic facts over the graph) is defined by the
`GraphMemoryRepository` SPI it wires; this frame's contribution to that FunctionPoint is the
auto-configuration decision and property binding, not the storage behaviour itself.

| Anchor | Fact ID |
|---|---|
| Auto-config entry class | `code-symbol/com-huawei-ascend-service-runtime-graphmemory-graphmemoryautoconfiguration` |
| Bound properties type | `code-symbol/com-huawei-ascend-service-runtime-graphmemory-graphmemoryproperties` |
| Wired SPI (owned by `agent-service`) | `code-symbol/com-huawei-ascend-service-runtime-memory-spi-graphmemoryrepository` |
| Test | `test/com-huawei-ascend-service-runtime-graphmemory-graphmemoryautoconfigurationtest` |

> Note: the anchored FunctionPoint `FP-GRAPH-MEMORY-STORE` carries `saa.status "shipped"` in
> `function-points.dsl`, but the frame that anchors it is `design_only` because the wiring
> contributes no adapter bean yet (section 7). The structural anchor edge exists; the
> production realization behind it does not. This frame stays `design_only` until that gap
> closes.

## 7. Verification

> AUTHORED prose. The constraints, ArchUnit enforcers, and gate rules that hold this
> boundary. Cite each enforcer / rule as a structural identifier.

**Constraints / enforcers holding the boundary:**

- The starter's dependency envelope is held structurally by its `module-metadata.yaml`
  (`allowed_dependencies: [agent-service]`, `forbidden_dependencies: []`, `spi_packages: []`)
  under the module-metadata completeness gate rule; the module-build fact layer is the
  authority for that envelope (ADR-0082 makes `module-metadata.yaml` the single source of
  truth for SPI ownership topology).
- The Frame-Card consistency gate holds this card's identity block against the
  `efGraphmemoryAutoconfig` DSL element (`saa.id` / `saa.owner` / `saa.status` /
  `saa.primaryPackage`) and cross-checks every `fact_refs` entry against
  `architecture/facts/generated/*.json` (ADR-0161).

**Tests anchoring the behaviour** (fact-cited):

- `test/com-huawei-ascend-service-runtime-graphmemory-graphmemoryautoconfigurationtest` —
  proves the auto-configuration loads without failing **and** that it contributes **no**
  `GraphMemoryRepository` bean. This is the running evidence that the frame is wiring-only:
  the posture gate is in place, but no production adapter is registered.

> **Missing proof before promotion to `shipped`:** the DSL element declares no
> `saa.primaryPackage`, and the auto-configuration ships no `GraphMemoryRepository` adapter
> bean (its test asserts the bean is absent). The starter's reserved connection properties
> are documented orphan-config, not live wiring. Until the frame declares a
> `primaryPackage`, contributes a real adapter, and that adapter's behaviour is contract-
> and test-backed, this frame stays `design_only` and carries no fact-cited Type Inventory
> in sections 3–4.

## Cross-references

- Frames directory + Card-over-DSL rules: [`README.md`](README.md).
- This frame's DSL element (authority): [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl).
- Generated facts cited above (authority over this prose): [`../../../facts/generated/`](../../../facts/generated/).
- Collective structural map: [`../engineering-frames.md`](../engineering-frames.md).
- Owner module L1 design: [`../graphmemory-starter/ARCHITECTURE.md`](../graphmemory-starter/ARCHITECTURE.md).
- Wired SPI ownership (authority): [`../../../../docs/adr/0082-graphmemory-ownership-canonical-and-topology-truth.yaml`](../../../../docs/adr/0082-graphmemory-ownership-canonical-and-topology-truth.yaml).
- This frame's L2 detail sink (adapter runtime mechanics, when it lands): `architecture/docs/L2/graphmemory-autoconfig/`.
