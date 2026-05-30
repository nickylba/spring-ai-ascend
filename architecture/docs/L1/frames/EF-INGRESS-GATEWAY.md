---
level: L1
view: development
status: shipped
authority: "ADR-0161 (Frame Card shape + Card-over-DSL); ADR-0157 (EngineeringFrame Ontology)"

# --- Identity block: COPIED from the DSL frame element (do not invent) ---
frame_id: EF-INGRESS-GATEWAY
dsl_element: efIngressGateway
owner_module: agent-bus
primary_package: com.huawei.ascend.bus.spi.ingress
source_adr: ADR-0157

# --- fact_refs: every generated fact_id this card cites. Each MUST resolve in
#     architecture/facts/generated/*.json. The gate cross-checks these. ---
fact_refs:
  - code-symbol/com-huawei-ascend-bus-spi-ingress-ingressgateway
  - code-symbol/com-huawei-ascend-bus-spi-ingress-ingressenvelope
  - code-symbol/com-huawei-ascend-bus-spi-ingress-ingressenvelope-ingressrequesttype
  - code-symbol/com-huawei-ascend-bus-spi-ingress-ingressresponse
  - code-symbol/com-huawei-ascend-bus-spi-ingress-ingressresponse-ingressstatus
  - test/com-huawei-ascend-client-architecture-edgetocomputedirectlinkarchtest
  - test/com-huawei-ascend-service-runtime-architecture-spipuritygeneralizedarchtest
---

# `EF-INGRESS-GATEWAY` — Ingress Gateway Frame

> The single cross-plane control surface that normalizes a client-originated
> request into an `IngressEnvelope` and routes it from the edge plane to the
> compute_control plane, returning a synchronous acknowledgement.

## 1. Identity

> COPIED from the DSL frame element. These fields MUST match the DSL byte-for-byte;
> the gate fails a card that disagrees.

| Field | Value | Source |
|---|---|---|
| Frame ID (`saa.id`) | `EF-INGRESS-GATEWAY` | DSL element |
| DSL element | `efIngressGateway` | `architecture/features/engineering-frames.dsl` |
| Owner module (`saa.owner`) | `agent-bus` | DSL element |
| Status (`saa.status`) | `shipped` | DSL element |
| Primary package (`saa.primaryPackage`) | `com.huawei.ascend.bus.spi.ingress` | DSL element |
| Source ADR (`saa.sourceAdr`) | `ADR-0157` | DSL element |
| Card path (`saa.cardPath`) | `architecture/docs/L1/frames/EF-INGRESS-GATEWAY.md` | DSL element ↔ this file |

## 2. Capability Boundary

> AUTHORED prose. Package names are CITED (they must exist); the lists below are the
> human-readable boundary, not a second registry.

**Can do** — the responsibilities that live inside this frame:

- Declare the one public ingress SPI (`com.huawei.ascend.bus.spi.ingress`) that
  carries every client-to-server (C2S) call across the edge → compute_control plane
  boundary.
- Define the request normalization shape (`IngressEnvelope`) and its closed request
  taxonomy (`IngressEnvelope$IngressRequestType`) so the bus has one validated
  envelope to route, regardless of how the edge client expressed the request.
- Define the synchronous acknowledgement shape (`IngressResponse`) and its closed
  outcome set (`IngressResponse$IngressStatus`: accept / reject / defer), including
  the factory surface that constructs each outcome.
- Validate the contract-spine invariants every envelope must satisfy on
  construction — a non-null tenant scope and a well-formed trace identifier — so a
  malformed C2S request is refused at the boundary, not deep in the runtime.

**Cannot do** — explicitly out of scope (handled by another frame or an L2 detail):

- Execute the request. The frame is a boundary identity only; the runtime binding
  that satisfies `IngressGateway` lives in the compute_control plane and is owned by
  the agent-service request-admission frame, not here.
- Speak the server-to-client (S2C) direction. The reverse callback transport is the
  separate `EF-S2C-TRANSPORT` frame (`com.huawei.ascend.bus.spi.s2c`).
- Carry the on-the-wire request/response mechanics, the HTTP route/verb/status
  mapping, or the channel-forwarding sequence. Those are L2 runtime detail delegated
  to the contract surface and the frame's L2 sink, not restated in this card.

**Owned state** — the data/state this frame is the structural home for:

- The C2S ingress SPI surface: the `IngressGateway` boundary interface plus its two
  immutable value carriers (`IngressEnvelope`, `IngressResponse`) and their nested
  closed enums. The frame owns no mutable runtime state — it is a pure contract
  surface with no implementation behind it at this status.

