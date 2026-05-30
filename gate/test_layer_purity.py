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


def _write_view(root: Path, rel: str, description: str) -> Path:
    """Write a minimal one-view Structurizr fragment with the given description.

    The structural tokens (element name + key, ``include *``, ``autoLayout``,
    ``title``) are present exactly so a test can prove they are NOT scanned — only
    the ``description`` value is. Returns the absolute path written.
    """
    path = root / rel
    path.parent.mkdir(parents=True, exist_ok=True)
    body = (
        'container springAiAscend "L1-Process" "Process view — runtime/process model" {\n'
        "    include *\n"
        "    autoLayout lr\n"
        '    title "Spring AI Ascend — L1 Process View"\n'
        f'    description "{description}"\n'
        "}\n"
    )
    path.write_text(body, encoding="utf-8")
    return path


def _view_roster(*entries: tuple[str, str]):
    """Context-manager that TEMPORARILY swaps ``chk.SCANNED_VIEW_DSLS``.

    The roster is a module constant the helper reads at scan time; a test stages a
    synthetic view under a chosen rel-path and must make the helper treat it as a
    rostered view. Save/restore keeps each case hermetic.
    """
    import contextlib

    @contextlib.contextmanager
    def _cm():
        saved = chk.SCANNED_VIEW_DSLS
        chk.SCANNED_VIEW_DSLS = dict(entries)
        try:
            yield
        finally:
            chk.SCANNED_VIEW_DSLS = saved

    return _cm()


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
# L8 kebab fact-id probe (convergence scan round 4).
#
# Frame cards (ADR-0161 §4) cite tests as `test/<kebab-fqn>` fact IDs, not as
# PascalCase `FooIT` / `FooTest` tokens, so the original L8 probes (each keyed on
# `[A-Z]\w+(?:IT|Test|Spec)`) never matched the kebab surface — a genuine test
# INVENTORY written in kebab form evaded L8 by surface form. These cases lock the
# round-4 widening: a single `test/<fqn>` citation (the SANCTIONED per-anchor /
# Tests-anchoring form, parallel to the method-descriptor citation) stays in-layer;
# a 3+ kebab inventory crammed into one line is a leak; and the D3 carve-out (all
# tokens ArchUnit, or a <=2-token mechanism citation) is symmetric on the kebab
# surface. Probe-level unit tests (no staged repo needed), mirroring the
# `_parse_locus` unit cases above.
# ---------------------------------------------------------------------------
def _l8_flagged(line: str) -> bool:
    """Return True iff ``line`` is reported as an L8 leak.

    A line is a leak when some L8 trigger matches AND the D3 enforcer-citation
    carve-out does NOT spare it — exactly the decision ``scan_document`` makes for
    the L8 category.
    """
    matched = any(pat.search(line) for pat, _label in chk.TRIGGERS["L8-test-class-inventory"])
    if not matched:
        return False
    return not chk._is_d3_enforcer_citation(line)


def test_l8_sanctioned_single_kebab_citation_not_flagged() -> None:
    # The ADR-0161 §4 form: one `test/<fqn>` fact-id per bullet, optionally with a
    # one-line behaviour gloss. This is the sanctioned per-anchor / Tests-anchoring
    # citation (parallel to the method-descriptor citation) and MUST NOT be flagged.
    bare = "- `test/com-huawei-ascend-bus-spi-engine-orchestrationspiarchtest`"
    glossed = (
        "- `test/com-huawei-ascend-service-runtime-orchestration-"
        "engineportsignaturenoregressiontest` - execute is the streaming signature"
    )
    _check(
        "sanctioned_bare_single_kebab_not_flagged",
        not _l8_flagged(bare),
        "a single `test/<fqn>` bullet is the sanctioned citation form, not an inventory",
    )
    _check(
        "sanctioned_glossed_single_kebab_not_flagged",
        not _l8_flagged(glossed),
        "a single `test/<fqn>` + one-line gloss is the sanctioned ADR-0161 §4 form",
    )


