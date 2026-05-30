#!/usr/bin/env python3
"""Gate check: Frame-Card / DSL parity + fact-citation integrity (Rule G-29).

Authority: ADR-0161 (EngineeringFrame as Package-Cluster Anchor + Card-over-DSL).
Consumed by Rule G-29 (gate Rule 146, enforcer E196).

An EngineeringFrame Frame Card (``architecture/docs/L1/frames/<frame-id>.md``) is
a READABLE INTERPRETATION of the Structurizr DSL and the generated facts — never
an authority. ADR-0161 §3 fixes the authority direction and makes it one-way:

    ADR
      -> architecture/profile/*
        -> architecture/workspace.dsl
          -> architecture/features/engineering-frames.dsl
            -> architecture/facts/generated/*.json
              -> architecture/docs/L1/frames/<frame-id>.md   (Card: derived)
                -> gate
                  -> docs/governance/architecture-status.yaml

This check is the executable form of "the Card invents nothing". It reads the
DSL frame/FunctionPoint elements + their ``anchors`` edges as the identity
authority, the generated facts as the factual authority, and the cards as the
data; it reports any card that:

  1. carries a frontmatter ``frame_id`` that does not resolve to a DSL
     EngineeringFrame whose ``saa.id`` equals it (identity drift), or whose
     copied identity fields (``owner_module`` / ``status`` / ``primary_package``)
     disagree with that DSL element;
  2. cites a code symbol (``code-symbol/<kebab-fqn>``), test (``test/<kebab-fqn>``)
     or contract operation (``contract-op/<kebab-op-id>``) fact ID — or a
     ``code-symbol/<kebab-fqn>#<jvm-method-descriptor>`` method ref — that does
     not resolve in ``architecture/facts/generated/*.json`` (the method ref must
     resolve to the class fact's ``public_methods[]``);
  3. names a FunctionPoint (``FP-…``) that the frame does not ``anchors`` in the
     DSL and that the card does not declare as a participating-frame reference
     (an invented anchor).

It invents no ID and no relationship, and it never outranks a generated fact: it
is a classifier over the cards, pinned to the lowest interpretation tier of the
ADR-0154 cascade.

Modes (``--mode``):

  advisory                 Validate every Frame Card, print findings to stderr,
                           and ALWAYS exit 0. This is the Phase-1 landing posture
                           per ADR-0161 §6 (the card gate ships ADVISORY).
  changed-files-blocking   Validate only the Frame Cards that changed relative to
                           a base ref (``--base``, default ``origin/main``). Exit
                           1 if any CHANGED card has a finding; pre-existing
                           findings on untouched cards do not block. The ratchet
                           posture: a PR may not ADD or WORSEN a violation.
  full-blocking            Validate every Frame Card; exit 1 on any finding. The
                           terminal posture (ADR-0161 §6 Phase 3), reached after
                           a 14-day soak on a clean corpus.

Greenfield posture. ``architecture/docs/L1/frames/`` does not exist until the
pilot card lands (ADR-0161 §4). When the directory is absent OR contains no
authored cards (only ``README.md`` / ``_template.md``), the check is vacuously
clean in every mode — there is nothing to validate yet. The moment one authored
card exists, the DSL frame elements and the generated facts MUST be readable, or
the check fails closed (a missing authority is never an advisory condition): a
card cannot be judged against authorities that vanished.

Usage:
    python3 gate/lib/check_frame_card_consistency.py --mode advisory
    python3 gate/lib/check_frame_card_consistency.py --mode changed-files-blocking
    python3 gate/lib/check_frame_card_consistency.py --mode changed-files-blocking --base origin/main
    python3 gate/lib/check_frame_card_consistency.py --mode full-blocking
    python3 gate/lib/check_frame_card_consistency.py --mode full-blocking --repo /path/to/repo

Exit codes:
    0 — passed (always, in advisory mode); or no in-scope findings in a blocking
        mode; or no authored cards exist yet (greenfield)
    1 — one or more in-scope findings in a blocking mode (printed to stderr)
    2 — usage / configuration error (bad mode, unreadable DSL/facts while cards
        exist, --repo not a directory, etc.)
"""
from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