**External dependencies** — frames / modules this frame is allowed to depend on:

- `java.*` only. As an SPI package the frame depends on the JDK and its own
  same-package siblings; it takes no framework or cross-module dependency.

**Forbidden dependencies** — dependencies the boundary must never take (held by an
ArchUnit enforcer; cited in section 7):

- Spring, Reactor, Jackson, Micrometer/OpenTelemetry, the `com.huawei.ascend.service.platform`
  layer, and in-memory reference implementations — forbidden because an SPI package must stay
  framework-free so any plane can realize it (held by `SpiPurityGeneralizedArchTest`).
- `com.huawei.ascend.service.*`, `com.huawei.ascend.engine.*`,
  `com.huawei.ascend.middleware.*` — the edge plane must never import compute_control
  plane production code; every C2S call routes through this SPI instead (held by
  `EdgeToComputeDirectLinkArchTest` and gate Rule 105).

**Included / excluded packages** (this frame is a single-root package, not a cluster):

- Included: `com.huawei.ascend.bus.spi.ingress`.
- Excluded: `com.huawei.ascend.bus.spi.s2c` (belongs to `EF-S2C-TRANSPORT`).

## 3. Type Inventory

> GENERATED — do not hand-edit between the markers. Rendered from
> `architecture/facts/generated/code-symbols.json`, filtered to the frame's in-boundary
> package(s). Every row cites its `code-symbol/<kebab-fqn>` fact ID. The Card generator owns
> this region and overwrites it on every re-render.

<!-- BEGIN GENERATED: type-inventory -->
| Type | Kind | Fact ID |
|---|---|---|
| `com.huawei.ascend.bus.spi.ingress.IngressEnvelope` | record | `code-symbol/com-huawei-ascend-bus-spi-ingress-ingressenvelope` |
| `com.huawei.ascend.bus.spi.ingress.IngressEnvelope$IngressRequestType` | enum | `code-symbol/com-huawei-ascend-bus-spi-ingress-ingressenvelope-ingressrequesttype` |
| `com.huawei.ascend.bus.spi.ingress.IngressGateway` | interface | `code-symbol/com-huawei-ascend-bus-spi-ingress-ingressgateway` |
| `com.huawei.ascend.bus.spi.ingress.IngressResponse` | record | `code-symbol/com-huawei-ascend-bus-spi-ingress-ingressresponse` |
| `com.huawei.ascend.bus.spi.ingress.IngressResponse$IngressStatus` | enum | `code-symbol/com-huawei-ascend-bus-spi-ingress-ingressresponse-ingressstatus` |
<!-- END GENERATED: type-inventory -->

## 4. Internal Collaboration

> GENERATED — do not hand-edit between the markers. Rendered from `code-symbols.json`:
> the structural relationships (implements / extends / references) among the in-boundary
> types listed in section 3. This is the *structural* collaboration only — runtime call
> sequences belong in the frame's L2 sink, not here.

<!-- BEGIN GENERATED: internal-collaboration -->
| From | Relationship | To |
|---|---|---|
| `IngressEnvelope` | references | `IngressEnvelope$IngressRequestType` |
| `IngressGateway` | references | `IngressEnvelope` |
| `IngressGateway` | references | `IngressResponse` |
| `IngressResponse` | references | `IngressResponse$IngressStatus` |
<!-- END GENERATED: internal-collaboration -->

## 5. Contracts

> AUTHORED prose. The communication contracts this frame exposes or consumes. Cite each
> contract operation by its `contract-op/<id>` fact ID and each SPI by its package identity.
> Wire-field and over-the-wire mechanics are L2 — link down, do not inline.

**Exposed SPI / public surface (boundary identity):**

- `com.huawei.ascend.bus.spi.ingress` — the public package that *is* the boundary. Its
  routing entrypoint is the interface `IngressGateway`
  (`code-symbol/com-huawei-ascend-bus-spi-ingress-ingressgateway`), with the request and
  acknowledgement carriers `IngressEnvelope`
  (`code-symbol/com-huawei-ascend-bus-spi-ingress-ingressenvelope`) and `IngressResponse`
  (`code-symbol/com-huawei-ascend-bus-spi-ingress-ingressresponse`).

**Contract operations (OpenAPI / AsyncAPI):**

This frame exposes no HTTP/AsyncAPI operation. Its routing entrypoint is an in-process
SPI method, not a wire endpoint, so no `contract-op/<id>` fact exists for it. The
envelope/response wire shape is governed by the schema contract
`contract-yaml/ingress-envelope` (`docs/contracts/ingress-envelope.v1.yaml`), whose
status is `design_only` and which is not runtime-enforced until the edge SDK lands.

