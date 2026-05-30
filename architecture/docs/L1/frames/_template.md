---
# Frame Card frontmatter. The identity block is COPIED from the frame's DSL
# element in architecture/features/engineering-frames.dsl (or, for the six
# agent-service frames, the re-tagged element in features.dsl). Every value
# here MUST match the DSL; the gate fails a card whose frontmatter disagrees
# with the DSL element's saa.id / saa.owner / saa.status / saa.primaryPackage.
level: L1
view: development
status: template            # replace with the frame's saa.status (shipped | design_only | mock_functional)
authority: "ADR-0161 (Frame Card shape + Card-over-DSL); ADR-0157 (EngineeringFrame Ontology)"

# --- Identity block: COPIED from the DSL frame element (do not invent) ---
frame_id: EF-<SLUG>                 # = saa.id of the frame element
dsl_element: ef<CamelName>         # = the DSL element name (e.g. efAccessAdmission, efEnginePort)
owner_module: <module>            # = saa.owner (one of the 7 domain modules)
primary_package: ""               # = saa.primaryPackage; REQUIRED on shipped frames, "" allowed on design_only/skeleton
source_adr: ADR-<NNNN>            # = saa.sourceAdr

# --- fact_refs: every generated fact_id this card cites. Each MUST resolve in
#     architecture/facts/generated/*.json. The gate cross-checks these. ---
fact_refs: []                     # e.g. [code-symbol/com-huawei-ascend-..., test/com-huawei-ascend-..., contract-op/createrun]
---

<!--
  HOW TO USE THIS TEMPLATE
  ========================
  1. Copy to architecture/docs/L1/frames/<frame-id>.md (filename = saa.id, e.g. EF-ACCESS-ADMISSION.md).
  2. Fill the frontmatter identity block by COPYING the frame's DSL element fields. They MUST match.
  3. Author sections 1, 2, 5, 6, 7 as prose. CITE every code / type / method / contract / test
     claim by its generated fact_id; invent no IDs, owners, statuses, or edges (ADR-0161 §3).
  4. Populate sections 3 and 4 ONLY inside the GENERATED managed blocks, from the Card generator
     sourced from architecture/facts/generated/code-symbols.json. Do not hand-edit between the markers.
  5. Replace every <PLACEHOLDER> and remove the illustrative example rows.

  ALTITUDE: this is an L1 artifact. Name the boundary, package root, public API/SPI surface, and
  inter-frame contract. Do NOT carry L2 runtime detail (private call chains, runtime sequences,
  SQL/RLS/GUC, HTTP status/verb/header behaviour, filter ordering, wire formats, exhaustive method
  or test dumps) — that detail is delegated to the frame's architecture/docs/L2/<slug>/ sink + the
  contract surface, not restated here. Link down to the frame's L2 sink instead.
  <!-- l2-detail-sink-allow: template altitude disclaimer NAMES the forbidden L2 categories to forbid them (a delegation pointer, not an inlined leak); the home is architecture/docs/L2/<slug>/ -->

-->

# `EF-<SLUG>` — <Frame Display Name>

> One-sentence statement of the durable engineering responsibility this frame anchors.
> Carry no rationale here — rationale lives in `source_adr`.

## 1. Identity

> COPIED from the DSL frame element. These fields MUST match the DSL byte-for-byte;
> the gate fails a card that disagrees.

| Field | Value | Source |
|---|---|---|
| Frame ID (`saa.id`) | `EF-<SLUG>` | DSL element |
| DSL element | `ef<CamelName>` | `architecture/features/engineering-frames.dsl` (or `features.dsl` for agent-service) |
| Owner module (`saa.owner`) | `<module>` | DSL element |
| Status (`saa.status`) | `<shipped \| design_only \| mock_functional>` | DSL element |
| Primary package (`saa.primaryPackage`) | `<com.huawei.ascend...>` or `—` (design_only) | DSL element |
| Source ADR (`saa.sourceAdr`) | `ADR-<NNNN>` | DSL element |
| Card path (`saa.cardPath`) | `architecture/docs/L1/frames/EF-<SLUG>.md` | DSL element ↔ this file |

## 2. Capability Boundary

> AUTHORED prose. Package names are CITED (they must exist); the lists below are the
> human-readable boundary, not a second registry.

**Can do** — the responsibilities that live inside this frame:

- <responsibility 1>
- <responsibility 2>

**Cannot do** — explicitly out of scope (handled by another frame or an L2 detail):

- <non-responsibility 1 — name the owning frame / layer>
- <non-responsibility 2>

**Owned state** — the data/state this frame is the structural home for:

- <owned aggregate / SPI surface / in-memory structure>

**External dependencies** — frames / modules this frame is allowed to depend on:

- `<module or frame>` — why.

**Forbidden dependencies** — dependencies the boundary must never take (held by an
ArchUnit enforcer; cite it in section 7):

- `<package the frame must not import>` — why forbidden.

**Included / excluded packages** (when the frame is a package *cluster*, not a single root):

- Included: `<com.huawei.ascend...>`, `<com.huawei.ascend...sibling>`
- Excluded: `<com.huawei.ascend...neighbour>` (belongs to `<other frame>`)

## 3. Type Inventory

> GENERATED — do not hand-edit between the markers. Rendered from
> `architecture/facts/generated/code-symbols.json`, filtered to the frame's in-boundary
> package(s). Every row cites its `code-symbol/<kebab-fqn>` fact ID. The Card generator owns
> this region and overwrites it on every re-render.

