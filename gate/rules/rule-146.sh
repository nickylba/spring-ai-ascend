#!/usr/bin/env bash
# Auto-extracted from gate/check_architecture_sync.sh by gate/lib/extract_rules.sh
# Rule 146 — frame_card_consistency. DO NOT HAND-EDIT — re-run extract_rules.sh to refresh.
# Authority: PR-E5.

# Rule 146 — frame_card_consistency (enforcer E196, kernel Rule G-29)
#
# Authority: ADR-0161 (EngineeringFrame as Package-Cluster Anchor + Card-over-DSL).
# One ADVISORY helper that pins the Frame Card to the lowest interpretation tier
# of the ADR-0154 cascade — the card invents NO id and NO relationship and never
# outranks a generated fact:
#   * gate/lib/check_frame_card_consistency.py (E196, slug frame_card_consistency)
#     — for every authored Frame Card (architecture/docs/L1/frames/<frame-id>.md)
#     it checks (1) frontmatter frame_id / owner_module / status / primary_package
#     copy the DSL EngineeringFrame element exactly (the DSL wins); (2) every cited
#     code-symbol/<kebab-fqn>, test/<kebab-fqn>, contract-op/<kebab-op-id> — and any
#     code-symbol/<fqn>#<jvm-method-descriptor> — resolves in
#     architecture/facts/generated/*.json (the method ref against the class fact's
#     public_methods[]); (3) every FunctionPoint (FP-…) the card names is anchored
#     by THIS frame in architecture/features/engineering-frames.dsl, or is declared
#     a participating-frame reference (no invented anchors).
# Runs ADVISORY here (`--mode advisory`): it reports findings to the gate log but
# never blocks, the Phase-1 landing rung in the ADR-0161 §6 ratchet: advisory (this
# rung) -> changed-files-blocking -> full-blocking (the terminal rung after a
# 14-day soak on a clean corpus + a green ProfileYamlParityTest). The frames/
# directory is greenfield until the pilot card lands; with no authored cards the
# helper is vacuously clean. The instant one card exists, the DSL elements and the
# generated facts MUST be readable or the helper fails closed (exit 2) — a missing
# authority is never an advisory condition. A missing helper fails closed; a
# missing python interpreter is a vacuous pass (Rule G-7 lists WSL as canonical).
#
# scope_surfaces: architecture/docs/L1/frames/*.md, architecture/features/engineering-frames.dsl, architecture/features/features.dsl, architecture/features/function-points.dsl, architecture/facts/generated/code-symbols.json, architecture/facts/generated/tests.json, architecture/facts/generated/contract-surfaces.json, gate/lib/check_frame_card_consistency.py
# ---------------------------------------------------------------------------
_r146_fail=0
_r146_helper="gate/lib/check_frame_card_consistency.py"
if [[ ! -f "$_r146_helper" ]]; then
  fail_rule "frame_card_consistency" "$_r146_helper missing -- Rule G-29 / E196"
  _r146_fail=1
elif [[ -z "$GATE_PYTHON_BIN" ]]; then
  : # vacuous pass on hosts without python (Rule G-7 lists WSL as canonical env)
else
  _r146_out=$("$GATE_PYTHON_BIN" "$_r146_helper" --mode advisory 2>&1)
  _r146_rc=$?
  # Advisory: a non-zero rc here is NOT a finding-block — it is a CONFIG ERROR
  # (a card exists but the DSL/facts authority vanished, exit 2). The helper
  # fails closed in every mode for that case, so surface it as a hard fail; a
  # plain finding count (exit 0 in advisory) is reported, never blocked.
  if [[ $_r146_rc -ne 0 ]]; then
    _r146_err=$(printf '%s' "$_r146_out" | grep -E 'config error' | head -1)
    fail_rule "frame_card_consistency" "${_r146_err:-frame-card helper exited $_r146_rc} -- Rule G-29 / E196"
    _r146_fail=1
  else
    _r146_sum=$(printf '%s' "$_r146_out" | grep -E 'finding\(s\)' | tail -1)
    [[ -n "$_r146_sum" ]] && echo "ADVISORY (Rule G-29 / E196): $_r146_sum"
  fi
fi
[[ $_r146_fail -eq 0 ]] && pass_rule "frame_card_consistency"

