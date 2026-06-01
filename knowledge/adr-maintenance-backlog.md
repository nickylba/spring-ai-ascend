# ADR record — knowledge-maintenance backlog (advisory)

The ADR corpus (`docs/adr/`) is **knowledge in place**: the keystone excludes it from governance
enforcement, and it is also the generated fact layer's decision source. Its *content* is correct; the
items below are label/format/index hygiene. They are **advisory** — fixed incrementally as knowledge
maintenance, never blocking. None of them affect the green gate. (PRE2 of the knowledge/governance
rebalancing tracks them here rather than forcing high-ripple renames or fact-layer-severing edits.)

## 1. Filename slug ↔ content drift (~10 files)
A handful of ADR filenames name a *different* decision than the file's own H1/body (the content is the
truth). Examples: `0013-vault-secrets-management.md` actually decides UUIDv7 surrogate IDs;
`0011-flyway-schema-migration.md` actually decides Spring Cloud Gateway ingress; `0008` is OPA-sidecar,
not resilience4j; `0012` is Maven-vs-Gradle, not valkey. **Fix when touched:** rename to match content
*with* a fact-layer regen (`adrs.json`), or correct the index maps (below) — do not trust the slug.

## 2. ADR-0155 dual format
`0155-agent-service-l1-v1-2-internal-module-design` exists as both `.md` and `.yaml`. The gate is green
(the fact extractor dedups by id). **Fix when touched:** keep the `.yaml`, remove the `.md`, and
regenerate `architecture/facts/generated/adrs.json` (Maven `ExtractFactsCli`) so Rule G-15 byte-identity
holds. Coupled to the fact layer — do it as a deliberate step, not a drive-by.

## 3. Index registries out of sync (3)
`docs/adr/ADR-CLASSIFICATION.md`, `docs/adr/README.md`, and `docs/adr/adr-level-module-map.yaml` carry
three different slug sets, none matching the files, and some stop at ~ADR-0139. These are hand-authored
maps (not the fact layer), so they are **safe to correct in place** against each file's real H1.

## 4. Conflicts / redundancy clusters (knowledge consolidation)
Unresolved decision conflicts and mergeable clusters identified in
`docs/reviews/2026-06-01-rebalancing-inventory/02-adr-conflicts.md`: e.g. the suspend taxonomy
(0019 ↔ 0112 ↔ 0146) and the engine-contract boundary decided across 0072/0112/0140/0158. **Fix when
touched:** resolve the supersession metadata; optionally consolidate each cluster into one knowledge doc
under `knowledge/`. Pure knowledge work — no governance impact.

---
Use `knowledge/_tools/check_integrity.py` and `knowledge/_tools/search.sh` to surface and verify these as
they are worked. Knowledge stays honest by advisory tooling, not by a blocking gate.
