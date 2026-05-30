#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 147 — feature_readiness. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 147 — feature_readiness (enforcer E197, kernel Rule G-30)
#
# Authority: ADR-0159 (Progressive Learning Curve and Authority Lanes). One
# ADVISORY helper that evaluates every FunctionPoint against the readiness bar
# its saa.status resolves to under the policy data file
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
# Runs ADVISORY here (`--mode advisory`): the helper evaluates every FunctionPoint,
# reports findings to the gate log, and ALWAYS exits 0 — the ADR-0159 §13.3
# first-cleanup-wave landing rung. The ratchet is advisory (this rung) ->
# changed-files-blocking (a PR may not ADD/worsen a finding on a FunctionPoint
# whose authoring surfaces it touches; self-derives scope from git against --base,
# same pattern as Rule 145 / E194 + Rule 146 / E196) -> full-blocking (the terminal
# rung once the corpus reaches the acceptance bar). architecture/features/
# function-points.dsl is greenfield-vacuous until the first FunctionPoint exists;
# the instant one does, the policy file + DSL surfaces + generated facts MUST be
# readable or the helper fails closed (exit 2) in EVERY mode including advisory —
# a missing authority is never an advisory condition, so it is surfaced as a hard
# fail here. A missing helper fails closed; a missing python interpreter is a
# vacuous pass (Rule G-7 lists WSL as canonical).
#
# scope_surfaces: architecture/features/function-points.dsl, architecture/features/engineering-frames.dsl, architecture/features/features.dsl, docs/governance/feature-readiness-policy.yaml, architecture/facts/generated/code-symbols.json, architecture/facts/generated/tests.json, architecture/facts/generated/contract-surfaces.json, docs/adr/normalized, architecture/docs/L2, gate/lib/check_feature_readiness.py
# ---------------------------------------------------------------------------
_r147_fail=0
_r147_helper="gate/lib/check_feature_readiness.py"
if [[ ! -f "$_r147_helper" ]]; then
  fail_rule "feature_readiness" "$_r147_helper missing -- Rule G-30 / E197"
  _r147_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r147_out=$("$GATE_PYTHON_BIN" "$_r147_helper" --mode advisory 2>&1)
  _r147_rc=$?
  # Advisory mode ALWAYS exits 0 when the authorities are readable. A non-zero rc
  # is therefore a CONFIG ERROR (exit 2 — a FunctionPoint exists but the policy /
  # DSL / facts authority vanished); fail the rule and surface it verbatim.
  if [[ $_r147_rc -ne 0 ]]; then
    _r147_err=$(printf '%s' "$_r147_out" | grep -E 'config error' | head -1)
    fail_rule "feature_readiness" "${_r147_err:-feature-readiness helper exited $_r147_rc (advisory must exit 0)} -- Rule G-30 / E197"
    _r147_fail=1
  else
    _r147_sum=$(printf '%s' "$_r147_out" | grep -E 'finding\(s\)' | tail -1)
    [[ -n "$_r147_sum" ]] && echo "OK (Rule G-30 / E197 advisory): $_r147_sum"
  fi
fi
[[ $_r147_fail -eq 0 ]] && pass_rule "feature_readiness"

