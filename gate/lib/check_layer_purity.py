#!/usr/bin/env python3
"""Gate check: layer purity — L2/code detail leaked into L0/L1 architecture prose.

Encodes the adjudicated verdict (the "L0/L1 contains L2/code detail" critique is
TRUE) as an executable, three-mode advisory gate. It reads the machine-readable
owns/forbids policy and the dated grandfather allow-list, scans both renderings
of the L0 + L1 architecture authority surface — the prose documents AND the
free-text ``description`` strings of the C4 4+1 view fragments mounted into
``architecture/workspace.dsl`` — for leaked-category trigger patterns, and
reports any LEAKED block that is not redeemed by a still-open temporary-violation
row.

Two policy surfaces drive this check (both authored separately; this script is
their executable consumer, it invents no id and no relationship and it never
outranks a generated fact):

  * ``docs/governance/layer-purity-policy.yaml`` — the owns/forbids category
    vocabulary. Fixes the closed category set (defensible D1..D3, leaked
    L1..L8), the layers each category is OWNED / FORBIDDEN at, and the
    authoritative home a leaked category must migrate to. Authority: ADR-0159.
  * ``docs/governance/layer-purity-temporary-violations.yaml`` — the closed,
    dated grandfather list. Each row freezes one known, not-yet-migrated leak in
    an L0/L1 document AND declares a per-entry ``sunset_date`` plus a per-entry
    ``locus`` (the smallest in-file anchor — a section label and a line range).
    A leaked block is TOLERATED (reported as a grandfathered advisory, never a
    finding) only when a still-open row matches it on ALL of file, category, AND
    locus — the leak's line number MUST fall inside one of the row's enumerated
    line ranges. A leak that matches a row's file + category but lands OUTSIDE
    every range that row enumerates is NOT that row's adjudicated leak and stays
    a finding. A leak in no row — or one whose ``sunset_date`` has passed (UTC) —
    is a finding. (A row whose ``locus`` carries no parseable line range — a
    deliberately whole-file ``row-level pass deferred`` entry — is anchorless and
    falls back to file + category matching; ``load_policy`` records which rows are
    anchored so the locus invariant stays auditable.)

What this check is NOT. It is not the migration itself, not a rule card, not an
enforcer row, and it does not edit any authority surface. It is a READABLE-
INTERPRETATION classifier over layer prose: a mechanical pattern match cannot
reproduce the scan's per-line semantic judgement, so its trigger library is
deliberately specific (drawn from the verdict's concrete trigger phrases) to keep
false positives low; the policy YAML remains the normative owns/forbids source
where the two disagree, and the dated allow-list remains the sole tolerance
gate.

Scope of the scan. Two L0/L1-authority RENDERINGS are scanned, because the
layer-purity VERDICT is expressed against the L0/L1 architecture AUTHORITY
SURFACE, not against one file extension:

  * the prose rendering — ``architecture/docs/L0/**/*.md`` and
    ``architecture/docs/L1/**/*.md`` (every line), the over-layer documents a
    leak can sit in; and
  * the C4 view rendering — the free-text ``description`` strings of the
    Structurizr 4+1 view fragments under ``architecture/views/*.dsl``
    (``L0-system-context.dsl`` is L0; ``L1-development.dsl`` / ``L1-process.dsl``
    / ``L1-physical.dsl`` / ``L1-scenarios.dsl`` are L1). These ``.dsl`` files
    are the SAME 4+1 views the per-module ``.md`` narratives render, and they are
    the MORE-authoritative twin — they are what ``architecture/workspace.dsl``
    mounts (``!include views/...``), so under the authority cascade
    (generated facts > DSL > Card/prose) a view ``description`` sits ABOVE the
    ``.md`` narrative of the same view. A leak cleaned in the ``.md`` twin but
    left in the ``.dsl`` twin (or the reverse) is the SAME leak surviving in a
    co-equal rendering; scoping to ``.md`` alone left a by-file-TYPE hole through
    which a whole authority rendering escaped the gate — and the non-vacuity
    guard could not catch it, since it only fired on ZERO ``.md`` docs, never on
    a whole file-type being out of scope. Only the ``description`` VALUE of a
    view is scanned: the structural DSL tokens (the
    ``container``/``systemContext`` element name + key, ``include *``,
    ``autoLayout``, ``title``) are the D2 development-view index, not a
    behavioural claim, and are never scanned — mirroring how the sibling
    status-claim helper spares the ledger's structural keys.

``architecture/docs/L2/`` and ``docs/contracts/`` are never scanned: L2 OWNS
every leaked category (it is the migration home), so detail there is in-layer by
construction. The ``architecture/features/*.dsl`` FEATURE fragments
(function-points / engineering-frames / verification / capabilities) are a
DIFFERENT, separately-registered surface (policy ``governed_surfaces``
``dsl-element-description``, the reserved E202 gate) and are NOT scanned here —
this helper covers the 4+1 VIEW fragments under ``architecture/views/``, not the
feature fragments. The L1 ``_template/`` skeletons are excluded (they are
placeholder scaffolding, not authored content).

Modes (``--mode``):

  advisory                 Scan every L0/L1 document, print findings +
                           grandfathered notes to stderr, and ALWAYS exit 0.
                           This is the soak posture: the check reports but never
                           blocks. Default until the policy promotes to blocking
                           per ADR-0159 §9.
  changed-files-blocking   Scan only the L0/L1 documents that changed relative to
                           a base ref (``--base``, default ``origin/main``). Exit
                           1 if any CHANGED document carries a non-grandfathered
                           leak; pre-existing leaks in untouched documents do not
                           block. This is the ratchet posture: a PR may not ADD a
                           leak, but is not blocked by debt it did not touch.
  full-blocking            Scan every L0/L1 document; exit 1 on any
                           non-grandfathered leak. This is the terminal posture
                           once the corpus is clean and every grandfather row has
                           been retired.

Usage:
    python3 gate/lib/check_layer_purity.py --mode advisory
    python3 gate/lib/check_layer_purity.py --mode changed-files-blocking
    python3 gate/lib/check_layer_purity.py --mode changed-files-blocking --base origin/main
    python3 gate/lib/check_layer_purity.py --mode full-blocking
    python3 gate/lib/check_layer_purity.py --mode full-blocking --repo /path/to/repo

Exit codes:
    0 — passed (always, in advisory mode); or no in-scope findings in a blocking
        mode
    1 — one or more in-scope, non-grandfathered findings in a blocking mode
        (printed to stderr)
    2 — usage / configuration error (bad mode, unreadable/empty policy file, no
        L0/L1 documents discovered, the view-DSL roster present on disk yet zero
        of its files reached the scan — a whole-rendering scope drift, etc.)

Authority: ADR-0159 (Progressive Learning Curve and Authority Lanes) §7 + §9.
"""
from __future__ import annotations

import argparse
import datetime
import re
import subprocess
import sys
from dataclasses import dataclass, field
from pathlib import Path

# ---------------------------------------------------------------------------
# Canonical policy-surface + scan-scope locations (repo-relative, forward slash).
# ---------------------------------------------------------------------------
POLICY_REL = "docs/governance/layer-purity-policy.yaml"
VIOLATIONS_REL = "docs/governance/layer-purity-temporary-violations.yaml"

# The over-layers a leak can sit in. Each maps to its scanned document root.
# L2 is intentionally absent — it OWNS every leaked category.
SCANNED_LAYER_ROOTS = {
    "L0": "architecture/docs/L0",
    "L1": "architecture/docs/L1",
}

# Path fragments that are excluded from the scan even under a scanned root:
# the per-module template skeleton carries placeholder prose, not authored
# layer content, so it must not generate leaks.
EXCLUDED_PATH_FRAGMENTS = ("/_template/",)

# The C4 4+1 view fragments — the SECOND L0/L1-authority rendering. Each is a
# Structurizr view file mounted into architecture/workspace.dsl
# (``!include views/...``), so under the authority cascade (generated facts >
# DSL > Card/prose) a view's free-text ``description`` sits ABOVE the per-module
# ``.md`` narrative of the same 4+1 view. The map pins each view file to the
# layer (L0/L1) it renders, so a leak in a view ``description`` is reported at
# the right layer and matched against the right grandfather rows. This is a
# CLOSED roster (these five views are exactly what workspace.dsl mounts); a new
# view added to the workspace is added here too — the file-type non-vacuity
# guard below fails closed if this roster exists on disk yet contributes nothing
# to the scan, so a future rename cannot silently drop the whole rendering.
SCANNED_VIEW_DSLS: dict[str, str] = {
    "architecture/views/L0-system-context.dsl": "L0",
    "architecture/views/L1-development.dsl": "L1",
    "architecture/views/L1-process.dsl": "L1",
    "architecture/views/L1-physical.dsl": "L1",
    "architecture/views/L1-scenarios.dsl": "L1",
}