def test_l8_kebab_inventory_three_plus_is_flagged() -> None:
    # The gap the round closes: a genuine test INVENTORY in kebab-fact-id form —
    # 3+ tests enumerated with concrete asserted behaviour, no FunctionPoint
    # scoping — crammed into one line. The PascalCase probes never saw it.
    backticked = (
        "Tests: `test/foo-aaatest` asserts X, `test/foo-bbbit` verifies Y, "
        "`test/foo-cccspec` checks Z."
    )
    plain = "Coverage by test/foo-onetest, test/foo-twoit, test/foo-threespec, test/foo-fourit."
    _check(
        "kebab_inventory_backticked_three_flagged",
        _l8_flagged(backticked),
        "a 3-test kebab fact-id enumeration in one line is a test catalogue (a leak)",
    )
    _check(
        "kebab_inventory_plain_four_flagged",
        _l8_flagged(plain),
        "a 4-test kebab fact-id enumeration (no backticks) is a test catalogue (a leak)",
    )


def test_l8_kebab_all_archunit_is_exempt() -> None:
    # Symmetry with the PascalCase carve-out shape 1: a kebab line whose tokens are
    # ALL ArchUnit architecture-test fact-ids is a mechanism citation, not a
    # behaviour catalogue — D3-defensible even at 3+ tokens.
    line = (
        "Held by test/a-spipuritygeneralizedarchtest, test/b-orchestrationspiarchtest, "
        "test/c-edgetocomputedirectlinkarchtest."
    )
    _check(
        "kebab_all_archunit_three_exempt",
        not _l8_flagged(line),
        "3 kebab fact-ids that are all *archtest are an ArchUnit mechanism citation (exempt)",
    )


def test_l8_kebab_mechanism_citation_threshold() -> None:
    # Symmetry with carve-out shape 3 (Rule R-C.a): a constraint may cite its
    # enforcing test plus one companion (<= 2 behaviour tests) beside an
    # enforcement clause and stay in-layer; a THIRD enumerated behaviour test makes
    # the line an inventory the clause can no longer redeem.
    two = "The constraint is enforced by test/foo-primaryit and test/foo-companionit per Rule R-C.a."
    three = "Enforced by test/foo-onit, test/foo-twoit, test/foo-threeit covering the full path."
    _check(
        "kebab_two_behaviour_tests_enforced_exempt",
        not _l8_flagged(two),
        "<=2 kebab behaviour tests beside an enforcement clause is a citation (exempt)",
    )
    _check(
        "kebab_three_behaviour_tests_even_enforced_flagged",
        _l8_flagged(three),
        "3 kebab behaviour tests are an inventory even beside an 'enforced by' clause",
    )


def test_l8_pascalcase_surface_unchanged() -> None:
    # Regression guard: the round-4 kebab widening must not change the PascalCase
    # verdict. A PascalCase table-row inventory still leaks; an all-ArchTest
    # citation is still exempt.
    table_row = "| `RunHttpContractIT` | asserts the create-run path |"
    archtest = "Enforced by `SpiPurityGeneralizedArchTest` and `OrchestrationSpiArchTest`."
    _check(
        "pascalcase_table_row_still_flagged",
        _l8_flagged(table_row),
        "a PascalCase test-inventory table row must still be flagged (no regression)",
    )
    _check(
        "pascalcase_all_archtest_still_exempt",
        not _l8_flagged(archtest),
        "an all-ArchTest PascalCase citation must still be exempt (no regression)",
    )


# ---------------------------------------------------------------------------
# C4 view-fragment rendering (the file-TYPE scope gap this wave closes).
#
# The detector originally scanned ONLY *.md, so the SECOND L0/L1-authority
# rendering — the free-text `description` strings of the Structurizr 4+1 view
# fragments mounted into workspace.dsl — was categorically out of scope. A leak
# cleaned in a view's .md twin could survive in its .dsl twin with no gate signal,
# and the markdown-only non-vacuity guard (it fired only on zero .md docs) could
# not catch a whole rendering going dark. These cases lock: the description value
# IS scanned, the surrounding structural tokens are NOT, a grandfather row pins a
# view leak by its description line, and the file-TYPE non-vacuity guard fails
# closed when the roster is present on disk yet contributes nothing.
# ---------------------------------------------------------------------------
def _leaked_categories(root: Path) -> dict:
    policy = _load(root)
    return policy.leaked_categories()


