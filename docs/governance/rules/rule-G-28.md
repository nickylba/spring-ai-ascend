---
rule_id: G-28
title: "ADR Normalization"
level: L1
view: scenarios
principle_ref: P-C
authority_refs: [ADR-0160]
enforcer_refs: [E192, E193]
status: active
scope_phase: design
kernel_cap: 8
governance_infra: true
scope_surfaces:
  - docs/governance/adr-taxonomy.yaml
  - docs/governance/adr-governance-policy.yaml
  - docs/governance/adr-remediation-ledger.yaml
  - docs/adr/normalized
  - architecture/facts/generated/adrs.json
  - gate/lib/check_adr_taxonomy.py
  - gate/lib/check_historical_adr_governance.py
kernel: |
  Architecture review reads the NORMALIZED ADR view, not raw historical prose. Every raw ADR enumerated in `architecture/facts/generated/adrs.json` MUST have a ledger entry in `docs/governance/adr-remediation-ledger.yaml`, and every `accepted` ADR MUST have a normalized view at `docs/adr/normalized/ADR-NNNN.yaml` — this ledger-totality + normalized-view-coverage assertion (E193) is BLOCKING: the normalization wave back-filled the corpus to total coverage. Each normalized view MUST satisfy `docs/governance/adr-governance-policy.yaml` (required fields + the closed five-value `current_state` set + per-state field invariants) AND `docs/governance/adr-taxonomy.yaml` (the per-`decision_level` `decision_type` altitude — a forbidden lower-altitude `decision_type` is layer-purity leakage and is rejected); this per-view altitude validation (E192) stays advisory while its corpus is brought into altitude. Ratchet: E193 is at the full-blocking rung; E192 remains advisory, then changed-files-blocking, then full-blocking once every view is altitude-clean.
---

# Rule G-28 — ADR Normalization

## What

Pins the normalized-ADR review surface as the single authority an architecture
review may cite. Two assertions, both keyed off the apex authority for the raw
ADR set (`architecture/facts/generated/adrs.json`, a generated fact projection
under the ADR-0154 / Rule G-15 cascade `generated facts > DSL > Card/prose`):

1. **Ledger totality** — every raw ADR in `adrs.json` has a matching entry (by
   `adr` id) in `docs/governance/adr-remediation-ledger.yaml`.
2. **Normalized-view coverage** — every `accepted` ADR has a normalized view at
   `docs/adr/normalized/ADR-NNNN.yaml`.

Each normalized view is validated against the two hand-authored governance
policy surfaces that together fix its shape and altitude:

- `docs/governance/adr-governance-policy.yaml` — the required-field list, the
  closed five-value `current_state` set (`active_guidance`, `partial_guidance`,
  `superseded`, `historical_evidence`, `remediation_record`), and the per-state
  field-presence invariants.
- `docs/governance/adr-taxonomy.yaml` — the closed `decision_level` key set, the
  per-level `decision_type` allow / forbid lists (the layer-purity altitude
  control), and the closed `affected_level_vocabulary`.

## Why

Closes the ADR-0160 review-blocking condition: a future architecture review
cannot reliably distinguish current decision authority from historical evidence
when it reads raw ADR prose, because a single raw ADR may mix decision levels
and time states. The normalized view is the de-noised, altitude-pure projection
the review cites; the ledger guarantees no raw ADR is silently un-governed.

## How it works

The single gate Rule 144 invokes two helpers at different ratchet rungs:

- `gate/lib/check_adr_taxonomy.py` (E192) — runs **advisory** (reports findings
  but never blocks while the per-view altitude corpus is brought clean).
  Validates each normalized view against the two policy surfaces. A
  `decision_type` that is forbidden at its `decision_level` is reported as
  lower-altitude leakage; a `superseded` view with no `superseded_by`, a
  `partial_guidance` view with no `non_authoritative_legacy_content`, or a
  `historical_evidence` view that still carries `active_guidance` each violate a
  per-state invariant.
- `gate/lib/check_historical_adr_governance.py` (E193) — runs **blocking**
  (`--mode blocking`): ledger totality + normalized-view coverage. The
  normalization wave back-filled the ledger and `docs/adr/normalized/` to total
  coverage, so a non-zero helper rc — a raw ADR with no ledger entry, an
  `accepted` ADR with no normalized view, OR a vanished apex `adrs.json` (fatal
  in every mode) — now fails the gate.

Neither helper invents an ADR id or a relationship — they cross-reference the
generated raw-ADR set, the ledger, and the on-disk normalized views only.

## Ratchet

advisory → changed-files-blocking (a PR may not add or worsen a finding on a
changed view) → full-blocking (the terminal posture once the corpus is complete).
The helper modes (`--mode advisory` / `changed-files-blocking` / `full-blocking`
for the taxonomy helper; `advisory` / `changed-files` / `blocking` for the
historical helper) implement the rungs. The two helpers now sit at different
rungs: **E193 (historical, ledger totality + view coverage) is at the
full-blocking rung** — its corpus is complete; **E192 (taxonomy, per-view
altitude) remains advisory** until every view is altitude-clean.

## Test fixtures

  - VALID  : a complete, in-altitude `active_guidance` view passes full-blocking
             with zero findings.
  - INVALID: an L2-altitude `decision_type` declared at `decision_level: L0`
             fails closed (layer-purity leakage).
  - INVALID: a `superseded` view missing `superseded_by` fails (state invariant).
  - INVALID: a `partial_guidance` view with no legacy-content split fails.
  - INVALID: a `historical_evidence` view that still carries `active_guidance`
             fails.
  - VALID  : advisory mode reports the leak but never blocks (exit 0).
  - VALID  : an accepted ADR with a ledger entry + normalized view passes
             blocking with full coverage.
  - INVALID: a raw ADR with no ledger entry fails closed (blocking).
  - INVALID: an accepted ADR with no normalized view fails closed (blocking).
  - VALID  : historical advisory mode reports the gaps but never blocks.
  - INVALID: a missing `adrs.json` fails closed (exit 1) even in advisory mode.

## Cross-references

  - Rule G-15 — Fact-Layer Integrity (adrs.json is the apex raw-ADR fact)
  - Rule G-22 / G-23 — EngineeringFrame frame-map / anchor governance (sibling
    accepted-ADR coherence rules)