# A Structurizr ``description "<text>"`` line inside a view fragment. Only the
# QUOTED value is the free-text behavioural narrative the verdict governs; the
# structural tokens around it (element name/key, ``include *``, ``autoLayout``,
# ``title``) are the D2 development-view index and are never scanned. The match
# is anchored to a line that STARTS (after indentation) with ``description`` so a
# stray ``description`` word inside another value is not mistaken for the field.
_DSL_DESCRIPTION_RE = re.compile(r'^\s*description\s+"((?:[^"\\]|\\.)*)"\s*$')


def repo_root() -> Path:
    """Return the repository root (the directory two levels above this script)."""
    return Path(__file__).resolve().parent.parent.parent


def _rel(path: Path, root: Path) -> str:
    try:
        return str(path.relative_to(root)).replace("\\", "/")
    except ValueError:
        return str(path).replace("\\", "/")


def _utc_today() -> datetime.date:
    """Today's date in UTC.

    The freshness/sunset convention in this repo is UTC: a local +08 date can be
    one day ahead of the CI clock (which runs in UTC), so a sunset compared
    against ``date.today()`` could expire a row a day early on a local run and
    pass on CI. Anchoring on UTC keeps local and CI verdicts identical.
    """
    return datetime.datetime.now(datetime.timezone.utc).date()


# ===========================================================================
# YAML loading. PyYAML is a hard dependency of the gate (Rule 38 fails fast
# without it); a missing parser for a policy surface here is therefore a config
# error (exit 2) — we fail closed rather than pass vacuously without the schema.
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
# Policy model — the typed projection of the two governance-policy surfaces.
# ===========================================================================
@dataclass
class Category:
    """One owns/forbids category row from layer-purity-policy.yaml."""

    id: str
    kind: str  # "defensible" | "leaked"
    title: str
    forbidden_at: set[str]
    home: str


@dataclass
class Violation:
    """One row from layer-purity-temporary-violations.yaml (a dated grandfather)."""

    id: str
    layer: str
    file: str  # repo-relative, forward slash
    categories: set[str]
    sunset_date: datetime.date | None
    raw_sunset: str
    locus_ranges: list[tuple[int, int]] = field(default_factory=list)
    raw_locus: str = ""

    @property
    def locus_anchored(self) -> bool:
        """True when this row enumerates at least one parseable line range.

        An anchored row tolerates a leak only inside its enumerated ranges; an
        anchorless row (``row-level pass deferred`` whole-file entries whose
        ``locus`` carries no line number) falls back to file + category matching.
        """
        return bool(self.locus_ranges)

    def covers_line(self, line_no: int) -> bool:
        """Whether ``line_no`` falls inside one of this row's enumerated ranges.

        The locus is the row's adjudicated anchor: a same-file, same-category
        leak OUTSIDE every range is a different leak the row never enumerated, so
        it is NOT tolerated. An anchorless row covers every line by construction
        (it deliberately defers a whole-file pass); ``locus_anchored`` lets the
        caller decide whether to require a range match.
        """
        if not self.locus_ranges:
            return True
        return any(start <= line_no <= end for start, end in self.locus_ranges)

    def is_open(self, today: datetime.date) -> bool:
        """A row tolerates a leak only while its sunset is in the future (inclusive).

        A missing/unparseable sunset is treated as ALREADY-EXPIRED (it cannot
        prove it is still open), so a malformed allow-list row never silently
        suppresses a leak.
        """
        return self.sunset_date is not None and self.sunset_date >= today


@dataclass
class Policy:
    """Typed view over the two layer-purity policy surfaces."""

    categories: dict[str, Category]
    status: str
    violations: list[Violation] = field(default_factory=list)
    violations_status: str = ""
    violations_list_closed: bool = False

    def leaked_categories(self) -> dict[str, Category]:
        return {cid: c for cid, c in self.categories.items() if c.kind == "leaked"}


def _parse_sunset(value: object) -> tuple[datetime.date | None, str]:
    """Parse a sunset_date cell. Returns ``(date_or_None, raw_string)``."""
    raw = "" if value is None else str(value)
    if not raw.strip():
        return None, raw
    try:
        return datetime.date.fromisoformat(raw.strip()), raw
    except ValueError:
        return None, raw


# A single comma-segment of a clean line spec: a bare line ("360") or a closed
# range ("520-535"). The ^...$ anchors are deliberate — a segment is a line spec
# ONLY when the WHOLE segment is numeric, so a textual fragment such as
# "3-track" (a stray digit inside prose) is NOT mistaken for a range.
_LOCUS_RANGE_SEG_RE = re.compile(r"^(\d+)\s*-\s*(\d+)$")
_LOCUS_SINGLE_SEG_RE = re.compile(r"^(\d+)$")
# A parenthetical note ("(P6)", "(row-level pass deferred)") is never a line
# spec; strip it before deciding whether the remainder is a clean spec.
_LOCUS_NOTE_RE = re.compile(r"\([^)]*\)")


def _parse_locus(value: object) -> tuple[list[tuple[int, int]], str]:
    """Parse a ``locus`` cell into a list of (start, end) line ranges.

    Returns ``(ranges, raw_string)``. ``ranges`` is empty when the locus is NOT a
    clean line spec — a deliberately whole-file ``row-level pass deferred`` entry
    (``"matched RLS / 3-track / sandbox (row-level pass deferred)"``) or a
    malformed cell. Such a row is anchorless and falls back to file + category
    matching.

    Locus grammar (the only shapes the dated allow-list uses):
      * an optional ``<section label> :`` prefix, then a comma-separated list of
        single lines and ``start-end`` ranges, then an optional ``(note)`` tail:
        ``"§4 #20 : 520-535"`` -> [(520, 535)];
        ``"31, 39, 67"``        -> [(31, 31), (39, 39), (67, 67)];
        ``"259-286 (P6)"``      -> [(259, 286)];
        ``"22, 89, 113 (P1-P6)"``-> [(22, 22), (89, 89), (113, 113)].

    Parsing is STRICT and all-or-nothing per the clean-spec contract:
      1. drop any ``(...)`` note (it holds human text, never a line number — so
         the ``6`` in ``(P6)`` and the ``1``/``6`` in ``(P1-P6)`` never leak in);
      2. take only the segment AFTER the LAST ``:`` when a label separator is
         present (so the digits in a section label, ``§4 #20`` -> ``4``/``20``,
         never leak in);
      3. split the remainder on commas; EVERY segment must be a bare integer or a
         closed ``int-int`` range. If any segment is non-numeric (a textual
         deferred locus such as ``matched RLS / 3-track / sandbox``), the locus
         is NOT a line spec and ``ranges`` is returned empty (anchorless), rather
         than letting a stray in-prose digit (``3-track``) anchor the row to a
         wrong line.
    """
    raw = "" if value is None else str(value)
    spec = _LOCUS_NOTE_RE.sub(" ", raw)
    if ":" in spec:
        spec = spec.rsplit(":", 1)[1]
    segments = [s.strip() for s in spec.split(",") if s.strip()]
    if not segments:
        return [], raw
    ranges: list[tuple[int, int]] = []
    for seg in segments:
        rng = _LOCUS_RANGE_SEG_RE.match(seg)
        if rng:
            start, end = int(rng.group(1)), int(rng.group(2))
            if start > end:
                start, end = end, start
            ranges.append((start, end))
            continue
        single = _LOCUS_SINGLE_SEG_RE.match(seg)
        if single:
            n = int(single.group(1))
            ranges.append((n, n))
            continue
        # A non-numeric segment -> this locus is not a clean line spec; treat the
        # whole row as anchorless (whole-file deferred) rather than half-parsing.
        return [], raw
    ranges.sort()
    return ranges, raw