# ---------------------------------------------------------------------------
# Canonical surface locations (repo-relative, forward slash).
# ---------------------------------------------------------------------------
FRAMES_DSL_REL = "architecture/features/engineering-frames.dsl"
FEATURES_DSL_REL = "architecture/features/features.dsl"
FUNCTION_POINTS_DSL_REL = "architecture/features/function-points.dsl"
CARDS_DIR_REL = "architecture/docs/L1/frames"
FACTS_DIR_REL = "architecture/facts/generated"

# Generated fact files whose top-level ``facts[]`` carry the resolvable fact IDs.
CODE_SYMBOLS_FACT_REL = f"{FACTS_DIR_REL}/code-symbols.json"
TESTS_FACT_REL = f"{FACTS_DIR_REL}/tests.json"
CONTRACT_SURFACES_FACT_REL = f"{FACTS_DIR_REL}/contract-surfaces.json"

# A card file under CARDS_DIR_REL is an authored Frame Card unless it is one of
# these scaffolding files (the directory ships a README + template per ADR-0161).
NON_CARD_BASENAMES = {"readme.md", "_template.md"}

# Frontmatter identity keys (ADR-0161 §3): COPIED from the DSL element, MUST match.
# Maps the card frontmatter key -> the DSL property it is copied from.
IDENTITY_KEY_TO_DSL = {
    "frame_id": "saa.id",
    "owner_module": "saa.owner",
    "status": "saa.status",
    "primary_package": "saa.primaryPackage",
    # `dsl_element` names the DSL var; checked separately (it is not a saa.* prop).
}

# ---------------------------------------------------------------------------
# DSL element/edge regexes (mirror gate/lib/check_frame_shipped_anchors.py).
# ---------------------------------------------------------------------------
FRAME_ELEMENT_RE = re.compile(
    r'(\w+)\s*=\s*element\s+"[^"]*"\s+"EngineeringFrame"', re.MULTILINE
)
FP_ELEMENT_RE = re.compile(
    r'(\w+)\s*=\s*element\s+"[^"]*"\s+"FunctionPoint"', re.MULTILINE
)
ID_RE = re.compile(r'"saa\.id"\s+"([^"]+)"')
STATUS_RE = re.compile(r'"saa\.status"\s+"([^"]+)"')
OWNER_RE = re.compile(r'"saa\.owner"\s+"([^"]+)"')
PRIMARY_PACKAGE_RE = re.compile(r'"saa\.primaryPackage"\s+"([^"]+)"')
CARD_PATH_RE = re.compile(r'"saa\.cardPath"\s+"([^"]+)"')
# A relationship edge whose properties block declares saa.rel = anchors:
#   <frameVar> -> <fpVar> "..." "SAA Relationship" { ... "saa.rel" "anchors" ... }
ANCHOR_EDGE_RE = re.compile(
    r'(\w+)\s*->\s*(\w+)\s+"[^"]*"\s+"SAA Relationship"\s*\{[^}]*?"saa\.rel"\s+"anchors"',
    re.DOTALL,
)

