#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 150 — adr_id_uniqueness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 150 — adr_id_uniqueness (enforcer E200, kernel Rule G-33)
#
# Authority: ADR-0160 (ADR Governance Model — one remediation-ledger entry per
# raw ADR, one normalized view per accepted ADR, both keyed by the ADR number).
# One BLOCKING helper that asserts every ADR number is claimed by exactly one raw
# ADR source file. An ADR number is the single authoritative IDENTIFIER for one
# architecture decision; a duplicate is a structural lie the ID-keyed projections
# silently collapse (AdrFactExtractor + AdrGraphFragmentEmitter key ADRs by number
# and the emitter's TreeMap<String,...> is last-writer-wins, dropping the other
# N-1 ADRs from adr-graph.dsl / adrs.json / the workspace closure). The check
# invents no ADR id and no relationship and never outranks a generated fact
# (cascade: generated facts > DSL > Card/prose):
#   * gate/lib/check_adr_id_uniqueness.py (E200, slug adr_id_uniqueness) — globs
#     the numbered raw sources (docs/adr/NNNN-*.yaml whose number is the id: field
#     + docs/adr/NNNN-*.md and docs/adr/locked/NNNN-*.md whose number is the
#     leading # NNNN. / # ADR-NNNN heading), groups files by number, and reports a
#     number claimed by two or more files (DUPLICATE-ID) or a scanned ADR file with
#     no extractable number (UNPARSEABLE-ID). A Markdown prose companion that
#     delegates to its sibling .yaml (the engineering-prose-companion shape) is
#     excluded from identity competition. Cross-checks
#     architecture/facts/generated/adrs.json as the apex factual authority — a
#     number the fact layer resolves to a different raw path than the file that
#     declared it is the same collision surfaced from the fact side.
# Runs BLOCKING here (`--mode blocking`): a duplicate or unparseable ADR number is
# a hard fail. Unlike the lane-purity / readiness / reading-path ratchets there is
# NO advisory soak, NO grandfather list, and NO changed-files scoping — the
# identifier space is global, so a collision is a collision regardless of which
# file a PR touched. NON-VACUITY GUARD: the helper fails closed (exit 2) when its
# glob matches ZERO ADR sources while docs/adr/ exists — a format/path drift that
# silently empties the scan set is never a pass; the check is materially vacuous
# only when docs/adr/ itself is absent (greenfield). A vanished apex adrs.json also
# fails closed (exit 2). A missing helper fails closed; a missing python
# interpreter is a vacuous pass (Rule G-7 lists WSL as canonical).
#
# scope_surfaces: docs/adr, docs/adr/locked, architecture/facts/generated/adrs.json, gate/lib/check_adr_id_uniqueness.py
# ---------------------------------------------------------------------------
_r150_fail=0
_r150_helper="gate/lib/check_adr_id_uniqueness.py"
if [[ ! -f "$_r150_helper" ]]; then
  fail_rule "adr_id_uniqueness" "$_r150_helper missing -- Rule G-33 / E200"
  _r150_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r150_out=$("$GATE_PYTHON_BIN" "$_r150_helper" --mode blocking 2>&1)
  _r150_rc=$?
  if [[ $_r150_rc -ne 0 ]]; then
    # rc 1 = a DUPLICATE-ID / UNPARSEABLE-ID finding; rc 2 = a config error
    # (non-vacuity guard tripped or the apex adrs.json vanished). Both block.
    _r150_msg=$(printf '%s' "$_r150_out" | grep -E 'DUPLICATE-ID|UNPARSEABLE-ID|config error' | head -1)
    fail_rule "adr_id_uniqueness" "${_r150_msg:-adr-id-uniqueness helper exited $_r150_rc} -- Rule G-33 / E200"
    _r150_fail=1
  else
    _r150_sum=$(printf '%s' "$_r150_out" | grep -E 'finding\(s\)' | tail -1)
    [[ -n "$_r150_sum" ]] && echo "OK (Rule G-33 / E200 blocking): $_r150_sum"
  fi
fi
[[ $_r150_fail -eq 0 ]] && pass_rule "adr_id_uniqueness"

# ---------------------------------------------------------------------------