def load_policy(root: Path) -> tuple[Policy | None, list[str]]:
    """Load + validate both policy surfaces. Returns ``(policy, config_errors)``.

    A non-empty ``config_errors`` list means the policy could not be loaded; the
    caller MUST treat that as exit-2 (the schema is unavailable, so no document
    can be judged — failing closed rather than passing vacuously).
    """
    errors: list[str] = []

    policy_doc, perr = _load_yaml(root / POLICY_REL)
    if perr:
        errors.append(f"policy: {perr}")
    violations_doc, verr = _load_yaml(root / VIOLATIONS_REL)
    if verr:
        errors.append(f"temporary-violations: {verr}")
    if errors:
        return None, errors

    if not isinstance(policy_doc, dict):
        errors.append(f"policy: top-level of {POLICY_REL} must be a mapping")
    if not isinstance(violations_doc, dict):
        errors.append(f"temporary-violations: top-level of {VIOLATIONS_REL} must be a mapping")
    if errors:
        return None, errors

    # --- categories[] ------------------------------------------------------
    cat_rows = policy_doc.get("categories")
    if not isinstance(cat_rows, list) or not cat_rows:
        errors.append(f"policy: {POLICY_REL} missing non-empty 'categories' list")
        cat_rows = []
    categories: dict[str, Category] = {}
    for idx, row in enumerate(cat_rows):
        if not isinstance(row, dict):
            errors.append(f"policy: categories[{idx}] must be a mapping")
            continue
        cid = str(row.get("id", "")).strip()
        kind = str(row.get("kind", "")).strip()
        if not cid:
            errors.append(f"policy: categories[{idx}] missing 'id'")
            continue
        if kind not in ("defensible", "leaked"):
            errors.append(
                f"policy: category {cid!r} has kind {kind!r}; expected 'defensible' or 'leaked'"
            )
        forbidden_raw = row.get("forbidden_at", []) or []
        forbidden = {str(x).strip() for x in forbidden_raw} if isinstance(forbidden_raw, list) else set()
        categories[cid] = Category(
            id=cid,
            kind=kind,
            title=str(row.get("title", cid)).strip(),
            forbidden_at=forbidden,
            home=str(row.get("home", "")).strip(),
        )

    status = str(policy_doc.get("status", "")).strip()

    # --- violations[] ------------------------------------------------------
    viol_rows = violations_doc.get("violations")
    # An empty/absent violations list is legitimate (the corpus may be clean):
    # it is NOT a config error. Only a wrong TYPE is.
    if viol_rows is None:
        viol_rows = []
    if not isinstance(viol_rows, list):
        errors.append(f"temporary-violations: {VIOLATIONS_REL} 'violations' must be a list")
        viol_rows = []
    violations: list[Violation] = []
    for idx, row in enumerate(viol_rows):
        if not isinstance(row, dict):
            errors.append(f"temporary-violations: violations[{idx}] must be a mapping")
            continue
        vid = str(row.get("id", f"<row-{idx}>")).strip()
        layer = str(row.get("layer", "")).strip()
        vfile = str(row.get("file", "")).strip().replace("\\", "/")
        # A row may carry a single `category` or a `categories` list.
        cats: set[str] = set()
        if isinstance(row.get("categories"), list):
            cats.update(str(x).strip() for x in row["categories"] if str(x).strip())
        single = row.get("category")
        if single is not None and str(single).strip():
            cats.add(str(single).strip())
        sunset, raw_sunset = _parse_sunset(row.get("sunset_date"))
        locus_ranges, raw_locus = _parse_locus(row.get("locus"))
        # Cross-check each cited category id against the policy vocabulary so a
        # typo in the allow-list cannot silently over-suppress.
        for c in cats:
            if c not in categories:
                errors.append(
                    f"temporary-violations: row {vid!r} cites category {c!r} "
                    f"absent from {POLICY_REL} categories"
                )
        violations.append(
            Violation(
                id=vid,
                layer=layer,
                file=vfile,
                categories=cats,
                sunset_date=sunset,
                raw_sunset=raw_sunset,
                locus_ranges=locus_ranges,
                raw_locus=raw_locus,
            )
        )

    if errors:
        return None, errors

    return (
        Policy(
            categories=categories,
            status=status,
            violations=violations,
            violations_status=str(violations_doc.get("status", "")).strip(),
            violations_list_closed=bool(violations_doc.get("list_closed", False)),
        ),
        [],
    )


