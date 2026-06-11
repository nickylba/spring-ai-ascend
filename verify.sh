#!/usr/bin/env bash
# One command == what CI runs. Kills "it was green on my machine".
#
#   bash verify.sh          # full verification (reactor + scenarios + gates + facts)
#   bash verify.sh fast     # inner loop: reactor tests only, parallel, no gates
#
# Rule G-7: on Windows hosts run through WSL.
set -euo pipefail
cd "$(dirname "$0")"

MVNW=./mvnw
if [ -n "${JAVA_HOME:-}" ]; then :; elif [ -d /Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home ]; then
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/openjdk-21.jdk/Contents/Home
fi

if [ "${1:-}" = "fast" ]; then
  # Inner loop: parallel is safe for plain `test` (no concurrent context boots
  # across module ITs the way -Pquality verify triggers them).
  exec "$MVNW" -q -T 1C test -DskipTests=false
fi

echo "[verify] 1/5 reactor install (unit + integration tests)"
"$MVNW" -q -B --strict-checksums install -DskipTests=false

echo "[verify] 2/5 scenario suite (behavior anchor)"
"$MVNW" -q -B -f examples/agent-runtime-a2a-llm-e2e/pom.xml test -DskipTests=false

echo "[verify] 3/5 gates"
bash gate/check_parallel.sh
bash gate/check_architecture_sync.sh

echo "[verify] 4/5 baseline drift"
# The baseline checker needs pyyaml; macOS system python blocks pip installs
# (PEP 668), so bootstrap a repo-local venv once and reuse it.
GATE_PY=python3
if ! python3 -c 'import yaml' >/dev/null 2>&1; then
  if [ ! -x .gate-venv/bin/python ]; then
    python3 -m venv .gate-venv
    .gate-venv/bin/pip -q install -r gate/requirements.txt
  fi
  GATE_PY=.gate-venv/bin/python
fi
"$GATE_PY" gate/lib/sync_baseline.py --check

echo "[verify] 5/5 facts consistency"
"$MVNW" -q -B -f tools/architecture-workspace exec:java@extract-facts >/dev/null
git diff -I'[0-9a-f]\{40\}' --exit-code -- architecture/facts/generated

echo "[verify] ALL GREEN"
