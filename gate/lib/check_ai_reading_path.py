#!/usr/bin/env python3
"""Gate check: the AI reading path is materializable and entry docs route onto it.

Authority: ADR-0159 (Progressive Learning Curve and Authority Lanes — the
product-first eight-node entry chain) read through its data file
``docs/governance/ai-reading-path.yaml`` and that file's human-readable companion
``docs/onboarding/ai-understanding-path.md``. Consumed by Rule G-31 (gate Rule
148, enforcer E198), authored ADVISORY.

The reading-path data file is the single source of truth for the order an AI
agent (or a new engineer) reads the repository: an eight-node chain from product
definition down to verification and gate, expressed as the ``version`` /
``status`` header, an ``entry_contract`` (including the ``factual_claim_switch``
fact files an agent loads before prose), and the ``orientation_learning_path``
step list whose surfaces realise each node. This check verifies three things and
NEVER outranks any surface it points at — it invents no id and no relationship,
it only asserts that the path the data file declares is materializable on disk
and that the entry documents route a reader onto it:

  1. SURFACE EXISTENCE. Every surface listed under ``orientation_learning_path``
     whose ``presence`` is ``present`` resolves to an existing file or directory;
     a surface marked ``presence: planned`` MAY be absent (it names a governed
     target that lands in a later wave). The ``companion_human_form`` mirror and
     every ``entry_contract.factual_claim_switch.read_before_prose`` generated-
     fact file MUST exist. A required-but-absent surface is a ``MISSING-SURFACE``
     finding (companion / fact files report ``MISSING-COMPANION`` /
     ``MISSING-FACT-FILE``).

  2. ENTRY-DOC ROUTING. Every step-1 entry document (the surfaces of the
     ``repository_entry`` step — ``README.md`` / ``CLAUDE.md`` / ``AGENTS.md``)
     plus the always-load session doc ``docs/governance/SESSION-START-CONTEXT.md``
     MUST point a reader at the canonical path: each MUST reference either the
     data file (``docs/governance/ai-reading-path.yaml``) or its companion
     (``docs/onboarding/ai-understanding-path.md``). An entry doc that does not is
     a ``MISSING-MARKER`` finding (advisory while the entry-doc corpus migrates
     from the legacy architecture-first reading path to this product-first chain).

  3. YAML <-> COMPANION LOCKSTEP. The data file and its companion mirror MUST stay
     in lockstep (the invariant both files declare in their own headers): the
     companion references the data file, the data file's ``companion_human_form``
     points back to the companion, and the companion carries a heading for every
     step the YAML declares (so step coverage cannot silently drift). A break is a
     ``LOCKSTEP-BROKEN`` finding; missing per-step coverage is ``LOCKSTEP-STEP``.

This check reads the data file + the companion as IDENTITY/NAVIGATION authority
and the disk as the factual authority; it asserts none of its own. Authority
cascade is unchanged: generated facts > DSL > Card/prose.

Modes (``--mode``):

  advisory                 Evaluate the whole path, print findings to stderr, and
                           ALWAYS exit 0. The landing posture per the ADR-0159
                           §13.3 ratchet (first cleanup wave).
  changed-files-blocking   Exit 1 on any finding ONLY when the path's authoring
                           surfaces changed relative to a base ref (``--base``,
                           default ``origin/main``, else ``HEAD``): the data file,
                           the companion mirror, or any step-1 entry doc. A change
                           to any of them re-scopes the WHOLE path (the path is a
                           single shared navigation surface). Otherwise advisory.
  full-blocking            Exit 1 on any finding — the terminal posture once the
                           entry-doc corpus is migrated and the path is clean.

The reading-path data file is the only required input. When it is absent the
check is vacuously clean in every mode (the path has not been authored yet —
greenfield). The instant it exists it MUST parse, and its companion +
factual-claim-switch fact files MUST be readable, or the check fails closed
(exit 2) in EVERY mode including advisory — a missing authority is never an
advisory condition.

Usage:
    python3 gate/lib/check_ai_reading_path.py --mode advisory
    python3 gate/lib/check_ai_reading_path.py --mode changed-files-blocking
    python3 gate/lib/check_ai_reading_path.py --mode changed-files-blocking --base origin/main
    python3 gate/lib/check_ai_reading_path.py --mode full-blocking
    python3 gate/lib/check_ai_reading_path.py --mode full-blocking --repo /path/to/repo

Exit codes:
    0 — passed (always, in advisory mode); or no in-scope findings in a blocking
        mode; or the reading-path data file does not exist yet (greenfield)
    1 — one or more in-scope findings in a blocking mode (printed to stderr)
    2 — usage / configuration error (bad mode, unparseable/unreadable data file or
        companion while the data file exists, --repo not a directory, etc.)
"""
from __future__ import annotations

