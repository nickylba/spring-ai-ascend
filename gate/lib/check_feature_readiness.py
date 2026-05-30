#!/usr/bin/env python3
"""Gate check: FunctionPoint readiness across the four axes (Rule G-30).

Authority: ADR-0159 (Progressive Learning Curve and Authority Lanes — the eight-
node chain, the four axes, and the layer-purity lane invariant). The readiness
schema is the data file ``docs/governance/feature-readiness-policy.yaml`` (which
transcribes the systemic-remediation review section 12.3 acceptance rule and
draws its lifecycle vocabulary from ADR-0151). Consumed by Rule G-30 (gate Rule
147, enforcer E197), authored ADVISORY.

The unit of evaluation is the FunctionPoint — the policy's behavioral JOIN POINT
(the single object a requirement demands, anchored by an EngineeringFrame,
described by a contract, evidenced by generated facts, verified by tests,
enforced by gates). For every ``SAA FunctionPoint`` element in
``architecture/features/function-points.dsl`` the check resolves a READINESS BAR
from the element's ``saa.status`` (via the policy ``status_vocabulary`` map:
``design_only`` -> ``proposed``, ``mock_functional`` -> ``active``, ``shipped`` ->
``shipped``; the ADR-0151 lifecycle states map to themselves) and then asserts the
per-bar hard requirements from the policy ``status_rules``:

  proposed   may_lack_facts: nothing is required yet — never a finding.
  active     requires_frame_anchor  -> an EngineeringFrame ``anchors`` edge exists;
             requires_l2_design     -> an L2 design landing exists for the FP
             (``architecture/docs/L2/<fp-slug>/README.md`` or ``<fp-slug>.md``).
  shipped    the full acceptance bar, stated per-axis:
             * structure axis: anchored by exactly one EngineeringFrame, and a
               module ``implements`` edge exists (the owning Maven module);
             * value axis: at least one Feature ``requires`` the FP;
             * evidence axis: a contract ref (``saa.contract_op_refs``) OR an
               explicit no-contract rationale (``saa.no_contract_rationale``); a
               generated-fact reference that resolves in
               ``architecture/facts/generated/*.json``; a test ref
               (``saa.test_refs``) OR an explicit approved exception
               (``saa.test_exception``); and a gate reference (``saa.gate_refs``,
               defaulting to the always-on architecture-sync gate when absent —
               see GATE_REF rationale);
             * decision axis: ``saa.sourceAdr`` resolves to a normalized ADR view
               (``docs/adr/normalized/ADR-NNNN.yaml``) whose ``current_state`` is
               ``active_guidance`` or ``partial_guidance`` (the two citeable
               states per ``adr-governance-policy.yaml``); a citation that resolves
               only to raw prose, or to a superseded / historical_evidence /
               remediation_record view, is not current authority.

It also enforces the policy ACCEPTANCE-RULE ownership invariant: no ProductClaim,
Requirement, or Feature may ``anchors`` a FunctionPoint — only an EngineeringFrame
may (the derived ``Feature --traverses--> EngineeringFrame`` navigation is not
ownership). An ``anchors`` edge whose source is not an EngineeringFrame is a
hard structural finding regardless of mode-scope.

This check invents no ID and no relationship and never outranks a generated fact:
it reads the policy file as the schema, the DSL elements + edges as the identity
authority, the generated facts as the factual authority, and the normalized-ADR
views as the decision authority; it reports which obligations a FunctionPoint has
NOT yet discharged for its declared state. Authority cascade is unchanged:
generated facts > DSL > Card/prose.

Modes (``--mode``):

  advisory                 Evaluate every FunctionPoint, print findings to stderr,
                           and ALWAYS exit 0. This is the landing posture per the
                           remediation review section 13.3 (first cleanup wave).
  changed-files-blocking   Evaluate only the FunctionPoints whose authoring
                           surfaces changed relative to a base ref (``--base``,
                           default ``origin/main``): a change to
                           function-points.dsl / engineering-frames.dsl /
                           features.dsl / the policy file scopes EVERY FunctionPoint
                           (the dependency graph is shared); a change to an L2
                           design dir scopes the FunctionPoint it describes. Exit 1
                           if any IN-SCOPE FunctionPoint has a NON-BASELINED finding;
                           pre-existing findings on untouched FunctionPoints do not
                           block, and a known historical finding frozen in the dated
                           baseline allow-list (below) is TOLERATED even when its
                           FunctionPoint is in scope. The ratchet posture: a PR may
                           not ADD or WORSEN a finding beyond the frozen baseline.
  full-blocking            Evaluate every FunctionPoint; exit 1 on any finding —
                           the baseline allow-list does NOT apply (the terminal
                           posture demands a fully clean corpus, remediation review
                           section 13.3).

Dated baseline allow-list. ``docs/governance/feature-readiness-baseline.yaml``
(the sibling of ``layer-purity-temporary-violations.yaml``) freezes the known,
not-yet-discharged readiness findings that already exist on shipped FunctionPoints
at the moment this gate promoted to changed-files-blocking. Each row keys a
``(fp_id, axis, code)`` finding AND declares a per-row ``sunset_date`` by which the
evidence MUST be wired (or the FunctionPoint demoted). Under changed-files-blocking
a finding whose ``(fp_id, axis, code)`` matches a STILL-OPEN row is reported
BASELINED and never blocks; a finding matching no row, or matching only an EXPIRED
row, blocks if its FunctionPoint is in scope. The list is the sanctioned tolerance
for historical debt under the review section 13.3 ratchet ("changed-files-blocking:
block NEW violations in changed files"); it is honoured ONLY in changed-files mode,
never in full-blocking. The file is OPTIONAL: when it is absent the gate tolerates
nothing (every in-scope finding blocks); when it is present it MUST parse, or the
gate fails closed (a malformed allow-list never silently suppresses a finding).

Ownership-invariant findings (a non-frame ``anchors`` source) are STRUCTURAL and
block in both blocking modes irrespective of changed-file scope AND irrespective of
the baseline allow-list: the dual-track model is invalid the instant a value-axis
node owns a frame, so the violation is never tolerated once a blocking mode is
active (a baseline row may not freeze an ownership lie).

Greenfield / vacuity posture. When ``function-points.dsl`` declares no
FunctionPoint element the check is vacuously clean in every mode (there is
nothing to evaluate). The moment one FunctionPoint exists, the policy file, the
DSL surfaces, and the generated facts MUST be readable, or the check fails closed
(exit 2) — a missing authority is never an advisory condition.

Usage:
    python3 gate/lib/check_feature_readiness.py --mode advisory
    python3 gate/lib/check_feature_readiness.py --mode changed-files-blocking
    python3 gate/lib/check_feature_readiness.py --mode changed-files-blocking --base origin/main
    python3 gate/lib/check_feature_readiness.py --mode full-blocking
    python3 gate/lib/check_feature_readiness.py --mode full-blocking --repo /path/to/repo

Exit codes:
    0 — passed (always, in advisory mode); or no in-scope findings in a blocking
        mode; or no FunctionPoints exist yet (greenfield)
    1 — one or more in-scope findings in a blocking mode (printed to stderr)
    2 — usage / configuration error (bad mode, unreadable policy/DSL/facts while
        FunctionPoints exist, --repo not a directory, etc.)
"""
from __future__ import annotations

