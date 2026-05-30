#!/usr/bin/env python3
"""Standalone unit harness for gate/lib/check_layer_purity.py (Rule G-27 / E194).

Locks the LOCUS-ANCHORED grandfather-tolerance matcher: a dated
layer-purity-temporary-violations row tolerates a leak only when the leak's line
number falls inside one of the row's enumerated locus ranges (or the row is a
deliberately anchorless ``row-level pass deferred`` whole-file entry). Before
this anchor existed the matcher paired a leak to a row on file + category alone,
so any same-category leak ANYWHERE in a file collapsed under whichever row first
declared that category for the file — granting false tolerance to leaks the
dated list never adjudicated.

The scenarios stage a synthetic repo (policy + violations + L0 document) in a
temp directory and assert the verdict for each case. Mirrors the standalone
harness pattern used by gate/test_adr_id_uniqueness.py and the sibling
map/readiness checks.

Run:  python3 gate/test_layer_purity.py
Exit: 0 when every case passes; 1 on the first failure.
"""
from __future__ import annotations

import sys
import tempfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent / "lib"))
import check_layer_purity as chk  # noqa: E402

_passed = 0
_failed = 0


def _check(name: str, cond: bool, detail: str = "") -> None:
    global _passed, _failed
    if cond:
        _passed += 1
        print(f"ok   - {name}")
    else:
        _failed += 1
        print(f"FAIL - {name}: {detail}", file=sys.stderr)


# A minimal policy fixing one leaked category with a probe that fires on a bare
# HTTP status code (mirrors the real L4-http-status-route-verb probe) so a leak
# is trivial to stage on a chosen line.
_POLICY_YAML = """\
schema_version: 1
authority: ADR-0159
status: advisory
categories:
  - id: L4-http-status-route-verb
    kind: leaked
    title: HTTP status / route-verb / header behaviour
    owned_at: [L2]
    forbidden_at: [L0, L1]
    home: "docs/contracts/*.v1.yaml"
"""


def _stage(root: Path, *, violations_yaml: str, doc_lines: list[str]) -> None:
    gov = root / "docs/governance"
    gov.mkdir(parents=True, exist_ok=True)
    (gov / "layer-purity-policy.yaml").write_text(_POLICY_YAML, encoding="utf-8")
    (gov / "layer-purity-temporary-violations.yaml").write_text(
        violations_yaml, encoding="utf-8"
    )
    l0 = root / "architecture/docs/L0"
    l0.mkdir(parents=True, exist_ok=True)
    (l0 / "ARCHITECTURE.md").write_text("\n".join(doc_lines) + "\n", encoding="utf-8")
    # Non-vacuity guard wants the L1 root to exist as a directory too, but the
    # discover step only needs at least one document; the L0 doc above suffices.


def _run(root: Path) -> int:
    # Advisory mode always exits 0; the harness inspects the loaded policy +
    # matcher directly rather than the exit code, so the mode is immaterial here.
    return chk.main(["--repo", str(root), "--mode", "advisory"])


def _load(root: Path) -> chk.Policy:
    policy, errors = chk.load_policy(root)
    assert policy is not None, f"policy failed to load: {errors}"
    return policy


def _leak(line_no: int, category_id: str = "L4-http-status-route-verb") -> chk.Leak:
    return chk.Leak(
        rel_path="architecture/docs/L0/ARCHITECTURE.md",
        layer="L0",
        line_no=line_no,
        category_id=category_id,
        label="test",
        excerpt="test",
    )


# A sunset comfortably in the future so rows are open regardless of the run date.
_FUTURE = "2099-12-31"


def _violations(locus: str, *, sunset: str = _FUTURE) -> str:
    return f"""\
schema_version: 1
authority: ADR-0159
status: advisory
list_closed: true
violations:
  - id: LPV-test-anchored
    layer: L0
    file: architecture/docs/L0/ARCHITECTURE.md
    locus: "{locus}"
    category: L4-http-status-route-verb
    trigger: "test"
    migrate_to: "docs/contracts/*.v1.yaml"
    sunset_date: {sunset}
"""


# ---------------------------------------------------------------------------
# Locus parser (unit-level — the building block the matcher rests on).
# ---------------------------------------------------------------------------
def test_parse_locus_section_prefix_range() -> None:
    ranges, _ = chk._parse_locus("§4 #20 : 520-535")
    _check(
        "parse_strips_section_label_keeps_range",
        ranges == [(520, 535)],
        f"expected [(520, 535)], got {ranges} — section-label digits (4, 20) must "
        "not leak in as single-line anchors",
    )


