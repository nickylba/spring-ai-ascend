#!/usr/bin/env python3
"""Tests for the AI reading-path gate helper (Rule G-31).

Covers ``gate/lib/check_ai_reading_path.py``: the three checks (surface
existence, entry-doc routing, YAML <-> companion lockstep), the three ratchet
modes (advisory / changed-files-blocking / full-blocking), the greenfield /
vacuity posture, and the fail-closed config errors. The helper is run both
in-process (unit-level projection of the data file) and via subprocess
(end-to-end exit codes), mirroring the sibling ``gate/test_feature_readiness.py``
/ ``gate/test_release_readiness_tools.py`` style.
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
HELPER = GATE_LIB / "check_ai_reading_path.py"

READING_PATH_REL = "docs/governance/ai-reading-path.yaml"
COMPANION_REL = "docs/onboarding/ai-understanding-path.md"
SESSION_DOC_REL = "docs/governance/SESSION-START-CONTEXT.md"


def _import_helper():
    sys.path.insert(0, str(GATE_LIB))
    try:
        return __import__("check_ai_reading_path")
    finally:
        sys.path.pop(0)


def write(path: Path, text: str) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(text).lstrip(), encoding="utf-8")


def _run(root: Path, *args: str) -> subprocess.CompletedProcess:
    return subprocess.run(
        [sys.executable, str(HELPER), "--repo", str(root), *args],
        text=True,
        capture_output=True,
        check=False,
    )


# A minimal-but-complete data file: two steps (step 1 = the repository_entry
# entry docs, step 2 = a product step with one present + one planned surface),
# a factual-claim switch with one fact file, and a companion pointer. Negative
# tests drop exactly one obligation from a CLEAN materialization of this model.
DATA_FILE = """
version: 1
status: active
companion_human_form: docs/onboarding/ai-understanding-path.md
entry_contract:
  factual_claim_switch:
    read_before_prose:
      - architecture/facts/generated/code-symbols.json
orientation_learning_path:
  - step: 1
    name: repository_entry
    surfaces:
      - path: README.md
        presence: present
      - path: AGENTS.md
        presence: present
  - step: 2
    name: product_definition
    surfaces:
      - path: product/PRODUCT.md
        presence: present
      - path: architecture/mappings/future-map.yaml
        presence: planned
"""


def _materialize_clean(root: Path) -> None:
    """Build a repo tree the helper reports clean (0 findings) in every mode."""
    write(root / READING_PATH_REL, DATA_FILE)
    # Present surfaces.
    write(root / "README.md", f"see {READING_PATH_REL}\n")
    write(root / "AGENTS.md", f"see {COMPANION_REL}\n")
    write(root / "product/PRODUCT.md", "# product\n")
    write(root / "architecture/facts/generated/code-symbols.json", '{"facts": []}\n')
    # The always-load session doc is an entry document and must route too.
    write(root / SESSION_DOC_REL, f"session start\nsee {READING_PATH_REL}\n")
    # Companion mirror: back-references the YAML and covers Step 1 + Step 2.
    write(
        root / COMPANION_REL,
        f"# companion\nsource: {READING_PATH_REL}\n## Step 1\n## Step 2\n",
    )


class ReadingPathUnitTests(unittest.TestCase):
    """In-process projection of the data file into the check's model."""

    def setUp(self) -> None:
        self.mod = _import_helper()

    def test_build_collects_present_surfaces_and_skips_planned(self) -> None:
        import yaml

        rp = self.mod.build_reading_path(yaml.safe_load(DATA_FILE))
        present = {rel for _, rel in rp.present_surfaces}
        self.assertIn("README.md", present)
        self.assertIn("product/PRODUCT.md", present)
        # The `planned` surface is NOT a required surface.
        self.assertNotIn("architecture/mappings/future-map.yaml", present)

    def test_entry_docs_are_step1_surfaces_plus_session_doc(self) -> None:
        import yaml

        rp = self.mod.build_reading_path(yaml.safe_load(DATA_FILE))
        self.assertIn("README.md", rp.entry_doc_rels)
        self.assertIn("AGENTS.md", rp.entry_doc_rels)
        # The product step's surfaces are NOT entry docs.
        self.assertNotIn("product/PRODUCT.md", rp.entry_doc_rels)
        # The session doc is appended as an always-load entry document.
        self.assertIn(SESSION_DOC_REL, rp.entry_doc_rels)

    def test_fact_files_collected(self) -> None:
        import yaml

        rp = self.mod.build_reading_path(yaml.safe_load(DATA_FILE))
        self.assertEqual(rp.fact_files, ["architecture/facts/generated/code-symbols.json"])

    def test_build_rejects_non_list_path(self) -> None:
        with self.assertRaises(self.mod.ConfigError):
            self.mod.build_reading_path({"version": 1, "orientation_learning_path": "nope"})

    def test_build_rejects_surface_without_path(self) -> None:
        bad = {
            "orientation_learning_path": [
                {"step": 1, "name": "repository_entry", "surfaces": [{"presence": "present"}]}
            ]
        }
        with self.assertRaises(self.mod.ConfigError):
            self.mod.build_reading_path(bad)