import argparse
import datetime
import json
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

# ---------------------------------------------------------------------------
# Canonical surface locations (repo-relative, forward slash).
# ---------------------------------------------------------------------------
POLICY_REL = "docs/governance/feature-readiness-policy.yaml"
BASELINE_REL = "docs/governance/feature-readiness-baseline.yaml"
FUNCTION_POINTS_DSL_REL = "architecture/features/function-points.dsl"
FRAMES_DSL_REL = "architecture/features/engineering-frames.dsl"
FEATURES_DSL_REL = "architecture/features/features.dsl"
FACTS_DIR_REL = "architecture/facts/generated"
L2_DOCS_DIR_REL = "architecture/docs/L2"
NORMALIZED_ADR_DIR_REL = "docs/adr/normalized"

CODE_SYMBOLS_FACT_REL = f"{FACTS_DIR_REL}/code-symbols.json"
TESTS_FACT_REL = f"{FACTS_DIR_REL}/tests.json"
CONTRACT_SURFACES_FACT_REL = f"{FACTS_DIR_REL}/contract-surfaces.json"

# A change to any of these shared authoring surfaces re-scopes EVERY FunctionPoint
# under changed-files-blocking: they are the common dependency graph (the policy
# schema, the FP inventory, the anchor/owner/feature edges). A change to an L2
# design dir scopes only the FunctionPoint it describes (handled separately).
SHARED_SCOPE_SURFACES = (
    POLICY_REL,
    FUNCTION_POINTS_DSL_REL,
    FRAMES_DSL_REL,
    FEATURES_DSL_REL,
)

# The two `current_state` values a normalized ADR view may carry and still be
# CITEABLE as current authority (adr-governance-policy.yaml#current_states:
# active_guidance + partial_guidance). superseded / historical_evidence /
# remediation_record are NOT current authority for a shipped FunctionPoint.
CITEABLE_ADR_STATES = frozenset({"active_guidance", "partial_guidance"})

# Default gate reference for a shipped FunctionPoint that does not name its own
# `saa.gate_refs`. Every FunctionPoint in the DSL is, by construction, validated
# by the architecture-sync gate (the workspace/profile parity + this readiness
# check run there); so the evidence-axis gate obligation is structurally always
# satisfiable and we only FLAG it when a FunctionPoint explicitly declares an
# EMPTY gate_refs, which is an authoring assertion that no gate covers it.
DEFAULT_GATE_REF = "gate.architecture_sync"


# ===========================================================================
# Repo + path helpers.
# ===========================================================================
def repo_root() -> Path:
    """Return the repository root (the directory two levels above this script)."""
    return Path(__file__).resolve().parent.parent.parent


def _rel(path: Path, root: Path) -> str:
    try:
        return str(path.relative_to(root)).replace("\\", "/")
    except ValueError:
        return str(path).replace("\\", "/")


def _slug(fp_id: str) -> str:
    """Kebab-lowercase an FP id for the L2 directory probe (FP-CREATE-RUN -> create-run).

    The ``FP-`` prefix is dropped; the remainder is lowercased. This mirrors the
    review section 12.1 L2 layout ``architecture/docs/L2/<function-point-id>/`` —
    we accept both the prefixed and the unprefixed slug as the directory name so a
    repo that names the dir ``create-run`` or ``fp-create-run`` both resolve.
    """
    body = fp_id[3:] if fp_id.upper().startswith("FP-") else fp_id
    return body.strip("-").lower()


def _utc_today() -> datetime.date:
    """Today's date in UTC.

    The freshness/sunset convention in this repo is UTC: a local +08 date can be
    one day ahead of the CI clock (which runs in UTC), so a sunset compared
    against ``date.today()`` could expire a baseline row a day early on a local
    run and pass on CI. Anchoring on UTC keeps local and CI verdicts identical
    (mirrors gate/lib/check_layer_purity.py._utc_today).
    """
    return datetime.datetime.now(datetime.timezone.utc).date()


# ===========================================================================
# YAML loading (policy file). PyYAML is the authority; its absence while a
# FunctionPoint exists is a hard config error, mirroring the sibling helpers
# (check_adr_taxonomy.py / check_frame_card_consistency.py).
# ===========================================================================
def _load_yaml(path: Path) -> tuple[object | None, str]:
    """Load ``path`` as YAML. Returns ``(data, error)``; ``error`` is '' on success."""
    if not path.is_file():
        return None, f"missing file {path}"
    try:
        import yaml  # type: ignore[import-not-found]
    except ImportError:
        return None, (
            "PyYAML is not installed; cannot parse "
            f"{path} (run: pip install -r gate/requirements.txt)"
        )
    try:
        with path.open("r", encoding="utf-8") as fh:
            return yaml.safe_load(fh), ""
    except yaml.YAMLError as exc:  # type: ignore[attr-defined]
        return None, f"{path} failed YAML parse: {exc}"
    except (OSError, ValueError) as exc:
        return None, f"cannot read {path}: {exc}"


# ===========================================================================
# Policy model — the typed projection of feature-readiness-policy.yaml.
# ===========================================================================
@dataclass
class ReadinessPolicy:
    """Typed view over feature-readiness-policy.yaml.

    Only the readiness-bearing keys this check consumes are projected: the
    status -> readiness-bar map, and the per-bar requirement booleans. The map is
    built from ``status_vocabulary`` (the structural-axis subset records the bar
    each DSL status resolves to) and the ADR-0151 lifecycle names map to
    themselves so a FunctionPoint carrying a full-lifecycle status is honoured.
    """

    # DSL/lifecycle status string -> readiness bar (one of proposed/active/shipped).
    status_to_bar: dict[str, str]
    # readiness bar -> {requirement_key: bool}.
    bar_rules: dict[str, dict[str, bool]]

    def bar_for(self, status: str) -> str:
        """Resolve a declared status to its readiness bar.

        An unknown status conservatively resolves to ``shipped`` so an
        unrecognised value is held to the strictest bar (a typo never silently
        relaxes the requirements); the resolution is surfaced in the finding.
        """
        return self.status_to_bar.get(status, "shipped")

    def requires(self, bar: str, key: str) -> bool:
        return bool(self.bar_rules.get(bar, {}).get(key, False))