# ===========================================================================
# Leaked-category trigger library.
#
# Each leaked category id (L1..L8) maps to a list of (compiled regex, label)
# probes derived from the verdict's concrete trigger phrases (recorded in
# docs/governance/remediation-inventory/layer-purity-scan.md and mirrored in the
# policy `definition` fields). The probes are deliberately specific — they target
# the mechanism (SQL keyword, HTTP status code, @Order value, sequenceDiagram
# fence, traceparent header, JVM-shape sentence) rather than the topic — so that
# a one-line SPI boundary identity (D1) or a package-decomposition sentence (D2)
# does not trip them. A line that matches several probes is reported once, under
# the first category in policy order, with the matching label.
#
# The category ids MUST exist in layer-purity-policy.yaml; load_policy() rejects
# any probe category absent from the policy vocabulary (fail-closed on drift).
# ===========================================================================
TRIGGERS: dict[str, list[tuple[re.Pattern[str], str]]] = {
    # L1 — Method call chains: inter-method dispatch sequences "a -> b", named
    # method-to-method chains, "invoked via", "atomic CAS via <method>(...)".
    # Widened (convergence scan round 3) so a call hop whose surface form evades
    # the arrow/parenthesis shapes — a "via Type.method()" hop, or a slash/comma
    # method-name RUN ("init / execute / suspend / teardown") — is also a leak:
    # the LEAKED rubric is semantic (a named method-to-method chain at L0/L1 is
    # forbidden regardless of the separator), so the probe must not key only on
    # the "->" arrow it was first written for. Two of these widened probes are
    # anchored TIGHTLY so they match a method CALL and not its prose look-alike:
    #   * the "via Type.method(" hop requires the paren to ABUT the name — a real
    #     call is `Checkpointer.save()`, never `sessionId (existing)` /
    #     `newMessages (ADR-0112)` where the parenthesis is a prose annotation a
    #     space away from a bare field/identity reference;
    #   * the lifecycle method-name RUN requires whitespace-flanked slashes
    #     (`init / execute`) and forbids a path char in the lead-in gap, so a file
    #     PATH (`architecture/docs/L0/...`) whose segments happen to start
    #     lowercase is not mis-read as a method run.
    # Both keep their genuine matches (the corpus's only real hits, `Checkpointer
    # .save(` and the §4 #27 "init / execute / suspend / teardown" run) while
    # shedding the prose-paren / path false positives.
    "L1-method-call-chain": [
        (re.compile(r"\b[A-Za-z_]\w*\([^)]*\)\s*->\s*[A-Za-z_]\w*\s*\("), "method-call arrow chain a(...) -> b("),
        (re.compile(r"\bupdateIfNotTerminal\s*\("), "named CAS dispatch updateIfNotTerminal(...)"),
        (re.compile(r"\bwithStatus\s*\([^)]*\)\s*MUST\s+invoke\b", re.IGNORECASE), "withStatus(...) MUST invoke ... call chain"),
        (re.compile(r"\b(?:invoked|invoke[ds]?)\s+via\b.*\b[A-Za-z_]\w*\s*\(", re.IGNORECASE), "'invoked via <method>(...)' dispatch"),
        (re.compile(r"\b[A-Za-z_]\w*\.[A-Za-z_]\w*\s*\([^)]*\)\s*(?:->|=>)\s*[A-Za-z_]"), "Type.method(...) -> ... dispatch"),
        (re.compile(r"\bvia\s+[A-Z][A-Za-z0-9_]*\.[A-Za-z_]\w*\(", re.IGNORECASE), "'via Type.method(...)' call hop"),
        (re.compile(r"\b[a-z][A-Za-z0-9_]*\s*\(\s*\)\s*(?:/|,)\s*[a-z][A-Za-z0-9_]*\s*\(\s*\)\s*(?:/|,)\s*[a-z][A-Za-z0-9_]*\s*\(\s*\)"), "slash/comma method-name run a()/b()/c()"),
        (re.compile(r"\b(?:lifecycle\s+methods?|methods?)\b[^./\n]{0,20}?`?[a-z][A-Za-z0-9_]*`?(?:\s+/\s+`?[a-z][A-Za-z0-9_]*`?){2,}", re.IGNORECASE), "lifecycle method-name run (init / execute / suspend / teardown)"),
    ],
    # L2 — Runtime sequences: sequence diagrams, race winner/loser narratives,
    # CAS-ordering ("CAS WINS"/"LOSES", "post-CAS re-read", "no-op").
    "L2-runtime-sequence": [
        (re.compile(r"\bsequenceDiagram\b"), "mermaid sequenceDiagram fence"),
        (re.compile(r"\bCAS\s+(?:WINS|LOSES|WIN|LOSE)\b", re.IGNORECASE), "CAS winner/loser race narrative"),
        (re.compile(r"\bpost-CAS\s+re-?read\b", re.IGNORECASE), "post-CAS re-read race ordering"),
        (re.compile(r"\bcancel-race\b", re.IGNORECASE), "cancel-race runtime ordering"),
        (re.compile(r"\(\s*CAS\s+no-?op\s*\)", re.IGNORECASE), "(CAS no-op) transition ordering"),
        (re.compile(r"\bwinner\s*/\s*loser\b", re.IGNORECASE), "winner/loser sequence"),
    ],
    # L3 — SQL / RLS / GUC / persistence: SET LOCAL, ON CONFLICT, WHERE ...
    # NOT IN, RLS policy wiring, migration-file names, CHECK-constraint enums.
    "L3-sql-rls-persistence": [
        (re.compile(r"\bSET\s+LOCAL\b", re.IGNORECASE), "SET LOCAL GUC statement"),
        (re.compile(r"\bON\s+CONFLICT\b", re.IGNORECASE), "SQL ON CONFLICT clause"),
        (re.compile(r"\bWHERE\s+status\s+NOT\s+IN\b", re.IGNORECASE), "WHERE status NOT IN (...) CAS predicate"),
        (re.compile(r"\bDO\s+NOTHING\b", re.IGNORECASE), "SQL DO NOTHING upsert"),
        (re.compile(r"\bCHECK\s+constraint\b", re.IGNORECASE), "CHECK-constraint enum detail"),
        (re.compile(r"\bRLS\s+(?:policy|policies|migration|layer|coverage|SELECT)\b", re.IGNORECASE), "RLS policy/migration wiring"),
        (re.compile(r"\benable\s+(?:Postgres\s+)?RLS\b", re.IGNORECASE), "enable Postgres RLS"),
        (re.compile(r"\bapp\.tenant_id\b"), "app.tenant_id GUC key"),
        (re.compile(r"\bV\??\d*_*_[A-Za-z0-9_]*\.sql\b"), "Flyway migration-file name (V__*.sql)"),
        (re.compile(r"\bidempotency_dedup\b"), "named idempotency_dedup table"),
    ],
    # L4 — HTTP status / route-verb / header behaviour: bare 2xx/4xx/5xx status
    # codes, "POST ... not DELETE", header parse/emit, route x verb tables.
    "L4-http-status-route-verb": [
        (re.compile(r"(?<![\w./-])(?:200|201|400|403|404|409|422|500)(?![\w%./-])"), "concrete HTTP status code"),
        (re.compile(r"\b(?:not|never)\s+DELETE\b", re.IGNORECASE), "route-verb semantics (POST not DELETE)"),
        (re.compile(r"\bPOST\s+/v\d+/"), "concrete route x verb (POST /vN/...)"),
        (re.compile(r"\bGET\s+/v\d+/"), "concrete route x verb (GET /vN/...)"),
        (re.compile(r"\b(?:Idempotency-Key|X-Tenant-Id|traceparent|traceresponse)\b"), "named HTTP header parse/emit"),
        (re.compile(r"\billegal_state_transition\b|\bidempotency_conflict\b|\bidempotency_body_drift\b|\btenant_mismatch\b"), "wire error-code token"),
    ],
    # L5 — Filter ordering: numeric @Order/order values, chain-position rules,
    # tie-break-by-Class.getName(), LIFO unwind.
    "L5-filter-ordering": [
        (re.compile(r"@Order\b"), "@Order annotation ordering"),
        (re.compile(r"\border\s+\d+\b", re.IGNORECASE), "numeric filter order value"),
        (re.compile(r"\bClass\.getName\(\)"), "Class.getName() tie-break"),
        (re.compile(r"\bLIFO\s+unwind\b", re.IGNORECASE), "LIFO unwind ordering"),
        (re.compile(r"\bfilter\s+chain\s+order\b", re.IGNORECASE), "filter chain order rule"),
        (re.compile(r"\bregistration\s+order\b", re.IGNORECASE), "registration-order dispatch rule"),
    ],
    # L6 — Wire formats: OTLP/HTTP, attribute namespaces gen_ai.*/langfuse.*,
    # buffer sizes + overflow strategy, sampling rates, traceparent grammar.
    "L6-wire-format": [
        (re.compile(r"\bOTLP/HTTP\b", re.IGNORECASE), "OTLP/HTTP wire encoding"),
        (re.compile(r"\bgen_ai\.\*|\blangfuse\.\*"), "telemetry attribute namespace"),
        (re.compile(r"\bDROP_OLDEST\b"), "buffer overflow strategy DROP_OLDEST"),
        (re.compile(r"\bsampling\b.*\b\d+\s*%", re.IGNORECASE), "numeric sampling rate"),
        (re.compile(r"\b\d+\s*events?\b.*\boverflow\b", re.IGNORECASE), "bounded buffer size + overflow"),
        (re.compile(r"\b00-<trace_id>-<span_id>-\d+\b"), "traceparent/traceresponse wire grammar"),
        (re.compile(r"\bW3C\s+version-\d+\s+traceparent\b", re.IGNORECASE), "W3C traceparent header grammar"),
    ],
    # L7 — Method signatures: parameter lists / return types / JVM shapes,
    # enumerated SPI method lists, interface-vs-record classification, forbidden
    # method names, canonical field names. (Distinct from D1: a one-line SPI
    # IDENTITY is allowed; a parameter/return inventory is not. Distinct from D3:
    # an "X is forbidden (Rule R-...)" structural CONSTRAINT is a defensible
    # enforcer citation; only a forbidden METHOD NAME — `name()` ... forbidden —
    # is the L7 leak, so the probe requires the method-call token, not bare
    # "is forbidden".)
    # Widened (convergence scan round 3): the L7 leak is a method/return SHAPE,
    # which survives equally in a colon-return form (`deadline() : Instant`, the
    # Pascal-style signature notation) and in a record-component constructor whose
    # args are FIELD names rather than JVM types (`ResiliencePolicy(cbName,
    # retryName, tlName)`). The first probe keyed only on the `->` arrow and the
    # JVM-typed-args inventory keyed only on capitalised Type tokens, so both
    # surface forms evaded the regex while the semantic verdict forbids them.
    # Widened (convergence scan round 4): the SAME method-signature SHAPE survives
    # in the JVM-descriptor surface form the Frame Cards adopted
    # (`validate(Lcom/.../RunStatus;Lcom/.../RunStatus;)V`, `isTerminal(...)Z`,
    # `cancel(...)Lorg/.../ResponseEntity;`). The arrow and colon-return probes
    # above recognise only the two human-readable notations the L0 corpus used, so a
    # raw descriptor -- slash-separated `L<fqn>;` reference tokens, primitive/void/
    # array return codes abutting the close paren -- evaded every L7 probe even
    # though a re-authored descriptor inventory is exactly the L7 leak. The
    # descriptor probe below recognises that surface; the post-match carve-out
    # (_is_factid_method_citation, applied in scan_document / _scan_text_for_leaks)
    # then spares the SANCTIONED by-fact-id citation form (ADR-0161 section 4: a
    # method is CITED as the class fact's `public_methods[]` entry --
    # `code-symbol/<fqn> :: <descriptor>` or `code-symbol/<fqn>#<descriptor>`), so the
    # frame-card `Entry method` / participant rows that quote a descriptor NEXT TO
    # its fact id stay in-layer, while a descriptor transcribed WITHOUT a fact id
    # (re-authoring a signature inventory) is a finding. This is the L7 analogue of
    # the L8 kebab fact-id widening: the by-fact-id citation is defensible regardless
    # of which notation the descriptor uses, but an unbacked descriptor has no
    # backstop without it.
    "L7-method-signature": [
        (re.compile(r"\b[A-Za-z_]\w*\([^)]*\)\s*->\s*[A-Z]\w+"), "method signature args -> ReturnType"),
        (re.compile(r"\b[A-Za-z_]\w*\(\s*\)\s*:\s*[A-Z]\w+"), "colon-return signature name() : ReturnType"),
        (re.compile(r"\bclassified\s+as\s+(?:an?\s+)?[`'\"]?(?:interface|record)\b", re.IGNORECASE), "interface-vs-record classification"),
        (re.compile(r"\b[A-Za-z_]\w*\(\)\s+(?:is|are)\s+forbidden\b", re.IGNORECASE), "forbidden method-name rule (name() is forbidden)"),
        (re.compile(r"\bcanonical\s+field\s+name\b", re.IGNORECASE), "canonical field-name detail"),
        (re.compile(r"\bsubset\s+of\s+the\s+canonical\s+interface\b", re.IGNORECASE), "enumerated SPI method-subset rule"),
        # A parenthesised parameter list with >=2 JVM-typed args, or a single
        # JVM-typed arg paired with a return arrow — a parameter/return inventory,
        # not a one-line identity.
        (re.compile(r"\(\s*(?:UUID|Instant|String|List<[^>]+>|Map<[^>]+>|StateDelta|TaskMetadata|InjectedContext|ChildFailurePolicy|SessionContext)\b[^)]*,[^)]*\)"), "JVM-typed parameter inventory (multi-arg)"),
        # A record-component constructor whose args are >=3 lowercase camelCase
        # FIELD names (no JVM Type token) — a record-shape inventory the JVM-typed
        # probe above cannot see (`Policy(cbName, retryName, tlName)`). Anchored on
        # a PascalCase type name immediately followed by the paren so a prose list
        # in plain `(a, b, c)` parentheses is not swept in.
        (re.compile(r"\b[A-Z][A-Za-z0-9_]*\(\s*[a-z][A-Za-z0-9_]*\s*,\s*[a-z][A-Za-z0-9_]*\s*,\s*[a-z][A-Za-z0-9_]*[^)]*\)"), "record-component constructor (field-name args)"),
        # A JVM method descriptor: name(<descriptor-args>)<descriptor-return> whose
        # args carry slash-separated `L<fqn>;` reference tokens / primitive / array
        # descriptors, OR whose return is a JVM return code (`)V`, `)Z`, `)I`, ...,
        # `)L<fqn>;`, `)[...`) abutting the close paren. None of these surfaces in a
        # human-readable Java rendering, so the arrow/colon probes are blind to it.
        # The by-fact-id citation form (a descriptor quoted next to its
        # `code-symbol/<fqn>` fact id) is spared post-match by
        # _is_factid_method_citation; this probe fires on a descriptor with NO fact id.
        (re.compile(r"\b[A-Za-z_]\w*\((?:\[*[VZIJDFCBS]|\[*L[\w/$]+;|\s)*\)(?:\[*[VZIJDFCBS]\b|\[*L[\w/$]+;)"), "JVM method-descriptor inventory (name(...)<descriptor>)"),
    ],
    # L8 — Test-class inventories: named test classes carrying their ASSERTED
    # runtime behaviour, embedded in layer prose — a test catalogue. NOT an
    # ArchUnit enforcer citation: an architecture-test (`*ArchTest` / `*PurityTest`)
    # or a test named inside an "enforced by ..." / "ArchUnit (...)" clause is a
    # D3-defensible MECHANISM citation and is filtered out post-match (see
    # _is_d3_enforcer_citation). The positive signal is a test class enumerated
    # as inventory: a markdown table row, a bullet-list entry, or a test name
    # paired with a behaviour verb.
    # Widened (convergence scan round 4): the same test-INVENTORY leak survives in
    # the kebab fact-id surface form. Frame cards (ADR-0161 §4) cite tests as
    # `test/<kebab-fqn>` fact IDs (`test/com-huawei-ascend-...-engineportsignature
    # noregressiontest`), NOT as PascalCase `FooIT` / `FooTest` tokens, so every
    # probe above (each keyed on `[A-Z]\w+(?:IT|Test|Spec)`) skips a kebab bullet
    # by surface form. A single `test/<fqn>` fact-id paired with a one-line
    # behaviour gloss, under a FunctionPoint-Mapping or "Tests anchoring the
    # behaviour" Verification anchor, is the SANCTIONED per-anchor citation form
    # (ADR-0161 §4 — parallel to the method-descriptor citation), NOT a catalogue;
    # the kebab probe below targets only the genuine inventory shape (>=3 kebab
    # `test/<fqn>` tokens crammed into one line), mirroring the PascalCase
    # `_D3_CITATION_MAX_TESTS = 2` threshold where a third enumerated test makes a
    # line a catalogue. A one-token-per-bullet kebab citation (one `test/<fqn>` on
    # the line) never has three tokens, so the sanctioned form stays in-layer.
    "L8-test-class-inventory": [
        # Markdown table row whose cells name an integration/unit/spec test.
        (re.compile(r"^\s*\|.*`[A-Z]\w+(?:IT|Test|Spec)`"), "test-inventory table row (named test in a table)"),
        # Bullet-list entry naming a test class (FQN or simple name).
        (re.compile(r"^\s*[-*]\s+`?(?:[a-z][\w.]*\.)?[A-Z]\w+(?:IT|Test|Spec)`?\s*$"), "test-inventory bullet (enumerated test class)"),
        # A test class paired with a behaviour clause (em-dash/colon + verb).
        (re.compile(r"\b[A-Z]\w+(?:IT|Test|Spec)\b[^\n]{0,40}?(?:[-—:]\s*)(?:asserts?|verif(?:y|ies)|checks?|arms?|covers?|ensures?|exercises?|proves?)\b", re.IGNORECASE), "named test + asserted-behaviour clause"),
        (re.compile(r"\b@(?:Test|SpringBootTest|DataR2dbcTest|WebFluxTest)\b"), "test annotation in prose"),
        # Kebab fact-id INVENTORY: three-or-more `test/<kebab-fqn>` fact IDs crammed
        # into one line — a test catalogue in the fact-id surface, not a per-anchor
        # citation. The post-match carve-out (_is_d3_enforcer_citation) still spares
        # a kebab line whose tokens are all ArchUnit architecture-tests or which is a
        # single mechanism citation; the threshold here makes a 3rd kebab test the
        # catalogue signal (parallel to the PascalCase side).
        (re.compile(r"(?:`?test/[a-z0-9][a-z0-9-]*`?[^\n]*?){3,}"), "kebab fact-id test inventory (>=3 test/<fqn> in one line)"),
    ],
}

