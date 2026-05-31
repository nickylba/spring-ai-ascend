#!/usr/bin/env python3
"""Advisory integrity check for the AI knowledge system.

Scope: knowledge/ only. Checks that knowledge does not drift into corruption or
self-contradiction. It is ADVISORY — it exists to help a maintainer catch
mistakes, NOT to gate work. It is never wired into the blocking architecture gate.

Red line: if this is ever turned into a blocking/coverage gate over the corpus,
the knowledge<->governance conflation has returned. Keep it advisory.

Checks (stdlib only, no external deps):
  1. parse        — every *.yaml is loadable if PyYAML is present (else skipped);
                    every *.md is readable as utf-8.
  2. links        — every relative markdown link ](path) inside knowledge/ resolves.
  3. unique-ids   — front-matter `id:` values are unique across the tree.
  4. contradiction— no two docs claim the same id with a different `status:`.

Exit code: 0 if clean, 1 if any finding (advisory signal only).
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))  # the knowledge/ dir
LINK_RE = re.compile(r"\]\(([^)]+)\)")
FM_ID_RE = re.compile(r"^id:\s*(.+?)\s*$", re.MULTILINE)
FM_STATUS_RE = re.compile(r"^status:\s*(.+?)\s*$", re.MULTILINE)

try:
    import yaml  # optional
    HAVE_YAML = True
except Exception:
    yaml = None
    HAVE_YAML = False


def iter_files(suffixes):
    for base, dirs, files in os.walk(ROOT):
        dirs[:] = [d for d in dirs if d not in {".git", "target", "node_modules"}]
        for fn in files:
            if fn.endswith(suffixes):
                yield os.path.join(base, fn)


def front_matter(text):
    if not text.startswith("---"):
        return ""
    end = text.find("\n---", 3)
    return text[: end if end != -1 else 0]


def main():
    findings = []
    ids = {}  # id -> (path, status)

    # 1. parse
    for path in iter_files((".md", ".yaml", ".yml")):
        try:
            text = open(path, encoding="utf-8", errors="strict").read()
        except (OSError, UnicodeDecodeError) as e:
            findings.append(f"parse: {rel(path)} unreadable as utf-8 ({e.__class__.__name__})")
            continue
        if path.endswith((".yaml", ".yml")) and yaml is not None:
            try:
                yaml.safe_load(text)
            except Exception as e:
                findings.append(f"parse: {rel(path)} invalid YAML ({str(e).splitlines()[0][:80]})")

    # 2. links (markdown, relative, within repo)
    for path in iter_files((".md",)):
        try:
            text = open(path, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        fdir = os.path.dirname(path)
        for m in LINK_RE.finditer(text):
            target = m.group(1).split("#", 1)[0].strip()
            if not target or target.startswith(("http://", "https://", "mailto:")):
                continue
            resolved = os.path.normpath(os.path.join(fdir, target))
            if not os.path.exists(resolved):
                findings.append(f"links: {rel(path)} -> broken link '{target}'")

    # 3 + 4. unique ids + contradiction
    for path in iter_files((".md", ".yaml", ".yml")):
        try:
            text = open(path, encoding="utf-8", errors="replace").read()
        except OSError:
            continue
        fm = front_matter(text) if path.endswith(".md") else text[:2000]
        mid = FM_ID_RE.search(fm)
        if not mid:
            continue
        the_id = mid.group(1)
        status_m = FM_STATUS_RE.search(fm)
        status = status_m.group(1) if status_m else ""
        if the_id in ids:
            prev_path, prev_status = ids[the_id]
            findings.append(f"unique-ids: id '{the_id}' in both {rel(prev_path)} and {rel(path)}")
            if status and prev_status and status != prev_status:
                findings.append(
                    f"contradiction: id '{the_id}' status '{prev_status}' vs '{status}'"
                )
        else:
            ids[the_id] = (path, status)

    if findings:
        print(f"knowledge integrity: {len(findings)} advisory finding(s):")
        for f in findings:
            print(f"  - {f}")
        return 1
    print("knowledge integrity: clean")
    return 0


def rel(path):
    return os.path.relpath(path, os.path.dirname(ROOT))


if __name__ == "__main__":
    sys.exit(main())