import argparse
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path

import yaml

# ---------------------------------------------------------------------------
# Canonical surface locations (repo-relative, forward slash).
# ---------------------------------------------------------------------------
READING_PATH_REL = "docs/governance/ai-reading-path.yaml"
COMPANION_DEFAULT_REL = "docs/onboarding/ai-understanding-path.md"
SESSION_DOC_REL = "docs/governance/SESSION-START-CONTEXT.md"

VALID_MODES = ("advisory", "changed-files-blocking", "full-blocking")

# The step-1 ("repository_entry") surfaces plus the session doc are the entry
# documents that MUST route a reader onto the path. The actual entry-doc list is
# derived from the data file (the surfaces of the first step); the session doc is
# always appended because it is the always-load orientation surface and is not a
# step-1 surface in the data file.
ENTRY_STEP_NAME = "repository_entry"


# ===========================================================================
# Repo + path helpers.
# ===========================================================================
def repo_root() -> Path:
    """Return the repository root (the directory two levels above this script)."""
    return Path(__file__).resolve().parent.parent.parent


def _norm(rel: str) -> str:
    """Strip a trailing slash + normalise separators for a repo-relative path."""
    return rel.replace("\\", "/").rstrip("/")


def _exists(root: Path, rel: str) -> bool:
    """True when the repo-relative path resolves to a file OR a directory."""
    return (root / _norm(rel)).exists()


# ===========================================================================
# Findings.
# ===========================================================================
@dataclass(frozen=True)
class Finding:
    code: str  # MISSING-SURFACE / MISSING-COMPANION / MISSING-FACT-FILE / MISSING-MARKER / LOCKSTEP-BROKEN / LOCKSTEP-STEP
    subject: str  # the step id / doc path the finding is about
    detail: str

    def line(self) -> str:
        return f"ai-reading-path [{self.code}] {self.subject}: {self.detail}"


class ConfigError(Exception):
    """A required authority is missing/unreadable while the path exists (exit 2)."""


# ===========================================================================
# Data-file model.
# ===========================================================================
@dataclass
class ReadingPath:
    companion_rel: str
    fact_files: list[str]            # entry_contract.factual_claim_switch.read_before_prose
    entry_doc_rels: list[str]        # step-1 surfaces + the session doc
    present_surfaces: list[tuple[str, str]]  # (step_id, surface_rel) for presence == present
    step_ids: list[str]              # ordered step ids (for the lockstep coverage check)


def _step_id(step: dict) -> str:
    """A stable, human-readable id for a learning-path step."""
    num = step.get("step")
    name = step.get("name") or step.get("se_node") or "?"
    return f"step-{num}-{name}" if num is not None else f"step-{name}"


def build_reading_path(doc: dict) -> ReadingPath:
    """Project the parsed YAML into the fields this check needs. Fails closed on
    a structurally invalid data file (a present-but-malformed authority)."""
    if not isinstance(doc, dict):
        raise ConfigError("reading-path data file is not a mapping")

    steps = doc.get("orientation_learning_path")
    if not isinstance(steps, list) or not steps:
        raise ConfigError("orientation_learning_path is missing or not a non-empty list")

    companion = doc.get("companion_human_form") or COMPANION_DEFAULT_REL
    if not isinstance(companion, str):
        raise ConfigError("companion_human_form is not a string")

    # entry_contract.factual_claim_switch.read_before_prose (optional block, but
    # when present every listed fact file is a required surface).
    fact_files: list[str] = []
    entry_contract = doc.get("entry_contract")
    if isinstance(entry_contract, dict):
        switch = entry_contract.get("factual_claim_switch")
        if isinstance(switch, dict):
            rbp = switch.get("read_before_prose")
            if rbp is not None:
                if not isinstance(rbp, list):
                    raise ConfigError("factual_claim_switch.read_before_prose is not a list")
                fact_files = [str(x) for x in rbp]

    present_surfaces: list[tuple[str, str]] = []
    step_ids: list[str] = []
    entry_doc_rels: list[str] = []
    for step in steps:
        if not isinstance(step, dict):
            raise ConfigError("a learning-path step is not a mapping")
        sid = _step_id(step)
        step_ids.append(sid)
        surfaces = step.get("surfaces") or []
        if not isinstance(surfaces, list):
            raise ConfigError(f"{sid}: surfaces is not a list")
        is_entry_step = step.get("name") == ENTRY_STEP_NAME
        for surf in surfaces:
            if not isinstance(surf, dict):
                raise ConfigError(f"{sid}: a surface entry is not a mapping")
            path = surf.get("path")
            if not isinstance(path, str) or not path:
                raise ConfigError(f"{sid}: a surface entry has no string path")
            presence = surf.get("presence", "present")
            if presence == "present":
                present_surfaces.append((sid, path))
            if is_entry_step:
                entry_doc_rels.append(path)

    # The session doc is an always-load entry document even though it is not a
    # step-1 surface in the data file; append it (de-duplicated).
    if SESSION_DOC_REL not in entry_doc_rels:
        entry_doc_rels.append(SESSION_DOC_REL)

    return ReadingPath(
        companion_rel=companion,
        fact_files=fact_files,
        entry_doc_rels=entry_doc_rels,
        present_surfaces=present_surfaces,
        step_ids=step_ids,
    )