def test_view_dsl_description_leak_detected() -> None:
    # The motivating case: a route x verb / HTTP status leak in a view DESCRIPTION
    # is detected at the description's source line, at the view's layer.
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root, violations_yaml=_violations("§4 #20 : 1-1"), doc_lines=["clean"] * 5)
        rel = "architecture/views/L1-process.dsl"
        path = _write_view(
            root, rel, "Run admission via POST /v1/runs returning 409 on conflict."
        )
        with _view_roster((rel, "L1")):
            leaks = chk.scan_view_dsl(path, root, _leaked_categories(root))
        _check(
            "view_description_leak_detected",
            len(leaks) == 1
            and leaks[0].category_id == "L4-http-status-route-verb"
            and leaks[0].layer == "L1"
            and leaks[0].rel_path == rel,
            f"expected one L4 leak at L1 on {rel}, got {[(l.category_id, l.layer, l.line_no) for l in leaks]}",
        )
        # The leak is anchored at the `description` line (line 5 of the fragment
        # written by _write_view), so a grandfather row can pin it by range.
        _check(
            "view_leak_anchored_at_description_line",
            leaks and leaks[0].line_no == 5,
            f"expected the leak at the description line (5), got "
            f"{leaks[0].line_no if leaks else 'no leak'}",
        )


def test_view_dsl_structural_tokens_not_scanned() -> None:
    # A clean description with a leak-shaped token ONLY in the structural tokens
    # (element key, title) must NOT be flagged — only the description VALUE is in
    # scope. The element key carries `409` and the title carries `POST /v1/x`; both
    # are structural, neither is the description.
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root, violations_yaml=_violations("§4 #20 : 1-1"), doc_lines=["clean"] * 5)
        rel = "architecture/views/L1-process.dsl"
        path = root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        # Structural tokens carry the leak-shaped tokens; the description is clean.
        path.write_text(
            'container springAiAscend "View-409" "POST /v1/runs container" {\n'
            "    include *\n"
            "    autoLayout lr\n"
            '    title "Status 409 — POST /v1/runs view"\n'
            '    description "Run admission traverses the agent-service inbound boundary."\n'
            "}\n",
            encoding="utf-8",
        )
        with _view_roster((rel, "L1")):
            leaks = chk.scan_view_dsl(path, root, _leaked_categories(root))
        _check(
            "view_structural_tokens_not_scanned",
            leaks == [],
            "a leak-shaped token in the element key / title (not the description) "
            f"must NOT be flagged; got {[(l.category_id, l.line_no) for l in leaks]}",
        )


def test_view_dsl_clean_description_no_leak() -> None:
    # A genuinely clean, altitude-correct description (layer-interaction identity +
    # delegation pointer, the corrected form) must produce no leak.
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root, violations_yaml=_violations("§4 #20 : 1-1"), doc_lines=["clean"] * 5)
        rel = "architecture/views/L1-process.dsl"
        path = _write_view(
            root,
            rel,
            "Run admission — the agent-service inbound boundary admits a Run and "
            "dispatches to the agent-execution-engine EngineRegistry boundary; wire "
            "steps delegated to docs/contracts.",
        )
        with _view_roster((rel, "L1")):
            leaks = chk.scan_view_dsl(path, root, _leaked_categories(root))
        _check(
            "view_clean_description_no_leak",
            leaks == [],
            f"a clean layer-interaction description must not leak; got "
            f"{[(l.category_id, l.line_no) for l in leaks]}",
        )