def build_policy(doc: object) -> tuple[ReadinessPolicy | None, str]:
    """Build a ReadinessPolicy from the parsed policy document.

    Returns ``(policy, error)``. The required keys are ``status_rules`` and
    ``status_vocabulary``; a malformed document is a config error (the schema is
    the contract this check enforces, so it cannot degrade to a vacuous pass).
    """
    if not isinstance(doc, dict):
        return None, "policy root is not a mapping"

    status_rules = doc.get("status_rules")
    if not isinstance(status_rules, dict):
        return None, "policy 'status_rules' is missing or not a mapping"

    bar_rules: dict[str, dict[str, bool]] = {}
    for bar, rules in status_rules.items():
        if not isinstance(rules, dict):
            return None, f"policy status_rules['{bar}'] is not a mapping"
        bar_rules[str(bar)] = {str(k): bool(v) for k, v in rules.items()}

    # The lifecycle states map to themselves; the structural-axis subset records
    # an explicit `readiness_bar` per DSL status value.
    status_to_bar: dict[str, str] = {bar: bar for bar in bar_rules}
    vocab = doc.get("status_vocabulary")
    if isinstance(vocab, dict):
        lifecycle = vocab.get("feature_lifecycle")
        if isinstance(lifecycle, list):
            for state in lifecycle:
                s = str(state)
                # A lifecycle state with no own bar rules resolves to the nearest
                # listed bar by name when it IS a bar, else stays unmapped (falls
                # to the conservative shipped default in bar_for).
                status_to_bar.setdefault(s, s if s in bar_rules else status_to_bar.get(s, s))
        subset = vocab.get("structural_axis_subset")
        if isinstance(subset, dict):
            for status, spec in subset.items():
                if isinstance(spec, dict) and spec.get("readiness_bar"):
                    status_to_bar[str(status)] = str(spec["readiness_bar"])

    if not bar_rules:
        return None, "policy declares no status_rules bars"
    return ReadinessPolicy(status_to_bar=status_to_bar, bar_rules=bar_rules), ""


# ===========================================================================
# Dated baseline allow-list — the frozen historical-debt tolerance for the
# changed-files-blocking rung. The sibling of layer-purity-temporary-
# violations.yaml: each row freezes one known (fp_id, axis, code) finding AND
# declares a per-row sunset_date by which the evidence MUST be wired. The file
# is OPTIONAL (absent => tolerate nothing); when present it MUST parse.
# ===========================================================================
@dataclass
class BaselineRow:
    """One row from feature-readiness-baseline.yaml (a dated tolerance)."""

    id: str
    fp_id: str
    axis: str
    code: str
    sunset_date: datetime.date | None
    raw_sunset: str

    def is_open(self, today: datetime.date) -> bool:
        """A row tolerates a finding only while its sunset is in the future (inclusive).

        A missing/unparseable sunset is treated as ALREADY-EXPIRED (it cannot
        prove it is still open), so a malformed allow-list row never silently
        suppresses a finding (mirrors check_layer_purity.Violation.is_open).
        """
        return self.sunset_date is not None and self.sunset_date >= today


@dataclass
class Baseline:
    """The parsed baseline allow-list (possibly empty)."""

    rows: list[BaselineRow] = field(default_factory=list)
    # True only when the file existed and parsed (distinguishes "no file" from
    # "empty file"); used to surface an expired-but-unmatched row note honestly.
    present: bool = False


def _parse_sunset(value: object) -> tuple[datetime.date | None, str]:
    """Parse a sunset_date cell. Returns ``(date_or_None, raw_string)``."""
    raw = "" if value is None else str(value)
    if not raw.strip():
        return None, raw
    try:
        return datetime.date.fromisoformat(raw.strip()), raw
    except ValueError:
        return None, raw


def load_baseline(root: Path) -> tuple[Baseline | None, str]:
    """Load the dated baseline allow-list. Returns ``(baseline, error)``.

    The file is OPTIONAL: an absent file yields an empty, not-present Baseline
    (the gate tolerates nothing). A present-but-malformed file is a config error
    (exit 2) — a malformed allow-list must never silently suppress a finding.
    Each well-formed ``findings[]`` row requires ``id``, ``fp_id``, ``axis``,
    ``code``, and ``sunset_date``; an unparseable sunset is retained (the row
    will simply never be open) so the expired-row note can still cite it.
    """
    path = root / BASELINE_REL
    if not path.is_file():
        return Baseline(rows=[], present=False), ""
    doc, err = _load_yaml(path)
    if err:
        return None, err
    if not isinstance(doc, dict):
        return None, f"{BASELINE_REL} root is not a mapping"
    raw_rows = doc.get("findings")
    if raw_rows is None:
        # A present file that declares no findings list is a valid empty baseline
        # (it may carry only the schema header) — tolerate nothing, but mark it
        # present so an author who emptied it does not see a "no file" message.
        return Baseline(rows=[], present=True), ""
    if not isinstance(raw_rows, list):
        return None, f"{BASELINE_REL} 'findings' is not a list"
    rows: list[BaselineRow] = []
    for i, raw in enumerate(raw_rows):
        if not isinstance(raw, dict):
            return None, f"{BASELINE_REL} findings[{i}] is not a mapping"
        rid = str(raw.get("id", "")).strip()
        fp_id = str(raw.get("fp_id", "")).strip()
        axis = str(raw.get("axis", "")).strip()
        code = str(raw.get("code", "")).strip()
        if not (rid and fp_id and axis and code):
            return None, (
                f"{BASELINE_REL} findings[{i}] missing one of id/fp_id/axis/code "
                f"(got id={rid!r} fp_id={fp_id!r} axis={axis!r} code={code!r})"
            )
        sunset, raw_sunset = _parse_sunset(raw.get("sunset_date"))
        rows.append(
            BaselineRow(
                id=rid,
                fp_id=fp_id,
                axis=axis,
                code=code,
                sunset_date=sunset,
                raw_sunset=raw_sunset,
            )
        )
    return Baseline(rows=rows, present=True), ""


def suppressing_baseline_row(
    finding: "Finding", baseline: Baseline, today: datetime.date
) -> BaselineRow | None:
    """Return the open baseline row that tolerates ``finding``, or None.

    A row suppresses a finding when ALL hold: the row's ``fp_id`` equals the
    finding's ``fp_id``, the row's ``axis`` equals the finding's axis, the row's
    ``code`` equals the finding's code, and the row is still open (sunset is today
    or later, UTC). Ownership findings are NEVER suppressible (handled by the
    caller, which never offers them here). Expired rows do not suppress.
    """
    for row in baseline.rows:
        if row.fp_id != finding.fp_id:
            continue
        if row.axis != finding.axis:
            continue
        if row.code != finding.code:
            continue
        if row.is_open(today):
            return row
    return None