**Consumed contracts** (operations this frame calls on another frame):

- None. As a pure boundary declaration the frame consumes no other frame's contract; it
  is the surface that a compute_control-plane implementation consumes, not a caller.

## 6. FunctionPoint Mapping

> AUTHORED prose over the frame's DSL `anchors` edges. Lists ONLY the FunctionPoint the
> DSL anchors to this frame (`efIngressGateway -> fpIngressEnvelope` in
> `engineering-frames.dsl`).

### `FP-INGRESS-ENVELOPE` — route a normalized client request across the edge → compute_control boundary

| Anchor | Fact ID |
|---|---|
| Entry interface | `code-symbol/com-huawei-ascend-bus-spi-ingress-ingressgateway` |
| Entry method | `code-symbol/com-huawei-ascend-bus-spi-ingress-ingressgateway#routeClientRequest(Lcom/huawei/ascend/bus/spi/ingress/IngressEnvelope;)Lcom/huawei/ascend/bus/spi/ingress/IngressResponse;` |
| Request carrier | `code-symbol/com-huawei-ascend-bus-spi-ingress-ingressenvelope` |
| Acknowledgement carrier | `code-symbol/com-huawei-ascend-bus-spi-ingress-ingressresponse` |
| Contract (schema) | `contract-yaml/ingress-envelope` (`docs/contracts/ingress-envelope.v1.yaml`) |

The DSL declares this anchor as `efIngressGateway -> fpIngressEnvelope` with
`saa.rel = anchors`; the underlying FunctionPoint element is `FP-INGRESS-ENVELOPE`
(`saa.owner = agent-bus`, `saa.channel = internal`). No fact-cited test anchors the
routing behaviour yet — the frame ships the SPI boundary only, with no runtime binding,
so there is no implementation to exercise (see section 7).

## 7. Verification

> AUTHORED prose. The constraints, ArchUnit enforcers, and gate rules that hold this
> boundary. Cite each enforcer / rule as a structural identifier (not version metadata).

**Constraints / enforcers holding the boundary:**

- `SpiPurityGeneralizedArchTest` (enforcer `E48`) — asserts that any
  `com.huawei.ascend..spi..` package, which includes
  `com.huawei.ascend.bus.spi.ingress`, depends only on the JDK and same-package siblings
  (no Spring, no `com.huawei.ascend.service.platform`, no in-memory reference impls, no
  Micrometer, no OpenTelemetry).
- `EdgeToComputeDirectLinkArchTest` (enforcer `E143`) — asserts at the bytecode level
  that no edge-plane class imports any `com.huawei.ascend.{service,engine,middleware}.*`
  class, forcing every C2S call through this ingress SPI.
- Rule `105` — `edge_no_direct_compute_link` (enforcer `E144`) — the source-grep
  complement to `E143`: it scans edge-plane source for forbidden compute_control imports
  and for direct HTTP-client construction against non-bus hosts.

**Tests anchoring the behaviour** (fact-cited):

- `test/com-huawei-ascend-service-runtime-architecture-spipuritygeneralizedarchtest` —
  proves the ingress SPI package stays framework-free (SPI purity).
- `test/com-huawei-ascend-client-architecture-edgetocomputedirectlinkarchtest` —
  proves the edge plane takes no direct compute_control dependency, so the ingress SPI
  is the only C2S path. Armed-but-vacuous while `agent-client` is a skeleton; it begins
  gating PRs once the edge SDK lands.

> **Why the FunctionPoint carries no behavioural test:** the frame ships the
> `IngressGateway` SPI declaration and its envelope/response carriers only. There is no
> production implementation behind the boundary at this status, and the
> `ingress-envelope.v1.yaml` contract is `design_only` (not runtime-enforced). The two
> tests above hold the boundary's *negative* invariants (purity + no edge→compute link);
> a behavioural test for `routeClientRequest` lands with the runtime binding when the
> edge SDK arrives.

## Cross-references

- Frames directory + Card-over-DSL rules: [`README.md`](README.md).
- This frame's DSL element (authority): [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl).
- Generated facts cited above (authority over this prose): [`../../../facts/generated/`](../../../facts/generated/).
- Collective structural map: [`../engineering-frames.md`](../engineering-frames.md).
- This frame's L2 detail sink (runtime mechanics, if any): `architecture/docs/L2/ingress-gateway/`.