def test_view_dsl_leak_grandfathered_by_anchored_row() -> None:
    # A view-description leak whose layer + file + category + description-line match
    # an open, locus-anchored grandfather row is tolerated (grandfathered), exactly
    # as a markdown leak would be. The view file is the row's `file`, the
    # description line (5) falls in the row's range.
    viol = f"""\
schema_version: 1
authority: ADR-0159
status: advisory
list_closed: true
violations:
  - id: LPV-view-process-route
    layer: L1
    file: architecture/views/L1-process.dsl
    locus: "view description : 1-10"
    category: L4-http-status-route-verb
    trigger: "POST /v1/runs"
    migrate_to: "docs/contracts/*.v1.yaml"
    sunset_date: {_FUTURE}
"""
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root, violations_yaml=viol, doc_lines=["clean"] * 5)
        rel = "architecture/views/L1-process.dsl"
        path = _write_view(
            root, rel, "Run admission via POST /v1/runs returning 409 on conflict."
        )
        with _view_roster((rel, "L1")):
            policy = _load(root)
            leaks = chk.scan_view_dsl(path, root, policy.leaked_categories())
            _check("view_leak_present_before_grandfather", len(leaks) == 1, "setup: one leak")
            today = chk._utc_today()
            row = chk.suppressing_row(leaks[0], policy, today) if leaks else None
        _check(
            "view_leak_tolerated_by_anchored_row",
            row is not None and row.id == "LPV-view-process-route",
            "a view-description leak inside an open anchored row's range must be "
            f"grandfathered; got {row.id if row else None}",
        )


def test_view_roster_present_but_empty_fails_closed() -> None:
    # The file-TYPE non-vacuity guard: when architecture/views/ exists on disk but
    # NONE of the rostered view files are present, the whole rendering silently fell
    # out of scope (a rename / re-org) — main MUST fail closed (exit 2), not pass
    # vacuously. This is the gap the markdown-only guard could not catch.
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root, violations_yaml=_violations("§4 #20 : 1-1"), doc_lines=["clean"] * 5)
        # Create the views directory but put a NON-rostered file in it, then point
        # the roster at a file that does not exist.
        views = root / "architecture/views"
        views.mkdir(parents=True, exist_ok=True)
        (views / "some-other.dsl").write_text("// not rostered\n", encoding="utf-8")
        with _view_roster(("architecture/views/does-not-exist.dsl", "L1")):
            rc = chk.main(["--repo", str(root), "--mode", "full-blocking"])
        _check(
            "view_roster_present_but_empty_exit_2",
            rc == 2,
            f"a present views/ dir with zero rostered files must fail closed (exit 2), got {rc}",
        )


def test_view_roster_absent_dir_does_not_fail() -> None:
    # The complement: a checkout with NO architecture/views/ directory at all has no
    # rendering to scan, so the file-TYPE guard must NOT fire — the run proceeds on
    # the markdown corpus alone. (full-blocking on a clean staged corpus -> 0.)
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        _stage(root, violations_yaml=_violations("§4 #20 : 1-1"), doc_lines=["clean"] * 5)
        # No architecture/views/ directory created.
        with _view_roster(("architecture/views/L1-process.dsl", "L1")):
            rc = chk.main(["--repo", str(root), "--mode", "full-blocking"])
        _check(
            "view_roster_absent_dir_no_guard",
            rc == 0,
            f"absent views/ dir must not trip the file-type guard; full-blocking on a "
            f"clean corpus must exit 0, got {rc}",
        )


def test_view_dsl_leak_blocks_full_blocking_end_to_end() -> None:
    # End-to-end through main: a non-grandfathered view-description leak fails
    # full-blocking (exit 1), proving the view rendering is wired into the verdict,
    # not just the standalone scan_view_dsl unit.
    with tempfile.TemporaryDirectory() as d:
        root = Path(d)
        # Empty violations list -> the leak is NOT grandfathered.
        empty_viol = (
            "schema_version: 1\nauthority: ADR-0159\nstatus: advisory\n"
            "list_closed: true\nviolations: []\n"
        )
        _stage(root, violations_yaml=empty_viol, doc_lines=["clean"] * 5)
        rel = "architecture/views/L1-process.dsl"
        _write_view(root, rel, "Run admission via POST /v1/runs returning 409 on conflict.")
        with _view_roster((rel, "L1")):
            rc = chk.main(["--repo", str(root), "--mode", "full-blocking"])
        _check(
            "view_leak_blocks_full_blocking",
            rc == 1,
            f"a non-grandfathered view-description leak must fail full-blocking (exit 1), got {rc}",
        )