# ===========================================================================
# DSL parsing — the identity + anchor + ownership authority.
# ===========================================================================
# An element declaration: <var> = element "<name>" "<metaType>" ... "SAA <Tag>".
ELEMENT_RE = re.compile(
    r'(\w+)\s*=\s*element\s+"[^"]*"\s+"(\w+)"', re.MULTILINE
)
ID_RE = re.compile(r'"saa\.id"\s+"([^"]+)"')
STATUS_RE = re.compile(r'"saa\.status"\s+"([^"]+)"')
OWNER_RE = re.compile(r'"saa\.owner"\s+"([^"]+)"')
SOURCE_ADR_RE = re.compile(r'"saa\.sourceAdr"\s+"([^"]+)"')
REQUIREMENT_RE = re.compile(r'"saa\.requirement"\s+"([^"]+)"')
TEST_REFS_RE = re.compile(r'"saa\.test_refs"\s+"([^"]+)"')
TEST_EXCEPTION_RE = re.compile(r'"saa\.test_exception"\s+"([^"]+)"')
CONTRACT_OP_REFS_RE = re.compile(r'"saa\.contract_op_refs"\s+"([^"]+)"')
NO_CONTRACT_RATIONALE_RE = re.compile(r'"saa\.no_contract_rationale"\s+"([^"]+)"')
CODE_ENTRYPOINT_REFS_RE = re.compile(r'"saa\.code_entrypoint_refs"\s+"([^"]+)"')
FACT_REFS_RE = re.compile(r'"saa\.fact_refs"\s+"([^"]+)"')
GATE_REFS_RE = re.compile(r'"saa\.gate_refs"\s+"([^"]*)"')
# A relationship edge: <srcVar> -> <dstVar> "..." "SAA Relationship" { ... "saa.rel" "<rel>" ... }
EDGE_RE = re.compile(
    r'(\w+)\s*->\s*(\w+)\s+"[^"]*"\s+"SAA Relationship"\s*\{[^}]*?"saa\.rel"\s+"(\w+)"',
    re.DOTALL,
)
# Element meta-types whose elements are EngineeringFrames / value-axis nodes. Used
# for the ownership invariant: only an EngineeringFrame may anchor a FunctionPoint.
FRAME_META = "EngineeringFrame"
VALUE_AXIS_META = frozenset({"Feature", "ProductClaim", "Requirement", "Capability"})


@dataclass
class Element:
    """One profile-tagged DSL element, projected for readiness evaluation."""

    var: str
    meta_type: str  # the second element token, e.g. FunctionPoint / EngineeringFrame
    saa_id: str
    status: str = ""
    owner: str = ""
    source_adr: str = ""
    requirement: str = ""
    test_refs: str = ""
    test_exception: str = ""
    contract_op_refs: str = ""
    no_contract_rationale: str = ""
    code_entrypoint_refs: str = ""
    fact_refs: str = ""
    gate_refs: str | None = None  # None = key absent; "" = key present but empty


def _parse_block(text: str, start: int, window: int = 1400) -> str:
    """Return the properties window that follows an element match start."""
    return text[start : start + window]


def _first(rx: re.Pattern[str], block: str) -> str:
    m = rx.search(block)
    return m.group(1) if m else ""


def parse_elements(text: str) -> dict[str, Element]:
    """Return {var: Element} for every profile-tagged element in ``text``."""
    out: dict[str, Element] = {}
    for m in ELEMENT_RE.finditer(text):
        var, meta = m.group(1), m.group(2)
        block = _parse_block(text, m.start())
        gate_m = GATE_REFS_RE.search(block)
        out[var] = Element(
            var=var,
            meta_type=meta,
            saa_id=_first(ID_RE, block),
            status=_first(STATUS_RE, block),
            owner=_first(OWNER_RE, block),
            source_adr=_first(SOURCE_ADR_RE, block),
            requirement=_first(REQUIREMENT_RE, block),
            test_refs=_first(TEST_REFS_RE, block),
            test_exception=_first(TEST_EXCEPTION_RE, block),
            contract_op_refs=_first(CONTRACT_OP_REFS_RE, block),
            no_contract_rationale=_first(NO_CONTRACT_RATIONALE_RE, block),
            code_entrypoint_refs=_first(CODE_ENTRYPOINT_REFS_RE, block),
            fact_refs=_first(FACT_REFS_RE, block),
            gate_refs=(gate_m.group(1) if gate_m else None),
        )
    return out


def parse_edges(text: str) -> list[tuple[str, str, str]]:
    """Return (srcVar, dstVar, rel) for every SAA Relationship edge in ``text``."""
    return [(m.group(1), m.group(2), m.group(3)) for m in EDGE_RE.finditer(text)]


@dataclass
class DslModel:
    """The resolved DSL authority a FunctionPoint is evaluated against."""

    # var -> Element, merged across all DSL surfaces.
    elements_by_var: dict[str, Element]
    # FunctionPoint var -> Element (the evaluation subjects).
    function_points: dict[str, Element]
    # FunctionPoint var -> set of source EngineeringFrame vars that anchor it.
    frame_anchors_by_fp: dict[str, set[str]] = field(default_factory=dict)
    # FunctionPoint var -> set of module vars that `implements` it.
    module_impls_by_fp: dict[str, set[str]] = field(default_factory=dict)
    # FunctionPoint var -> set of Feature vars that `requires` it.
    feature_requires_by_fp: dict[str, set[str]] = field(default_factory=dict)
    # Ownership-invariant violations: (srcVar, fpVar, srcMetaType) for any anchors
    # edge whose source is NOT an EngineeringFrame.
    nonframe_anchor_edges: list[tuple[str, str, str]] = field(default_factory=list)


