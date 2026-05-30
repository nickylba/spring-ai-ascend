#!/usr/bin/env python3
"""Tests for the FunctionPoint readiness gate helper (Rule G-30).

Covers ``gate/lib/check_feature_readiness.py``: the four-axis readiness
evaluation driven by ``docs/governance/feature-readiness-policy.yaml``, the
three ratchet modes (advisory / changed-files-blocking / full-blocking), the
ownership invariant, the greenfield/vacuity posture, and the fail-closed config
errors. The helper is run both in-process (unit-level resolution of the policy +
DSL parsing) and via subprocess (end-to-end exit codes), mirroring the sibling
``gate/test_release_readiness_tools.py`` / ``gate/test_template_render.py`` style.
"""
from __future__ import annotations

import subprocess
import sys
import tempfile
import textwrap
import unittest
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[1]
GATE_LIB = REPO_ROOT / "gate" / "lib"
HELPER = GATE_LIB / "check_feature_readiness.py"


def _import_helper():
    sys.path.insert(0, str(GATE_LIB))
    try:
        return __import__("check_feature_readiness")
    finally:
        sys.path.pop(0)


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(text).lstrip(), encoding="utf-8")


# The canonical policy file shipped in the repo is the schema; the fixtures reuse
# its readiness bars verbatim so the tests exercise the SAME bar mapping the gate
# enforces (status_vocabulary: design_only->proposed, mock_functional->active,
# shipped->shipped) rather than a divergent synthetic policy.
POLICY_YAML = """
version: 1
status: active
required_axes_for_shipped_work:
  value_axis:
    required: [product_claim_or_requirement, feature, function_point]
  structure_axis:
    required: [module, engineering_frame, function_point_anchor]
  join:
    required: [function_point_behavioral_join, derived_feature_to_frame_navigation]
  evidence_axis:
    required: [contract_or_no_contract_rationale, generated_fact_ref, test_ref_or_exception, gate_ref]
  decision_axis:
    required: [normalized_adr_ref]
status_rules:
  proposed:
    may_lack_facts: true
  active:
    requires_l2_design: true
    requires_frame_anchor: true
  shipped:
    requires_contract_or_exception: true
    requires_generated_facts: true
    requires_tests_or_exception: true
    requires_gate_ref: true
    requires_normalized_adr: true
status_vocabulary:
  feature_lifecycle:
    [proposed, accepted, design_only, ready_for_impl, implemented_unverified, test_verified, shipped, deprecated, removed]
  structural_axis_subset:
    design_only:
      readiness_bar: proposed
    mock_functional:
      readiness_bar: active
    shipped:
      readiness_bar: shipped
"""

EMPTY_FACTS = '{"facts": []}'


def _fp_element(
    var: str,
    fp_id: str,
    status: str,
    *,
    source_adr: str = "ADR-0001",
    extra_props: str = "",
) -> str:
    """Render one FunctionPoint DSL element with the given properties."""
    return f'''
{var} = element "{fp_id}" "FunctionPoint" "desc" "SAA FunctionPoint" {{
    properties {{
        "saa.id" "{fp_id}"
        "saa.kind" "function_point"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "{status}"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "{source_adr}"
{extra_props}
    }}
}}
'''


def _frame_element(var: str, frame_id: str) -> str:
    return f'''
{var} = element "{frame_id}" "EngineeringFrame" "desc" "SAA EngineeringFrame" {{
    properties {{
        "saa.id" "{frame_id}"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
    }}
}}
'''


def _feature_element(var: str, feature_id: str) -> str:
    return f'''
{var} = element "{feature_id}" "Feature" "desc" "SAA Feature" {{
    properties {{
        "saa.id" "{feature_id}"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
    }}
}}
'''


def _module_element(var: str, module_id: str) -> str:
    return f'''
{var} = element "{module_id}" "Module" "desc" "SAA Module" {{
    properties {{
        "saa.id" "{module_id}"
    }}
}}
'''


def _edge(src: str, dst: str, rel: str) -> str:
    return f'''
{src} -> {dst} "edge" "SAA Relationship" {{
    properties {{
        "saa.rel" "{rel}"
    }}
}}
'''


def _normalized_adr(adr_id: str, state: str) -> str:
    return f"adr: {adr_id}\ncurrent_state: {state}\n"