# ---------------------------------------------------------------------------
# Card citation grammar. Fact IDs and FunctionPoint IDs appear as tokens in the
# frontmatter and the prose body. The probes are deliberately specific so that
# ordinary prose (a sentence mentioning a class in passing) does not masquerade
# as a fact citation: a citation is the canonical fact-ID shape, optionally
# wrapped in backticks. Method refs are a class fact ID followed by `#<descriptor>`.
# ---------------------------------------------------------------------------
# code-symbol/<kebab-fqn> optionally followed by #<jvm-method-descriptor>.
# The kebab-fqn is lowercase letters/digits/hyphen (dots->hyphens, ADR-0161 §3);
# the char class itself stops the fqn cleanly at '#', whitespace, or punctuation.
# The optional method tail is the JVM descriptor as stored in public_methods[],
# e.g. `#runId()Ljava/lang/String;` or `#<init>(Ljava/lang/String;)V`. A JVM
# descriptor is DENSE and legitimately contains ';' '/' '<' '>' '(' ')', so the
# tail is captured greedily up to a HARD boundary (whitespace, a backtick code-
# span close, or end of line) — NOT at the first ';' — and a single trailing
# sentence-punctuation char ('.' ',' ')') is trimmed afterwards (see
# _trim_descriptor) for the bare (non-backticked) prose-citation case.
CODE_SYMBOL_REF_RE = re.compile(r"\bcode-symbol/([a-z0-9-]+)(?:#([^\s`]+))?")
TEST_REF_RE = re.compile(r"\btest/([a-z0-9-]+)")
CONTRACT_OP_REF_RE = re.compile(r"\bcontract-op/([a-z0-9-]+)")
# FunctionPoint IDs: FP- followed by uppercase letters/digits/hyphen.
FP_ID_RE = re.compile(r"\bFP-[A-Z0-9][A-Z0-9-]*\b")

# A bare (non-backticked) descriptor citation in prose may pick up trailing
# sentence punctuation under the greedy capture. We trim ONLY '.', ',', and ')'
# from the tail — never ';', because a ';' legitimately terminates an object
# return type (`()Ljava/lang/String;`). The raw capture is tried first; the
# trimmed form is a fallback (see _resolve_descriptor).
_DESCRIPTOR_TAIL_TRIM = re.compile(r"[.,)]+$")


# ---------------------------------------------------------------------------
# Repo + path helpers.
# ---------------------------------------------------------------------------
def repo_root() -> Path:
    """Return the repository root (the directory two levels above this script)."""
    return Path(__file__).resolve().parent.parent.parent


def _rel(path: Path, root: Path) -> str:
    try:
        return str(path.relative_to(root)).replace("\\", "/")
    except ValueError:
        return str(path).replace("\\", "/")


# ===========================================================================
# DSL parsing — the identity + anchor authority.
# ===========================================================================
@dataclass
class FrameElement:
    """One EngineeringFrame DSL element (the copied-from identity authority)."""

    var: str
    saa_id: str
    owner: str
    status: str
    primary_package: str  # "" when absent (design_only frames may omit it)
    card_path: str  # "" when absent


def _parse_block(text: str, start: int, window: int = 1200) -> str:
    """Return the properties window that follows an element match start."""
    return text[start : start + window]


def parse_frames(text: str) -> dict[str, FrameElement]:
    """Return {var: FrameElement} for every EngineeringFrame element in ``text``."""
    frames: dict[str, FrameElement] = {}
    for m in FRAME_ELEMENT_RE.finditer(text):
        var = m.group(1)
        block = _parse_block(text, m.start())
        sid = ID_RE.search(block)
        owner = OWNER_RE.search(block)
        status = STATUS_RE.search(block)
        pkg = PRIMARY_PACKAGE_RE.search(block)
        card = CARD_PATH_RE.search(block)
        frames[var] = FrameElement(
            var=var,
            saa_id=sid.group(1) if sid else "",
            owner=owner.group(1) if owner else "",
            status=status.group(1) if status else "",
            primary_package=pkg.group(1) if pkg else "",
            card_path=card.group(1) if card else "",
        )
    return frames


def parse_function_points(text: str) -> dict[str, str]:
    """Return {var: saa.id} for every FunctionPoint element in ``text``."""
    fps: dict[str, str] = {}
    for m in FP_ELEMENT_RE.finditer(text):
        var = m.group(1)
        block = _parse_block(text, m.start())
        sid = ID_RE.search(block)
        if sid:
            fps[var] = sid.group(1)
    return fps


def parse_anchor_edges(text: str) -> list[tuple[str, str]]:
    """Return (frameVar, fpVar) for every anchors edge in ``text``."""
    return [(m.group(1), m.group(2)) for m in ANCHOR_EDGE_RE.finditer(text)]


@dataclass
class DslModel:
    """The resolved DSL identity authority a card is checked against."""

    frames_by_id: dict[str, FrameElement]
    # frame saa.id -> set of FunctionPoint saa.ids it anchors.
    anchors_by_frame_id: dict[str, set[str]]