# A line whose only test-ish token is a D3-defensible enforcer citation MUST NOT
# be flagged L8: the verdict keeps "citing an ArchUnit enforcer [or gate rule] as
# the mechanism" at L0/L1 (policy D3, ADR-0159). Three shapes mark such a citation:
#   * the token is an ArchUnit architecture-test name (suffix ArchTest /
#     ArchUnitTest / PurityTest) — these ARE enforcers, not behaviour catalogues;
#   * the test token sits inside an explicit mechanism clause ("enforced by ...",
#     "ArchUnit `...`", "(enforcer E<n>)"); or
#   * a numbered/prose CONSTRAINT names its single locked enforcing test under
#     the Rule R-C.a Code-as-Contract discipline ("Enforced by integration `X`
#     ... class FQN locked here per Rule R-C.a") — the test is the constraint's
#     enforcement mechanism, named once, NOT a behaviour catalogue. This stays
#     D3-defensible even when the enforcing test is an *IT (not an *ArchTest):
#     the verdict's keep-list is about the ROLE (mechanism citation), not the
#     test's harness flavour.
_D3_ARCHUNIT_TOKEN_RE = re.compile(r"\b[A-Z]\w*(?:ArchTest|ArchUnitTest|PurityTest)\b")
_D3_MECHANISM_CLAUSE_RE = re.compile(r"enforced by|ArchUnit|\(enforcer\s+E\d+\)", re.IGNORECASE)
# Explicit enforcement / FQN-lock clause that marks a constraint's enforcing-test
# CITATION (Rule R-C.a Code-as-Contract), distinct from a behaviour inventory.
_D3_ENFORCEMENT_CLAUSE_RE = re.compile(
    r"enforced by|verified by|asserted by|locked here per rule|per rule\s+r-c\.a|class fqn locked",
    re.IGNORECASE,
)
# A non-ArchUnit behaviour test (e.g. RunHttpContractIT, S2cCallbackRoundTripIT)
# whose presence — in INVENTORY quantity (3+) or INVENTORY structure (a markdown
# table row / a test-leading bullet) — means the line is a genuine catalogue, not
# a single mechanism citation.
_NON_ARCHUNIT_TEST_TOKEN_RE = re.compile(r"\b[A-Z]\w+(?:IT|Test|Spec)\b")
# Kebab-surface equivalents (convergence scan round 4). Frame cards cite tests as
# `test/<kebab-fqn>` fact IDs, so the same ArchUnit-vs-behaviour split must hold on
# the kebab surface for the carve-out to be symmetric: a kebab fact-id whose tail
# is an ArchUnit architecture-test (`...orchestrationspiarchtest`,
# `...spipuritygeneralizedarchtest`) is the kebab ArchUnit token; any other
# `test/<fqn>` is a kebab behaviour token.
_D3_ARCHUNIT_KEBAB_TOKEN_RE = re.compile(r"test/[a-z0-9-]*(?:archtest|archunittest|puritytest)\b")
_ANY_KEBAB_TEST_TOKEN_RE = re.compile(r"test/[a-z0-9][a-z0-9-]*\b")
# Inventory STRUCTURE: a markdown table row, or a bullet whose leading content is
# a test class. These are the catalogue shapes the L8 leak targets; a mechanism
# clause does NOT redeem them. Covers both the PascalCase and the kebab fact-id
# surface (a `- test/<fqn> — ...` bullet is a test-leading bullet too).
_TEST_INVENTORY_STRUCTURE_RE = re.compile(
    r"^\s*\|.*`?[A-Z]\w+(?:IT|Test|Spec)`?"          # table row naming a test (PascalCase)
    r"|^\s*\|.*`?test/[a-z0-9][a-z0-9-]*`?"           # table row naming a test (kebab fact-id)
    r"|^\s*[-*]\s+`?(?:[a-z][\w.]*\.)?[A-Z]\w+(?:IT|Test|Spec)`?\b"  # test-leading bullet (PascalCase)
    r"|^\s*[-*]\s+`?test/[a-z0-9][a-z0-9-]*`?\b",     # test-leading bullet (kebab fact-id)
)
# At most this many behaviour-test tokens may appear in a mechanism CITATION
# before the line is treated as an inventory. A constraint may name its primary
# enforcing test plus one deferred companion (e.g. a W2 negative-emission test),
# so the threshold is 2; a third enumerated test makes it a catalogue.
_D3_CITATION_MAX_TESTS = 2