class _RepoBuilder:
    """Assemble a minimal repo tree for the readiness helper under a temp root."""

    def __init__(self, root: Path):
        self.root = root
        self.fp_dsl = ""
        self.frames_dsl = ""
        self.features_dsl = ""
        self.write_policy = True
        self.write_facts = True

    def with_policy(self, text: str | None) -> "_RepoBuilder":
        self.policy_text = text
        return self

    def materialize(self) -> None:
        write(self.root / "architecture/features/function-points.dsl", self.fp_dsl or "// none\n")
        write(self.root / "architecture/features/engineering-frames.dsl", self.frames_dsl or "// none\n")
        write(self.root / "architecture/features/features.dsl", self.features_dsl or "// none\n")
        if self.write_policy:
            write(self.root / "docs/governance/feature-readiness-policy.yaml", POLICY_YAML)
        if self.write_facts:
            for name in ("code-symbols.json", "tests.json", "contract-surfaces.json"):
                write(self.root / "architecture/facts/generated" / name, EMPTY_FACTS)


def _run(root: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(HELPER), "--repo", str(root), *args],
        text=True,
        capture_output=True,
        check=False,
    )


# A fully-shipped FunctionPoint that satisfies every axis: anchored by a frame,
# implemented by a module, required by a feature, carries contract+test+fact
# refs, and cites an ADR with a citeable normalized view. Used as the clean
# baseline; individual negative tests drop exactly one obligation.
_SHIPPED_EVIDENCE_PROPS = (
    '        "saa.contract_op_refs" "contract-op/createrun"\n'
    '        "saa.test_refs" "com.example.FooIT"\n'
    '        "saa.code_entrypoint_refs" "agent-service/src/main/java/Foo.java#bar"\n'
)


def _clean_shipped_repo(root: Path) -> _RepoBuilder:
    b = _RepoBuilder(root)
    b.fp_dsl = (
        _fp_element("fpFoo", "FP-FOO", "shipped", source_adr="ADR-0001", extra_props=_SHIPPED_EVIDENCE_PROPS)
        + _module_element("modSvc", "agent-service")
        + _edge("modSvc", "fpFoo", "implements")
    )
    b.frames_dsl = _frame_element("efFoo", "EF-FOO") + _edge("efFoo", "fpFoo", "anchors")
    b.features_dsl = _feature_element("featFoo", "FEAT-FOO") + _edge("featFoo", "fpFoo", "requires")
    b.materialize()
    write(root / "docs/adr/normalized/ADR-0001.yaml", _normalized_adr("ADR-0001", "active_guidance"))
    return b


class ReadinessUnitTests(unittest.TestCase):
    """In-process tests of the policy + DSL + ADR resolution helpers."""

    def setUp(self) -> None:
        self.mod = _import_helper()

    def test_policy_status_to_bar_mapping(self) -> None:
        import yaml

        policy, err = self.mod.build_policy(yaml.safe_load(POLICY_YAML))
        self.assertEqual(err, "")
        self.assertIsNotNone(policy)
        # structural-axis subset values resolve to their declared bar.
        self.assertEqual(policy.bar_for("design_only"), "proposed")
        self.assertEqual(policy.bar_for("mock_functional"), "active")
        self.assertEqual(policy.bar_for("shipped"), "shipped")
        # lifecycle states that are themselves bars map to themselves.
        self.assertEqual(policy.bar_for("proposed"), "proposed")
        # an unknown status resolves conservatively to the strict shipped bar.
        self.assertEqual(policy.bar_for("totally-unknown"), "shipped")
        self.assertTrue(policy.requires("shipped", "requires_normalized_adr"))
        self.assertTrue(policy.requires("active", "requires_l2_design"))
        self.assertFalse(policy.requires("active", "requires_normalized_adr"))

    def test_build_policy_rejects_malformed(self) -> None:
        policy, err = self.mod.build_policy({"version": 1})
        self.assertIsNone(policy)
        self.assertIn("status_rules", err)

    def test_adr_id_canonicalization(self) -> None:
        self.assertEqual(self.mod._adr_id("ADR-0020"), "ADR-0020")
        self.assertEqual(self.mod._adr_id("adr/0020-foo-bar"), "ADR-0020")
        self.assertEqual(self.mod._adr_id("ADR-118"), "ADR-0118")
        self.assertEqual(self.mod._adr_id("no adr here"), "")

    def test_slug_strips_fp_prefix(self) -> None:
        self.assertEqual(self.mod._slug("FP-CREATE-RUN"), "create-run")
        self.assertEqual(self.mod._slug("FP-S2C-CALLBACK"), "s2c-callback")

    def test_normalized_adr_state_resolution(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / "docs/adr/normalized/ADR-0001.yaml", _normalized_adr("ADR-0001", "active_guidance"))
            self.assertEqual(self.mod.normalized_adr_state(root, "ADR-0001"), "active_guidance")
            # absent view -> None (distinct from a non-citeable state).
            self.assertIsNone(self.mod.normalized_adr_state(root, "ADR-9999"))


