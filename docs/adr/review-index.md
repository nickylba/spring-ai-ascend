---
inventory_id: ADR-REVIEW-INDEX
governance_infra: true
wave: W5
phase: ledger-and-index-assembly
authority: ADR-0160
schema_refs:
  - docs/governance/adr-governance-policy.yaml
  - docs/governance/adr-taxonomy.yaml
last_updated: 2026-05-29
source_of_truth:
  - docs/adr/normalized/*.yaml
  - architecture/facts/generated/adrs.json
authority_note: |
  READABLE INTERPRETATION layer over the normalized ADR views
  (docs/adr/normalized/ADR-NNNN.yaml) and the apex raw-ADR fact source
  (architecture/facts/generated/adrs.json, the AdrFactExtractor projection of
  docs/adr/*.yaml). This index invents no ADR ids and no relationships: the
  per-state tallies below are a count of the current_state field that each
  normalized view declares, nothing more. Authority cascade is unchanged:
  generated facts > DSL > Card/prose; this index never outranks a generated
  fact. It is assembled for this wave by reading the on-disk normalized views;
  once a generator emits it, it obeys the Rule G-13 byte-identical re-render
  contract (ADR-0119) and is regenerated, not hand-amended. Enforced by Rule
  G-28 (ADR Normalization) via gate/lib/check_adr_taxonomy.py +
  gate/lib/check_historical_adr_governance.py. UTC date for any date field
  (CI runs the gate in UTC; Rule G-9.a).
---

# ADR Review Index

## Purpose

This is the single summary an architecture review reads to learn the **current
authority state** of the ADR corpus without re-reading raw historical prose
(ADR-0160 decision item 4). For each ADR the authoritative state lives in its
normalized view's `current_state` field, drawn from the closed five-value set
fixed by `docs/governance/adr-governance-policy.yaml`:

| `current_state` | Meaning | Review behavior |
|---|---|---|
| `active_guidance` | Current decision authority. | Citeable as governing architecture. |
| `partial_guidance` | Some guidance still active; some content stale, lower-level, or superseded. | Citeable ONLY through the normalized `active_guidance` clauses; the legacy clauses are explicitly non-authoritative. |
| `superseded` | Replaced by a later decision. | NOT citeable as current authority (history only); points to its replacement. |
| `historical_evidence` | Explains why the system evolved; governs nothing now. | Citeable for context only. |
| `remediation_record` | Captures cleanup work or review findings. | Citeable for governance traceability, not architecture authority. |

> **Acceptance rule (ADR-0160 §9.6):** architecture review MUST fail if an ADR
> cited as authority has no normalized record, or if its normalized
> `current_state` is not `active_guidance` or `partial_guidance`.

## Corpus scope and the fact-layer gap

The apex fact source `architecture/facts/generated/adrs.json` enumerates only
the **YAML**-format ADRs under `docs/adr/`. The repository holds ADRs in two
source formats plus a locked partition; the Markdown and locked ADRs are real
governing decisions that the extractor does not yet enumerate (the standing
fact-layer gap recorded in `docs/governance/remediation-inventory/adr-census.md`
and ADR-0160 context). The preferred long-term close is widening
`AdrFactExtractor` to the Markdown / locked sources, or converging the corpus to
a single format.

| Source set | Format | Count | In the fact layer (`adrs.json`)? | In this index's normalized tally? |
|---|---|---|---|---|
| Active YAML ADRs (`docs/adr/*.yaml`) | yaml | 91 | Yes (the census baseline at source commit `98a58c1` enumerated 88; ADR-0159 / ADR-0160 / ADR-0161 were added by the progressive-learning-curve remediation after that commit) | Yes — all but ADR-0079 (see below) |
| Active / deferred Markdown ADRs (`docs/adr/*.md`) | md | 56 | No — absent from the fact layer | Not yet (fact-layer gap; ledger-tracked) |
| Locked foundational ADRs (`docs/adr/locked/*.md`) | md | 11 | No — absent from the fact layer | Not yet (fact-layer gap; ledger-tracked) |
| Archived / superseded ADRs (`docs/logs/adr-amendment-narratives/*`) | md | 4 | No — intentionally archived | No (archived out of the active corpus) |

ADR-0155 exists in **both** `docs/adr/0155-*.yaml` and a `docs/adr/0155-*.md`
stub; its canonical source is the YAML view and it is counted once (as a YAML
ADR with a normalized view). The numbering gaps ADR-0083 / ADR-0084 / ADR-0085
were superseded by ADR-0086 and demoted to the archive partition; their absence
is expected.

## Counts

All tallies below are a direct count of the `current_state` declared by the
on-disk normalized views under `docs/adr/normalized/`. They are an
interpretation layer over those views and over `adrs.json`; they assert no
authority of their own.

| Metric | Count |
|---|---|
| Total raw YAML ADRs (`docs/adr/*.yaml`) | 91 |
| Normalized ADR views (`docs/adr/normalized/*.yaml`) | 90 |
| Unclassified — raw YAML ADRs with no normalized view | 1 |
| — of which `accepted` (review-blocking once G-28 is blocking) | 0 |
| — of which `superseded`/archived (no view required) | 1 (ADR-0079) |
| `active_guidance` | 26 |
| `partial_guidance` | 55 |
| `superseded` | 1 |
| `historical_evidence` | 1 |
| `remediation_record` | 7 |
| **Sum of per-state tallies (must equal normalized views)** | **90** |
| ADRs blocked from architecture-review authority | 9 |
| ADRs allowed as current authority | 81 |
| ADRs allowed only as context | 8 |

Derivation of the three review partitions from the per-state tallies:

- **Allowed as current authority** = `active_guidance` (26) + `partial_guidance`
  (55) = **81**. A `partial_guidance` ADR is citeable only through its
  normalized `active_guidance` clauses; its `non_authoritative_legacy_content`
  clauses are not authority.
- **Blocked from architecture-review authority** = `superseded` (1) +
  `historical_evidence` (1) + `remediation_record` (7) = **9** normalized views,
  plus the 1 raw YAML ADR with no view (ADR-0079, itself `superseded`). None of
  these may be cited as governing architecture.
- **Allowed only as context** = `historical_evidence` (1, context only) +
  `remediation_record` (7, governance traceability only) = **8**. (`superseded`
  ADRs are history that points to a replacement; cite the replacement's view,
  not the superseded ADR.)

### Unclassified detail

| ADR | Raw status | Why no normalized view | Action |
|---|---|---|---|
| ADR-0079 | `superseded` (superseded_by ADR-0088) | Demoted engine-extraction record; a purely-superseded archived ADR may carry an empty `normalized_path` per the ledger state invariant. | None required for review authority. Cite ADR-0088's normalized view (state `active_guidance`) for the dissolution decision. |

## ADRs blocked from architecture-review authority

These ADRs MUST NOT be cited as current governing architecture. Where a
`superseded` ADR has a replacement, cite the replacement's normalized view
instead.

### `superseded` (cite the replacement, not this ADR)

| ADR | Superseded by | Normalized view |
|---|---|---|
| ADR-0143 | ADR-0150 | `docs/adr/normalized/ADR-0143.yaml` |

(The raw-only `superseded` ADR-0079 → ADR-0088 has no normalized view; see
*Unclassified detail* above.)

### `historical_evidence` (context only; governs nothing now)

| ADR | Normalized view |
|---|---|
| ADR-0148 | `docs/adr/normalized/ADR-0148.yaml` |

### `remediation_record` (governance traceability only; not architecture authority)

| ADR | Normalized view |
|---|---|
| ADR-0087 | `docs/adr/normalized/ADR-0087.yaml` |
| ADR-0095 | `docs/adr/normalized/ADR-0095.yaml` |
| ADR-0096 | `docs/adr/normalized/ADR-0096.yaml` |
| ADR-0097 | `docs/adr/normalized/ADR-0097.yaml` |
| ADR-0105 | `docs/adr/normalized/ADR-0105.yaml` |
| ADR-0116 | `docs/adr/normalized/ADR-0116.yaml` |
| ADR-0118 | `docs/adr/normalized/ADR-0118.yaml` |

## ADRs allowed as current authority

These 81 ADRs are citeable as governing architecture. A `partial_guidance` ADR
is citeable **only** through its normalized `active_guidance` clauses.

### `active_guidance` (26) — fully citeable as current authority

ADR-0078, ADR-0088, ADR-0092, ADR-0098, ADR-0099, ADR-0101, ADR-0102, ADR-0119,
ADR-0120, ADR-0122, ADR-0128, ADR-0132, ADR-0138, ADR-0142, ADR-0147, ADR-0151,
ADR-0152, ADR-0153, ADR-0154, ADR-0155, ADR-0156, ADR-0157, ADR-0158, ADR-0159,
ADR-0160, ADR-0161.

### `partial_guidance` (55) — citeable only through the view's `active_guidance` clauses

ADR-0068, ADR-0069, ADR-0070, ADR-0071, ADR-0072, ADR-0073, ADR-0074, ADR-0075,
ADR-0076, ADR-0077, ADR-0080, ADR-0081, ADR-0082, ADR-0086, ADR-0089, ADR-0090,
ADR-0091, ADR-0093, ADR-0094, ADR-0100, ADR-0103, ADR-0104, ADR-0106, ADR-0107,
ADR-0108, ADR-0109, ADR-0110, ADR-0111, ADR-0112, ADR-0113, ADR-0114, ADR-0115,
ADR-0117, ADR-0121, ADR-0123, ADR-0124, ADR-0125, ADR-0126, ADR-0127, ADR-0129,
ADR-0130, ADR-0131, ADR-0133, ADR-0134, ADR-0135, ADR-0136, ADR-0137, ADR-0139,
ADR-0140, ADR-0141, ADR-0144, ADR-0145, ADR-0146, ADR-0149, ADR-0150.

## How to use this index in a review

1. Find the ADR you want to cite in one of the tables above.
2. If it is `active_guidance`, cite its normalized view directly.
3. If it is `partial_guidance`, open its normalized view
   (`docs/adr/normalized/ADR-NNNN.yaml`) and cite only the bullet items under
   `active_guidance`; its `non_authoritative_legacy_content` is explicitly not
   authority.
4. If it is `superseded`, do not cite it as current authority — cite the
   `superseded_by` replacement's normalized view.
5. If it is `historical_evidence` or `remediation_record`, cite it only for
   context / governance traceability, never as governing architecture.
6. If the ADR appears in *Unclassified detail* (no normalized view) and you need
   it as current authority, that is a review-blocking condition under Rule G-28;
   route it through the normalization wave first.

## Regeneration

This index is a readable-interpretation summary of the normalized views. It is
assembled for this wave by counting the `current_state` field of every file
under `docs/adr/normalized/`. When a generator emits it, the generator obeys the
Rule G-13 single-source byte-identical re-render contract (ADR-0119): re-read the
normalized views (and `architecture/facts/generated/adrs.json` for corpus
scope), recompute the tallies, and re-emit the tables — never hand-amend a count
out of step with the views. The companion enumeration is the per-ADR ledger
`docs/governance/adr-remediation-ledger.yaml`; the per-ADR detail is the
normalized view itself.