# ---------------------------------------------------------------------------
# L7 JVM-descriptor probe + by-fact-id carve-out (convergence scan round 4).
#
# Frame cards (ADR-0161 section 4) cite a method via the class fact's
# `public_methods[]` entry, quoting the JVM descriptor next to its
# `code-symbol/<fqn>` fact id (`... :: <descriptor>` or `...#<descriptor>`). The
# original L7 probes recognised only the two human-readable notations the L0
# corpus used (the `->` arrow and the `name() : ReturnType` colon-return), so a
# raw JVM descriptor evaded L7 by surface form: coverage was adequate only because
# ADR-0161's fact-citation rule + the Frame-Card/DSL parity gate independently
# force every descriptor to resolve to a real fact. These cases lock the round-4
# backstop: the descriptor SHAPE is now recognised; a descriptor quoted next to
# its fact id (the sanctioned citation) is spared; a descriptor transcribed
# WITHOUT a fact id (a re-authored signature inventory) is a finding. Probe-level
# unit tests, mirroring the L8 kebab cases above.
# ---------------------------------------------------------------------------
def _l7_flagged(line: str) -> bool:
    """Return True iff ``line`` is reported as an L7 leak.

    A line is a leak when some L7 trigger matches AND the by-fact-id citation
    carve-out does NOT spare it -- exactly the decision ``scan_document`` makes
    for the L7 category.
    """
    matched = any(pat.search(line) for pat, _label in chk.TRIGGERS["L7-method-signature"])
    if not matched:
        return False
    return not chk._is_factid_method_citation(line)


def test_l7_jvm_descriptor_shape_is_recognised() -> None:
    # Non-vacuity guard: the JVM-descriptor probe must FIRE on a raw descriptor the
    # arrow/colon probes never saw. Without the carve-out these are leaks; the next
    # test proves the carve-out spares the fact-cited form.
    matched_void = any(
        pat.search("`validate(Lcom/huawei/ascend/service/runtime/runs/RunStatus;Lcom/huawei/ascend/service/runtime/runs/RunStatus;)V`")
        for pat, _ in chk.TRIGGERS["L7-method-signature"]
    )
    matched_ref = any(
        pat.search("`cancel(Ljava/lang/String;)Lorg/springframework/http/ResponseEntity;`")
        for pat, _ in chk.TRIGGERS["L7-method-signature"]
    )
    matched_prim = any(
        pat.search("`isTerminal(Lcom/huawei/ascend/service/runtime/runs/RunStatus;)Z`")
        for pat, _ in chk.TRIGGERS["L7-method-signature"]
    )
    _check(
        "jvm_descriptor_void_return_recognised",
        matched_void,
        "a `(...)V` JVM descriptor must be recognised by an L7 probe (it was blind before)",
    )
    _check(
        "jvm_descriptor_reference_return_recognised",
        matched_ref,
        "a `(...)L<fqn>;` JVM descriptor must be recognised by an L7 probe",
    )
    _check(
        "jvm_descriptor_primitive_return_recognised",
        matched_prim,
        "a `(...)Z` JVM descriptor must be recognised by an L7 probe",
    )