def load_dsl_model(root: Path) -> tuple[DslModel | None, list[str]]:
    """Parse the frame/FP elements + anchors edges. Returns ``(model, errors)``.

    Frame elements live across engineering-frames.dsl AND features.dsl (the
    agent-service Layer features re-tagged as frames); FunctionPoint elements and
    the ``anchors`` edges live in their own files. A missing required DSL file is
    a config error (exit 2) — without the identity authority no card can be judged.
    """
    errors: list[str] = []

    ef_path = root / FRAMES_DSL_REL
    feat_path = root / FEATURES_DSL_REL
    fp_path = root / FUNCTION_POINTS_DSL_REL

    if not ef_path.is_file():
        errors.append(f"missing {FRAMES_DSL_REL}")
    if not fp_path.is_file():
        errors.append(f"missing {FUNCTION_POINTS_DSL_REL}")
    if errors:
        return None, errors

    ef_text = ef_path.read_text(encoding="utf-8")
    feat_text = feat_path.read_text(encoding="utf-8") if feat_path.is_file() else ""
    fp_text = fp_path.read_text(encoding="utf-8")

    # Frame elements: features.dsl first so engineering-frames.dsl wins on a
    # (non-)collision, mirroring check_frame_shipped_anchors.py's merge order.
    frames_by_var: dict[str, FrameElement] = {
        **parse_frames(feat_text),
        **parse_frames(ef_text),
    }
    frames_by_id: dict[str, FrameElement] = {}
    for fr in frames_by_var.values():
        if fr.saa_id:
            frames_by_id[fr.saa_id] = fr

    # FunctionPoint elements may be authored in function-points.dsl (and, defensively,
    # anywhere a FunctionPoint element is declared — scan features.dsl + frames too).
    fp_var_to_id: dict[str, str] = {
        **parse_function_points(fp_text),
        **parse_function_points(feat_text),
        **parse_function_points(ef_text),
    }

    # anchors edges live in engineering-frames.dsl (per ADR-0157/0158 layout).
    anchors_by_frame_id: dict[str, set[str]] = {}
    for frame_var, fp_var in parse_anchor_edges(ef_text):
        frame = frames_by_var.get(frame_var)
        if frame is None or not frame.saa_id:
            # An anchors edge from an unknown frame var is a DSL-internal problem
            # the workspace gate owns; here we simply cannot attribute it.
            continue
        fp_id = fp_var_to_id.get(fp_var)
        if fp_id is None:
            continue
        anchors_by_frame_id.setdefault(frame.saa_id, set()).add(fp_id)

    return DslModel(frames_by_id=frames_by_id, anchors_by_frame_id=anchors_by_frame_id), []


# ===========================================================================
# Generated-fact loading — the factual authority.
# ===========================================================================
@dataclass
class FactIndex:
    """Resolvable fact IDs from the generated fact files."""

    code_symbol_ids: set[str]
    test_ids: set[str]
    contract_op_ids: set[str]
    # code-symbol fact_id -> set of JVM method descriptors (public_methods[]).
    methods_by_code_symbol: dict[str, set[str]]

    def has_code_symbol(self, fact_id: str) -> bool:
        return fact_id in self.code_symbol_ids

    def has_method(self, code_symbol_id: str, descriptor: str) -> bool:
        return descriptor in self.methods_by_code_symbol.get(code_symbol_id, set())


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
    error (exit 2): the Card is checked AGAINST the facts, so without them no
    factual claim can be judged — failing closed rather than passing vacuously.
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

    code_symbol_ids: set[str] = set()
    methods_by_code_symbol: dict[str, set[str]] = {}
    for fact in code_facts or []:
        if not isinstance(fact, dict):
            continue
        fid = fact.get("fact_id")
        if not isinstance(fid, str):
            continue
        code_symbol_ids.add(fid)
        observed = fact.get("observed_value")
        methods = observed.get("public_methods") if isinstance(observed, dict) else None
        if isinstance(methods, list):
            methods_by_code_symbol[fid] = {m for m in methods if isinstance(m, str)}

    test_ids = {
        fact["fact_id"]
        for fact in (test_facts or [])
        if isinstance(fact, dict) and isinstance(fact.get("fact_id"), str)
    }
    contract_op_ids = {
        fact["fact_id"]
        for fact in (contract_facts or [])
        if isinstance(fact, dict) and isinstance(fact.get("fact_id"), str)
    }

    return (
        FactIndex(
            code_symbol_ids=code_symbol_ids,
            test_ids=test_ids,
            contract_op_ids=contract_op_ids,
            methods_by_code_symbol=methods_by_code_symbol,
        ),
        [],
    )