<!-- BEGIN GENERATED: type-inventory -->
| Type | Kind | Fact ID |
|---|---|---|
| `<com.huawei.ascend...ClassName>` | class | `code-symbol/com-huawei-ascend-...-classname` |
| `<com.huawei.ascend...InterfaceName>` | interface | `code-symbol/com-huawei-ascend-...-interfacename` |
<!-- END GENERATED: type-inventory -->

## 4. Internal Collaboration

> GENERATED — do not hand-edit between the markers. Rendered from `code-symbols.json`:
> the structural relationships (implements / extends / references) among the in-boundary
> types listed in section 3. This is the *structural* collaboration only — runtime call
> sequences belong in the frame's L2 sink, not here.

<!-- BEGIN GENERATED: internal-collaboration -->
| From | Relationship | To |
|---|---|---|
| `<TypeA>` | implements | `<InterfaceB>` |
| `<TypeC>` | references | `<TypeD>` |
<!-- END GENERATED: internal-collaboration -->

## 5. Contracts

> AUTHORED prose. The communication contracts this frame exposes or consumes. Cite each
> contract operation by its `contract-op/<id>` fact ID and each SPI by its package identity.
> Wire-field and over-the-wire mechanics are L2 — link down, do not inline.

**Exposed SPI / public surface (boundary identity):**

- `<com.huawei.ascend...spi>` — the public package that *is* the boundary (cite the key
  interface(s) by `code-symbol/<kebab-fqn>`).

**Contract operations (OpenAPI / AsyncAPI):**

| Operation | Fact ID | Contract source |
|---|---|---|
| `<METHOD /path>` or `<asyncapi op>` | `contract-op/<kebab-op-id>` | `docs/contracts/<name>.v1.yaml` |

**Consumed contracts** (operations this frame calls on another frame):

- `contract-op/<id>` on `<other frame>` — why.

## 6. FunctionPoint Mapping

> AUTHORED prose over the frame's DSL `anchors` edges. List ONLY FunctionPoints the DSL
> anchors to this frame (`ef<CamelName> -> fp<Name>` in `engineering-frames.dsl`). For each,
> give the entry + participating class/method anchors, contract refs, and test refs — every
> anchor CITED by generated fact ID. The gate fails a card that lists a FunctionPoint with no
> backing `anchors` edge. A `design_only` frame may anchor zero FunctionPoints — say so.

### `FP-<NAME>` — <one-line behaviour>

| Anchor | Fact ID |
|---|---|
| Entry class | `code-symbol/<kebab-fqn>` |
| Entry method | `code-symbol/<kebab-fqn>` :: `<JVM descriptor>` |
| Contract op | `contract-op/<kebab-op-id>` |
| Test | `test/<kebab-fqn>` |

<!-- Repeat one ### block per anchored FunctionPoint. Example (illustrative; replace):
### `FP-CREATE-RUN` — admits a new Run (the on-the-wire route/verb is the `contract-op/createrun` material, not this heading)
| Anchor | Fact ID |
|---|---|
| Entry class | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller` |
| Entry method | `code-symbol/com-huawei-ascend-service-platform-web-runs-runcontroller` :: `create(Lcom/huawei/ascend/service/platform/web/runs/CreateRunRequest;Ljakarta/servlet/http/HttpServletRequest;)Lorg/springframework/http/ResponseEntity;` |
| Contract op | `contract-op/createrun` |
| Test | `test/com-huawei-ascend-service-platform-web-runs-runhttpcontractit` |
-->

## 7. Verification

> AUTHORED prose. The constraints, ArchUnit enforcers, and gate rules that hold this
> boundary. Cite each enforcer / rule as a structural identifier (not version metadata).

**Constraints / enforcers holding the boundary:**

- `<EnforcerName>` (`<ArchUnit test FQN or enforcer id>`) — asserts `<the forbidden-dependency / boundary invariant>`.
- Rule `<G-NN>` — `<what the gate rule checks for this frame>`.

**Tests anchoring the behaviour** (fact-cited):

- `test/<kebab-fqn>` — `<what it proves>`.

<!--
  STATUS-CONDITIONAL — keep the block that matches saa.status; delete the others.

  If status: design_only — REQUIRED missing-proof statement:
  > **Missing proof before promotion to `shipped`:** <what is absent — e.g. no
  > `primaryPackage` Java home exists yet; anchors zero FunctionPoints; no production
  > implementation, only the SPI declaration>. Until these land, this frame stays
  > `design_only` and carries no fact-cited Type Inventory.

  If status: mock_functional — REQUIRED not-yet-real statement:
  > **Why this is not yet a real production implementation:** <e.g. the in-process
  > realization is a reference/mock transport; the networked production adapter is
  > unimplemented>. The boundary and contract are real; the implementation behind them is a mock.

  If status: shipped — no conditional block; section 3 MUST be a non-empty fact-cited
  Type Inventory and `primary_package` MUST be declared.
-->

## Cross-references

- Frames directory + Card-over-DSL rules: [`README.md`](README.md).
- This frame's DSL element (authority): [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl) (or [`../../../features/features.dsl`](../../../features/features.dsl) for agent-service frames).
- Generated facts cited above (authority over this prose): [`../../../facts/generated/`](../../../facts/generated/).
- Collective structural map: [`../engineering-frames.md`](../engineering-frames.md).
- This frame's L2 detail sink (runtime mechanics, if any): `architecture/docs/L2/<slug>/`.
