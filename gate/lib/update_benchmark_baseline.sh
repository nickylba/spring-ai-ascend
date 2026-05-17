#!/usr/bin/env bash
# Update gate/log/benchmarks/median.json from the last N successful runs.
#
# Authority: Rule 72 (rule_duration_regression_check). Run after every
# successful gate run to maintain the rolling regression baseline.
#
# Usage: bash gate/lib/update_benchmark_baseline.sh [window_size=5]
#
# Reads gate/log/runs/*/per-rule.ndjson (sorted by timestamp; takes last N).
# Writes a JSON object mapping rule_slug -> median duration_ms.

set -euo pipefail
export LC_ALL=C

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$repo_root"

window="${1:-5}"
runs_dir='gate/log/runs'
out='gate/log/benchmarks/median.json'

if [[ ! -d "$runs_dir" ]]; then
  echo "No $runs_dir; nothing to bootstrap." >&2
  exit 0
fi
mkdir -p "$(dirname "$out")"

if ! command -v jq >/dev/null 2>&1; then
  echo "WARN: jq missing; cannot compute median. Writing empty baseline." >&2
  echo '{}' > "$out"
  exit 0
fi

# Take the most recent N run directories (sorted by directory name which embeds
# the unix timestamp suffix _<ts>).
mapfile -t run_files < <(find "$runs_dir" -maxdepth 2 -name 'per-rule.ndjson' -type f 2>/dev/null | sort -r | head -n "$window")
if [[ ${#run_files[@]} -eq 0 ]]; then
  echo "No per-rule.ndjson files; writing empty baseline." >&2
  echo '{}' > "$out"
  exit 0
fi

# Concatenate all runs, group by rule_slug, take median per slug, emit JSON.
cat "${run_files[@]}" | jq -s '
  group_by(.rule_slug) |
  map({
    slug: .[0].rule_slug,
    median: (
      [.[].duration_ms | numbers] |
      sort |
      (if length == 0 then 0
       elif length == 1 then .[0]
       elif length % 2 == 1 then .[length / 2 | floor]
       else (.[length / 2 - 1] + .[length / 2]) / 2
       end)
    )
  }) |
  from_entries(map({key: .slug, value: .median}))
' > "$out"

echo "Wrote $out ($(jq 'length' "$out") rules, window=${#run_files[@]} runs)"