def test_parse_locus_single_line() -> None:
    ranges, _ = chk._parse_locus("§4 #3 : 360")
    _check("parse_single_line", ranges == [(360, 360)], f"got {ranges}")


def test_parse_locus_comma_list() -> None:
    ranges, _ = chk._parse_locus("31, 39, 67")
    _check(
        "parse_comma_separated_singles",
        ranges == [(31, 31), (39, 39), (67, 67)],
        f"got {ranges}",
    )


def test_parse_locus_range_with_paren_note() -> None:
    ranges, _ = chk._parse_locus("259-286 (P6)")
    _check(
        "parse_range_with_digitfree_note",
        ranges == [(259, 286)],
        f"got {ranges} — a digit-free (P6) note must contribute no range",
    )


def test_parse_locus_singles_with_hyphen_note() -> None:
    # A trailing note may itself carry a hyphenated token (P1-P6); range
    # extraction consumes only the real start-end pairs, so the singles list is
    # exactly the line numbers and the note's 1-6 must NOT become a range.
    ranges, _ = chk._parse_locus("22, 89, 113, 165, 207, 259 (sequenceDiagram P1-P6)")
    _check(
        "parse_singles_ignore_note_hyphen",
        ranges == [(22, 22), (89, 89), (113, 113), (165, 165), (207, 207), (259, 259)],
        f"got {ranges}",
    )


def test_parse_locus_anchorless() -> None:
    # A purely textual deferred locus (no line numbers at all) is anchorless.
    ranges_text, _ = chk._parse_locus("matched SPI signatures (row-level pass deferred)")
    _check(
        "parse_textonly_deferred_is_anchorless",
        ranges_text == [],
        f"a digit-free deferred locus must yield no ranges, got {ranges_text}",
    )
    # The critical case: a textual deferred locus that happens to embed a stray
    # in-prose digit ("3-track") MUST stay anchorless — the strict clean-spec rule
    # rejects the whole locus rather than anchoring the row to a wrong line 3.
    ranges_noisy, _ = chk._parse_locus(
        "matched RLS / 3-track / sandbox (row-level pass deferred)"
    )
    _check(
        "parse_noisy_deferred_stays_anchorless",
        ranges_noisy == [],
        "a stray in-prose digit ('3-track') in a textual deferred locus must NOT "
        f"anchor the row; expected [] (anchorless), got {ranges_noisy}",
    )
    # The sibling-pointer deferred locus is also anchorless.
    ranges_sib, _ = chk._parse_locus(
        "matched CAS / RLS (row-level pass deferred; sibling: features/task-centric-control.md)"
    )
    _check(
        "parse_sibling_pointer_deferred_is_anchorless",
        ranges_sib == [],
        f"a sibling-pointer deferred locus must be anchorless, got {ranges_sib}",
    )


# ---------------------------------------------------------------------------
# Matcher (the integration the bug lived in).
# ---------------------------------------------------------------------------
def test_in_range_leak_is_tolerated() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(
            root,
            violations_yaml=_violations("§4 #20 : 520-535"),
            doc_lines=["x"] * 600,
        )
        policy = _load(root)
        today = chk._utc_today()
        row = chk.suppressing_row(_leak(525), policy, today)
        _check(
            "leak_inside_locus_range_tolerated",
            row is not None and row.id == "LPV-test-anchored",
            "a leak at line 525 inside locus 520-535 must be tolerated by the row",
        )


def test_out_of_range_leak_is_not_tolerated() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(
            root,
            violations_yaml=_violations("§4 #20 : 520-535"),
            doc_lines=["x"] * 1100,
        )
        policy = _load(root)
        today = chk._utc_today()
        # This is the exact bug: line 999 shares category L4 and the same file,
        # but is far outside 520-535. It MUST NOT be tolerated.
        row = chk.suppressing_row(_leak(999), policy, today)
        _check(
            "leak_outside_locus_range_not_tolerated",
            row is None,
            "a same-file same-category leak at line 999 outside locus 520-535 must "
            "NOT be tolerated (this was the false-tolerance bug)",
        )