def test_l7_byfactid_descriptor_citation_not_flagged() -> None:
    # The sanctioned ADR-0161 section 4 form: a descriptor quoted next to its
    # `code-symbol/<fqn>` fact id, in either the `::` continuation or the `#`
    # hash-joined surface. Both MUST be spared (caught by shape, redeemed by the
    # carve-out -> net: no finding).
    colon_form = (
        "| In-frame transition guard | "
        "`code-symbol/com-huawei-ascend-service-runtime-runs-runstatemachine` :: "
        "`validate(Lcom/huawei/ascend/service/runtime/runs/RunStatus;Lcom/huawei/ascend/service/runtime/runs/RunStatus;)V` |"
    )
    hash_form = (
        "| Entry method | "
        "`code-symbol/com-huawei-ascend-bus-spi-s2c-s2ccallbacktransport#dispatch(Lcom/huawei/ascend/bus/spi/s2c/S2cCallbackEnvelope;)Ljava/util/concurrent/CompletionStage;` |"
    )
    convention_row = (
        "| A method on a class | the matching JVM-descriptor entry in that class fact's "
        "`observed_value.public_methods[]` | `code-symbols.json` | `runId()Ljava/lang/String;` |"
    )
    _check(
        "byfactid_colon_descriptor_not_flagged",
        not _l7_flagged(colon_form),
        "a descriptor cited as `code-symbol/<fqn> :: <descriptor>` is the sanctioned form (exempt)",
    )
    _check(
        "byfactid_hash_descriptor_not_flagged",
        not _l7_flagged(hash_form),
        "a descriptor cited as `code-symbol/<fqn>#<descriptor>` is the sanctioned form (exempt)",
    )
    _check(
        "byfactid_convention_row_not_flagged",
        not _l7_flagged(convention_row),
        "the Fact-ID README row defining the `public_methods[]` citation form is a citation, not an inventory",
    )


def test_l7_unbacked_descriptor_is_flagged() -> None:
    # The gap the round closes: a JVM descriptor transcribed with NO adjacent fact
    # id -- a re-authored signature inventory -- is NOT a citation and stays a leak.
    no_factid = (
        "The SPI method is "
        "`validate(Lcom/huawei/ascend/service/runtime/runs/RunStatus;Lcom/huawei/ascend/service/runtime/runs/RunStatus;)V` "
        "and returns void."
    )
    bare_inventory = "Methods: cancel(Ljava/lang/String;)Lorg/springframework/http/ResponseEntity;."
    _check(
        "unbacked_descriptor_flagged",
        _l7_flagged(no_factid),
        "a JVM descriptor with no adjacent `code-symbol/<fqn>` fact id is a re-authored signature (a leak)",
    )
    _check(
        "bare_descriptor_inventory_flagged",
        _l7_flagged(bare_inventory),
        "a bare descriptor inventory (no fact id) is a leak the backstop now catches",
    )


def test_l7_human_readable_surfaces_unchanged() -> None:
    # Regression guard: the round-4 descriptor probe must not change the verdict on
    # the human-readable notations or on the defensible one-line SPI identity.
    arrow = "resolve(EngineEnvelope) -> ExecutorAdapter is the dispatch arrow form."
    spi_identity = "A one-line SPI boundary identity such as `EnginePort` is allowed at L1."
    pkg = "The package `com.huawei.ascend.service.runtime.runs` decomposes the runtime view."
    _check(
        "arrow_signature_still_flagged",
        _l7_flagged(arrow),
        "the `->` arrow signature must still be flagged (no regression on the round-3 probe)",
    )
    _check(
        "spi_identity_still_not_flagged",
        not _l7_flagged(spi_identity),
        "a one-line SPI identity must stay defensible (the descriptor probe must not over-reach)",
    )
    _check(
        "package_decomposition_not_flagged",
        not _l7_flagged(pkg),
        "a package-decomposition sentence carries no descriptor and must not be flagged",
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
        test_l8_sanctioned_single_kebab_citation_not_flagged,
        test_l8_kebab_inventory_three_plus_is_flagged,
        test_l8_kebab_all_archunit_is_exempt,
        test_l8_kebab_mechanism_citation_threshold,
        test_l8_pascalcase_surface_unchanged,
        test_l7_jvm_descriptor_shape_is_recognised,
        test_l7_byfactid_descriptor_citation_not_flagged,
        test_l7_unbacked_descriptor_is_flagged,
        test_l7_human_readable_surfaces_unchanged,
        test_view_dsl_description_leak_detected,
        test_view_dsl_structural_tokens_not_scanned,
        test_view_dsl_clean_description_no_leak,
        test_view_dsl_leak_grandfathered_by_anchored_row,
        test_view_roster_present_but_empty_fails_closed,
        test_view_roster_absent_dir_does_not_fail,
        test_view_dsl_leak_blocks_full_blocking_end_to_end,
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