def load_dsl_model(root: Path) -> tuple[DslModel | None, list[str]]:
    """Parse FunctionPoint elements + their anchor / implements / requires edges.

    Returns ``(model, errors)``. FunctionPoint elements live in
    function-points.dsl; the ``anchors`` edges live in engineering-frames.dsl; the
    ``implements`` edges live in function-points.dsl; the ``requires`` edges live
    in features.dsl. Elements (module / frame / feature) are merged across every
    surface so an edge's endpoints resolve to a meta-type. A missing
    function-points.dsl is a config error (no subjects to evaluate against).
    """
    errors: list[str] = []
    fp_path = root / FUNCTION_POINTS_DSL_REL
    if not fp_path.is_file():
        errors.append(f"missing {FUNCTION_POINTS_DSL_REL}")
        return None, errors

    fp_text = fp_path.read_text(encoding="utf-8")
    ef_path = root / FRAMES_DSL_REL
    feat_path = root / FEATURES_DSL_REL
    ef_text = ef_path.read_text(encoding="utf-8") if ef_path.is_file() else ""
    feat_text = feat_path.read_text(encoding="utf-8") if feat_path.is_file() else ""

    # Merge element declarations from every surface (later wins on a var collision,
    # which should not happen — vars are unique across the include set).
    elements_by_var: dict[str, Element] = {}
    for txt in (feat_text, ef_text, fp_text):
        elements_by_var.update(parse_elements(txt))

    function_points = {
        var: el for var, el in elements_by_var.items() if el.meta_type == "FunctionPoint"
    }

    frame_anchors_by_fp: dict[str, set[str]] = {}
    module_impls_by_fp: dict[str, set[str]] = {}
    feature_requires_by_fp: dict[str, set[str]] = {}
    nonframe_anchor_edges: list[tuple[str, str, str]] = []

    # anchors edges: engineering-frames.dsl. implements: function-points.dsl.
    # requires: features.dsl. Scan all three so a relocated edge is still seen.
    for txt in (ef_text, fp_text, feat_text):
        for src, dst, rel in parse_edges(txt):
            if dst not in function_points:
                continue
            if rel == "anchors":
                src_el = elements_by_var.get(src)
                src_meta = src_el.meta_type if src_el else ""
                if src_meta == FRAME_META:
                    frame_anchors_by_fp.setdefault(dst, set()).add(src)
                else:
                    # Ownership invariant: a non-frame source anchoring a FP is a
                    # structural lie (value-axis ownership of a frame/FP).
                    nonframe_anchor_edges.append((src, dst, src_meta or "?"))
            elif rel == "implements":
                module_impls_by_fp.setdefault(dst, set()).add(src)
            elif rel == "requires":
                feature_requires_by_fp.setdefault(dst, set()).add(src)

    return (
        DslModel(
            elements_by_var=elements_by_var,
            function_points=function_points,
            frame_anchors_by_fp=frame_anchors_by_fp,
            module_impls_by_fp=module_impls_by_fp,
            feature_requires_by_fp=feature_requires_by_fp,
            nonframe_anchor_edges=nonframe_anchor_edges,
        ),
        [],
    )


# ===========================================================================
# Generated-fact loading — the factual authority for the evidence axis.
# ===========================================================================
@dataclass
class FactIndex:
    """Resolvable fact IDs from the generated fact files."""

    code_symbol_ids: set[str]
    test_ids: set[str]
    contract_op_ids: set[str]

    def has_any_code_symbol(self) -> bool:
        return bool(self.code_symbol_ids)


def _load_json_facts(path: Path) -> tuple[list[dict] | None, str]:
    """Load a generated fact file's ``facts[]``. Returns ``(facts, error)``."""
    if not path.is_file():
        return None, f"missing file {path}"
    try:
        with path.open("r", encoding="utf-8") as fh:
            doc = json.load(fh)
    except (OSError, ValueError) as exc:
        return None, f"cannot parse {path}: {exc}"
    facts = doc.get("facts") if isinstance(doc, dict) else None
    if not isinstance(facts, list):
        return None, f"{path} has no top-level 'facts' list"
    return facts, ""


def load_fact_index(root: Path) -> tuple[FactIndex | None, list[str]]:
    """Build the fact index from code-symbols/tests/contract-surfaces JSON.

    Returns ``(index, errors)``. A missing/unparseable fact file is a config
    error (exit 2): a shipped FunctionPoint is checked AGAINST the facts, so
    without them the evidence axis cannot be judged — failing closed.
    """
    errors: list[str] = []
    code_facts, cerr = _load_json_facts(root / CODE_SYMBOLS_FACT_REL)
    if cerr:
        errors.append(f"code-symbols: {cerr}")
    test_facts, terr = _load_json_facts(root / TESTS_FACT_REL)
    if terr:
        errors.append(f"tests: {terr}")
    contract_facts, qerr = _load_json_facts(root / CONTRACT_SURFACES_FACT_REL)
    if qerr:
        errors.append(f"contract-surfaces: {qerr}")
    if errors:
        return None, errors

    def _ids(facts: list[dict] | None) -> set[str]:
        return {
            f["fact_id"]
            for f in (facts or [])
            if isinstance(f, dict) and isinstance(f.get("fact_id"), str)
        }

    return (
        FactIndex(
            code_symbol_ids=_ids(code_facts),
            test_ids=_ids(test_facts),
            contract_op_ids=_ids(contract_facts),
        ),
        [],
    )


# ===========================================================================
# Normalized-ADR decision authority.
# ===========================================================================
# `current_state: <value>` line in a normalized view. The views are large YAML
# files; a single-line regex avoids importing/parsing every one of them (the
# field is authored one-per-line per adr-governance-policy.yaml).
CURRENT_STATE_LINE_RE = re.compile(r"^\s*current_state\s*:\s*(\S+)", re.MULTILINE)
# Normalize a sourceAdr token to the bare id. Accepts both the DSL form
# "ADR-0020" and the generated-fact form "adr/0020-foo-bar" (ADR-0154): the
# digit run after either an "ADR-" prefix or an "adr/" path segment is the id.
ADR_ID_RE = re.compile(r"(?:ADR-|adr/)(\d{3,4})", re.IGNORECASE)


def _adr_id(token: str) -> str:
    """Canonicalize an ADR reference to 'ADR-NNNN' (zero-padded to 4), or '' ."""
    m = ADR_ID_RE.search(token)
    if not m:
        return ""
    return f"ADR-{int(m.group(1)):04d}"


def normalized_adr_state(root: Path, adr_id: str) -> str | None:
    """Return the ``current_state`` of a normalized ADR view, or None if absent.

    Reads ``docs/adr/normalized/<adr_id>.yaml`` and extracts the single
    ``current_state`` field. None means the view file does not exist (no
    normalized authority for that ADR); an empty/garbled field returns '' so the
    caller distinguishes "no view" from "view in a non-citeable state".
    """
    if not adr_id:
        return None
    view = root / NORMALIZED_ADR_DIR_REL / f"{adr_id}.yaml"
    if not view.is_file():
        return None
    try:
        text = view.read_text(encoding="utf-8")
    except OSError:
        return None
    m = CURRENT_STATE_LINE_RE.search(text)
    return m.group(1).strip() if m else ""


# ===========================================================================
# L2 design landing probe (active-bar requires_l2_design).
# ===========================================================================
def has_l2_design(root: Path, fp_id: str) -> bool:
    """True when an L2 design landing exists for ``fp_id``.

    Accepts either the directory form ``architecture/docs/L2/<slug>/README.md``
    (the review section 12.1 layout) or the flat form
    ``architecture/docs/L2/<slug>.md``; both the unprefixed slug (``create-run``)
    and the prefixed slug (``fp-create-run``) are tried.
    """
    base = root / L2_DOCS_DIR_REL
    slug = _slug(fp_id)
    candidates = (
        base / slug / "README.md",
        base / f"{slug}.md",
        base / f"fp-{slug}" / "README.md",
        base / f"fp-{slug}.md",
    )
    return any(c.is_file() for c in candidates)


# ===========================================================================
# Per-FunctionPoint readiness evaluation.
# ===========================================================================
@dataclass
class Finding:
    """One FunctionPoint readiness finding."""

    fp_id: str
    fp_var: str
    axis: str  # value | structure | evidence | decision | ownership
    code: str  # short machine code, e.g. EVIDENCE-NO-CONTRACT
    message: str