def _is_d3_enforcer_citation(line: str) -> bool:
    """Return True when an L8 match on ``line`` is a D3-defensible enforcer citation.

    Three D3 shapes are spared (see the module comment above):
      1. every enumerated test token is an ArchUnit architecture-test;
      2. the line carries a mechanism clause and enumerates NO behaviour test
         (pure ArchUnit / enforcer-id citation); or
      3. a prose constraint cites its enforcing test(s) via an explicit
         enforcement / FQN-lock clause (Rule R-C.a), the line is NOT an inventory
         STRUCTURE (table row / test-leading bullet), and it names at most
         ``_D3_CITATION_MAX_TESTS`` behaviour tests.

    A genuine integration-test INVENTORY — a table of tests, a bullet list of
    tests, or three-plus behaviour tests enumerated in one line — stays a leak
    even beside an "enforced by" clause.

    The same three shapes apply on the kebab `test/<fqn>` fact-id surface
    (convergence scan round 4): a kebab fact-id whose tail is an ArchUnit
    architecture-test is the kebab ArchUnit token, and the BEHAVIOUR-token set is
    the union of PascalCase behaviour tokens and kebab behaviour tokens (every
    `test/<fqn>` that is NOT a kebab ArchUnit token). A kebab line of all-ArchUnit
    fact-ids is Shape 1; a 3+ kebab-behaviour inventory exceeds
    ``_D3_CITATION_MAX_TESTS`` and stays a leak.
    """
    # Behaviour tokens on both surfaces. Kebab behaviour tokens are every
    # `test/<fqn>` minus the kebab ArchUnit fact-ids.
    pascal_tokens = _NON_ARCHUNIT_TEST_TOKEN_RE.findall(line)
    kebab_all = _ANY_KEBAB_TEST_TOKEN_RE.findall(line)
    kebab_archunit = set(_D3_ARCHUNIT_KEBAB_TOKEN_RE.findall(line))
    kebab_behaviour = [t for t in kebab_all if t not in kebab_archunit]
    behaviour_tokens = pascal_tokens + kebab_behaviour
    has_archunit = bool(
        _D3_ARCHUNIT_TOKEN_RE.search(line) or kebab_archunit
    )
    if not behaviour_tokens:
        # No behaviour-test token on either surface (e.g. only a `@Test` annotation
        # match, or a line of nothing but ArchUnit fact-ids / a mechanism clause) —
        # defer to the mechanism / ArchUnit signals.
        return has_archunit or bool(_D3_MECHANISM_CLAUSE_RE.search(line))
    # Shape 1: every enumerated test token (both surfaces) is an ArchUnit
    # architecture-test -> D3. Behaviour tokens by construction exclude the kebab
    # ArchUnit fact-ids, so a non-empty behaviour set means at least one non-ArchUnit
    # test is present and Shape 1 cannot hold.
    pascal_archunit = set(_D3_ARCHUNIT_TOKEN_RE.findall(line))
    if has_archunit and not kebab_behaviour and all(t in pascal_archunit for t in pascal_tokens):
        return True
    # Shape 3: a constraint's enforcing-test citation (Rule R-C.a). Spared only
    # when it is NOT an inventory structure AND names few tests AND carries an
    # explicit enforcement/FQN-lock clause. A 3+ kebab inventory (the new probe's
    # trigger) exceeds the threshold and is never redeemed here.
    if (
        _D3_ENFORCEMENT_CLAUSE_RE.search(line)
        and not _TEST_INVENTORY_STRUCTURE_RE.search(line)
        and len(behaviour_tokens) <= _D3_CITATION_MAX_TESTS
    ):
        return True
    return False


# A line whose L7 match is a JVM method DESCRIPTOR quoted as a by-fact-id citation
# MUST NOT be flagged: ADR-0161 section 4 sanctions citing a method via the class
# fact's `public_methods[]` entry, and the Frame Cards adopt exactly that form --
# the descriptor sits NEXT TO its `code-symbol/<fqn>` fact id, either as
# `code-symbol/<fqn> :: <descriptor>` (double-colon continuation) or
# `code-symbol/<fqn>#<descriptor>` (hash-joined). Such a line is a defensible
# fact citation, not a re-authored signature inventory, so the descriptor probe's
# hit is suppressed post-match (parallel to the L8 _is_d3_enforcer_citation
# carve-out). A descriptor transcribed WITHOUT an adjacent fact id is NOT spared
# and stays a finding -- that is the coverage backstop this widening adds. The
# Fact-ID-citation README row that DEFINES the convention (it names the
# `public_methods[]` entry and shows the descriptor as the example) is likewise a
# citation context, not an inventory, so the `public_methods[]` token also spares.
_FACTID_METHOD_CITATION_RE = re.compile(
    r"code-symbol/[\w/-]+(?:`?\s*::\s*`?|#)[A-Za-z_]\w*\("  # descriptor next to a code-symbol fact id
    r"|public_methods\[\]"  # the convention-defining row (cites the public_methods[] entry form)
)


def _is_factid_method_citation(line: str) -> bool:
    """Return True when an L7 descriptor match on ``line`` is a by-fact-id citation.

    The Frame Card method citations quote a JVM descriptor adjacent to its
    ``code-symbol/<fqn>`` fact id (``... :: <descriptor>`` or ``...#<descriptor>``);
    ADR-0161 section 4 sanctions that form (cite a method as the class fact's
    ``public_methods[]`` entry). A descriptor with NO adjacent fact id -- a
    re-authored signature inventory -- is NOT a citation and stays a finding.
    """
    return bool(_FACTID_METHOD_CITATION_RE.search(line))


# ===========================================================================
# Document discovery + changed-file scoping.
# ===========================================================================
def discover_documents(root: Path) -> list[Path]:
    """Return every scanned L0/L1 markdown document, sorted, template excluded."""
    out: list[Path] = []
    for layer_root in SCANNED_LAYER_ROOTS.values():
        base = root / layer_root
        if not base.is_dir():
            continue
        for md in base.rglob("*.md"):
            rel = _rel(md, root)
            if any(frag in "/" + rel for frag in EXCLUDED_PATH_FRAGMENTS):
                continue
            out.append(md)
    return sorted(out, key=lambda p: _rel(p, root))


def discover_view_dsls(root: Path) -> list[Path]:
    """Return the C4 4+1 view fragments that exist on disk, in roster order.

    The roster ``SCANNED_VIEW_DSLS`` is the closed set of view files mounted into
    ``architecture/workspace.dsl``. A roster entry whose file is absent on disk is
    skipped (the workspace may not mount every view in a partial checkout); the
    file-type non-vacuity guard in ``main`` distinguishes "the whole roster is
    absent" (acceptable on a clone that lacks ``architecture/views/``) from "the
    roster directory exists yet contributed nothing" (a rename/scope drift —
    fail closed).
    """
    out: list[Path] = []
    for rel in SCANNED_VIEW_DSLS:
        candidate = root / rel
        if candidate.is_file():
            out.append(candidate)
    return out


def layer_of(rel_path: str) -> str | None:
    """Return the layer id (L0/L1) a repo-relative path belongs to, or None.

    Covers both renderings: a markdown path under a scanned L0/L1 document root,
    and a C4 view fragment whose layer the ``SCANNED_VIEW_DSLS`` roster pins.
    """
    norm = rel_path.replace("\\", "/")
    view_layer = SCANNED_VIEW_DSLS.get(norm)
    if view_layer is not None:
        return view_layer
    for layer, layer_root in SCANNED_LAYER_ROOTS.items():
        if norm == layer_root or norm.startswith(layer_root + "/"):
            return layer
    return None


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