def test_comma_list_only_covers_listed_lines() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(
            root,
            violations_yaml=_violations("31, 39, 67"),
            doc_lines=["x"] * 100,
        )
        policy = _load(root)
        today = chk._utc_today()
        _check(
            "comma_locus_covers_listed_line",
            chk.suppressing_row(_leak(39), policy, today) is not None,
            "line 39 is enumerated and must be tolerated",
        )
        _check(
            "comma_locus_skips_unlisted_line",
            chk.suppressing_row(_leak(40), policy, today) is None,
            "line 40 is NOT enumerated (31/39/67) and must stay a finding",
        )


def test_anchorless_row_falls_back_to_file_category() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(
            root,
            violations_yaml=_violations("matched HTTP behaviour (row-level pass deferred)"),
            doc_lines=["x"] * 100,
        )
        policy = _load(root)
        today = chk._utc_today()
        row = policy.violations[0]
        _check(
            "anchorless_row_is_not_anchored",
            not row.locus_anchored,
            "a digit-free deferred locus must be anchorless",
        )
        _check(
            "anchorless_row_tolerates_any_line",
            chk.suppressing_row(_leak(73), policy, today) is not None,
            "an anchorless deferred row keeps file + category matching (escape hatch)",
        )


def test_expired_anchored_row_does_not_tolerate() -> None:
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(
            root,
            violations_yaml=_violations("§4 #20 : 520-535", sunset="2000-01-01"),
            doc_lines=["x"] * 600,
        )
        policy = _load(root)
        today = chk._utc_today()
        _check(
            "expired_row_inside_range_not_tolerated",
            chk.suppressing_row(_leak(525), policy, today) is None,
            "an expired row tolerates nothing even inside its locus range",
        )


def test_anchored_row_preferred_over_anchorless() -> None:
    # Two open rows match file + category: an anchored one covering line 525 and
    # an anchorless deferred one. The matcher must cite the anchored row.
    viol = f"""\
schema_version: 1
authority: ADR-0159
status: advisory
list_closed: true
violations:
  - id: LPV-deferred
    layer: L0
    file: architecture/docs/L0/ARCHITECTURE.md
    locus: "whole file (row-level pass deferred)"
    category: L4-http-status-route-verb
    trigger: "test"
    migrate_to: "docs/contracts/*.v1.yaml"
    sunset_date: {_FUTURE}
  - id: LPV-anchored
    layer: L0
    file: architecture/docs/L0/ARCHITECTURE.md
    locus: "§4 #20 : 520-535"
    category: L4-http-status-route-verb
    trigger: "test"
    migrate_to: "docs/contracts/*.v1.yaml"
    sunset_date: {_FUTURE}
"""
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root, violations_yaml=viol, doc_lines=["x"] * 600)
        policy = _load(root)
        today = chk._utc_today()
        row = chk.suppressing_row(_leak(525), policy, today)
        _check(
            "anchored_row_wins_over_anchorless_for_covered_line",
            row is not None and row.id == "LPV-anchored",
            f"expected LPV-anchored, got {row.id if row else None}",
        )
        # A line the anchored row does NOT cover falls back to the anchorless row.
        row2 = chk.suppressing_row(_leak(999), policy, today)
        _check(
            "uncovered_line_falls_back_to_anchorless",
            row2 is not None and row2.id == "LPV-deferred",
            f"expected fallback to LPV-deferred, got {row2.id if row2 else None}",
        )


def test_full_advisory_run_smoke() -> None:
    # End-to-end: advisory mode must still exit 0 and not raise on a staged repo.
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(
            root,
            violations_yaml=_violations("§4 #20 : 520-535"),
            doc_lines=["x"] * 600,
        )
        _check("advisory_run_exits_0", _run(root) == 0, "advisory mode must exit 0")


def main() -> int:
    for fn in (
        test_parse_locus_section_prefix_range,
        test_parse_locus_single_line,
        test_parse_locus_comma_list,
        test_parse_locus_range_with_paren_note,
        test_parse_locus_singles_with_hyphen_note,
        test_parse_locus_anchorless,
        test_in_range_leak_is_tolerated,
        test_out_of_range_leak_is_not_tolerated,
        test_comma_list_only_covers_listed_lines,
        test_anchorless_row_falls_back_to_file_category,
        test_expired_anchored_row_does_not_tolerate,
        test_anchored_row_preferred_over_anchorless,
        test_full_advisory_run_smoke,
    ):
        fn()
    print(f"\n{_passed} passed, {_failed} failed")
    return 1 if _failed else 0


if __name__ == "__main__":
    sys.exit(main())
