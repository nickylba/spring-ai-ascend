---
level: L1
view: development
status: shipped
authority: "ADR-0161 (Frame Card shape + Card-over-DSL); ADR-0157 (EngineeringFrame Ontology); ADR-0073 (Runtime-Owned Middleware via Engine Hooks)"

# --- Identity block: COPIED from the DSL frame element (do not invent) ---
frame_id: EF-HOOK-SURFACE
dsl_element: efHookSurface
owner_module: agent-middleware
primary_package: "com.huawei.ascend.middleware"
source_adr: ADR-0157

# --- fact_refs: every generated fact_id this card cites. Each MUST resolve in
#     architecture/facts/generated/*.json. ---
fact_refs:
  - code-symbol/com-huawei-ascend-middleware-hookdispatcher
  - code-symbol/com-huawei-ascend-middleware-spi-hookpoint
  - code-symbol/com-huawei-ascend-middleware-spi-runtimemiddleware
  - code-symbol/com-huawei-ascend-middleware-spi-hookcontext
  - code-symbol/com-huawei-ascend-middleware-spi-hookoutcome
  - code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-proceed
  - code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-shortcircuit
  - code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-fail
  - contract-yaml/engine-hooks
  - test/com-huawei-ascend-middleware-hookdispatcherfireordertest
  - test/com-huawei-ascend-middleware-spi-spicarrierimmutabilitytest
  - test/com-huawei-ascend-engine-runtime-runtimemiddlewareinterceptshooksit
  - test/com-huawei-ascend-engine-runtime-everyenginedeclareshooksurfacetest
---

# `EF-HOOK-SURFACE` — Hook Surface Frame

> The engineering home for the runtime middleware hook surface: the structural anchor
> that owns *how* cross-cutting policy attaches to a Run dispatch — the `HookPoint`
> taxonomy, the `RuntimeMiddleware` SPI, the `HookContext` / `HookOutcome` carriers, and
> the `HookDispatcher` that fires the chain.

## 1. Identity

> COPIED from the DSL frame element. These fields MUST match the DSL byte-for-byte;
> the gate fails a card that disagrees.

| Field | Value | Source |
|---|---|---|
| Frame ID (`saa.id`) | `EF-HOOK-SURFACE` | DSL element |
| DSL element | `efHookSurface` | `architecture/features/engineering-frames.dsl` |
| Owner module (`saa.owner`) | `agent-middleware` | DSL element |
| Status (`saa.status`) | `shipped` | DSL element |
| Primary package (`saa.primaryPackage`) | `com.huawei.ascend.middleware` | DSL element |
| Source ADR (`saa.sourceAdr`) | `ADR-0157` | DSL element |
| Card path (`saa.cardPath`) | `architecture/docs/L1/frames/EF-HOOK-SURFACE.md` | DSL element ↔ this file |

## 2. Capability Boundary

> AUTHORED prose. Package names are CITED (they must exist); the lists below are the
> human-readable boundary, not a second registry.

This frame is a **package cluster** rooted at the declared primary package
`com.huawei.ascend.middleware`. The cluster is exactly two packages: the dispatcher
class home `com.huawei.ascend.middleware` (the `HookDispatcher` class,
`code-symbol/com-huawei-ascend-middleware-hookdispatcher`) and the hook SPI package
`com.huawei.ascend.middleware.spi`, which carries the boundary identity — the
`HookPoint` taxonomy (`code-symbol/com-huawei-ascend-middleware-spi-hookpoint`), the
`RuntimeMiddleware` SPI (`code-symbol/com-huawei-ascend-middleware-spi-runtimemiddleware`),
and the `HookContext` (`code-symbol/com-huawei-ascend-middleware-spi-hookcontext`) and
`HookOutcome` (`code-symbol/com-huawei-ascend-middleware-spi-hookoutcome`) carriers. The
GENERATED Type Inventory (section 3) is rendered for the literal declared primary
package, so the hook SPI carriers are CITED here in the authored boundary and detailed in
sections 5–6 rather than re-listed by the generator.

**Can do** — the responsibilities that live inside this frame:

- Own the canonical `HookPoint` lifecycle taxonomy a Run dispatch fires against.
- Own the `RuntimeMiddleware` SPI — the single interface a cross-cutting policy
  implements to observe a dispatch lifecycle point.