# ===========================================================================
# Checks.
# ===========================================================================
def check_surface_existence(root: Path, rp: ReadingPath) -> list[Finding]:
    """Every `present` surface, the companion, and the fact files must exist."""
    findings: list[Finding] = []
    for sid, rel in rp.present_surfaces:
        if not _exists(root, rel):
            findings.append(
                Finding("MISSING-SURFACE", sid, f"{_norm(rel)} (presence: present) does not exist on disk")
            )
    if not _exists(root, rp.companion_rel):
        findings.append(
            Finding("MISSING-COMPANION", "companion_human_form",
                    f"{_norm(rp.companion_rel)} does not exist on disk")
        )
    for rel in rp.fact_files:
        if not _exists(root, rel):
            findings.append(
                Finding("MISSING-FACT-FILE", "factual_claim_switch",
                        f"{_norm(rel)} (read_before_prose) does not exist on disk")
            )
    return findings


def check_entry_doc_routing(root: Path, rp: ReadingPath) -> list[Finding]:
    """Each entry doc must reference the data file or its companion."""
    findings: list[Finding] = []
    needles = (READING_PATH_REL, _norm(rp.companion_rel))
    for rel in rp.entry_doc_rels:
        doc_path = root / _norm(rel)
        if not doc_path.is_file():
            # A missing entry doc is reported as a routing finding (it cannot
            # route onto the path if it does not exist); it is NOT a config error
            # because entry docs are not the path's own authority.
            findings.append(
                Finding("MISSING-MARKER", _norm(rel),
                        "entry document does not exist; cannot route onto the canonical path")
            )
            continue
        text = doc_path.read_text(encoding="utf-8", errors="replace")
        if not any(n in text for n in needles):
            findings.append(
                Finding("MISSING-MARKER", _norm(rel),
                        f"does not reference the canonical path ({READING_PATH_REL} "
                        f"or {_norm(rp.companion_rel)})")
            )
    return findings


def check_lockstep(root: Path, rp: ReadingPath) -> list[Finding]:
    """The YAML and its companion mirror must stay in lockstep."""
    findings: list[Finding] = []
    companion_path = root / _norm(rp.companion_rel)
    if not companion_path.is_file():
        # Already reported as MISSING-COMPANION by surface existence; nothing to
        # cross-check here.
        return findings
    companion_text = companion_path.read_text(encoding="utf-8", errors="replace")

    # The companion MUST point at the data file (back-reference).
    if READING_PATH_REL not in companion_text:
        findings.append(
            Finding("LOCKSTEP-BROKEN", _norm(rp.companion_rel),
                    f"companion mirror does not reference its source {READING_PATH_REL}")
        )

    # The companion MUST carry a heading for every step the YAML declares (step
    # coverage). We look for a "Step <N>" token per declared step number.
    for sid in rp.step_ids:
        # sid is "step-<N>-<name>"; recover <N> for a tolerant heading probe.
        parts = sid.split("-", 2)
        num = parts[1] if len(parts) >= 2 else ""
        if num and f"Step {num}" not in companion_text and f"step {num}" not in companion_text:
            findings.append(
                Finding("LOCKSTEP-STEP", sid,
                        f"companion mirror has no 'Step {num}' heading for this learning-path step")
            )
    return findings