class ReadingPathGreenfieldTests(unittest.TestCase):
    def test_absent_data_file_is_vacuously_clean(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for mode in ("advisory", "full-blocking", "changed-files-blocking"):
                result = _run(root, "--mode", mode)
                self.assertEqual(result.returncode, 0, result.stderr)
                self.assertIn("greenfield", result.stderr)


class ReadingPathHappyPathTests(unittest.TestCase):
    def test_clean_repo_has_no_findings(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _materialize_clean(root)
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("0 finding(s)", result.stderr)

    def test_planned_surface_absence_is_not_a_finding(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _materialize_clean(root)
            # The planned surface architecture/mappings/future-map.yaml is absent
            # by construction; full-blocking must still be clean.
            self.assertFalse((root / "architecture/mappings/future-map.yaml").exists())
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 0, result.stderr)


class ReadingPathFindingTests(unittest.TestCase):
    """Each test drops exactly one obligation from the clean model."""

    def _assert_finding(self, mutate, code: str) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            _materialize_clean(root)
            mutate(root)
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 1, f"{code}: {result.stderr}")
            self.assertIn(code, result.stderr, f"expected {code}:\n{result.stderr}")

    def test_missing_present_surface(self) -> None:
        self._assert_finding(lambda r: (r / "product/PRODUCT.md").unlink(), "MISSING-SURFACE")

    def test_missing_companion(self) -> None:
        self._assert_finding(lambda r: (r / COMPANION_REL).unlink(), "MISSING-COMPANION")

    def test_missing_fact_file(self) -> None:
        self._assert_finding(
            lambda r: (r / "architecture/facts/generated/code-symbols.json").unlink(),
            "MISSING-FACT-FILE",
        )

    def test_entry_doc_without_marker(self) -> None:
        self._assert_finding(
            lambda r: (r / "README.md").write_text("no canonical marker here\n", encoding="utf-8"),
            "MISSING-MARKER",
        )

    def test_session_doc_without_marker(self) -> None:
        self._assert_finding(
            lambda r: (r / SESSION_DOC_REL).write_text("no marker\n", encoding="utf-8"),
            "MISSING-MARKER",
        )

    def test_companion_without_back_reference_breaks_lockstep(self) -> None:
        self._assert_finding(
            lambda r: (r / COMPANION_REL).write_text("# companion\n## Step 1\n## Step 2\n", encoding="utf-8"),
            "LOCKSTEP-BROKEN",
        )

    def test_companion_missing_step_heading(self) -> None:
        self._assert_finding(
            lambda r: (r / COMPANION_REL).write_text(
                f"# companion\nsource: {READING_PATH_REL}\n## Step 1\n", encoding="utf-8"
            ),
            "LOCKSTEP-STEP",
        )


class ReadingPathModeTests(unittest.TestCase):
    def _materialize_with_one_finding(self, root: Path) -> None:
        _materialize_clean(root)
        # Drop the README marker -> exactly one MISSING-MARKER finding.
        (root / "README.md").write_text("no marker\n", encoding="utf-8")

    def test_advisory_always_exits_zero_despite_findings(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._materialize_with_one_finding(root)
            result = _run(root, "--mode", "advisory")
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("[advisory]", result.stderr)
            # Confirm a finding really surfaced (so exit-0 is meaningful).
            self.assertNotIn("0 finding(s)", result.stderr)

    def test_default_mode_is_advisory(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._materialize_with_one_finding(root)
            result = _run(root)  # no --mode
            self.assertEqual(result.returncode, 0, result.stderr)
            self.assertIn("[advisory]", result.stderr)

    def test_full_blocking_exits_one_on_finding(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._materialize_with_one_finding(root)
            result = _run(root, "--mode", "full-blocking")
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("BLOCKING", result.stderr)

    def test_changed_files_blocking_blocks_when_data_file_untracked(self) -> None:
        # A non-git temp dir -> base ref unresolved -> the whole path is in scope,
        # so a finding blocks under changed-files-blocking.
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            self._materialize_with_one_finding(root)
            result = _run(root, "--mode", "changed-files-blocking", "--base", "origin/main")
            self.assertEqual(result.returncode, 1, result.stderr)
            self.assertIn("BLOCKING", result.stderr)


class ReadingPathConfigErrorTests(unittest.TestCase):
    def test_malformed_data_file_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / READING_PATH_REL, "version: 1\norientation_learning_path: not-a-list\n")
            # Even advisory must fail closed (exit 2): a present-but-broken
            # authority is never an advisory condition.
            result = _run(root, "--mode", "advisory")
            self.assertEqual(result.returncode, 2, result.stderr)
            self.assertIn("config error", result.stderr)

    def test_unparseable_yaml_fails_closed(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            write(root / READING_PATH_REL, "version: 1\n  : : not valid yaml : :\n")
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


class ReadingPathLiveCorpusTests(unittest.TestCase):
    """Smoke the helper against the real repo (advisory must always pass)."""

    def test_advisory_against_repo_root_exits_zero(self) -> None:
        result = subprocess.run(
            [sys.executable, str(HELPER), "--repo", str(REPO_ROOT), "--mode", "advisory"],
            text=True,
            capture_output=True,
            check=False,
        )
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("ai-reading-path [advisory]", result.stderr)
        # The canonical data file ships in the repo, so this is not greenfield;
        # every `present` surface it declares must resolve (no MISSING-SURFACE).
        self.assertNotIn("MISSING-SURFACE", result.stderr)
        self.assertNotIn("MISSING-COMPANION", result.stderr)
        self.assertNotIn("MISSING-FACT-FILE", result.stderr)


if __name__ == "__main__":
    unittest.main()