# ===========================================================================
# YAML frontmatter parsing for a Frame Card.
# ===========================================================================
def _split_frontmatter(text: str) -> tuple[str | None, str]:
    """Split a markdown doc into (frontmatter_yaml, body).

    Returns ``(None, text)`` when the doc has no leading ``---`` fenced block.
    The body is everything after the closing fence (used for body fact-citation
    scanning); when there is no frontmatter the whole text is the body.
    """
    if not text.startswith("---"):
        return None, text
    # First line must be exactly a `---` fence (allow a trailing CR).
    lines = text.splitlines(keepends=True)
    if not lines or lines[0].rstrip("\r\n") != "---":
        return None, text
    for idx in range(1, len(lines)):
        if lines[idx].rstrip("\r\n") == "---":
            fm = "".join(lines[1:idx])
            body = "".join(lines[idx + 1 :])
            return fm, body
    # Unterminated frontmatter fence.
    return None, text


def _load_frontmatter(fm_text: str) -> tuple[dict | None, str]:
    """Parse frontmatter YAML into a mapping. Returns ``(data, error)``."""
    try:
        import yaml  # type: ignore[import-not-found]
    except ImportError:
        return None, "PyYAML not installed (run: pip install -r gate/requirements.txt)"
    try:
        data = yaml.safe_load(fm_text)
    except yaml.YAMLError as exc:  # type: ignore[attr-defined]
        return None, f"frontmatter failed YAML parse: {exc}"
    if data is None:
        return {}, ""
    if not isinstance(data, dict):
        return None, f"frontmatter is not a mapping (got {type(data).__name__})"
    return data, ""


def _frontmatter_scalar(data: dict, key: str) -> str:
    """Return a frontmatter scalar as a trimmed string ('' when absent/empty)."""
    val = data.get(key)
    if val is None:
        return ""
    return str(val).strip()


def _frontmatter_id_list(data: dict, *keys: str) -> list[str]:
    """Collect string tokens from one or more frontmatter list/scalar keys."""
    out: list[str] = []
    for key in keys:
        val = data.get(key)
        if val is None:
            continue
        if isinstance(val, list):
            out.extend(str(x).strip() for x in val if str(x).strip())
        elif str(val).strip():
            out.append(str(val).strip())
    return out


# ===========================================================================
# Card discovery + changed-file scoping.
# ===========================================================================
def discover_cards(root: Path) -> list[Path]:
    """Return every authored Frame Card under the frames/ directory, sorted.

    Scaffolding (README.md, _template.md) is excluded. May be empty (greenfield:
    the directory does not exist yet, or carries only scaffolding).
    """
    cards_dir = root / CARDS_DIR_REL
    if not cards_dir.is_dir():
        return []
    out: list[Path] = []
    for md in cards_dir.glob("*.md"):
        if md.name.lower() in NON_CARD_BASENAMES:
            continue
        out.append(md)
    return sorted(out, key=lambda p: _rel(p, root))


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