def _has(value: str) -> bool:
    """True when a DSL string property carries a non-empty, non-placeholder value."""
    return bool(value and value.strip())


def evaluate_function_point(
    fp_var: str,
    el: Element,
    policy: ReadinessPolicy,
    dsl: DslModel,
    facts: FactIndex,
    root: Path,
) -> list[Finding]:
    """Evaluate one FunctionPoint against its readiness bar. Returns findings."""
    fp_id = el.saa_id or fp_var
    bar = policy.bar_for(el.status)
    findings: list[Finding] = []

    # The proposed bar (design_only / proposed) requires nothing yet.
    if policy.requires(bar, "may_lack_facts") or bar == "proposed":
        return findings

    anchored = bool(dsl.frame_anchors_by_fp.get(fp_var))

    # --- active bar -------------------------------------------------------
    if bar == "active":
        if policy.requires(bar, "requires_frame_anchor") and not anchored:
            findings.append(
                Finding(
                    fp_id, fp_var, "structure", "STRUCTURE-NO-ANCHOR",
                    f"status '{el.status}' (bar=active) requires an EngineeringFrame "
                    f"'anchors' edge in {FRAMES_DSL_REL}; none found",
                )
            )
        if policy.requires(bar, "requires_l2_design") and not has_l2_design(root, fp_id):
            findings.append(
                Finding(
                    fp_id, fp_var, "structure", "STRUCTURE-NO-L2-DESIGN",
                    f"status '{el.status}' (bar=active) requires an L2 design landing "
                    f"({L2_DOCS_DIR_REL}/{_slug(fp_id)}/README.md); none found",
                )
            )
        return findings

    # --- shipped bar (the full acceptance bar, per axis) ------------------
    if bar != "shipped":
        # An unmapped status was conservatively resolved to shipped; surface it so
        # the author either adds a status_vocabulary mapping or fixes the typo.
        findings.append(
            Finding(
                fp_id, fp_var, "decision", "STATUS-UNMAPPED",
                f"status '{el.status}' does not map to a readiness bar in "
                f"{POLICY_REL} status_vocabulary; evaluated at the strict 'shipped' bar",
            )
        )

    # Structure axis: anchored by exactly one frame + an owning-module implements.
    # These obligations are UNCONDITIONAL at the shipped bar — the policy
    # acceptance rule fixes "every shipped FunctionPoint maps to exactly one
    # primary EngineeringFrame" and is keyed on the state, not a per-field toggle.
    if not anchored:
        findings.append(
            Finding(
                fp_id, fp_var, "structure", "STRUCTURE-NO-ANCHOR",
                f"shipped FunctionPoint has no EngineeringFrame 'anchors' edge in "
                f"{FRAMES_DSL_REL} (acceptance rule: exactly one primary frame)",
            )
        )
    elif len(dsl.frame_anchors_by_fp.get(fp_var, set())) > 1:
        anchors = ", ".join(sorted(dsl.frame_anchors_by_fp[fp_var]))
        findings.append(
            Finding(
                fp_id, fp_var, "structure", "STRUCTURE-MULTI-ANCHOR",
                f"shipped FunctionPoint is anchored by {len(dsl.frame_anchors_by_fp[fp_var])} "
                f"frames ({anchors}); the acceptance rule requires exactly one primary frame",
            )
        )
    if not dsl.module_impls_by_fp.get(fp_var):
        findings.append(
            Finding(
                fp_id, fp_var, "structure", "STRUCTURE-NO-MODULE",
                f"shipped FunctionPoint has no module 'implements' edge "
                f"(the owning Maven module); add one in {FUNCTION_POINTS_DSL_REL}",
            )
        )

    # Value axis: at least one Feature requires the FunctionPoint.
    if not dsl.feature_requires_by_fp.get(fp_var):
        findings.append(
            Finding(
                fp_id, fp_var, "value", "VALUE-NO-FEATURE",
                f"shipped FunctionPoint is not required by any Feature "
                f"('requires' edge in {FEATURES_DSL_REL}); the value axis is incomplete",
            )
        )

    # Evidence axis: contract-or-rationale.
    if policy.requires("shipped", "requires_contract_or_exception"):
        if not _has(el.contract_op_refs) and not _has(el.no_contract_rationale):
            findings.append(
                Finding(
                    fp_id, fp_var, "evidence", "EVIDENCE-NO-CONTRACT",
                    "shipped FunctionPoint declares neither 'saa.contract_op_refs' nor an "
                    "explicit 'saa.no_contract_rationale'",
                )
            )

    # Evidence axis: a generated-fact reference (a code-entrypoint ref, an explicit
    # fact ref, or a resolving contract-op ref). At least one must resolve in the
    # generated facts; we treat a declared code_entrypoint_refs / fact_refs as the
    # citation and require the facts corpus to be non-empty (the fact-id-exact
    # resolution of an entrypoint is the Frame-Card check's job — here we assert
    # the FunctionPoint CITES generated-fact evidence at all).
    if policy.requires("shipped", "requires_generated_facts"):
        cites_fact = _has(el.code_entrypoint_refs) or _has(el.fact_refs)
        resolves = False
        if _has(el.fact_refs):
            resolves = any(
                ref.strip() in facts.code_symbol_ids
                or ref.strip() in facts.test_ids
                or ref.strip() in facts.contract_op_ids
                for ref in _split_refs(el.fact_refs)
            )
        if not cites_fact:
            findings.append(
                Finding(
                    fp_id, fp_var, "evidence", "EVIDENCE-NO-FACT",
                    "shipped FunctionPoint declares no generated-fact reference "
                    "('saa.code_entrypoint_refs' or 'saa.fact_refs')",
                )
            )
        elif _has(el.fact_refs) and not resolves:
            findings.append(
                Finding(
                    fp_id, fp_var, "evidence", "EVIDENCE-FACT-UNRESOLVED",
                    f"shipped FunctionPoint 'saa.fact_refs' resolves to no fact in "
                    f"{FACTS_DIR_REL}/*.json",
                )
            )

    # Evidence axis: test-ref-or-exception.
    if policy.requires("shipped", "requires_tests_or_exception"):
        if not _has(el.test_refs) and not _has(el.test_exception):
            findings.append(
                Finding(
                    fp_id, fp_var, "evidence", "EVIDENCE-NO-TEST",
                    "shipped FunctionPoint declares neither 'saa.test_refs' nor an "
                    "explicit approved 'saa.test_exception'",
                )
            )

    # Evidence axis: a gate reference. The architecture-sync gate always covers a
    # DSL FunctionPoint, so a missing key defaults to that; only an explicitly
    # EMPTY gate_refs is an authored assertion that no gate covers it.
    if policy.requires("shipped", "requires_gate_ref"):
        if el.gate_refs is not None and not _has(el.gate_refs):
            findings.append(
                Finding(
                    fp_id, fp_var, "evidence", "EVIDENCE-NO-GATE",
                    "shipped FunctionPoint declares an empty 'saa.gate_refs' (no gate "
                    f"reference); the default '{DEFAULT_GATE_REF}' was removed",
                )
            )

    # Decision axis: normalized ADR view in a citeable state.
    if policy.requires("shipped", "requires_normalized_adr"):
        adr_id = _adr_id(el.source_adr)
        if not adr_id:
            findings.append(
                Finding(
                    fp_id, fp_var, "decision", "DECISION-NO-ADR",
                    "shipped FunctionPoint declares no resolvable 'saa.sourceAdr' "
                    "(ADR-NNNN); the decision axis is incomplete",
                )
            )
        else:
            state = normalized_adr_state(root, adr_id)
            if state is None:
                findings.append(
                    Finding(
                        fp_id, fp_var, "decision", "DECISION-NO-NORMALIZED-VIEW",
                        f"shipped FunctionPoint cites {adr_id} but no normalized view "
                        f"exists at {NORMALIZED_ADR_DIR_REL}/{adr_id}.yaml (raw prose is "
                        "not current authority)",
                    )
                )
            elif state not in CITEABLE_ADR_STATES:
                findings.append(
                    Finding(
                        fp_id, fp_var, "decision", "DECISION-ADR-NOT-CITEABLE",
                        f"shipped FunctionPoint cites {adr_id} whose normalized view is "
                        f"'{state}', not active_guidance / partial_guidance (not current "
                        "authority)",
                    )
                )

    return findings