- Own the immutable hook carriers `HookContext` (the per-fire input) and `HookOutcome`
  (the per-middleware result, with its permitted shapes `HookOutcome.Proceed`,
  `HookOutcome.ShortCircuit`, `HookOutcome.Fail`).
- Own the `HookDispatcher` that holds an ordered middleware list and fires the chain for
  a given `HookContext`, applying the in-chain fail-fast property (a non-`Proceed`
  outcome stops later middlewares for that fire).

**Cannot do** — explicitly out of scope (handled by another frame or an L2 detail):

- Engine selection / dispatch routing — owned by `EF-ENGINE-REGISTRY`
  (`agent-execution-engine`); a Run reaches the hook surface only *because* the registry
  routed it.
- The cross-cutting capability SPI families (model gateway, skill/tool, memory,
  vector/retrieval/embedding, prompt/advisor) — owned by the sibling frame
  `EF-CAPABILITY-SPI` (same `agent-middleware` module). Those live under the excluded
  subpackages listed below; they are policies that *attach via* this hook surface, not
  part of it.
- Run-state consumption of a `HookOutcome` (mapping `Fail` → `Run.FAILED`,
  `ShortCircuit` → engine bypass) — that orchestrator behaviour is owned by the
  orchestrator/engine plane and is a runtime-sequence concern delegated to L2 + Rule
  R-M sub-clause .c (it is a deferred sub-clause, not part of this boundary today).
- The over-the-wire hook taxonomy / ordering / failure-propagation mechanics — declared
  by the `engine-hooks.v1.yaml` contract surface; this frame holds the Java SPI identity,
  not the wire mechanics.

**Owned state** — the data/state this frame is the structural home for:

- The `HookPoint` enum taxonomy (the canonical lifecycle point names).
- The immutable in-memory carriers `HookContext` and the `HookOutcome` family.
- The `HookDispatcher`'s ordered middleware list (per-instance, immutable after
  construction).

**External dependencies** — frames / modules this frame is allowed to depend on:

- The `engine-hooks.v1.yaml` contract surface (`contract-yaml/engine-hooks`) — the
  schema this SPI realizes; the `HookPoint` enum and the YAML hook taxonomy are held in
  bidirectional consistency by the gate (section 7).

**Forbidden dependencies** — dependencies the boundary must never take (held by an
ArchUnit enforcer; cite it in section 7):

- Concrete engine / orchestrator implementations — the hook surface is consumed *by* the
  engine plane, never the reverse; engines depend on this SPI, not vice versa (the
  inversion that lets heterogeneous engines share one middleware surface, ADR-0073).
- The capability SPI subpackages under `com.huawei.ascend.middleware.*.spi` — they belong
  to `EF-CAPABILITY-SPI`; this frame does not reach into them.

**Included / excluded packages** (this frame is a package *cluster*, not a single root):

- Included: `com.huawei.ascend.middleware` (the `HookDispatcher` class only),
  `com.huawei.ascend.middleware.spi` (the hook SPI carriers).
- Excluded (owned by `EF-CAPABILITY-SPI`): `com.huawei.ascend.middleware.model.spi`,
  `com.huawei.ascend.middleware.skill.spi`, `com.huawei.ascend.middleware.memory.spi`,
  `com.huawei.ascend.middleware.vector.spi`, `com.huawei.ascend.middleware.retrieval.spi`,
  `com.huawei.ascend.middleware.embedding.spi`, `com.huawei.ascend.middleware.prompt.spi`,
  `com.huawei.ascend.middleware.advisor.spi`, `com.huawei.ascend.middleware.advisor.adapter`.

## 3. Type Inventory

> GENERATED — do not hand-edit between the markers. Rendered from
> `architecture/facts/generated/code-symbols.json`, filtered to the frame's declared
> primary package. Every row cites its `code-symbol/<kebab-fqn>` fact ID. The Card
> generator (`gate/lib/render_frame_card_inventory.py`) owns this region and overwrites
> it on every re-render. The hook SPI carriers that complete this frame's boundary live
> in the cited sibling package `com.huawei.ascend.middleware.spi` and are named in
> sections 2, 5, and 6.