class ReadinessGreenfieldTests(unittest.TestCase):
    def test_no_function_points_is_vacuously_clean(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            b = _RepoBuilder(root)
            b.fp_dsl = "// no function points yet\n"
            b.materialize()
            for mode in ("advisory", "full-blocking", "changed-files-blocking"):
                result = _run(root, "--mode", mode)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("greenfield", result.stderr)

    def test_missing_function_points_dsl_is_clean(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            # No DSL files at all.
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("greenfield", result.stderr)


class ReadinessHappyPathTests(unittest.TestCase):
    def test_clean_shipped_fp_has_no_findings(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _clean_shipped_repo(root)
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("0 finding(s)", result.stderr)

    def test_design_only_fp_is_skipped(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            b = _RepoBuilder(root)
            # A design_only FP missing all evidence resolves to the proposed bar
            # and must NOT produce any finding.
            b.fp_dsl = _fp_element("fpDesign", "FP-DESIGN", "design_only")
            b.materialize()
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("0 finding(s)", result.stderr)


class ReadinessShippedAxisTests(unittest.TestCase):
    """Each test drops exactly one shipped-bar obligation and asserts the finding."""

    def _repo_missing(self, root: Path, drop: str) -> None:
        """Build a clean shipped repo, then remove one obligation named by ``drop``."""
        props = _SHIPPED_EVIDENCE_PROPS
        adr_state = "active_guidance"
        anchor = True
        module = True
        feature = True
        if drop == "contract":
            props = props.replace('        "saa.contract_op_refs" "contract-op/createrun"\n', "")
        elif drop == "test":
            props = props.replace('        "saa.test_refs" "com.example.FooIT"\n', "")
        elif drop == "fact":
            props = props.replace('        "saa.code_entrypoint_refs" "agent-service/src/main/java/Foo.java#bar"\n', "")
        elif drop == "adr-view":
            adr_state = None  # do not write the normalized view at all
        elif drop == "adr-noncite":
            adr_state = "superseded"
        elif drop == "anchor":
            anchor = False
        elif drop == "module":
            module = False
        elif drop == "feature":
            feature = False

        b = _RepoBuilder(root)
        fp = _fp_element("fpFoo", "FP-FOO", "shipped", source_adr="ADR-0001", extra_props=props)
        fp += _module_element("modSvc", "agent-service")
        if module:
            fp += _edge("modSvc", "fpFoo", "implements")
        b.fp_dsl = fp
        frames = _frame_element("efFoo", "EF-FOO")
        if anchor:
            frames += _edge("efFoo", "fpFoo", "anchors")
        b.frames_dsl = frames
        feats = _feature_element("featFoo", "FEAT-FOO")
        if feature:
            feats += _edge("featFoo", "fpFoo", "requires")
        b.features_dsl = feats
        b.materialize()
        if adr_state is not None:
            write(root / "docs/adr/normalized/ADR-0001.yaml", _normalized_adr("ADR-0001", adr_state))

    def _assert_finding(self, drop: str, code: str) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._repo_missing(root, drop)
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 1, f"{drop}: {result.stderr}")
            self.assertIn(code, result.stderr, f"{drop} expected {code}:\n{result.stderr}")

    def test_missing_contract(self) -> None:
        self._assert_finding("contract", "EVIDENCE-NO-CONTRACT")

    def test_missing_test(self) -> None:
        self._assert_finding("test", "EVIDENCE-NO-TEST")

    def test_missing_fact(self) -> None:
        self._assert_finding("fact", "EVIDENCE-NO-FACT")

    def test_missing_normalized_adr_view(self) -> None:
        self._assert_finding("adr-view", "DECISION-NO-NORMALIZED-VIEW")

    def test_nonciteable_adr_state(self) -> None:
        self._assert_finding("adr-noncite", "DECISION-ADR-NOT-CITEABLE")

    def test_missing_frame_anchor(self) -> None:
        self._assert_finding("anchor", "STRUCTURE-NO-ANCHOR")

    def test_missing_module_impl(self) -> None:
        self._assert_finding("module", "STRUCTURE-NO-MODULE")

    def test_missing_feature_requires(self) -> None:
        self._assert_finding("feature", "VALUE-NO-FEATURE")

    def test_no_contract_rationale_satisfies_evidence(self) -> None:
        # An explicit no-contract rationale discharges the contract obligation.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._repo_missing(root, "contract")
            # Re-author the FP with a no_contract_rationale in place of the contract.
            props = (
                _SHIPPED_EVIDENCE_PROPS.replace(
                    '        "saa.contract_op_refs" "contract-op/createrun"\n',
                    '        "saa.no_contract_rationale" "internal orchestration step; no external contract"\n',
                )
            )
            b = _RepoBuilder(root)
            b.fp_dsl = (
                _fp_element("fpFoo", "FP-FOO", "shipped", source_adr="ADR-0001", extra_props=props)
                + _module_element("modSvc", "agent-service")
                + _edge("modSvc", "fpFoo", "implements")
            )
            b.frames_dsl = _frame_element("efFoo", "EF-FOO") + _edge("efFoo", "fpFoo", "anchors")
            b.features_dsl = _feature_element("featFoo", "FEAT-FOO") + _edge("featFoo", "fpFoo", "requires")
            b.materialize()
            write(root / "docs/adr/normalized/ADR-0001.yaml", _normalized_adr("ADR-0001", "partial_guidance"))
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertNotIn("EVIDENCE-NO-CONTRACT", result.stderr)


class ReadinessActiveBarTests(unittest.TestCase):
    def test_mock_functional_requires_anchor_and_l2(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            b = _RepoBuilder(root)
            # mock_functional -> active bar; no anchor, no L2 design.
            b.fp_dsl = _fp_element("fpMock", "FP-MOCK", "mock_functional")
            b.frames_dsl = _frame_element("efMock", "EF-MOCK")  # no anchors edge
            b.materialize()
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("STRUCTURE-NO-ANCHOR", result.stderr)
            self.assertIn("STRUCTURE-NO-L2-DESIGN", result.stderr)

    def test_active_bar_satisfied_by_anchor_and_l2(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            b = _RepoBuilder(root)
            b.fp_dsl = _fp_element("fpMock", "FP-MOCK", "mock_functional")
            b.frames_dsl = _frame_element("efMock", "EF-MOCK") + _edge("efMock", "fpMock", "anchors")
            b.materialize()
            # An L2 design landing for FP-MOCK (slug "mock").
            write(root / "architecture/docs/L2/mock/README.md", "# FP-MOCK\n")
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("0 finding(s)", result.stderr)


class ReadinessOwnershipInvariantTests(unittest.TestCase):
    def test_feature_anchoring_fp_is_a_finding(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            b = _RepoBuilder(root)
            # A Feature (value-axis node) anchoring a FunctionPoint is forbidden.
            b.fp_dsl = _fp_element("fpFoo", "FP-FOO", "design_only")
            b.features_dsl = _feature_element("featFoo", "FEAT-FOO")
            # Put the offending anchors edge in engineering-frames.dsl (where anchors live).
            b.frames_dsl = _feature_element("featBad", "FEAT-BAD") + _edge("featBad", "fpFoo", "anchors")
            b.materialize()
            # Even though FP-FOO is design_only (no per-FP findings), the ownership
            # invariant is a structural finding that blocks.
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("OWNERSHIP-NONFRAME-ANCHOR", result.stderr)

    def test_ownership_finding_blocks_even_in_changed_files_mode(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            b = _RepoBuilder(root)
            b.fp_dsl = _fp_element("fpFoo", "FP-FOO", "design_only")
            b.frames_dsl = _feature_element("featBad", "FEAT-BAD") + _edge("featBad", "fpFoo", "anchors")
            b.materialize()
            # Not a git repo -> changed-file scoping falls back to full corpus, but
            # the ownership finding blocks irrespective of scope.
            result = _run(root, "--mode", "changed-files-blocking")
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("OWNERSHIP-NONFRAME-ANCHOR", result.stderr)


class ReadinessBaselineTests(unittest.TestCase):
    """The dated baseline allow-list: changed-files-mode toleration + expiry.

    These tests run the helper with --mode full-blocking when they need a finding
    to surface deterministically (full-blocking scopes the whole corpus without
    needing a git repo) — but full-blocking IGNORES the baseline by design, so the
    toleration tests use a not-a-git-repo changed-files-blocking run (which falls
    back to full-corpus scope, exercising the baseline application path).
    """

    def _bare_shipped_repo(self, root: Path) -> None:
        """A shipped FP missing every evidence + decision obligation (many findings)."""
        b = _RepoBuilder(root)
        b.fp_dsl = _fp_element("fpBare", "FP-BARE", "shipped", source_adr="ADR-0001")
        b.materialize()

    def _write_baseline(self, root: Path, text: str) -> None:
        write(root / "docs/governance/feature-readiness-baseline.yaml", text)

    def test_absent_baseline_tolerates_nothing(self) -> None:
        # No baseline file -> changed-files-blocking (full-corpus fallback) blocks.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._bare_shipped_repo(root)
            result = _run(root, "--mode", "changed-files-blocking")
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("BLOCKING", result.stderr)
            self.assertNotIn("BASELINED", result.stderr)

    def test_open_baseline_row_tolerates_matching_finding(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            # A clean shipped FP with ONLY its test ref dropped -> exactly one
            # finding (EVIDENCE-NO-TEST). Freeze precisely that row.
            b = _RepoBuilder(root)
            props = _SHIPPED_EVIDENCE_PROPS.replace(
                '        "saa.test_refs" "com.example.FooIT"\n', ""
            )
            b.fp_dsl = (
                _fp_element("fpFoo", "FP-FOO", "shipped", source_adr="ADR-0001", extra_props=props)
                + _module_element("modSvc", "agent-service")
                + _edge("modSvc", "fpFoo", "implements")
            )
            b.frames_dsl = _frame_element("efFoo", "EF-FOO") + _edge("efFoo", "fpFoo", "anchors")
            b.features_dsl = _feature_element("featFoo", "FEAT-FOO") + _edge("featFoo", "fpFoo", "requires")
            b.materialize()
            write(root / "docs/adr/normalized/ADR-0001.yaml", _normalized_adr("ADR-0001", "active_guidance"))
            self._write_baseline(
                root,
                textwrap.dedent(
                    """
                    schema_version: 1
                    authority: ADR-0159
                    status: blocking
                    list_closed: true
                    findings:
                      - {id: FRB-1, fp_id: FP-FOO, axis: evidence, code: EVIDENCE-NO-TEST, sunset_date: 2999-01-01}
                    """
                ),
            )
            result = _run(root, "--mode", "changed-files-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("BASELINED", result.stderr)
            self.assertNotIn("BLOCKING", result.stderr)

    def test_expired_baseline_row_does_not_tolerate(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._bare_shipped_repo(root)
            self._write_baseline(
                root,
                textwrap.dedent(
                    """
                    schema_version: 1
                    authority: ADR-0159
                    status: blocking
                    list_closed: true
                    findings:
                      - {id: FRB-1, fp_id: FP-BARE, axis: evidence, code: EVIDENCE-NO-CONTRACT, sunset_date: 2000-01-01}
                    """
                ),
            )
            result = _run(root, "--mode", "changed-files-blocking")
            # The expired row tolerates nothing; the finding blocks, and the expired
            # row is flagged for removal as a NOTE.
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("BLOCKING", result.stderr)
            self.assertIn("expired", result.stderr)

    def test_full_blocking_ignores_baseline(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._bare_shipped_repo(root)
            # Even a still-open row does not save full-blocking (terminal posture).
            self._write_baseline(
                root,
                textwrap.dedent(
                    """
                    schema_version: 1
                    authority: ADR-0159
                    status: blocking
                    list_closed: true
                    findings:
                      - {id: FRB-1, fp_id: FP-BARE, axis: evidence, code: EVIDENCE-NO-CONTRACT, sunset_date: 2999-01-01}
                    """
                ),
            )
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertNotIn("BASELINED", result.stderr)

    def test_malformed_baseline_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._bare_shipped_repo(root)
            # A findings row missing 'code' is a config error (exit 2) in every mode.
            self._write_baseline(
                root,
                textwrap.dedent(
                    """
                    schema_version: 1
                    authority: ADR-0159
                    findings:
                      - {id: FRB-1, fp_id: FP-BARE, axis: evidence, sunset_date: 2999-01-01}
                    """
                ),
            )
            result = _run(root, "--mode", "advisory")
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertIn("config error (baseline)", result.stderr)

    def test_ownership_finding_never_baselined(self) -> None:
        # An ownership lie blocks even when a baseline row "covers" it.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            b = _RepoBuilder(root)
            b.fp_dsl = _fp_element("fpFoo", "FP-FOO", "design_only")
            b.frames_dsl = _feature_element("featBad", "FEAT-BAD") + _edge("featBad", "fpFoo", "anchors")
            b.materialize()
            self._write_baseline(
                root,
                textwrap.dedent(
                    """
                    schema_version: 1
                    authority: ADR-0159
                    status: blocking
                    list_closed: true
                    findings:
                      - {id: FRB-own, fp_id: FP-FOO, axis: ownership, code: OWNERSHIP-NONFRAME-ANCHOR, sunset_date: 2999-01-01}
                    """
                ),
            )
            result = _run(root, "--mode", "changed-files-blocking")
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("OWNERSHIP-NONFRAME-ANCHOR", result.stderr)
            self.assertNotIn("BASELINED", result.stderr)


class ReadinessModeTests(unittest.TestCase):
    def test_advisory_always_exits_zero_despite_findings(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            # A shipped FP missing everything -> many findings, but advisory = exit 0.
            b = _RepoBuilder(root)
            b.fp_dsl = _fp_element("fpBare", "FP-BARE", "shipped", source_adr="ADR-0001")
            b.materialize()
            result = _run(root, "--mode", "advisory")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("finding(s)", result.stderr)
            # Confirm there really were findings (so the exit-0 is meaningful).
            self.assertNotIn("0 finding(s)", result.stderr)

    def test_full_blocking_exits_one_on_finding(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            b = _RepoBuilder(root)
            b.fp_dsl = _fp_element("fpBare", "FP-BARE", "shipped", source_adr="ADR-0001")
            b.materialize()
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("BLOCKING", result.stderr)

    def test_default_mode_is_advisory(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            b = _RepoBuilder(root)
            b.fp_dsl = _fp_element("fpBare", "FP-BARE", "shipped", source_adr="ADR-0001")
            b.materialize()
            result = _run(root)  # no --mode
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("[advisory]", result.stderr)


class ReadinessConfigErrorTests(unittest.TestCase):
    def test_missing_policy_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            b = _RepoBuilder(root)
            b.fp_dsl = _fp_element("fpFoo", "FP-FOO", "shipped")
            b.write_policy = False  # FPs exist but no policy
            b.materialize()
            # Even advisory must fail closed (exit 2): a missing authority is never
            # an advisory condition once FunctionPoints exist.
            result = _run(root, "--mode", "advisory")
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertIn("config error", result.stderr)

    def test_missing_facts_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            b = _RepoBuilder(root)
            b.fp_dsl = _fp_element("fpFoo", "FP-FOO", "shipped")
            b.write_facts = False  # FPs + policy exist but no generated facts
            b.materialize()
            result = _run(root, "--mode", "advisory")
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertIn("config error", result.stderr)

    def test_bad_repo_dir_exits_two(self) -> None:
        result = subprocess.run(
            [sys.executable, str(HELPER), "--repo", str(REPO_ROOT / "no-such-dir-xyz"), "--mode", "advisory"],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 2)
        self.assertIn("is not a directory", result.stderr)


class ReadinessLiveCorpusTests(unittest.TestCase):
    """Smoke the helper against the real repo (advisory must always pass)."""

    def test_advisory_against_repo_root_exits_zero(self) -> None:
        result = subprocess.run(
            [sys.executable, str(HELPER), "--repo", str(REPO_ROOT), "--mode", "advisory"],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("feature-readiness [advisory]", result.stderr)
        self.assertIn("FunctionPoint(s)", result.stderr)


if __name__ == "__main__":
    unittest.main()