def _changed_names(root: Path, base: str) -> tuple[set[str] | None, str]:
    """Return the repo-relative paths changed vs ``base``, or ``(None, reason)``.

    Scope = (committed diff merge-base...HEAD) UNION (uncommitted tracked
    changes) UNION (untracked files under the scanned L0/L1 document roots AND
    the view-fragment directory). A ``None`` return means git could not resolve
    the base ref (shallow clone without it, or not a git repo); the caller fails
    closed in a blocking mode. Shared by both rendering scopes so the markdown
    and the view-DSL changed sets are derived from one git pass.
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
    # Untracked-file scan over both rendering homes (a brand-new L0/L1 doc or a
    # brand-new view fragment is in scope before it is first committed).
    untracked_roots = list(SCANNED_LAYER_ROOTS.values()) + ["architecture/views"]
    for scan_root in untracked_roots:
        rc, out = _git_run(["ls-files", "--others", "--exclude-standard", scan_root], root)
        if rc == 0:
            names.update(line.strip() for line in out.splitlines() if line.strip())
    return names, ""


def changed_documents(root: Path, base: str) -> tuple[list[Path] | None, str]:
    """Return the scanned L0/L1 markdown documents changed vs ``base``.

    ``(None, reason)`` when git could not resolve the base ref; the caller fails
    closed in a blocking mode.
    """
    names, reason = _changed_names(root, base)
    if names is None:
        return None, reason
    selected: list[Path] = []
    for name in sorted(names):
        norm = name.replace("\\", "/")
        if layer_of(norm) is None:
            continue
        if not norm.endswith(".md"):
            continue
        if any(frag in "/" + norm for frag in EXCLUDED_PATH_FRAGMENTS):
            continue
        candidate = root / norm
        if candidate.is_file():
            selected.append(candidate)
    return selected, ""


def changed_view_dsls(root: Path, base: str) -> tuple[list[Path] | None, str]:
    """Return the C4 view fragments changed vs ``base`` (roster-scoped).

    Only files in the ``SCANNED_VIEW_DSLS`` roster are eligible — a changed
    ``.dsl`` outside the roster (a generated fragment, a feature fragment) is not
    this helper's surface. ``(None, reason)`` when the base ref is unresolvable.
    """
    names, reason = _changed_names(root, base)
    if names is None:
        return None, reason
    selected: list[Path] = []
    for name in sorted(names):
        norm = name.replace("\\", "/")
        if norm not in SCANNED_VIEW_DSLS:
            continue
        candidate = root / norm
        if candidate.is_file():
            selected.append(candidate)
    return selected, ""


# ===========================================================================
# Scanning.
# ===========================================================================
@dataclass
class Leak:
    """One detected leaked-category hit in a layer document."""

    rel_path: str
    layer: str
    line_no: int
    category_id: str
    label: str
    excerpt: str


def _strip_inline_code_noise(line: str) -> str:
    """Return the line as-is.

    We intentionally do NOT strip code spans: the verdict treats inlined
    mechanism (a backticked ``SET LOCAL`` statement, a ``sequenceDiagram`` fence,
    a backticked ``@Order``) as exactly the leak. Kept as a single hook so the
    policy on code spans lives in one place if it ever needs to change.
    """
    return line


def scan_document(path: Path, root: Path, leaked: dict[str, Category]) -> list[Leak]:
    """Scan one L0/L1 document line-by-line for leaked-category triggers.

    A line is reported at most once — under the first leaked category (in policy
    order) whose probe matches — to avoid double-counting a block that mixes
    several leaked categories. The per-category allow-list suppression is applied
    later by the caller (so the raw scan stays policy-pure and testable).
    """
    rel = _rel(path, root)
    layer = layer_of(rel) or "?"
    try:
        text = path.read_text(encoding="utf-8")
    except OSError:
        # An unreadable in-scope document is itself a problem; surface it as a
        # synthetic finding rather than skipping silently.
        return [
            Leak(
                rel_path=rel,
                layer=layer,
                line_no=0,
                category_id="<unreadable>",
                label="document could not be read",
                excerpt="",
            )
        ]

    leaks: list[Leak] = []
    for line_no, raw_line in enumerate(text.splitlines(), start=1):
        line = _strip_inline_code_noise(raw_line)
        if not line.strip():
            continue
        for category_id in leaked:  # dict preserves policy order
            matched_label = None
            for pattern, label in TRIGGERS.get(category_id, ()):  # type: ignore[arg-type]
                if pattern.search(line):
                    matched_label = label
                    break
            if matched_label is None:
                continue
            # D3-defensible carve-out: an L8 match that is really an ArchUnit /
            # enforcer MECHANISM citation is in-layer at L0/L1 — skip it and let a
            # genuinely-leaked lower-priority category (if any) still report.
            if category_id == "L8-test-class-inventory" and _is_d3_enforcer_citation(line):
                continue
            # By-fact-id carve-out: an L7 JVM-descriptor match that is a method
            # cited next to its `code-symbol/<fqn>` fact id (ADR-0161 section 4) is
            # a defensible fact citation, not a re-authored signature inventory.
            if category_id == "L7-method-signature" and _is_factid_method_citation(line):
                continue
            excerpt = line.strip()
            if len(excerpt) > 160:
                excerpt = excerpt[:157] + "..."
            leaks.append(
                Leak(
                    rel_path=rel,
                    layer=layer,
                    line_no=line_no,
                    category_id=category_id,
                    label=matched_label,
                    excerpt=excerpt,
                )
            )
            break  # one (leaked, non-exempt) category per line
    return leaks


def _scan_text_for_leaks(
    text: str,
    rel: str,
    layer: str,
    line_no: int,
    leaked: dict[str, Category],
) -> Leak | None:
    """Probe one already-extracted prose string; return its first leak or None.

    Shared classification core for both renderings: probe each leaked category in
    policy order, honour the L8 D3 enforcer-citation carve-out, and report at most
    one (leaked, non-exempt) category — anchored at ``line_no``. ``scan_document``
    keeps its own per-line loop (it reports many lines per file); the view-DSL
    scan uses this on each extracted ``description`` value.
    """
    if not text.strip():
        return None
    for category_id in leaked:  # dict preserves policy order
        matched_label = None
        for pattern, label in TRIGGERS.get(category_id, ()):  # type: ignore[arg-type]
            if pattern.search(text):
                matched_label = label
                break
        if matched_label is None:
            continue
        if category_id == "L8-test-class-inventory" and _is_d3_enforcer_citation(text):
            continue
        if category_id == "L7-method-signature" and _is_factid_method_citation(text):
            continue
        excerpt = text.strip()
        if len(excerpt) > 160:
            excerpt = excerpt[:157] + "..."
        return Leak(
            rel_path=rel,
            layer=layer,
            line_no=line_no,
            category_id=category_id,
            label=matched_label,
            excerpt=excerpt,
        )
    return None


def scan_view_dsl(path: Path, root: Path, leaked: dict[str, Category]) -> list[Leak]:
    """Scan one C4 view fragment's ``description`` strings for leaked categories.

    Only the QUOTED value of each ``description "<text>"`` line is probed (the
    free-text behavioural narrative the verdict governs); the structural DSL
    tokens around it are never scanned. Each description is anchored at the source
    line of its ``description`` key, so a grandfather row can pin it by line range
    exactly as it pins a markdown leak. A view fragment carries at most a handful
    of descriptions, each reported at most once (first leaked, non-exempt
    category). The per-row allow-list suppression is applied later by the caller.
    """
    rel = _rel(path, root)
    layer = layer_of(rel) or "?"
    try:
        text = path.read_text(encoding="utf-8")
    except OSError:
        return [
            Leak(
                rel_path=rel,
                layer=layer,
                line_no=0,
                category_id="<unreadable>",
                label="view fragment could not be read",
                excerpt="",
            )
        ]

    leaks: list[Leak] = []
    for line_no, raw_line in enumerate(text.splitlines(), start=1):
        m = _DSL_DESCRIPTION_RE.match(raw_line)
        if not m:
            continue
        description = m.group(1)
        leak = _scan_text_for_leaks(description, rel, layer, line_no, leaked)
        if leak is not None:
            leaks.append(leak)
    return leaks


def suppressing_row(leak: Leak, policy: Policy, today: datetime.date) -> Violation | None:
    """Return the open grandfather row that tolerates ``leak``, or None.

    A row suppresses a leak when ALL hold:
      * the row's ``file`` equals the leak's document (repo-relative path), and
      * the row's ``layer`` equals the leak's layer (defence in depth — the file
        match already implies the layer), and
      * the row cites the leak's leaked category, and
      * the row's ``locus`` COVERS the leak's line number — for an anchored row,
        the line MUST fall inside one of its enumerated ranges; an anchorless
        ``row-level pass deferred`` row covers every line by construction, and
      * the row is still open (sunset_date is today or later, UTC).

    The locus condition is what stops a row from tolerating an unrelated
    same-category leak elsewhere in the same file: ``§4 #20`` (lines 520-535) is
    the adjudicated DFA-table leak, so it must NOT redeem an L4 status code in a
    telemetry constraint at line 999. Without it, every same-category leak in a
    file collapses under whichever row first declares that category for the file.

    Match preference. When several open rows match file + category, an ANCHORED
    row whose range covers the line is preferred over an anchorless fallback, so
    the grandfathered report cites the row that genuinely enumerated the leak.

    Expired rows do not suppress (the migration deadline has passed); unknown or
    malformed sunsets are treated as expired by ``Violation.is_open``.
    """
    anchorless_fallback: Violation | None = None
    for row in policy.violations:
        if row.file != leak.rel_path:
            continue
        if row.layer and row.layer != leak.layer:
            continue
        if leak.category_id not in row.categories:
            continue
        if not row.is_open(today):
            continue
        if not row.covers_line(leak.line_no):
            # Anchored row whose ranges do not reach this line: this is not the
            # row's adjudicated leak, so it grants no tolerance here.
            continue
        if row.locus_anchored:
            return row
        # Remember the first open anchorless row but keep scanning for a more
        # specific anchored row that actually enumerates this line.
        if anchorless_fallback is None:
            anchorless_fallback = row
    return anchorless_fallback


# ===========================================================================
# CLI.
# ===========================================================================
def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Layer purity — detect L2/code detail leaked into L0/L1 "
        "architecture prose, honouring the dated grandfather allow-list "
        "(ADR-0159).",
    )
    parser.add_argument(
        "--mode",
        choices=("advisory", "changed-files-blocking", "full-blocking"),
        default="advisory",
        help="advisory (report, never block); changed-files-blocking (block only "
        "on changed L0/L1 docs); full-blocking (block on any). Default: advisory.",
    )
    parser.add_argument(
        "--base",
        default="origin/main",
        help="Base ref for changed-files-blocking scope. Default: origin/main.",
    )
    parser.add_argument(
        "--repo",
        default=None,
        help="Repository root. Defaults to script-derived root.",
    )
    return parser.parse_args(argv)


def main(argv: list[str]) -> int:
    args = parse_args(argv)
    root = Path(args.repo) if args.repo else repo_root()
    if not root.is_dir():
        print(f"layer-purity: --repo {root} is not a directory", file=sys.stderr)
        return 2

    policy, config_errors = load_policy(root)
    if policy is None:
        for err in config_errors:
            print(f"layer-purity config error: {err}", file=sys.stderr)
        return 2

    leaked = policy.leaked_categories()
    if not leaked:
        print(
            f"layer-purity config error: {POLICY_REL} defines no leaked categories",
            file=sys.stderr,
        )
        return 2
    # Every leaked category MUST have a trigger probe set, else the gate is
    # silently blind to it (non-vacuity guard for an auto-discovering rule).
    missing_probes = [cid for cid in leaked if not TRIGGERS.get(cid)]
    if missing_probes:
        print(
            "layer-purity config error: no trigger probes defined for leaked "
            f"categ/ies {sorted(missing_probes)} (gate would be blind to them)",
            file=sys.stderr,
        )
        return 2

    all_documents = discover_documents(root)
    # Non-vacuity guard: this rule auto-discovers its inputs by globbing the L0/L1
    # roots. If it discovers zero documents the scan is vacuous and a path/format
    # drift would pass silently — fail closed.
    if not all_documents:
        print(
            "layer-purity config error: discovered zero L0/L1 documents under "
            f"{sorted(SCANNED_LAYER_ROOTS.values())} (path drift? scan would be vacuous)",
            file=sys.stderr,
        )
        return 2

    all_view_dsls = discover_view_dsls(root)
    # File-TYPE non-vacuity guard for the second rendering. The markdown guard
    # above cannot catch a whole authority FILE-TYPE going out of scope (it only
    # fires on zero .md docs). If the view-fragment directory exists on disk yet
    # NONE of the roster files reached discovery, the rendering silently dropped
    # out of the gate (a rename / a workspace re-org) — exactly the gap that let a
    # .md-cleaned leak survive in its .dsl twin. Fail closed. (A clone that lacks
    # architecture/views/ entirely is acceptable — there is no rendering to scan —
    # so the guard keys on the directory being present.)
    views_dir = root / "architecture/views"
    if views_dir.is_dir() and not all_view_dsls:
        print(
            "layer-purity config error: architecture/views/ exists but none of the "
            f"rostered view fragments {sorted(SCANNED_VIEW_DSLS)} were discovered "
            "(view-rendering scope drift? a .md-cleaned leak could survive in its "
            ".dsl twin unseen) — fail closed",
            file=sys.stderr,
        )
        return 2

    if args.mode == "changed-files-blocking":
        scoped, reason = changed_documents(root, args.base)
        scoped_views, view_reason = changed_view_dsls(root, args.base)
        if scoped is None or scoped_views is None:
            print(
                "layer-purity: changed-file scoping unavailable "
                f"({reason or view_reason}); falling back to full-corpus scan",
                file=sys.stderr,
            )
            documents = all_documents
            view_dsls = all_view_dsls
        else:
            documents = scoped
            view_dsls = scoped_views
    else:
        documents = all_documents
        view_dsls = all_view_dsls

    today = _utc_today()

    findings: list[Leak] = []
    grandfathered: list[tuple[Leak, Violation]] = []

    def _route(leak: Leak) -> None:
        if leak.category_id == "<unreadable>":
            findings.append(leak)
            return
        row = suppressing_row(leak, policy, today)
        if row is not None:
            grandfathered.append((leak, row))
        else:
            findings.append(leak)

    for doc in documents:
        for leak in scan_document(doc, root, leaked):
            _route(leak)
    for view in view_dsls:
        for leak in scan_view_dsl(view, root, leaked):
            _route(leak)

    # --- report ------------------------------------------------------------
    for leak in findings:
        cat = policy.categories.get(leak.category_id)
        home = f" -> migrate to {cat.home}" if cat and cat.home else ""
        title = cat.title if cat else leak.category_id
        print(
            f"layer-purity {leak.rel_path}:{leak.line_no} [{leak.layer}] "
            f"{leak.category_id} ({title}): {leak.label}{home}\n"
            f"    > {leak.excerpt}",
            file=sys.stderr,
        )

    for leak, row in grandfathered:
        anchor = f"locus {row.raw_locus}" if row.locus_anchored else "locus anchorless (whole-file)"
        print(
            f"layer-purity GRANDFATHERED {leak.rel_path}:{leak.line_no} [{leak.layer}] "
            f"{leak.category_id}: tolerated by {row.id} ({anchor}; sunset {row.raw_sunset})",
            file=sys.stderr,
        )

    # --- expired-row advisory ---------------------------------------------
    # An allow-list row whose sunset has passed AND that no longer matches any
    # live leak is dead weight; surface it so the list can be pruned. This never
    # affects the exit code (it is housekeeping), but it keeps the closed,
    # dated grandfather list honest.
    matched_row_ids = {row.id for _, row in grandfathered}
    expired_unused = [
        row
        for row in policy.violations
        if row.sunset_date is not None
        and row.sunset_date < today
        and row.id not in matched_row_ids
    ]
    for row in sorted(expired_unused, key=lambda r: r.id):
        print(
            f"layer-purity NOTE: grandfather row {row.id} expired "
            f"({row.raw_sunset}) and matched no live leak; remove it from "
            f"{VIOLATIONS_REL}",
            file=sys.stderr,
        )

    scanned_n = len(documents)
    corpus_n = len(all_documents)
    scanned_views = len(view_dsls)
    corpus_views = len(all_view_dsls)
    summary = (
        f"layer-purity [{args.mode}] (policy status: {policy.status or 'unknown'}): "
        f"scanned {scanned_n} L0/L1 document(s)"
        + (f" (corpus total {corpus_n})" if scanned_n != corpus_n else "")
        + f" + {scanned_views} C4 view fragment(s)"
        + (f" (corpus total {corpus_views})" if scanned_views != corpus_views else "")
        + f"; {len(findings)} finding(s), {len(grandfathered)} grandfathered"
    )
    print(summary, file=sys.stderr)

    if args.mode == "advisory":
        return 0
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
