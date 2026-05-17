---
rule_id: 72
title: "Rule Duration Regression Check"
level: L0
view: scenarios
principle_ref: P-B
authority_refs: [ADR-0077]
enforcer_refs: [E102]
status: active
kernel_cap: 8
kernel: |
  **Every gate run records per-rule duration in `gate/log/runs/<sha>_<ts>/per-rule.ndjson`. After each successful run, `gate/lib/update_benchmark_baseline.sh` updates a rolling median over the last 5 runs at `gate/log/benchmarks/median.json`. Gate Rule 72 (`rule_duration_regression_check`) fails when any rule's current duration exceeds 2x its baseline median AND exceeds 200ms absolute. Bootstrap waits until 5 successful runs exist; until then Rule 72 vacuously passes.**
---

# Rule 72 — Rule Duration Regression Check

## Motivation

Gate performance regressions cost every engineer time. A rule that quietly
slows from 50ms to 800ms over six months adds 5 minutes to the daily gate
budget without anyone noticing until the gate runs longer than the build.
Rule 72 turns the NDJSON log produced by PR-E2 into an automatic regression
alert.

## Algorithm

For each rule in the current run's NDJSON:
1. Look up the rule's baseline median in `gate/log/benchmarks/median.json`.
2. If no baseline, skip (still bootstrapping).
3. If `current_duration_ms > baseline_median_ms * 2.0` AND
   `current_duration_ms > 200`, flag the rule as a regression.
4. If any regression flagged, fail Rule 72 with all flagged rules listed.

The 200ms absolute floor avoids noise on cheap rules where wall-clock
variance dwarfs the median.

## Baseline bootstrap

`gate/lib/update_benchmark_baseline.sh` reads the last 5 successful runs from
`gate/log/runs/*/per-rule.ndjson`, computes the median per rule, and writes
`gate/log/benchmarks/median.json` (checked into git).

## Activation

Activated 2026-05-18 by Wave 4 Track A per `D:\.claude\plans\spicy-mixing-galaxy.md`.
Pre-requisites met: PR-E2 NDJSON logger shipped (commit f8c8e95); 10+ successful
runs exist under `gate/log/runs/`.
