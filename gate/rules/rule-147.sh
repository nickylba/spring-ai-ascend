#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 147 — feature_readiness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 147 — feature_readiness (enforcer E197, kernel Rule G-30)
#
# Authority: ADR-0159 (Progressive Learning Curve and Authority Lanes). One
# CHANGED-FILES-BLOCKING helper that evaluates every FunctionPoint against the
# readiness bar its saa.status resolves to under the policy data file
# docs/governance/feature-readiness-policy.yaml — the FunctionPoint is the
# behavioral JOIN POINT of the curve, and a unit of work is shippable only when
# every required axis is complete:
#   * gate/lib/check_feature_readiness.py (E197, slug feature_readiness) — for
#     every SAA FunctionPoint element in architecture/features/function-points.dsl
#     it resolves a readiness bar from saa.status (design_only->proposed /
#     mock_functional->active / shipped->the full bar) and reports the obligations
#     the FunctionPoint has NOT discharged: STRUCTURE (anchored by exactly one
#     EngineeringFrame + an owning-module implements edge), VALUE (a Feature
#     requires it), EVIDENCE (a contract-op ref or no-contract rationale, a
#     generated-fact ref resolving in architecture/facts/generated/*.json, a test
#     ref or approved exception, a gate ref), DECISION (saa.sourceAdr resolves to
#     a normalized ADR view in active_guidance/partial_guidance), plus the
#     OWNERSHIP invariant (only an EngineeringFrame may 'anchors' a FunctionPoint).
# Runs CHANGED-FILES-BLOCKING here (`--mode changed-files-blocking --base`): a PR
# may not ADD or WORSEN a finding on a FunctionPoint whose authoring surfaces it
# touches; pre-existing findings on untouched FunctionPoints stay advisory, and a
# known historical finding frozen in the dated baseline allow-list
# docs/governance/feature-readiness-baseline.yaml (sibling of
# layer-purity-temporary-violations.yaml; each row keys a (fp_id, axis, code)
# finding + a sunset_date) is TOLERATED even when its FunctionPoint is in scope.
# The OWNERSHIP invariant blocks at every blocking rung regardless of scope AND of
# the baseline (a value-axis node anchoring a FunctionPoint is never tolerable).
# This is the ADR-0159 §13.3 Phase-2 rung: advisory -> changed-files-blocking
# (this rung) -> full-blocking (the terminal rung once the corpus reaches the
# acceptance bar and the baseline allow-list is fully retired; full-blocking
# ignores the baseline). The helper self-derives the changed set from git against
# --base (same git-deriving pattern as Rule 145 / E194 + Rule 146 / E196) and
# falls back to full-corpus evaluation when git cannot resolve the base; a change
# to any shared authoring surface (the policy file / function-points.dsl /
# engineering-frames.dsl / features.dsl) re-scopes EVERY FunctionPoint.
# architecture/features/function-points.dsl is greenfield-vacuous until the first
# FunctionPoint exists; the instant one does, the policy file + DSL surfaces +
# generated facts MUST be readable or the helper fails closed (exit 2) in EVERY
# mode — a missing authority is never an advisory condition, so it is surfaced as
# a hard fail here. The baseline allow-list is OPTIONAL but, when present, MUST
# parse (a malformed allow-list fails closed; it never silently suppresses). A
# missing helper fails closed; a missing python interpreter is a vacuous pass
# (Rule G-7 lists WSL as canonical).
#
# scope_surfaces: architecture/features/function-points.dsl, architecture/features/engineering-frames.dsl, architecture/features/features.dsl, docs/governance/feature-readiness-policy.yaml, docs/governance/feature-readiness-baseline.yaml, architecture/facts/generated/code-symbols.json, architecture/facts/generated/tests.json, architecture/facts/generated/contract-surfaces.json, docs/adr/normalized, architecture/docs/L2, gate/lib/check_feature_readiness.py
# ---------------------------------------------------------------------------
_r147_fail=0
_r147_helper="gate/lib/check_feature_readiness.py"
_r147_base="${BASE_REF:-origin/main}"
if ! { command -v git >/dev/null 2>&1 && git rev-parse --verify "$_r147_base" >/dev/null 2>&1; }; then
  _r147_base="HEAD"
fi
if [[ ! -f "$_r147_helper" ]]; then
  fail_rule "feature_readiness" "$_r147_helper missing -- Rule G-30 / E197"
  _r147_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r147_out=$("$GATE_PYTHON_BIN" "$_r147_helper" --mode changed-files-blocking --base "$_r147_base" 2>&1)
  _r147_rc=$?
  # A non-zero rc is EITHER a blocked in-scope finding (exit 1) OR a CONFIG ERROR
  # (a FunctionPoint exists but the policy / DSL / facts / baseline authority
  # vanished or is malformed, exit 2). Both fail the rule; the config-error
  # message is surfaced verbatim when present.
  if [[ $_r147_rc -ne 0 ]]; then
    _r147_err=$(printf '%s' "$_r147_out" | grep -E 'config error' | head -1)
    if [[ -n "$_r147_err" ]]; then
      fail_rule "feature_readiness" "${_r147_err} -- Rule G-30 / E197"
    else
      _r147_sum=$(printf '%s' "$_r147_out" | grep -E 'finding\(s\)' | tail -1)
      _r147_hits=$(printf '%s' "$_r147_out" | grep -E '^feature-readiness [^ ]+ \[' | grep -v 'BASELINED' | head -5)
      fail_rule "feature_readiness" "changed FunctionPoint adds a readiness finding (Rule G-30 / E197): ${_r147_sum:-feature-readiness helper exited $_r147_rc}${_r147_hits:+ || ${_r147_hits}}"
    fi
    _r147_fail=1
  else
    _r147_sum=$(printf '%s' "$_r147_out" | grep -E 'finding\(s\)' | tail -1)
    [[ -n "$_r147_sum" ]] && echo "OK (Rule G-30 / E197 changed-files-blocking): $_r147_sum"
  fi
fi
[[ $_r147_fail -eq 0 ]] && pass_rule "feature_readiness"

