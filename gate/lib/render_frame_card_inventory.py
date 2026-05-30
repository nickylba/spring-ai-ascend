#!/usr/bin/env python3
"""Render the Frame Card GENERATED managed blocks — section (3) Type Inventory
and section (4) Internal Collaboration — from
`architecture/facts/generated/code-symbols.json`, for one frame's in-boundary
package(s).

Authority: ADR-0161 (EngineeringFrame as Package-Cluster Anchor + Card-over-DSL).
A Frame Card is a READABLE INTERPRETATION of the DSL + generated facts; it
invents no IDs and cites every factual type claim by its generated fact_id.
Sections 3 and 4 of every card live between explicit managed-block markers that
this generator owns (see `architecture/docs/L1/frames/_template.md` and that
directory's README):

    <!-- BEGIN GENERATED: type-inventory -->
    ... rendered table ...
    <!-- END GENERATED: type-inventory -->

    <!-- BEGIN GENERATED: internal-collaboration -->
    ... rendered table ...
    <!-- END GENERATED: internal-collaboration -->

The block bodies are sourced ONLY from `code-symbols.json`, filtered to the
frame's in-boundary package(s); everything OUTSIDE the markers (frontmatter,
prose sections) is hand-authored and is never touched. Until the
`EngineeringFrameFactExtractor` (ADR-0161 §6 Phase 4-5) emits
`engineering-frames.json`, the Card generator populates these blocks directly
from `code-symbols.json`; the markers remain regardless so the consistency gate
has a stable anchor.

What this generator does and does NOT do (the Card-over-DSL authority direction,
ADR-0161 §3):

  * Reads ONLY generated facts. It never reads the DSL, invents an ID, or
    asserts a relationship the facts do not support.
  * Every type row cites its canonical `code-symbol/<kebab-fqn>` fact_id,
    COPIED verbatim from the fact (never re-derived), so a row cannot drift from
    the fact's own identity.
  * Section 4 is a TYPE-COLLABORATION graph, not a runtime call graph.
    `code-symbols.json` carries no invocation edges; the only collaboration the
    facts prove is structural — `extends` / `implements` (each type's `super` +
    `interfaces`) and `references` (object types named in a type's public-method
    and record-component JVM descriptors). Every emitted edge has BOTH endpoints
    inside the frame's in-scope type set; a descriptor reference to a type
    outside the boundary is dropped, never drawn as an in-frame edge. Runtime
    call sequences belong in the frame's L2 sink (`architecture/docs/L2/...`),
    not in a Frame Card.

Scope selection. By default the blocks cover exactly ONE primary package root
(ADR-0161's default: one frame normally anchors one primary package). A frame
whose true boundary is a package cluster passes `--include-subpackages` to fold
the primary package's descendant packages into the in-scope set. In `--into`
mode the primary package is read from the card's `primary_package` frontmatter
field unless `--primary-package` overrides it.

This is a focused renderer (a sibling of `gate/lib/render_features_catalog.py`),
not the general-purpose `gate/lib/render_template.py`, because its source is the
generated fact JSON rather than a render-context YAML. Output is byte-identical
on re-emit for a fixed `code-symbols.json` (Rule G-13.b): no timestamps, no
randomness, deterministic sort order.

Usage:
    # Splice both managed blocks into an existing card (overwrites only between
    # the markers; primary package read from the card frontmatter):
    python gate/lib/render_frame_card_inventory.py \
        --into architecture/docs/L1/frames/EF-ORCHESTRATION-SPI.md

    # Cluster frame: fold descendant packages into the in-scope set:
    python gate/lib/render_frame_card_inventory.py \
        --into architecture/docs/L1/frames/EF-ACCESS-ADMISSION.md \
        --include-subpackages

    # Drift mode for the consistency gate (exit 1 if the card is stale):
    python gate/lib/render_frame_card_inventory.py \
        --into architecture/docs/L1/frames/EF-ORCHESTRATION-SPI.md --check

    # Print the marker-wrapped block(s) to stdout (no card required):
    python gate/lib/render_frame_card_inventory.py \
        --primary-package com.huawei.ascend.bus.spi.engine --emit both

Exit codes:
    0 — blocks rendered (and, with --check, the card was already up to date)
    1 — no types for the package, bad arguments, missing/duplicated/out-of-order
        markers, or --check drift
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple

REPO = Path(__file__).resolve().parents[2]
CODE_SYMBOLS = REPO / "architecture" / "facts" / "generated" / "code-symbols.json"

# Managed-block marker pairs the Frame Card template declares. The generator
# owns the body BETWEEN each pair; the markers themselves are part of the card.
BLOCKS: Dict[str, Tuple[str, str]] = {
    "type-inventory": (
        "<!-- BEGIN GENERATED: type-inventory -->",
        "<!-- END GENERATED: type-inventory -->",
    ),
    "internal-collaboration": (
        "<!-- BEGIN GENERATED: internal-collaboration -->",
        "<!-- END GENERATED: internal-collaboration -->",
    ),
}

# A descriptor object reference, e.g. `Lcom/huawei/ascend/bus/spi/engine/EnginePort;`.
_OBJ_REF_RE = re.compile(r"L([A-Za-z0-9/$_]+);")

# Frontmatter `primary_package:` line (value may be quoted or bare).
# Frontmatter `primary_package:` line. The value is the package token; a closing
# quote and/or a trailing `# comment` (as in the template) are tolerated. Anchored
# to a line starting with `primary_package:`, so it never matches the Identity
# table row (`| Primary package (...) | ... |`).
_FM_PRIMARY_RE = re.compile(
    r'^\s*primary_package\s*:\s*["\']?([A-Za-z0-9_.]*)["\']?\s*(?:#.*)?$',
    re.MULTILINE,
)


# --------------------------------------------------------------------------- #
# Fact loading
# --------------------------------------------------------------------------- #
def load_code_symbol_facts(path: Path) -> List[dict]:
    """Load the code-symbol facts. SystemExit(1) on a missing/malformed file —
    a managed block must never be emitted from a half-read fact file.
    """
    if not path.is_file():
        print(f"render_frame_card_inventory: missing fact file {path}", file=sys.stderr)
        raise SystemExit(1)
    try:
        # UTF-8 is mandatory: the facts carry non-ASCII banner punctuation and
        # this script also runs on Windows hosts whose default codec is not UTF-8.
        payload = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"render_frame_card_inventory: cannot read {path}: {exc}", file=sys.stderr)
        raise SystemExit(1)
    facts = payload.get("facts") if isinstance(payload, dict) else None
    if not isinstance(facts, list):
        print(
            f"render_frame_card_inventory: {path} has no top-level 'facts' list",
            file=sys.stderr,
        )
        raise SystemExit(1)
    return facts


# --------------------------------------------------------------------------- #
# Scope selection
# --------------------------------------------------------------------------- #
def in_scope_packages(facts: List[dict], primary: str, include_sub: bool) -> List[str]:
    """Return the sorted set of packages the blocks cover.

    Default: exactly the primary package. With include_sub: the primary plus
    every descendant package (`<primary>.<...>`) that actually carries a type.
    """
    present = {
        f["observed_value"].get("package", "")
        for f in facts
        if isinstance(f, dict) and isinstance(f.get("observed_value"), dict)
    }
    selected = set()
    prefix = primary + "."
    for pkg in present:
        if pkg == primary or (include_sub and pkg.startswith(prefix)):
            selected.add(pkg)
    return sorted(selected)


def select_types(facts: List[dict], packages: List[str]) -> List[dict]:
    """Return the in-scope type facts, sorted by FQN for deterministic output."""
    pkgset = set(packages)
    selected = [
        f
        for f in facts
        if isinstance(f, dict)
        and isinstance(f.get("observed_value"), dict)
        and f["observed_value"].get("package", "") in pkgset
    ]
    selected.sort(key=lambda f: f["observed_value"].get("fqn", ""))
    return selected


# --------------------------------------------------------------------------- #
# Descriptor parsing (structural references only — no invocation semantics)
# --------------------------------------------------------------------------- #
def descriptor_object_refs(descriptor: str) -> List[str]:
    """Return the internal-name object types named in a JVM descriptor.

    Internal names use `/` separators and `$` for nested types
    (e.g. `com/huawei/ascend/bus/spi/engine/AgentEvent$Kind`). Primitives and
    array markers carry no object reference and are ignored.
    """
    return _OBJ_REF_RE.findall(descriptor or "")


def internal_to_fqn(internal: str) -> str:
    """`com/huawei/.../AgentEvent$Kind` -> `com.huawei.....AgentEvent$Kind`."""
    return internal.replace("/", ".")


# --------------------------------------------------------------------------- #
# Collaboration-edge derivation (fact-provable only)
# --------------------------------------------------------------------------- #
def derive_edges(
    types: List[dict], scope_fqns: set
) -> List[Tuple[str, str, str]]:
    """Compute the in-scope collaboration edges (from_fqn, to_fqn, relation).

    Relations, all proved by the facts:
      * `extends`    — `observed_value.super` names an in-scope type.
      * `implements` — an `observed_value.interfaces[]` entry is in-scope.
      * `references` — an in-scope object type appears in a public-method or
                       record-component descriptor (excluding the type's own
                       supertypes — already covered by extends/implements — and
                       self-references).

    Edges to types OUTSIDE the scope set are dropped: a Frame Card draws only
    in-boundary collaboration; outbound coupling is named in the authored
    Contracts/Constraints prose, not synthesised here.
    """
    edges: List[Tuple[str, str, str]] = []
    seen = set()

    for f in types:
        ov = f["observed_value"]
        fqn = ov.get("fqn", "")
        supertypes = set()

        sup = ov.get("super", "") or ""
        if sup and sup not in ("java.lang.Object", "java.lang.Record", "java.lang.Enum"):
            supertypes.add(sup)
            if sup in scope_fqns:
                key = (fqn, sup, "extends")
                if key not in seen:
                    seen.add(key)
                    edges.append(key)

        for iface in ov.get("interfaces", []) or []:
            supertypes.add(iface)
            if iface in scope_fqns:
                key = (fqn, iface, "implements")
                if key not in seen:
                    seen.add(key)
                    edges.append(key)

        descriptors = list(ov.get("public_methods", []) or [])
        descriptors.extend(ov.get("record_components", []) or [])
        for desc in descriptors:
            for internal in descriptor_object_refs(desc):
                ref_fqn = internal_to_fqn(internal)
                if ref_fqn == fqn or ref_fqn in supertypes or ref_fqn not in scope_fqns:
                    continue
                key = (fqn, ref_fqn, "references")
                if key not in seen:
                    seen.add(key)
                    edges.append(key)

    edges.sort(key=lambda e: (e[0], e[2], e[1]))
    return edges


# --------------------------------------------------------------------------- #
# Block-body rendering (matches the template's column shapes exactly)
# --------------------------------------------------------------------------- #
def render_type_inventory_body(types: List[dict]) -> List[str]:
    """Lines for the type-inventory block: | Type | Kind | Fact ID |.

    `Type` is the full FQN (the template's column); `Fact ID` is COPIED from the
    fact (never re-derived).
    """
    lines = ["| Type | Kind | Fact ID |", "|---|---|---|"]
    for f in types:
        ov = f["observed_value"]
        fqn = ov.get("fqn", "")
        kind = ov.get("kind", "")
        fid = f.get("fact_id", "")
        lines.append(f"| `{fqn}` | {kind} | `{fid}` |")
    return lines


def short_name(fqn: str) -> str:
    """Last dotted segment of an FQN (keeps the `$`-nested suffix)."""
    return fqn.rsplit(".", 1)[-1] if "." in fqn else fqn


def render_collaboration_body(
    edges: List[Tuple[str, str, str]],
) -> List[str]:
    """Lines for the internal-collaboration block: | From | Relationship | To |."""
    lines = ["| From | Relationship | To |", "|---|---|---|"]
    if not edges:
        lines.append(
            "| _(none — no in-boundary inheritance, interface realization, or "
            "descriptor reference between two of this frame's types)_ |  |  |"
        )
        return lines
    for src, dst, rel in edges:
        lines.append(f"| `{short_name(src)}` | {rel} | `{short_name(dst)}` |")
    return lines


def render_blocks(
    types: List[dict],
) -> Dict[str, str]:
    """Return {block-name: body-text} for each managed block (no marker lines)."""
    scope_fqns = {f["observed_value"].get("fqn", "") for f in types}
    edges = derive_edges(types, scope_fqns)
    return {
        "type-inventory": "\n".join(render_type_inventory_body(types)),
        "internal-collaboration": "\n".join(render_collaboration_body(edges)),
    }


# --------------------------------------------------------------------------- #
# Splice into a card (overwrite only between the markers)
# --------------------------------------------------------------------------- #
def splice_block(card_text: str, block_name: str, body: str) -> str:
    """Replace the content between this block's marker pair with `body`.

    Fails closed (SystemExit(1)) when a marker is missing, duplicated, or its
    END precedes its BEGIN — the same non-vacuity discipline the consistency
    gate applies when it locates blocks by marker.
    """
    begin, end = BLOCKS[block_name]
    if card_text.count(begin) != 1 or card_text.count(end) != 1:
        print(
            f"render_frame_card_inventory: card must contain exactly one "
            f"'{begin}' and one '{end}' (found "
            f"{card_text.count(begin)} / {card_text.count(end)})",
            file=sys.stderr,
        )
        raise SystemExit(1)
    b_idx = card_text.index(begin)
    e_idx = card_text.index(end)
    if e_idx < b_idx:
        print(
            f"render_frame_card_inventory: '{end}' precedes '{begin}' "
            f"(out-of-order markers)",
            file=sys.stderr,
        )
        raise SystemExit(1)
    head = card_text[: b_idx + len(begin)]
    tail = card_text[e_idx:]
    return f"{head}\n{body}\n{tail}"


def primary_from_frontmatter(card_text: str) -> Optional[str]:
    """Extract the `primary_package` value from the YAML frontmatter block.

    Restricts the search to the frontmatter (the text before the first prose,
    delimited by the leading `---` fence and its closing `---`) so a stray
    `primary_package:` mention in prose can never be mistaken for the binding.
    Returns None when the value is blank or absent (a design_only frame with no
    Java package home).
    """
    block = card_text
    if card_text.lstrip().startswith("---"):
        start = card_text.index("---")
        rest = card_text[start + 3 :]
        close = rest.find("\n---")
        if close >= 0:
            block = rest[:close]
    m = _FM_PRIMARY_RE.search(block)
    if not m:
        return None
    value = m.group(1).strip()
    return value or None


# --------------------------------------------------------------------------- #
# CLI
# --------------------------------------------------------------------------- #
def parse_args(argv: List[str]) -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description=(
            "Render the Frame Card Type-Inventory + Internal-Collaboration "
            "managed blocks from generated code-symbol facts (ADR-0161)."
        )
    )
    parser.add_argument(
        "--into",
        default=None,
        help="Frame Card to splice the two managed blocks into (overwrites only "
        "between the markers). Primary package is read from the card "
        "frontmatter unless --primary-package is given.",
    )
    parser.add_argument(
        "--primary-package",
        default=None,
        help="Fully-qualified primary Java package root the frame anchors "
        "(e.g. com.huawei.ascend.bus.spi.engine). Required in stdout mode; "
        "overrides the card frontmatter in --into mode.",
    )
    parser.add_argument(
        "--include-subpackages",
        action="store_true",
        help="Fold descendant packages of the primary package into the in-scope "
        "set (for a declared package-cluster frame).",
    )
    parser.add_argument(
        "--emit",
        choices=["type-inventory", "internal-collaboration", "both"],
        default="both",
        help="stdout mode: which marker-wrapped block(s) to print (default both). "
        "Ignored in --into mode.",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="--into mode only: exit 1 if the card is not already byte-identical "
        "to a fresh splice (the consistency-gate drift check).",
    )
    return parser.parse_args(argv)


def resolve_scope(
    facts: List[dict], primary: str, include_sub: bool
) -> List[dict]:
    packages = in_scope_packages(facts, primary, include_sub)
    if not packages:
        print(
            f"render_frame_card_inventory: no types found for package "
            f"'{primary}'"
            + (" (or its subpackages)" if include_sub else "")
            + f" in {CODE_SYMBOLS.relative_to(REPO)}",
            file=sys.stderr,
        )
        raise SystemExit(1)
    return select_types(facts, packages)


def main(argv: List[str]) -> int:
    args = parse_args(argv)
    facts = load_code_symbol_facts(CODE_SYMBOLS)

    # ---- stdout mode --------------------------------------------------------
    if not args.into:
        if not args.primary_package:
            print(
                "render_frame_card_inventory: stdout mode requires --primary-package",
                file=sys.stderr,
            )
            return 1
        types = resolve_scope(facts, args.primary_package, args.include_subpackages)
        bodies = render_blocks(types)
        wanted = (
            ["type-inventory", "internal-collaboration"]
            if args.emit == "both"
            else [args.emit]
        )
        out_parts: List[str] = []
        for name in wanted:
            begin, end = BLOCKS[name]
            out_parts.append(f"{begin}\n{bodies[name]}\n{end}")
        sys.stdout.write("\n\n".join(out_parts) + "\n")
        return 0

    # ---- --into (splice) mode ----------------------------------------------
    card_path = Path(args.into)
    if not card_path.is_file():
        print(f"render_frame_card_inventory: --into card not found: {args.into}", file=sys.stderr)
        return 1
    card_text = card_path.read_text(encoding="utf-8")

    primary = args.primary_package or primary_from_frontmatter(card_text)
    if not primary:
        print(
            f"render_frame_card_inventory: {args.into} has no primary_package "
            "frontmatter value and --primary-package was not given. A design_only "
            "frame with no Java package home cannot have a fact-cited inventory; "
            "give it a package or leave the managed blocks empty.",
            file=sys.stderr,
        )
        return 1

    types = resolve_scope(facts, primary, args.include_subpackages)
    bodies = render_blocks(types)

    updated = card_text
    for name in ("type-inventory", "internal-collaboration"):
        updated = splice_block(updated, name, bodies[name])

    if args.check:
        if updated != card_text:
            print(f"DRIFT: {args.into} (managed blocks are stale)", file=sys.stderr)
            return 1
        print(f"OK:    {args.into}")
        return 0

    card_path.write_text(updated, encoding="utf-8", newline="\n")
    print(
        f"spliced {args.into} "
        f"({len(types)} types; primary package {primary}"
        + ("; +subpackages" if args.include_subpackages else "")
        + ")"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
