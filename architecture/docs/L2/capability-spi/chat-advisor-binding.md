---
level: L2
view: process
status: design_only
authority: "ADR-0132 (ChatAdvisor SPI boundary) + ADR-0121 (ModelGateway invocation surface) + ADR-0129 (streaming-aware ModelGateway) + ADR-0073 (Engine Hooks + HookPoint) + ADR-0157 (EngineeringFrame Ontology)"
relates_to:
  - architecture/docs/L1/frames/EF-CAPABILITY-SPI.md
  - docs/contracts/chat-advisor.v1.yaml
  - docs/contracts/engine-hooks.v1.yaml
extends:
  - ADR-0132
---

# Chat Advisor Binding — L2 Process View (advisor ↔ model ↔ hook placement)

This view is the **L2 detail sink** for the chat-advisor hook-binding runtime
sequence — the `advisor-model-hook-order/v1` step order that places the
around-call advisor chain relative to the `BEFORE_LLM` / `AFTER_LLM` hook brackets
around the model gateway. The L0/L1 authority surfaces deliberately do **not**
carry this sequence: ADR-0132 (now an L1 public-SPI-surface decision) decides the
advisor *boundary identity* and the decoration principle, and the
[`EF-CAPABILITY-SPI`](../../L1/frames/EF-CAPABILITY-SPI.md) frame card delegates
the runtime mechanics to this sink ("Carry the on-the-wire model/memory/vector
mechanics … or any persistence/index detail … Those are L2 runtime detail
delegated to the per-family contract surfaces and the frame's L2 sink, not
restated in this card", `EF-CAPABILITY-SPI` §2).

This is a **readable interpretation layer** (ADR-0161). It invents no ID, no
method, and no relationship: the binding sequence below is sourced from the
contract surface [`docs/contracts/chat-advisor.v1.yaml`](../../../../docs/contracts/chat-advisor.v1.yaml)
(`binding.sequence_id: advisor-model-hook-order/v1`), and the hook-point taxonomy
from [`docs/contracts/engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml).
Where the two surfaces and this prose disagree, the contract surfaces win;
authority cascade is unchanged (generated facts > DSL > Card/prose).

## Status (honest assertion)

The chat-advisor capability is **`design_only`**. The contract surface is complete
(`status: design_only`, `runtime_enforced: false`) and the advisor SPI + its
carriers exist as fact-cited types in the cluster, but **no production advisor
chain executor exists today** and no advisor FunctionPoint is anchored in
`architecture/features/function-points.dsl`. The sequence documented here is the
**target placement** the W2 LLM gateway wave (Telemetry Vertical co-arrival with
the Hook SPI activation, ADR-0061 §7) will realise; it carries no generated
runtime fact to cite yet. This sink is authored ahead of the implementation — the
same posture the Telemetry Vertical L2 home takes for its W2 emission hooks — so
the placement detail has a home the moment ADR-0132 was reduced to a boundary
decision.

## Structural anchor

| Axis | Anchor |
|---|---|
| Structure | `agent-middleware` → `EF-CAPABILITY-SPI`, prompt/advisor capability family (frame `saa.id`, authored in [`../../../features/engineering-frames.dsl`](../../../features/engineering-frames.dsl), owner `agent-middleware`, source ADR-0157) |
| Contract surface (binding authority) | [`docs/contracts/chat-advisor.v1.yaml`](../../../../docs/contracts/chat-advisor.v1.yaml) (`binding.sequence_id: advisor-model-hook-order/v1`) |
| Hook taxonomy | [`docs/contracts/engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml) (the canonical `HookPoint` list the brackets draw from) |
| L1 boundary decision | ADR-0132 (the advisor boundary identity + decoration principle) |
| Model-call boundary wrapped | ADR-0121 (synchronous gateway) + ADR-0129 (streaming gateway) |
| Hook-bracket decision | ADR-0073 (engine hooks; the dispatcher stays platform-internal) |

## Binding sequence — `advisor-model-hook-order/v1`

The advisor chain wraps a single model-gateway invocation. The terminal chain step
is the gateway call; the hook brackets fire on the canonical model payload *outside*
the advisor chain. The ordered placement is:

```text
AgentDefinition.advisorBindings  ──┐  resolve ordered advisors by stable name
                                   │  (smaller effective order runs first inbound,
                                   ▼   last outbound)
translate ModelInvocation ──► AdvisedRequest.modelRequest
                                   │
fire BEFORE_LLM  (canonical ModelInvocation payload)
                                   │
        ┌──────────── advisor chain (inbound leg) ────────────┐
        │  ordered advisors transform the request;            │
        │  an advisor MAY short-circuit with a synthetic      │
        │  response (policy denial, cache hit, redaction)     │
        ▼                                                     │
   terminal step: invoke the model gateway                    │
   (synchronous gateway, or the streaming gateway per         │
    ADR-0129 emitting the chunk stream)                       │
        │                                                     │
        └──────────── advisor chain (outbound leg) ───────────┘
                                   │  outbound advisors produce
                                   ▼   AdvisedResponse.modelResponse
translate AdvisedResponse ──► final ModelResponse
                                   │
fire AFTER_LLM  (final translated ModelResponse)
```

Step order (the seven binding steps, transcribed from the contract header — the
contract surface is the source, this is its readable form):

1. `AgentDefinition.advisorBindings` resolve the ordered advisor set by stable
   advisor name.
2. The canonical `ModelInvocation` translates into the advised request envelope.
3. `BEFORE_LLM` fires with the canonical `ModelInvocation` payload.
4. The ordered advisor chain wraps the provider call (inbound transforms first).
5. The terminal chain step invokes the model gateway (synchronous, or the
   streaming gateway for streaming advice).
6. Outbound advisors produce the advised response envelope.
7. `AFTER_LLM` fires with the final translated `ModelResponse`.

### Placement rules (the load-bearing properties)

- **Hooks bracket the chain, on the canonical payload.** `BEFORE_LLM` /
  `AFTER_LLM` fire on the platform `ModelInvocation` / `ModelResponse`, not on the
  advisor envelope — so a change to advisor ordering never changes what a hook
  observes, and a change to hook granularity never becomes a contract-breaking
  event on the public advisor surface (the decoration-boundary property ADR-0132
  governs).
- **The dispatcher is platform-internal.** The application audience attaches
  advisors at agent-definition time and never imports the engine hook dispatcher;
  the brackets are fired by the platform around the gateway, behind the SPI.
- **Ordering is symmetric inbound/outbound.** A smaller effective order runs first
  on the inbound leg and last on the outbound leg, so an outer advisor wraps an
  inner one (the canonical decoration nesting). The numeric ordering and the chain
  method shapes are contract detail (`chat-advisor.v1.yaml`), not restated here.
- **Streaming binds the same shape.** For streaming advice the terminal step is the
  streaming gateway (ADR-0129); the chunk stream's completion contract (exactly one
  terminal completion element, last) is owned by the contract surface
  (`advised_stream_chunk.ordering`), not this sink.