def _split_refs(value: str) -> list[str]:
    """Split a multi-valued DSL property on the '|' separator (saa convention)."""
    return [part for part in re.split(r"[|,]", value) if part.strip()]


# ===========================================================================
# Changed-file scoping.
# ===========================================================================
def _git_run(args: list[str], cwd: Path) -> tuple[int, str]:
    """Run git; return ``(returncode, stdout)``. Forces UTF-8 (Windows GBK-safe)."""
    try:
        result = subprocess.run(
            ["git", *args],
            cwd=str(cwd),
            capture_output=True,
            text=True,
            encoding="utf-8",
            errors="replace",
            check=False,
        )
        return result.returncode, (result.stdout or "")
    except OSError:
        return 1, ""


def _changed_paths(root: Path, base: str) -> tuple[set[str] | None, str]:
    """Return the repo-relative changed paths vs ``base``, or ``(None, reason)``.

    Scope = (committed diff merge-base...HEAD) UNION (uncommitted tracked changes)
    UNION (untracked files). A ``None`` return means git could not resolve the
    base ref; the caller falls back to the full corpus (a safe superset).
    """
    rc, _ = _git_run(["rev-parse", "--git-dir"], root)
    if rc != 0:
        return None, "not a git repository (cannot scope changed files)"

    diff_spec = base
    rc, mb = _git_run(["merge-base", base, "HEAD"], root)
    if rc == 0 and mb.strip():
        diff_spec = mb.strip()
    else:
        rc_verify, _ = _git_run(["rev-parse", "--verify", "--quiet", base], root)
        if rc_verify != 0:
            return None, f"base ref {base!r} is not resolvable in this clone"

    names: set[str] = set()
    rc, out = _git_run(["diff", "--name-only", diff_spec, "HEAD"], root)
    if rc == 0:
        names.update(line.strip() for line in out.splitlines() if line.strip())
    rc, out = _git_run(["diff", "--name-only", "HEAD"], root)
    if rc == 0:
        names.update(line.strip() for line in out.splitlines() if line.strip())
    rc, out = _git_run(["ls-files", "--others", "--exclude-standard"], root)
    if rc == 0:
        names.update(line.strip() for line in out.splitlines() if line.strip())

    return {n.replace("\\", "/") for n in names}, ""


def scope_changed_function_points(
    root: Path, base: str, dsl: DslModel
) -> tuple[set[str] | None, str]:
    """Return the set of FunctionPoint VARS in scope for changed-files-blocking.

    A change to any SHARED_SCOPE_SURFACES re-scopes EVERY FunctionPoint (shared
    dependency graph). A change to an L2 design dir
    (``architecture/docs/L2/<slug>/...``) scopes the FunctionPoint whose slug
    matches. ``None`` => git could not scope; caller falls back to the full corpus.
    """
    changed, reason = _changed_paths(root, base)
    if changed is None:
        return None, reason

    # Any shared-surface change scopes the whole corpus.
    if any(s in changed for s in SHARED_SCOPE_SURFACES):
        return set(dsl.function_points.keys()), ""

    # Otherwise, map changed L2 design files back to FunctionPoint slugs.
    l2_prefix = L2_DOCS_DIR_REL + "/"
    changed_l2_slugs: set[str] = set()
    for path in changed:
        if not path.startswith(l2_prefix):
            continue
        remainder = path[len(l2_prefix):]
        # <slug>/README.md  OR  <slug>.md
        head = remainder.split("/", 1)[0]
        slug = head[:-3] if head.endswith(".md") else head
        changed_l2_slugs.add(slug.lower())
        changed_l2_slugs.add(slug.lower().removeprefix("fp-"))

    scoped: set[str] = set()
    for var, el in dsl.function_points.items():
        if _slug(el.saa_id or var) in changed_l2_slugs:
            scoped.add(var)
    return scoped, ""