# ===========================================================================
# Changed-file scoping (changed-files-blocking mode).
# ===========================================================================
def _git_changed(root: Path, base: str) -> set[str] | None:
    """Repo-relative paths changed vs `base` (committed + uncommitted + untracked).

    Returns None when git cannot resolve the base ref (caller then treats the
    whole path as in-scope — the safe superset on a clone without the base)."""
    try:
        ok = subprocess.run(
            ["git", "-C", str(root), "rev-parse", "--verify", base],
            capture_output=True, text=True, check=False,
        )
        if ok.returncode != 0:
            return None
    except (OSError, FileNotFoundError):
        return None

    changed: set[str] = set()
    for cmd in (
        ["git", "-C", str(root), "diff", "--name-only", f"{base}", "HEAD"],
        ["git", "-C", str(root), "diff", "--name-only", "HEAD"],
        ["git", "-C", str(root), "ls-files", "--others", "--exclude-standard"],
    ):
        try:
            out = subprocess.run(cmd, capture_output=True, text=True, check=False)
        except OSError:
            continue
        for ln in out.stdout.splitlines():
            ln = ln.strip()
            if ln:
                changed.add(ln.replace("\\", "/"))
    return changed


def path_in_scope(root: Path, base: str, rp: ReadingPath) -> bool:
    """True when the path's authoring surfaces changed vs base (or scope unknown).

    The reading path is a single shared navigation surface: a change to the data
    file, the companion mirror, or any entry doc re-scopes the WHOLE path."""
    changed = _git_changed(root, base)
    if changed is None:
        return True  # base unresolved -> full scope (safe superset)
    authoring = {READING_PATH_REL, _norm(rp.companion_rel)}
    authoring.update(_norm(r) for r in rp.entry_doc_rels)
    return bool(authoring & changed)


# ===========================================================================
# Orchestration.
# ===========================================================================
def evaluate(root: Path) -> tuple[ReadingPath, list[Finding]]:
    """Load the data file and run every check. Raises ConfigError (exit 2) when a
    present-but-broken authority cannot be read."""
    rp_path = root / READING_PATH_REL
    try:
        raw = rp_path.read_text(encoding="utf-8")
    except OSError as exc:  # exists() was true but read failed
        raise ConfigError(f"cannot read {READING_PATH_REL}: {exc}") from exc
    try:
        doc = yaml.safe_load(raw)
    except yaml.YAMLError as exc:
        raise ConfigError(f"cannot parse {READING_PATH_REL}: {exc}") from exc

    rp = build_reading_path(doc)
    findings: list[Finding] = []
    findings += check_surface_existence(root, rp)
    findings += check_entry_doc_routing(root, rp)
    findings += check_lockstep(root, rp)
    return rp, findings


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--mode", default="advisory", choices=VALID_MODES)
    ap.add_argument("--base", default="origin/main",
                    help="base ref for changed-files-blocking scope (default origin/main, else HEAD)")
    ap.add_argument("--repo", default=None, help="repository root (default: two levels above this script)")
    args = ap.parse_args(argv)

    root = Path(args.repo).resolve() if args.repo else repo_root()
    if not root.is_dir():
        print(f"ai-reading-path: config error: --repo {root} is not a directory", file=sys.stderr)
        return 2

    # Greenfield: the path has not been authored yet -> vacuously clean.
    if not (root / READING_PATH_REL).exists():
        print(f"ai-reading-path [{args.mode}]: {READING_PATH_REL} not present (greenfield) — 0 finding(s)",
              file=sys.stderr)
        return 0

    try:
        rp, findings = evaluate(root)
    except ConfigError as exc:
        print(f"ai-reading-path: config error: {exc}", file=sys.stderr)
        return 2

    # Decide blocking from the mode + scope.
    blocking = False
    if args.mode == "full-blocking":
        blocking = bool(findings)
    elif args.mode == "changed-files-blocking":
        if findings and path_in_scope(root, args.base, rp):
            blocking = True

    for f in findings:
        marker = "BLOCKING" if blocking else "advisory"
        print(f"{f.line()}  [{marker}]", file=sys.stderr)

    print(
        f"ai-reading-path [{args.mode}]: {len(findings)} finding(s) over "
        f"{len(rp.step_ids)} step(s) / {len(rp.present_surfaces)} present surface(s)",
        file=sys.stderr,
    )

    return 1 if blocking else 0


if __name__ == "__main__":
    sys.exit(main())