## What stays upstream (NOT carried here)

Per the layer-purity verdict, the following remain at L1 / contract and are only
*referenced* here, never duplicated:

- the **advisor boundary identity** and the **decoration principle** — owned by
  ADR-0132 and the `EF-CAPABILITY-SPI` frame card §2/§5 (this sink owns the step
  placement, not the boundary decision);
- the **advisor method signatures and value-record shapes** (the advisor /
  streaming-advisor / chain method signatures and the advised request / response /
  model-request / message / tool-call / usage / finish-reason / model-response
  carriers and sealed stream-chunk variants) — owned by `chat-advisor.v1.yaml` and
  the generated code-symbol facts for the advisor package;
- the **canonical `HookPoint` list, ordering, and failure-propagation** — owned by
  `engine-hooks.v1.yaml`; this sink names the two brackets it places against, it
  does not restate the hook schema.

## Gate behaviour

- Rule 37 (`architecture_artefact_front_matter`): this file declares `level: L2` +
  `view: process`.
- Rule 38 (`architecture_graph_well_formed`): the `relates_to:` / `extends:`
  front-matter links upward to the anchoring frame card and the contract surfaces;
  the regenerated architecture graph is owned by the reconcile/governance wave, not
  by this document.
- Rule 145 / Rule G-27 (`layer_purity`): the L2-detail-sink helper scans only
  L0 / L1 prose, so the sequence correctly sited here is never flagged — and the
  ADR-0132 reduction removes the same sequence from the L1 authority layer.

## Cross-references

- Anchoring frame: [`EF-CAPABILITY-SPI`](../../L1/frames/EF-CAPABILITY-SPI.md).
- Contract surface (binding authority): [`../../../../docs/contracts/chat-advisor.v1.yaml`](../../../../docs/contracts/chat-advisor.v1.yaml).
- Hook taxonomy: [`../../../../docs/contracts/engine-hooks.v1.yaml`](../../../../docs/contracts/engine-hooks.v1.yaml).
- L2 corpus index: [`../README.md`](../README.md).

## Authority

- ADR-0132 — ChatAdvisor SPI (the advisor boundary identity + decoration principle).
- ADR-0121 — ModelGateway SPI (the synchronous model-call boundary the chain wraps).
- ADR-0129 — Streaming-aware ModelGateway (the streaming terminal step).
- ADR-0073 — Engine Hooks + HookPoint (the `BEFORE_LLM` / `AFTER_LLM` brackets; the dispatcher stays platform-internal).
- ADR-0157 — EngineeringFrame Ontology (`EF-CAPABILITY-SPI` structural anchor).
- ADR-0068 — Layered 4+1 + Architecture Graph (the L2 altitude itself).
- Rule 33 — Layered 4+1 Discipline; Rule G-27 — L2 detail sink (`CLAUDE.md`).