# ===========================================================================
# CLI.
# ===========================================================================
def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Rule G-30 — FunctionPoint readiness across the four axes: "
        "evaluate each FunctionPoint against its readiness bar from "
        "docs/governance/feature-readiness-policy.yaml (ADR-0159).",
    )
    parser.add_argument(
        "--mode",
        choices=("advisory", "changed-files-blocking", "full-blocking"),
        default="advisory",
        help="advisory (report, never block); changed-files-blocking (block only "
        "on changed FunctionPoints); full-blocking (block on any). Default: advisory.",
    )
    parser.add_argument(
        "--base",
        default="origin/main",
        help="Base ref for changed-files-blocking scope. Default: origin/main.",
    )
    parser.add_argument(
        "--repo",
        default=None,
        help="Repository root. Defaults to the script-derived root.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    root = Path(args.repo) if args.repo else repo_root()
    if not root.is_dir():
        print(f"feature-readiness: --repo {root} is not a directory", file=sys.stderr)
        return 2

    # The DSL FunctionPoint inventory governs vacuity: no FunctionPoints => nothing
    # to evaluate, vacuously clean in every mode. A missing function-points.dsl is
    # itself the "no subjects" condition, surfaced as a clean greenfield pass.
    fp_path = root / FUNCTION_POINTS_DSL_REL
    if not fp_path.is_file():
        print(
            f"feature-readiness [{args.mode}]: {FUNCTION_POINTS_DSL_REL} absent "
            "(greenfield); 0 finding(s)",
            file=sys.stderr,
        )
        return 0

    dsl, dsl_errors = load_dsl_model(root)
    if dsl is None:
        for err in dsl_errors:
            print(f"feature-readiness config error (DSL): {err}", file=sys.stderr)
        return 2

    if not dsl.function_points:
        print(
            f"feature-readiness [{args.mode}]: no FunctionPoint elements in "
            f"{FUNCTION_POINTS_DSL_REL} yet (greenfield); 0 finding(s)",
            file=sys.stderr,
        )
        return 0

    # FunctionPoints exist -> the policy schema + generated facts MUST be readable.
    # A missing authority is never an advisory condition (fail closed, every mode).
    policy_doc, perr = _load_yaml(root / POLICY_REL)
    if perr:
        print(f"feature-readiness config error (policy): {perr}", file=sys.stderr)
        return 2
    policy, berr = build_policy(policy_doc)
    if policy is None:
        print(f"feature-readiness config error (policy): {berr}", file=sys.stderr)
        return 2

    facts, fact_errors = load_fact_index(root)
    if facts is None:
        for err in fact_errors:
            print(f"feature-readiness config error (facts): {err}", file=sys.stderr)
        return 2

    # The dated baseline allow-list is OPTIONAL (absent => tolerate nothing); when
    # present it MUST parse, or the gate fails closed in every mode — a malformed
    # allow-list must never silently suppress a finding.
    baseline, baseline_err = load_baseline(root)
    if baseline is None:
        print(
            f"feature-readiness config error (baseline): {baseline_err}",
            file=sys.stderr,
        )
        return 2

    # Ownership-invariant findings are computed over the WHOLE model (they are not
    # per-FunctionPoint-state): a non-frame anchors source is always a structural
    # lie. They block in any blocking mode regardless of changed-file scope.
    ownership_findings: list[Finding] = []
    for src, dst, src_meta in dsl.nonframe_anchor_edges:
        dst_el = dsl.function_points.get(dst)
        fp_id = (dst_el.saa_id if dst_el else "") or dst
        kind = (
            "value-axis node"
            if src_meta in VALUE_AXIS_META
            else f"non-frame element ({src_meta})"
        )
        ownership_findings.append(
            Finding(
                fp_id, dst, "ownership", "OWNERSHIP-NONFRAME-ANCHOR",
                f"FunctionPoint is 'anchors'-owned by {src!r} ({kind}); only an "
                "EngineeringFrame may anchor a FunctionPoint (ADR-0159 acceptance "
                "rule: no ProductClaim/Requirement/Feature owns a frame)",
            )
        )

    # Determine the evaluation scope.
    all_vars = set(dsl.function_points.keys())
    scoped_note = ""
    if args.mode == "changed-files-blocking":
        scoped, reason = scope_changed_function_points(root, args.base, dsl)
        if scoped is None:
            print(
                f"feature-readiness: changed-file scoping unavailable ({reason}); "
                "falling back to full-corpus evaluation",
                file=sys.stderr,
            )
            scope_vars = all_vars
        else:
            scope_vars = scoped
            scoped_note = f" (corpus total {len(all_vars)})"
    else:
        scope_vars = all_vars

    # Evaluate every FunctionPoint (so the advisory report is complete), but only
    # the in-scope findings drive a blocking exit.
    per_fp_findings: list[Finding] = []
    for var in sorted(all_vars, key=lambda v: dsl.function_points[v].saa_id or v):
        per_fp_findings.extend(
            evaluate_function_point(
                var, dsl.function_points[var], policy, dsl, facts, root
            )
        )

    all_findings = ownership_findings + per_fp_findings

    # Emit a deterministic, grep-friendly report to stderr.
    for f in all_findings:
        print(
            f"feature-readiness {f.fp_id} [{f.axis}/{f.code}]: {f.message}",
            file=sys.stderr,
        )

    # Axis-level summary line (consumed by the gate's advisory grep on 'finding(s)').
    by_axis: dict[str, int] = {}
    for f in all_findings:
        by_axis[f.axis] = by_axis.get(f.axis, 0) + 1
    breakdown = (
        ", ".join(f"{ax}={n}" for ax, n in sorted(by_axis.items())) if by_axis else "none"
    )
    summary = (
        f"feature-readiness [{args.mode}]: evaluated {len(all_vars)} FunctionPoint(s)"
        f"{scoped_note}; {len(all_findings)} finding(s) [{breakdown}]"
    )
    print(summary, file=sys.stderr)

    if args.mode == "advisory":
        return 0

    # Blocking modes: ownership findings ALWAYS block (never baselined). Per-FP
    # findings block when their FunctionPoint is in scope. Under changed-files-
    # blocking ONLY, a per-FP finding frozen in a STILL-OPEN baseline row is
    # tolerated (reported BASELINED, never blocks) — the section 13.3 ratchet
    # tolerance for historical debt; full-blocking ignores the baseline (the
    # terminal posture demands a fully clean corpus).
    today = _utc_today()
    baseline_applies = args.mode == "changed-files-blocking"

    blocking = list(ownership_findings)
    baselined: list[tuple[Finding, BaselineRow]] = []
    for f in per_fp_findings:
        if f.fp_var not in scope_vars:
            continue
        row = (
            suppressing_baseline_row(f, baseline, today) if baseline_applies else None
        )
        if row is not None:
            baselined.append((f, row))
        else:
            blocking.append(f)

    # Report each tolerated finding (deterministic, grep-friendly).
    for f, row in baselined:
        print(
            f"feature-readiness BASELINED {f.fp_id} [{f.axis}/{f.code}]: tolerated "
            f"by {row.id} (sunset {row.raw_sunset})",
            file=sys.stderr,
        )

    # Keep the allow-list honest: a row whose sunset has passed AND that matched no
    # live in-scope finding is dead weight — flag it for removal (a NOTE, not a
    # block), mirroring check_layer_purity's expired-row note. Only meaningful when
    # the baseline could apply (changed-files mode) and was present.
    if baseline_applies and baseline.present:
        matched_ids = {row.id for _, row in baselined}
        for row in baseline.rows:
            if row.id in matched_ids:
                continue
            if row.sunset_date is not None and row.sunset_date >= today:
                continue  # still-open, simply matched nothing in this scope
            print(
                f"feature-readiness NOTE: baseline row {row.id} expired "
                f"({row.raw_sunset or 'no sunset'}) and matched no live in-scope "
                f"finding; remove it from {BASELINE_REL}",
                file=sys.stderr,
            )

    if baselined:
        print(
            f"feature-readiness [{args.mode}]: {len(baselined)} finding(s) tolerated "
            f"by the dated baseline {BASELINE_REL}",
            file=sys.stderr,
        )

    if blocking:
        print(
            f"BLOCKING: {len(blocking)} in-scope feature-readiness finding(s) under "
            f"--mode {args.mode}",
            file=sys.stderr,
        )
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