def changed_cards(root: Path, base: str) -> tuple[list[Path] | None, str]:
    """Return the Frame Cards changed vs ``base``, or ``(None, reason)``.

    Scope = (committed diff merge-base...HEAD) UNION (uncommitted tracked
    changes) UNION (untracked files under the frames/ directory). A ``None``
    return means git could not resolve the base ref; the caller fails closed in a
    blocking mode (falls back to the full corpus — a safe superset).
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
    rc, out = _git_run(["ls-files", "--others", "--exclude-standard", CARDS_DIR_REL], root)
    if rc == 0:
        names.update(line.strip() for line in out.splitlines() if line.strip())

    prefix = CARDS_DIR_REL + "/"
    selected: list[Path] = []
    for name in sorted(names):
        norm = name.replace("\\", "/")
        if not norm.startswith(prefix) or not norm.endswith(".md"):
            continue
        if Path(norm).name.lower() in NON_CARD_BASENAMES:
            continue
        candidate = root / norm
        if candidate.is_file():
            selected.append(candidate)
    return selected, ""


# ===========================================================================
# Per-card validation.
# ===========================================================================
@dataclass
class Finding:
    """One Frame-Card consistency finding."""

    rel_path: str
    line_no: int  # 0 when not line-attributable (frontmatter-level)
    code: str  # short machine code, e.g. IDENTITY-FRAME-ID, FACT-UNRESOLVED, ANCHOR-INVENTED
    message: str


def _line_of(text: str, token: str, start_at: int = 0) -> int:
    """Best-effort 1-based line number of the first occurrence of ``token``.

    Used to make a finding file/line-oriented (ADR-0161 §5). Returns 0 when the
    token cannot be located (it was synthesised, not copied from the source).
    """
    idx = text.find(token, start_at)
    if idx < 0:
        return 0
    return text.count("\n", 0, idx) + 1


def validate_card(
    path: Path, root: Path, dsl: DslModel, facts: FactIndex
) -> list[Finding]:
    """Validate one Frame Card against the DSL identity + the generated facts."""
    rel = _rel(path, root)
    findings: list[Finding] = []

    try:
        text = path.read_text(encoding="utf-8")
    except OSError as exc:
        return [Finding(rel, 0, "CARD-UNREADABLE", f"cannot read card: {exc}")]

    fm_text, body = _split_frontmatter(text)
    if fm_text is None:
        return [
            Finding(
                rel,
                1,
                "FRONTMATTER-MISSING",
                "card has no leading '---' YAML frontmatter block "
                "(the copied-from-DSL identity block is required, ADR-0161 §3/§4)",
            )
        ]

    fm, ferr = _load_frontmatter(fm_text)
    if fm is None:
        return [Finding(rel, 1, "FRONTMATTER-PARSE", ferr)]

    # --- 1) Identity parity ------------------------------------------------
    frame_id = _frontmatter_scalar(fm, "frame_id")
    if not frame_id:
        findings.append(
            Finding(
                rel,
                0,
                "IDENTITY-FRAME-ID",
                "frontmatter 'frame_id' is missing or empty (the card cannot be "
                "bound to a DSL EngineeringFrame)",
            )
        )
        frame_el: FrameElement | None = None
    else:
        frame_el = dsl.frames_by_id.get(frame_id)
        if frame_el is None:
            findings.append(
                Finding(
                    rel,
                    _line_of(text, frame_id),
                    "IDENTITY-FRAME-ID",
                    f"frontmatter frame_id {frame_id!r} does not resolve to any "
                    f"EngineeringFrame saa.id in {FRAMES_DSL_REL} / {FEATURES_DSL_REL} "
                    f"(the card invents a frame identity)",
                )
            )

    # Copied identity fields must match the DSL element (when the frame resolved).
    if frame_el is not None:
        dsl_values = {
            "owner_module": frame_el.owner,
            "status": frame_el.status,
            "primary_package": frame_el.primary_package,
        }
        for card_key, dsl_value in dsl_values.items():
            card_value = _frontmatter_scalar(fm, card_key)
            # An absent optional field is allowed ONLY where the DSL value is also
            # absent (e.g. primary_package on a design_only frame). Where the DSL
            # carries a value, the card MUST copy it; where the card carries a
            # value, it MUST equal the DSL.
            if not card_value and not dsl_value:
                continue
            if card_value != dsl_value:
                findings.append(
                    Finding(
                        rel,
                        _line_of(text, card_key),
                        f"IDENTITY-{card_key.upper().replace('_', '-')}",
                        f"frontmatter {card_key}={card_value!r} disagrees with DSL "
                        f"{IDENTITY_KEY_TO_DSL.get(card_key, card_key)}={dsl_value!r} "
                        f"for frame {frame_id!r} (the DSL wins; copy it)",
                    )
                )

        # dsl_element, when present, must name the frame's DSL var.
        dsl_element = _frontmatter_scalar(fm, "dsl_element")
        if dsl_element and dsl_element != frame_el.var:
            findings.append(
                Finding(
                    rel,
                    _line_of(text, "dsl_element"),
                    "IDENTITY-DSL-ELEMENT",
                    f"frontmatter dsl_element {dsl_element!r} disagrees with the DSL "
                    f"var {frame_el.var!r} for frame {frame_id!r}",
                )
            )

    # --- 2) Fact-citation resolution (frontmatter refs + body citations) ---
    # Scan the WHOLE card text (frontmatter + body): fact IDs may be declared in
    # a frontmatter `fact_refs:` list AND cited inline in the prose sections.
    findings.extend(_check_fact_citations(rel, text, facts))

    # --- 3) Anchor honesty -------------------------------------------------
    # Every FunctionPoint the card names MUST be anchored by THIS frame in the
    # DSL, OR explicitly declared as a participating-frame reference in the
    # frontmatter (ADR-0161 §5: "or declared participating-frame references").
    if frame_el is not None and frame_id:
        anchored = dsl.anchors_by_frame_id.get(frame_id, set())
        participating = set(
            _frontmatter_id_list(fm, "participating_function_points", "participating_fps")
        )
        # FunctionPoints the card declares as its own (frontmatter) + names in body.
        declared = set(_frontmatter_id_list(fm, "function_points", "anchors"))
        named_in_body = set(FP_ID_RE.findall(body))
        for fp in sorted(declared | named_in_body):
            if fp in anchored or fp in participating:
                continue
            findings.append(
                Finding(
                    rel,
                    _line_of(text, fp),
                    "ANCHOR-INVENTED",
                    f"card names FunctionPoint {fp!r} which frame {frame_id!r} does "
                    f"not 'anchors' in {FRAMES_DSL_REL} and which is not declared a "
                    f"participating-frame reference (the card invents an anchor)",
                )
            )

    return findings


def _resolve_descriptor(
    code_symbol_id: str, descriptor: str, facts: FactIndex
) -> str | None:
    """Return the matching public_methods[] descriptor, or None.

    The raw greedy capture is tried first (the backticked / whitespace-bounded
    citation case). If it does not resolve, trailing prose punctuation ('.', ',',
    ')') is trimmed once and retried (the bare prose-citation case). ';' is never
    trimmed — it legitimately terminates an object return type.
    """
    if facts.has_method(code_symbol_id, descriptor):
        return descriptor
    trimmed = _DESCRIPTOR_TAIL_TRIM.sub("", descriptor)
    if trimmed != descriptor and facts.has_method(code_symbol_id, trimmed):
        return trimmed
    return None


def _check_fact_citations(rel: str, text: str, facts: FactIndex) -> list[Finding]:
    """Resolve every code-symbol/test/contract-op (and method) citation in ``text``."""
    findings: list[Finding] = []

    for m in CODE_SYMBOL_REF_RE.finditer(text):
        kebab = m.group(1)
        fact_id = f"code-symbol/{kebab}"
        descriptor = m.group(2)  # the #<jvm-descriptor> tail, or None
        line_no = text.count("\n", 0, m.start()) + 1
        if not facts.has_code_symbol(fact_id):
            findings.append(
                Finding(
                    rel,
                    line_no,
                    "FACT-UNRESOLVED",
                    f"cited code symbol {fact_id!r} does not resolve in "
                    f"{CODE_SYMBOLS_FACT_REL}",
                )
            )
            continue
        if descriptor:
            resolved = _resolve_descriptor(fact_id, descriptor, facts)
            if resolved is None:
                findings.append(
                    Finding(
                        rel,
                        line_no,
                        "FACT-METHOD-UNRESOLVED",
                        f"cited method {descriptor!r} is not in public_methods[] of "
                        f"{fact_id!r} (per {CODE_SYMBOLS_FACT_REL})",
                    )
                )

    for m in TEST_REF_RE.finditer(text):
        fact_id = f"test/{m.group(1)}"
        if fact_id not in facts.test_ids:
            findings.append(
                Finding(
                    rel,
                    text.count("\n", 0, m.start()) + 1,
                    "FACT-UNRESOLVED",
                    f"cited test {fact_id!r} does not resolve in {TESTS_FACT_REL}",
                )
            )

    for m in CONTRACT_OP_REF_RE.finditer(text):
        fact_id = f"contract-op/{m.group(1)}"
        if fact_id not in facts.contract_op_ids:
            findings.append(
                Finding(
                    rel,
                    text.count("\n", 0, m.start()) + 1,
                    "FACT-UNRESOLVED",
                    f"cited contract operation {fact_id!r} does not resolve in "
                    f"{CONTRACT_SURFACES_FACT_REL}",
                )
            )

    return findings


# ===========================================================================
# CLI.
# ===========================================================================
def parse_args(argv: list[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Rule G-29 — Frame-Card / DSL parity + fact-citation "
        "integrity: validate EngineeringFrame Frame Cards against the DSL "
        "identity + generated facts (ADR-0161).",
    )
    parser.add_argument(
        "--mode",
        choices=("advisory", "changed-files-blocking", "full-blocking"),
        default="advisory",
        help="advisory (report, never block); changed-files-blocking (block only "
        "on changed cards); full-blocking (block on any card). Default: advisory.",
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
        print(f"frame-card: --repo {root} is not a directory", file=sys.stderr)
        return 2

    all_cards = discover_cards(root)

    # Greenfield posture: no authored cards yet (ADR-0161 §4 — the frames/
    # directory lands with the pilot card). There is nothing to validate; the
    # check is vacuously clean in every mode. This is NOT the same as the
    # non-vacuity guard below: that guard fires only once cards EXIST.
    if not all_cards:
        print(
            f"frame-card [{args.mode}]: no authored Frame Cards under "
            f"{CARDS_DIR_REL} yet (greenfield, ADR-0161 §4); 0 finding(s)",
            file=sys.stderr,
        )
        return 0

    # Cards exist -> the DSL identity authority + the generated facts MUST be
    # readable. A missing authority is never an advisory condition (it fails
    # closed in every mode): a card cannot be judged against authorities that
    # vanished, and a vacuous pass would let card drift through silently.
    dsl, dsl_errors = load_dsl_model(root)
    if dsl is None:
        for err in dsl_errors:
            print(f"frame-card config error (DSL): {err}", file=sys.stderr)
        return 2
    facts, fact_errors = load_fact_index(root)
    if facts is None:
        for err in fact_errors:
            print(f"frame-card config error (facts): {err}", file=sys.stderr)
        return 2

    if args.mode == "changed-files-blocking":
        scoped, reason = changed_cards(root, args.base)
        if scoped is None:
            print(
                f"frame-card: changed-file scoping unavailable ({reason}); "
                "falling back to full-corpus validation",
                file=sys.stderr,
            )
            cards = all_cards
        else:
            cards = scoped
    else:
        cards = all_cards

    findings: list[Finding] = []
    for card in cards:
        findings.extend(validate_card(card, root, dsl, facts))

    for f in findings:
        loc = f"{f.rel_path}:{f.line_no}" if f.line_no else f.rel_path
        print(f"frame-card {loc} [{f.code}]: {f.message}", file=sys.stderr)

    summary = (
        f"frame-card [{args.mode}]: validated {len(cards)} Frame Card(s)"
        + (f" (corpus total {len(all_cards)})" if len(cards) != len(all_cards) else "")
        + f"; {len(findings)} finding(s)"
    )
    print(summary, file=sys.stderr)

    if args.mode == "advisory":
        return 0
    return 1 if findings else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