<!-- BEGIN GENERATED: type-inventory -->
| Type | Kind | Fact ID |
|---|---|---|
| `com.huawei.ascend.middleware.HookDispatcher` | class | `code-symbol/com-huawei-ascend-middleware-hookdispatcher` |
<!-- END GENERATED: type-inventory -->

## 4. Internal Collaboration

> GENERATED — do not hand-edit between the markers. Rendered from `code-symbols.json`:
> the structural relationships (implements / extends / references) among the in-boundary
> types of the declared primary package. This is the *structural* collaboration only —
> runtime call sequences belong in the frame's L2 sink, not here. The cross-package hook
> SPI collaboration (the records realizing `HookOutcome`, `HookContext` referencing
> `HookPoint`, `RuntimeMiddleware` referencing the carriers) is described in section 5.

<!-- BEGIN GENERATED: internal-collaboration -->
| From | Relationship | To |
|---|---|---|
| _(none — no in-boundary inheritance, interface realization, or descriptor reference between two of this frame's types)_ |  |  |
<!-- END GENERATED: internal-collaboration -->

## 5. Contracts

> AUTHORED prose. The communication contracts this frame exposes or consumes. Cite each
> contract operation by its fact ID and each SPI by its package identity. Wire-field and
> over-the-wire mechanics are L2 — link down, do not inline.

**Exposed SPI / public surface (boundary identity):**

- `com.huawei.ascend.middleware.spi` — the public package that *is* the hook boundary.
  Its key types: `RuntimeMiddleware`
  (`code-symbol/com-huawei-ascend-middleware-spi-runtimemiddleware`) — the single SPI a
  cross-cutting policy implements; `HookPoint`
  (`code-symbol/com-huawei-ascend-middleware-spi-hookpoint`) — the lifecycle-point
  taxonomy; `HookContext` (`code-symbol/com-huawei-ascend-middleware-spi-hookcontext`) —
  the immutable per-fire input carrier; and `HookOutcome`
  (`code-symbol/com-huawei-ascend-middleware-spi-hookoutcome`) — the sealed result whose
  permitted shapes are `HookOutcome.Proceed`
  (`code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-proceed`),
  `HookOutcome.ShortCircuit`
  (`code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-shortcircuit`), and
  `HookOutcome.Fail` (`code-symbol/com-huawei-ascend-middleware-spi-hookoutcome-fail`).
- `com.huawei.ascend.middleware` — the `HookDispatcher`
  (`code-symbol/com-huawei-ascend-middleware-hookdispatcher`) is the in-process driver
  that holds the ordered middleware list and fires it for a `HookContext`. It is the
  structural collaboration root: it consumes a `HookContext` and returns a `HookOutcome`,
  and each `RuntimeMiddleware` consumes a `HookContext` and returns a `HookOutcome`. The
  three `HookOutcome` records realize the `HookOutcome` interface, and `HookContext`
  carries a `HookPoint`. (The exact firing order, fail-fast semantics, and outcome
  handling are runtime mechanics — see the contract surface and the frame's L2 sink, not
  this Card.)

**Contract operations (OpenAPI / AsyncAPI):**

| Operation | Fact ID | Contract source |
|---|---|---|
| Engine hook taxonomy + ordering + failure-propagation schema | `contract-yaml/engine-hooks` | `docs/contracts/engine-hooks.v1.yaml` |

The `HookPoint` enum and the `hooks` / `ordering` / `failure_propagation` blocks of
`engine-hooks.v1.yaml` are held in bidirectional consistency by the gate (Rule 57,
enforcer E78 — section 7).

**Consumed contracts** (operations this frame calls on another frame):

- None. The hook surface is consumed *by* the engine/orchestrator plane (every engine
  declares this hook surface); it does not call out to another frame's contract.

## 6. FunctionPoint Mapping

> AUTHORED prose over the frame's DSL `anchors` edges. Lists ONLY FunctionPoints the DSL
> anchors to this frame (`efHookSurface -> fpHookDispatch` in `engineering-frames.dsl`).
> Every anchor is CITED by generated fact ID.

This frame anchors exactly one FunctionPoint in the DSL: `FP-HOOK-DISPATCH`.

### `FP-HOOK-DISPATCH` — a `RuntimeMiddleware` chain observes a Run dispatch at a canonical `HookPoint`

The behaviour: a `HookDispatcher` fires its ordered `RuntimeMiddleware` list for a given
`HookContext` and returns the resolved `HookOutcome` (the canonical `HookPoint` taxonomy
and the wire ordering/failure-propagation are the `contract-yaml/engine-hooks` material,
not this heading).

| Anchor | Fact ID |
|---|---|
| Entry class | `code-symbol/com-huawei-ascend-middleware-hookdispatcher` |
| Entry method | `code-symbol/com-huawei-ascend-middleware-hookdispatcher#fire(Lcom/huawei/ascend/middleware/spi/HookContext;)Lcom/huawei/ascend/middleware/spi/HookOutcome;` |
| SPI participant | `code-symbol/com-huawei-ascend-middleware-spi-runtimemiddleware#onHook(Lcom/huawei/ascend/middleware/spi/HookContext;)Lcom/huawei/ascend/middleware/spi/HookOutcome;` |
| Input carrier | `code-symbol/com-huawei-ascend-middleware-spi-hookcontext` |
| Result carrier | `code-symbol/com-huawei-ascend-middleware-spi-hookoutcome` |
| Contract surface | `contract-yaml/engine-hooks` |
| Test (fire order) | `test/com-huawei-ascend-middleware-hookdispatcherfireordertest` |
| Test (engine declares hook surface) | `test/com-huawei-ascend-engine-runtime-everyenginedeclareshooksurfacetest` |
| Test (middleware intercepts hooks) | `test/com-huawei-ascend-engine-runtime-runtimemiddlewareinterceptshooksit` |

## 7. Verification

> AUTHORED prose. The constraints, ArchUnit enforcers, and gate rules that hold this
> boundary. Each enforcer / rule is cited as a structural identifier (not version
> metadata).

**Constraints / enforcers holding the boundary:**

- Rule R-M sub-clause .c (Runtime-Owned Middleware via Engine Hooks) — asserts that
  cross-cutting policy MUST be a `RuntimeMiddleware` listening on a canonical `HookPoint`
  from `docs/contracts/engine-hooks.v1.yaml`, never an engine-embedded concern.
- Enforcer E79 (ArchUnit) — the uniform-hook-surface constraint: every engine declares
  the hook surface, so the SPI is the single attachment point across heterogeneous engines.
- Enforcer E80 (integration) — the interception constraint: a registered `RuntimeMiddleware`
  is bound into the fired hook lifecycle.
- Gate Rule 57 (`engine_hooks_yaml_present_and_wellformed`, enforcer E78) — the
  bidirectional-consistency constraint between the `HookPoint` enum and the
  `engine-hooks.v1.yaml` hook taxonomy.
- Gate Rule 146 (Frame-Card / DSL parity, enforcer E196, Rule G-29) — the Card-over-DSL
  constraint: this Card's copied identity matches the `efHookSurface` DSL element and every
  fact ID it cites resolves in the generated facts.

**Tests anchoring the behaviour.** The hook-surface behaviour (declared-order fail-fast
dispatch, hook-carrier immutability, engine-side interception, uniform surface declaration)
is proven by the test facts in this card's frontmatter `fact_refs:` block, which the gate
resolves against `architecture/facts/generated/tests.json`. The per-test asserted behaviour
is the behaviour catalogue and lives with those test facts and this frame's FunctionPoint
`saa.test_refs[]` in `function-points.dsl`, not as an inventory in this L1 card.

## Cross-references

- Frames directory + Card-over-DSL rules: [`README.md`](README.md).
- This frame's DSL element (authority): [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl).
- This frame's anchored FunctionPoint (`fpHookDispatch`): [`../../../features/function-points.dsl`](../../../features/function-points.dsl).
- Generated facts cited above (authority over this prose): [`../../../facts/generated/`](../../../facts/generated/).
- Sibling frame in the same module (the capability SPI families excluded above): `EF-CAPABILITY-SPI`.
- Collective structural map: [`../engineering-frames.md`](../engineering-frames.md).
- Hook contract surface (wire mechanics live here, not in this Card): [`../../../../docs/contracts/engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml).
