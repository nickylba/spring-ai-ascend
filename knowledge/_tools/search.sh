#!/usr/bin/env bash
# Advisory search over the AI knowledge system. ripgrep if available, else grep -r.
# Usage:
#   search.sh "<query>"            full-text search across knowledge/, grouped by file
#   search.sh --titles "<query>"   match markdown headings / front-matter titles only
#
# This is a convenience tool, not a gate. Load the smallest slice that answers the task.
set -euo pipefail

KROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

mode="full"
if [[ "${1:-}" == "--titles" ]]; then mode="titles"; shift; fi
query="${1:-}"
if [[ -z "$query" ]]; then
  echo "usage: search.sh [--titles] \"<query>\"" >&2
  exit 2
fi

has_rg=0; command -v rg >/dev/null 2>&1 && has_rg=1

if [[ "$mode" == "titles" ]]; then
  # Headings (#..) and front-matter title/id/topic lines.
  if [[ $has_rg -eq 1 ]]; then
    rg -n --no-heading -i -e "^#{1,6}\s.*${query}" -e "^(title|id|topic):.*${query}" "$KROOT" || true
  else
    grep -rniE "^#{1,6}[[:space:]].*${query}|^(title|id|topic):.*${query}" "$KROOT" || true
  fi
else
  if [[ $has_rg -eq 1 ]]; then
    rg -n -i --max-columns 200 "$query" "$KROOT" || true
  else
    grep -rni "$query" "$KROOT" || true
  fi
fi
