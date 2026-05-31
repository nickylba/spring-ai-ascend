# 04 — Gate-Check Inventory & ~50% Simplification Classification

**Scope:** every check in the canonical gate `gate/check_architecture_sync.sh`, classified for a ~50% rule-count reduction.
**Working tree:** branch `governance/knowledge-governance-separation`, HEAD `d70030b` (AHEAD of `origin/main` `97a4642` — verified against the live tree, not a prior exploration).
**Verification date:** 2026-06-01.

> NOTE ON MECHANISM: the gate is **strictly binary**. The only verdict helpers are `pass_rule()` and `fail_rule()` (the latter increments `fail_count`; the script exits 1 if any rule failed). There is **no severity tier** in the gate (no `advisory`/`warn`/`monitor` rule-level output — the single `INFO:` line lives *inside* Rule 121 for PMD findings and does not fail). Therefore the `CLASS` column below is a **semantic-role taxonomy assigned by this review** per the task definition, NOT a field the gate carries. "advisory / baseline_monitor / migration_scaffold / changed_files_blocking" describe the *nature of the invariant*, not a runtime severity. Every row, when it fails, blocks the gate today.

---

## 1. Headline counts (verified, not trusted from prior numbers)

| Metric | Value | How verified |
|---|---|---|
| **Gate-check rule bodies (canonical)** | **157** | `extract_rules.sh` awk over `# Rule N — slug` headers before `# === END OF RULES ===` = 157; **matches** `architecture-status.yaml#baseline_metrics.active_gate_checks: 157`; `check_parallel.sh` executes 157 and Rule 88 (serial↔parallel parity) + Rule 91 (manifest count) self-enforce it. |
| Distinct rule *numbers* | 155 | Rule 11 and Rule 28 each carry **two distinct check bodies** that reuse the number (11=envelope-cap fingerprint + tenant-id-on-record; 28=release-note baseline-truth + constraint-enforcer-coverage meta). Both halves are real checks → 157 bodies. |
| `gate/rules/rule-*.sh` extracted files | 155 | Same collapse: the 2 duplicate-number bodies overwrite to one filename each. Rule 92 (`gate_rules_corpus_freshness`) checks these mirror the canonical headers. |
| **Gate self-tests** | **287 unique assertion IDs** across **183 `test_*()` functions** (296 `ok` + 300 `fail` call sites) | `gate/test_architecture_sync_gate.sh`; runtime `TOTAL=passed+failed`; **matches** `gate_executable_test_cases: 287`. |
| Grandfather / exemption / allowlist `.txt` files | 16 | `gate/*.txt` (see §6). |
| Helper scripts feeding rules | 21 in `gate/lib/`, 5 top-level `gate/*.sh` invoked by rules | — |

**Verdict on the 157 claim: CONFIRMED.** The status YAML, the canonical manifest, the parallel runner, and the extracted-file corpus all agree at 157 (155 numbers + 2 reused).

---

## 2. Per-class counts (this review's taxonomy)

| CLASS | Count | Meaning |
|---|---|---|
| **safety_core** (blocking, real product/security/tenant/contract/release risk) | **18** | Failure = a genuine runtime/security/tenant/release hazard. The irreducible core. |
| **blocking_real_code** (structural code invariant, lower blast radius) | 9 | Asserts a real Java/CI/build invariant but the consequence of drift is design-integrity, not direct prod/tenant breach. |
| **corpus_truth / doc-altitude** (blocking today; mostly name/link/path/baseline consistency) | 71 | Keeps the prose/yaml corpus internally consistent. The bulk of the retire/demote tail. |
| **baseline-only** (asserts a count equals another count) | 11 | Pure number-equality between two surfaces. |
| **frozen-ADR-derived** (enforces an early decision now frozen) | 18 | Schema-presence/well-formedness of design-phase governance YAMLs + frozen W1/W2 contract decisions. |
| **migration_scaffold** (armed-but-vacuous / design-phase placeholder / forward contract) | 12 | Passes vacuously today; "arms" at a future wave. No live subject matter. |
| **meta / self-governance** (rules governing the rule/enforcer/card system itself) | 17 | Card↔kernel parity, enforcer wellformedness, namespace discipline, META self-application. |
| **advisory-in-spirit** (gate-script hygiene / quality-report presence; non-product) | 1 | Rule 121 (whitebox quality; PMD is INFO-only). |
| **TOTAL** | **157** | |

> The classes are mutually exclusive *as assigned*; many rules could carry two tags (e.g. a baseline-only check is also corpus_truth). Each rule is placed in its **dominant** bucket. The retire/demote tail (§5) = corpus_truth + baseline-only + frozen-ADR + migration_scaffold + most meta = **~118 rules**, comfortably exceeding the 50% reduction target while leaving the 18-rule safety core (+ 9 blocking_real_code) intact.

---

## 3. SAFETY CORE — the 18 irreducible rules

These protect a **real** product/security/tenant/contract/release invariant. Removing any one re-opens a concrete hazard class. **Keep all.**

| # | Slug | Invariant protected | Why it is core |
|---|---|---|---|
| 28a | tenant_column_present | Every tenant-scoped `CREATE TABLE` declares `tenant_id` | Tenant-isolation at the storage layer; a missing column = cross-tenant data leak. |
| 28b | high_cardinality_tag_guard | No `Tag.of("run_id"/"jwt_sub"/"body"/…)` on a metric in main java | Metric cardinality explosion + PII (jwt_sub/body) into telemetry. |
| 28c | no_secret_patterns | gitleaks-style sweep (AWS keys, PEM private keys, GitHub PATs) of tracked files | Credential-leak prevention. Direct security. |
| 50 | rls_for_new_tenant_tables | Flyway migration with `tenant_id` must `ENABLE ROW LEVEL SECURITY` (or be grandfathered) | Postgres RLS is the runtime tenant-isolation enforcement; the column alone is inert without it. |
| 11 (1st) | shipped_envelope_fingerprint_present | `InMemoryCheckpointer` contains `MAX_INLINE_PAYLOAD_BYTES` (§4 #13 16-KiB cap) | Memory-exhaustion / DoS guard on the checkpoint path. |
| 11 (2nd) | contract_spine_tenant_id_required | `Run`/`IdempotencyRecord` records declare `String tenantId` | Tenant identity on the core persistence spine. |
| 12 | inmemory_orchestrator_posture_guard_present | All 3 in-memory components call `AppPostureGate.requireDev*` | Prevents in-memory (non-durable) components silently running in prod posture. |
| 24.c | runlifecycle_cancel_reauthz_shipped | `RunController` exposes `POST /v1/runs/{id}/cancel` with tenant re-validation | Cancel is a privileged cross-tenant-sensitive op; re-authz must exist. |
| 47 | no_blocking_io_in_runtime_main | Runtime main java excludes `RestTemplate`/`JdbcTemplate` imports | Reactive-thread starvation; blocking I/O on the cognitive kernel = liveness hazard. |
| 48 | no_thread_sleep_in_business_code | Main java excludes `Thread.sleep`/`TimeUnit.*.sleep` | Same liveness class (Chronos-hydration discipline). |
| 16 | http_contract_w1_tenant_and_cancel_consistency | HTTP contract: no `replace X-Tenant-Id`, no CREATED-as-initial, no DELETE-cancel, no W2 idempotency-replay overclaim | Tenant header + cancel + idempotency wire-contract truth. Contract risk. |
| 105 | edge_no_direct_compute_link | Edge-plane modules must not import compute-control classes / construct direct HTTP clients (route via IngressGateway) | Plane-isolation boundary (ADR-0089). Armed-but-load-bearing once SDK lands; kept core for the boundary it guards. |
| 4 | ci_no_or_true_mask | No `gate/run_* \|\| true` in CI workflows | Prevents the gate itself being green-washed in CI. Release-integrity. |
| 89 | self_test_harness_fail_closed_coverage | Self-test harness fails closed (passed≠TOTAL) + manifest-derived TOTAL + ≥1 fixture per prevention rule | The gate that keeps the *test harness* honest; without it the whole self-test surface can silently pass. |
| 130 | feature_lifecycle_validity | Every `saa.status` in `features.dsl` ∈ 9-state lifecycle | Blocking (G-14); feature-registry integrity, release truth. |
| 131 | fact_layer_integrity | Fact-layer dirs/schema/provenance/banner/resolver present + parse (`--enforce a,b,c,d`) | Blocking (G-15); the machine-readable fact authority underpinning AI-readable release evidence. |
| 7 | shipped_impl_paths_exist | Every `shipped: true` row's `implementation:` path exists on disk | Release truth — a shipped claim with a missing impl path is a false release statement. |
| 19 | shipped_row_tests_evidence | Every `shipped: true` row has non-empty `tests:` pointing at real files | Release truth — "shipped" requires real test evidence (D-4/D-5 ship gate). |

**Safety-core count: 18.**

Borderline (kept OUT of core, classed `blocking_real_code` — defensible to promote): Rule 6 (metric namespace), Rule 53/54 (cursor-flow IT + skill-resolver impl presence), Rule 29.c (quickstart smoke job), Rule 81 (skeleton module has no prod java), Rule 10 (no dep on deleted module), Rule 65/66 (pom↔metadata dep/SPI parity). These assert real code/build facts but their drift is integrity/regression, not direct tenant/security/release-falsehood.

---

## 4. Full per-rule table (all 157, in gate order)

Legend for CLASS: `SAFETY` = safety_core · `CODE` = blocking_real_code · `CORPUS` = corpus_truth/doc-altitude · `BASE` = baseline-only · `FROZEN` = frozen-ADR-derived · `SCAF` = migration_scaffold (armed/vacuous) · `META` = meta/self-governance · `ADV` = advisory-in-spirit. **R/D** column: K=keep, D=demote/retire candidate.

| # | slug | CLASS | R/D | invariant / note |
|---|---|---|---|---|
| 1 | status_enum_invalid | CORPUS | D | status: values in architecture-status.yaml ∈ enum. Doc hygiene. |
| 2 | delivery_log_parity | CORPUS | D | gate/log/*.json sha field == filename. Log-artifact hygiene. |
| 3 | eol_policy | CODE | K | gate/*.sh must be LF. Cross-platform exec correctness. |
| 4 | ci_no_or_true_mask | SAFETY | K | no `\|\| true` masking gate in CI. Release-integrity. |
| 5 | required_files_present | CORPUS | K | contract-catalog.md + openapi-v1.yaml exist. Cheap presence. |
| 6 | metric_naming_namespace | CODE | K | java metric names use `springai_ascend_` prefix; no `springai_fin_`. |
| 7 | shipped_impl_paths_exist | SAFETY | K | shipped:true impl paths exist. Release truth. |
| 8 | no_hardcoded_versions_in_arch | CORPUS | D | module ARCH.md no inline x.y.z version. Doc-altitude. |
| 9 | openapi_path_consistency | CORPUS | D | `/v3/api-docs` documented in platform ARCH. Doc consistency. |
| 10 | module_dep_direction | CODE | K | agent-service pom must not depend on deleted agent-platform/runtime. |
| 11(a) | shipped_envelope_fingerprint_present | SAFETY | K | InMemoryCheckpointer MAX_INLINE_PAYLOAD_BYTES (16-KiB cap). |
| 12 | inmemory_orchestrator_posture_guard_present | SAFETY | K | 3 in-memory components call AppPostureGate.requireDev. |
| 13 | contract_catalog_no_deleted_spi_or_starter_names | CORPUS | D | catalog free of 16 deleted names. Name-drift guard. |
| 14 | module_arch_method_name_truth | CORPUS | D | single hard-coded `probe.check()` typo guard. Stale/narrow. |
| 15 | no_active_refs_deleted_wave_plan_paths | CORPUS | D | active .md must not ref 2 deleted plan paths. Frozen path-drift. |
| 16 | http_contract_w1_tenant_and_cancel_consistency | SAFETY | K | tenant/cancel/idempotency wire-contract truth (5 sub-checks). |
| 17 | contract_catalog_spi_table_matches_source | CORPUS | D | catalog lists 7 SPIs; OssApiProbe placement. Catalog-shape. |
| 18 | deleted_spi_starter_names_outside_catalog | CORPUS | D | widened Rule 13 across corpus. Name-drift guard. |
| 19 | shipped_row_tests_evidence | SAFETY | K | shipped rows have real test evidence. Release/ship-gate truth. |
| 20 | module_metadata_truth | CORPUS | D | README no 2 ghost class names. Narrow name guard. |
| 21 | bom_glue_paths_exist | CORPUS | D | BoM no 5 ghost impl paths. Narrow path guard. |
| 22 | lowercase_metrics_in_contract_docs | CORPUS | D | no `SPRINGAI_ASCEND_<lc>` in docs. Doc casing. |
| 23 | active_doc_internal_links_resolve | CORPUS | K | markdown links resolve. Broad doc-integrity (defensible keep). |
| 24 | shipped_row_evidence_paths_exist | CORPUS | D | l2_documents/latest_delivery paths exist. Overlaps 7/19. |
| 25 | peripheral_wave_qualifier | CORPUS | D | "Primary impl:"/"Sidecar adapter" need wave qualifier. Doc-altitude. |
| 26 | release_note_shipped_surface_truth | CORPUS | D | release notes no RunLifecycle/RunContext/AppPostureGate overclaim. Frozen prose guard. |
| 27 | active_entrypoint_baseline_truth | BASE | D | README 4 baseline counts == status YAML claim. |
| 28(a) | release_note_baseline_truth | BASE | D | release-note baseline table == canonical counts. |
| 29 | whitepaper_alignment_matrix_present | CORPUS | D | matrix lists 20 fixed concepts. Frozen-doc presence. |
| 28a | tenant_column_present | SAFETY | K | CREATE TABLE declares tenant_id. Tenant isolation. |
| 28b | high_cardinality_tag_guard | SAFETY | K | no high-card/PII metric tags. Security/telemetry. |
| 28c | no_secret_patterns | SAFETY | K | secret-pattern sweep. Security. |
| 28d | out_of_scope_name_guard | SCAF | D | 10 W2+ deferred names absent from main. Design-phase scaffold. |
| 28e | module_count_invariant | BASE | D | root pom exactly 8 `<module>`. Hardcoded; Rule 64 is data-driven dup. |
| 28f | enforcers_yaml_wellformed | META | K | enforcers.yaml rows well-formed (5 fields + legal kind). |
| 28g | no_prose_only_constraint_marker | META | D | no TODO:enforce markers in CLAUDE/ARCH/ADRs. Meta-hygiene. |
| 28h | l1_review_checklist_present | CORPUS | D | ADRs 0055-0060 carry §16 checklist. Frozen-ADR doc shape. |
| 28i | plan_enforcer_table_in_sync | META | D | plan §11 IDs == enforcers IDs. Plan lives outside repo → usually skipped. |
| 28j | enforcer_artifact_paths_exist | META | K | every enforcers.yaml artifact path+anchor resolves. Enforcer integrity. |
| 28k | javadoc_enforcer_citation_semantic_check | META | D | test Javadoc `#E<n>` citation matches E-row artifact. Narrow meta. |
| 28(b) | constraint_enforcer_coverage | META | D | enforcers.yaml references CLAUDE.md + ARCH.md (presence-only meta). |
| 30 | telemetry_vertical_constraint_coverage | FROZEN | D | §4 #53-59 each cited by an enforcer. Frozen vertical. |
| 31 | quickstart_present | CORPUS | K | docs/quickstart.md exists + linked from README. Onboarding. |
| 32 | competitive_baselines_present_and_wellformed | FROZEN | D | competitive-baselines.yaml has 4 pillars. Schema-presence. |
| 33 | release_note_references_four_pillars | CORPUS | D | latest note names 4 pillars. Doc-altitude. |
| 34 | module_metadata_present_and_complete | CODE | K | every module pom has module-metadata.yaml w/ 4 keys. Build-meta. |
| 35 | dfx_yaml_present_and_wellformed | FROZEN | D | platform/domain modules have dfx yaml w/ 5 dims. Frozen DFX scheme. |
| 36 | domain_module_has_spi_package | CODE | D | domain modules declare spi_packages resolving on disk. |
| 37 | architecture_artefact_front_matter | CORPUS | D | ARCH/L2/ADR carry level:+view: front-matter. Doc-altitude. |
| 38 | architecture_graph_well_formed | META | K | architecture-graph.yaml builds+validates (idempotent). |
| 39 | review_proposal_front_matter | CORPUS | D | review docs validate front-matter only if opted-in. Mostly vacuous. |
| 40 | enforcer_reachable_from_principle | META | D | every enforcer has a rule-edge in graph. Meta-graph. |
| 41 | enforcer_anchor_resolves | META | D | graph artefact anchors resolve. Overlaps 28j. |
| 42 | architecture_graph_idempotent | META | K | twice-built graph byte-identical. Determinism. |
| 43 | new_adr_must_be_yaml | CORPUS | K | highest-numbered ADR is .yaml. Format ratchet (cheap). |
| 44 | frozen_doc_edit_path_compliance | SCAF | D | freeze_id edits need review proposal. No-op today (all freeze_id null). |
| 45 | bus_channels_three_track_present | FROZEN | D | bus-channels.yaml 3 channels, unique physical_channel. Schema-presence. |
| 46 | cursor_flow_documented | FROZEN | D | openapi declares TaskCursor + x-cursor-flow. Schema-presence. |
| 47 | no_blocking_io_in_runtime_main | SAFETY | K | runtime main excludes RestTemplate/JdbcTemplate. Liveness. |
| 48 | no_thread_sleep_in_business_code | SAFETY | K | main excludes Thread.sleep. Liveness. |
| 49 | deployment_plane_in_module_metadata | CODE | D | module-metadata declares deployment_plane ∈ enum. |
| 50 | rls_for_new_tenant_tables | SAFETY | K | tenant tables ENABLE ROW LEVEL SECURITY. Tenant isolation. |
| 51 | skill_capacity_yaml_present_and_wellformed | FROZEN | D | skill-capacity.yaml schema (caps + queue_strategy). Schema-presence. |
| 52 | sandbox_policies_yaml_present_and_wellformed | FROZEN | D | sandbox-policies.yaml default_policy 6 keys. Schema-presence. |
| 53 | cursor_flow_integration_test_present | CODE | K | RunCursorFlowIT asserts 202<200ms. Real test presence. |
| 54 | skill_capacity_runtime_resolver_present | CODE | K | DefaultSkillResilienceContract resolve(String,String)+tryAcquire. Real impl. |
| 55 | engine_envelope_yaml_present_and_wellformed | FROZEN | D | engine-envelope.v1.yaml schema header + known_engines. |
| 56 | engine_registry_covers_all_known_engines | CODE | K | yaml id ⇄ ENGINE_TYPE bidirectional. Real compile-time consistency. |
| 57 | engine_hooks_yaml_present_and_wellformed | FROZEN | D | engine-hooks.v1.yaml 9 hooks ⇄ HookPoint enum. |
| 58 | s2c_callback_yaml_present_and_wellformed | FROZEN | D | s2c-callback.v1.yaml request/response/outcome schema. |
| 59 | evolution_scope_yaml_present_and_wellformed | FROZEN | D | evolution-scope.v1.yaml 3 discriminator blocks. |
| 60 | schema_first_domain_contracts | FROZEN | D | no new prose-enum sites (grandfather list CLOSED 2026-05-16). |
| 61 | legacy_powershell_gate_deprecated | FROZEN | D | PS gate carries DEPRECATED banner + absent from impl list. Frozen decision. |
| 62 | contract_yaml_declares_status | META | D | contract YAMLs declare status: ∈ enum. Meta-vocab. |
| 63 | release_note_retracted_tag_qualified | CORPUS | D | retracted tags marked (retracted)/Historical. Doc-altitude. |
| 64 | module_count_data_driven | BASE | K | pom `<module>` count == status YAML canonical. (Keep this; retire 28e.) |
| 65 | module_metadata_pom_dep_parity | CODE | D | pom huawei deps ⊆ metadata allowed_dependencies. |
| 66 | spi_package_exhaustiveness | CODE | D | every */spi/ dir declared in metadata spi_packages. |
| 67 | claude_md_kernel_size_bounded | META | D | CLAUDE.md rule kernels ≤ card kernel_cap. Token-budget meta. |
| 68 | claude_md_kernel_matches_card | META | K | CLAUDE.md kernel byte-matches card kernel. Authority-drift guard. |
| 69 | every_active_rule_has_card | META | K | every CLAUDE rule heading has a card. Authority integrity. |
| 70 | always_loaded_budget_enforced | META | K | no always-loaded file exceeds ceiling. Context-budget discipline. |
| 71 | deferred_doc_not_in_always_loaded | SCAF | D | CLAUDE-deferred.md not auto-loaded. File now eliminated → near-vacuous. |
| 73 | gate_config_well_formed | META | K | gate/config.yaml validates vs schema. Gate self-integrity. |
| 74 | linux_first_dev_doc_present | CORPUS | D | dev-environment.md names WSL2/WSL1/Linux. Doc-presence. |
| 11(b) | contract_spine_tenant_id_required | SAFETY | K | Run/IdempotencyRecord declare String tenantId. Tenant spine. |
| 24.c | runlifecycle_cancel_reauthz_shipped | SAFETY | K | cancel route + tenant re-validation in RunController. |
| 29.c | quickstart_smoke_job_present | CODE | K | CI has quickstart-smoke polling /v1/health. Release smoke. |
| 72 | rule_duration_regression_check | SCAF | D | perf regression vs median.json. Vacuous (needs jq + ≥5 baseline samples). |
| 75 | spi_packages_populated | CODE | D | declared spi packages have ≥1 real .java. Build-meta. |
| 76 | no_split_spi_packages | CODE | K | a spi package declared by exactly 1 module. JPMS correctness. |
| 77 | spi_packages_dot_spi_convention | CORPUS | D | spi package names end/contain `.spi`. Naming convention. |
| 78 | dfx_spi_packages_match_module_metadata | FROZEN | D | dfx yaml spi_packages == metadata set. Frozen DFX parity. |
| 79 | rule_79_runbook_present_and_cited | CORPUS | D | debug-first runbook exists + title string + cited by card. Doc-presence. |
| 80 | s2c_callback_signal_historical_only_in_authority | CORPUS | D | deleted type name only in historical-marked paragraphs. Name-drift. |
| 81 | skeleton_module_has_no_production_java | CODE | K | skeleton-status modules contain only package-info/ADR-waived stubs. |
| 82 | baseline_metrics_single_source | BASE | K | baseline_metrics block exists; README counts agree numerically. SSOT. |
| 83 | design_only_contract_registered_in_catalog | CORPUS | D | design-only contracts listed in catalog + cite real ADR. |
| 84 | active_module_architecture_path_truth | CORPUS | D | active module ARCH path claims resolve or marked historical. Doc-altitude. |
| 85 | catalog_spi_row_matches_module_spi_metadata | CORPUS | D | catalog SPI rows backed by metadata+dfx; header count parity. |
| 86 | root_architecture_count_and_path_truth | BASE | D | root ARCH "N-module" claims == pom count. Number-truth. |
| 87 | status_yaml_allowed_claim_module_name_truth | CORPUS | D | allowed_claim free of stale module names. Name-drift. |
| 88 | serial_parallel_gate_slug_parity | META | K | parallel runner executes == serial-defined rule set. Gate integrity. |
| 89 | self_test_harness_fail_closed_coverage | SAFETY | K | harness fails closed + manifest TOTAL + per-rule fixture. |
| 91 | baseline_metric_matches_executable_manifest | BASE | K | active_gate_checks == manifest count; enforcer_rows == live count. SSOT. |
| 92 | gate_rules_corpus_freshness | META | D | gate/rules/*.sh mirror canonical headers. IDE-only shadow corpus. |
| 93 | dfx_stem_matches_module | CORPUS | D | docs/dfx/*.yaml stems are real modules. Orphan-file guard. |
| 94 | active_corpus_deleted_module_name_truth | CORPUS | D | active corpus free of present-tense deleted module names (±3 marker). |
| 95 | spi_catalog_exhaustiveness | CORPUS | D | public spi interfaces appear in catalog or marked (internal). |
| 96 | kernel_deferred_clause_coherence | SCAF | D | deferred sub-clauses acknowledged in kernel/card. CLAUDE-deferred.md gone → vacuous. |
| 97 | release_note_numeric_truth | BASE | D | latest note node/edge/self-test counts == live. Number-truth. |
| 98 | broad_corpus_deleted_module_name_truth | CORPUS | D | Rule 94 widened to ops/contracts/metadata. Name-drift dup. |
| 99 | kernel_terminal_verb_vs_shipped_decision_check | SCAF | D | kernel end-state verbs vs deferred clause. CLAUDE-deferred.md gone → vacuous. |
| 100 | kernel_implementation_disjunction_truth | META | D | allow-list rules carry EITHER/OR wording. Allow-list EMPTY → vacuous. |
| 101 | rule_namespace_authority_completeness | META | K | card↔kernel↔status namespace parity (3 sub-checks). Authority integrity. |
| 102 | release_recency_resolver_correctness | META | D | no lex-sort tail-1 anti-pattern in gate scripts. Static guard. |
| 103 | deploy_entrypoint_deleted_module_truth | CORPUS | D | Dockerfile/CI/.puml free of deleted module names. Name-drift dup of 94/98. |
| 104 | openapi_implemented_route_catalog_truth | CORPUS | D | live routes not marked "(planned)" in catalog. Doc-truth. |
| 105 | edge_no_direct_compute_link | SAFETY | K | edge modules no direct compute imports/HTTP clients. Plane isolation. |
| 106 | cross_authority_parity | BASE | K | graph/spi/topology/grammar/carrier parity across surfaces (5 sub). Consolidator. |
| 107 | cross_authority_clause_parity | META | D | principle-coverage deferred clauses ⇄ card frontmatter. Meta-parity. |
| 108 | governance_text_java_anchor_truth | CORPUS | D | Class.method anchors in rule/principle docs resolve. Doc-truth. |
| 109 | namespaced_rule_reference_completeness | META | D | numeric Rule refs carry legacy marker. Namespace-discipline. |
| 110 | prevention_rule_scope_completeness | META | K | rules with scope_surfaces: have ≥2 fixtures. Meta-coverage. |
| 111 | architecture_refresh_defect_family_re_eval_required | META | K | recurring-defect-families.yaml wellformed+fresh+md-parity (3 sub). |
| 112 | meta_rule_self_application_check | META | D | every [META] rule sources a helper. Recursive-irony guard. |
| 113 | legacy_paren_no_reintroduction_and_migration_doc_complete | CORPUS | D | enforcers.yaml no `(legacy Rule NN)` parens + migration doc. Frozen cleanup. |
| 114 | rule_card_filename_dot_convention | META | D | rule cards match dotted-suffix filename. Naming convention. |
| 115 | no_version_log_metadata_in_code | CORPUS | K | prod code free of `rc<N> Wave`/`per ADR`/`Finding F<N>`/`closes #N`. D-9 hygiene (broad, keep). |
| 116 | parallel_linux_scripts_mandate | META | D | gate scripts parallel or serial-exempt. Gate-perf meta. |
| 117 | phase_contract_rule_allocation_coherence | META | K | phase contract ⇄ rule card citation coherence (P/X, no dual-P). |
| 118 | l1_dev_view_code_mapping | CORPUS | D | L1 dev-view ⇄ code tree (helper). Doc-altitude (G-1.1.a). |
| 119 | l1_spi_appendix_4way_parity | CORPUS | D | L1 SPI appendix 4-way parity (helper). Doc-altitude (G-1.1.b). |
| 120 | l1_l2_constraint_linkage | SCAF | D | hard-coded `pass_rule` — vacuous (no L2 docs; arms W3+). |
| 121 | whitebox_quality_reports | ADV | K | SpotBugs/Checkstyle blocking, PMD INFO-only. Code-quality gate. |
| 122 | proposal_immediate_scope_pending_contract_guard | CORPUS | D | proposals don't claim W0/W1 while contracts pending. Doc-truth. |
| 123 | proposal_engine_package_truth | CORPUS | D | proposal FQNs respect package authority unless marked proposed. |
| 124 | unsupported_absolute_claim_guard | CORPUS | D | proposal absolutes (bulletproof/sub-ms) need evidence wording. |
| 125 | codegraph_install_truth | CODE | D | codegraph pin/lockfile/.mcp.json wiring. Dev-tooling onboarding. |
| 126 | template_render_idempotency | SCAF | D | surface-classification.yaml parses; templates list empty → forward contract. |
| 127 | release_note_no_pending_evidence | CORPUS | K | current release notes carry no live placeholder tokens. Release truth. |
| 128 | model_gateway_authority_truth | CORPUS | D | ADR/java/catalog agree on ModelGateway package+signature. |
| 129 | contract_spi_count_truth | BASE | D | catalog/release SPI totals agree; promoted SPIs not deferred. |
| 130 | feature_lifecycle_validity | SAFETY | K | features.dsl saa.status ∈ 9-state. Blocking (G-14). |
| 131 | fact_layer_integrity | SAFETY | K | fact-layer structure/provenance/banner/resolver. Blocking (G-15). |
| 132 | feature_catalog_render_idempotency | META | D | feature-catalog render --check no drift. Render-idempotency sibling. |
| 133 | productclaim_referential_integrity | CORPUS | D | product_claim refs resolve to PC-NNN. Traceability (was advisory). |
| 134 | no_orphan_artefacts | CORPUS | D | every ADR/contract/card carries a ProductClaim marker. Traceability. |
| 135 | traceability_chain_completeness | CORPUS | D | every PC-NNN referenced by ≥1 artefact. Traceability. |
| 136 | autoload_tier_integrity | META | K | budget has PRODUCT.md ≠0 + CLAUDE.md ≤12000. Tier-1 discipline. |
| 137 | governance_infra_honesty | CORPUS | D | governance_infra artefacts avoid product-value vocab. Lexicon. |
| 138 | productclaim_placeholder_decreasing | CORPUS | D | 0 product_claim_placeholder:true markers. Convergence ratchet. |
| 139 | accepted_adr_frame_map_coherence | FROZEN | D | ADR-0158 ⇄ engineering-frames.dsl (EF-ENGINE-PORT). Targeted/frozen. |
| 140 | shipped_frame_anchor_integrity | FROZEN | D | shipped frames anchor ≥1 FunctionPoint (allowlist empty). Frame-map. |
| 141 | old_orchestration_spi_package_ban | CORPUS | D | banned old orchestration-spi package absent. Name-ban. |
| 142 | tier1_non_english_lint | CORPUS | K | Tier-1 product authority is English-only. Kernel-rule (CLAUDE.md mandate). |
| 143 | local_plan_path_ban | CORPUS | D | active authority free of `D:\.claude\plans` (exempt list). Path hygiene. |

---

## 5. RETIRE / DEMOTE TAIL — candidates (the ~50% cut)

The cut is drawn so the 18 safety-core (§3) + the 9 `CODE`-kept rows stay. Below are the **D-marked** rules grouped by *why* they are reducible. **Total demote/retire candidates: 118** (of 157) — more than the 50% target; trim to taste, but everything here is either redundant, frozen, vacuous, or pure doc-altitude.

### 5a. baseline-only / number-truth (11 — collapse to ~2)
27, 28(a), 28e, 64, 82, 86, 91, 97, 106, 129, plus the numeric sub-checks folded into 82/91.
- **Recommendation:** keep ONE canonical numeric-SSOT rule (82 *or* 91 + 106) and delete the rest. 27/28(a)/86/97/129 are all "does prose surface X echo canonical number Y" — pure duplication of the SSOT idea across README / release notes / root ARCH / latest note / catalog. 28e (hardcoded module count) is strictly dominated by 64 (data-driven). **Net: −9.**

### 5b. deleted-module / deleted-name drift (8 near-duplicates — collapse to 1)
13, 18, 80, 87, 94, 98, 103, 141 (+ 14, 20, 21 narrow single-name variants).
- **Recommendation:** one corpus-wide "no present-tense deleted-name without historical marker" scanner (94 generalised) replaces 13/18/80/87/98/103/141 and the hardcoded 14/20/21. **Net: −10.**

### 5c. frozen-ADR schema-presence (18 — these enforce now-frozen W1/W2 design YAMLs)
30, 32, 35, 45, 46, 51, 52, 55, 57, 58, 59, 60, 61, 78, 139, 140 (+ 28h, 26).
- Each asserts "design-phase governance/contract YAML has its expected schema header / key set." The decisions are frozen; the YAMLs don't churn. **Recommendation:** demote to a single periodic schema-lint (or a JSON-schema CI job), not 18 bespoke gate rules. **Net: −16+.**

### 5d. armed-but-vacuous / migration scaffold (12 — pass vacuously today)
28d, 44, 71, 72, 96, 99, 100, 120, 126, 132 (forward), 133–138 (advisory→recently-blocking traceability ratchet).
- 120 is a literal hardcoded `pass_rule`. 100's allow-list is EMPTY. 96/99 lost their subject (CLAUDE-deferred.md eliminated). 44 is no-op (all freeze_id null). 72 needs jq+5 samples. **Recommendation:** delete the dead ones (120, 100, 96, 99, 44), defer-register the forward contracts (126, 132) until they arm. **Net: −7 immediately.**

### 5e. meta / self-governance over-proliferation (17 → keep ~6)
Keep: 28f, 28j, 38/42 (graph), 68/69 (card authority), 88 (parity), 89 (harness), 101, 110, 111, 117, 136, 73, 70.
Demote: 28g, 28i, 28k, 28(b), 40, 41, 62, 67, 92, 102, 107, 109, 112, 114, 116, 132.
- These govern the rule/enforcer/card/graph machinery. Many are second-order (41 overlaps 28j; 28(b) is presence-only vs 28j; 112 is a recursive-irony guard; 114 is a filename convention). **Recommendation:** fold to the ~6 that protect authority integrity. **Net: −11.**

### 5f. pure doc-altitude / layer-purity (the long CORPUS tail — keep cross-cutting integrity, drop narrow)
1, 2, 8, 9, 17, 22, 25, 33, 37, 39, 63, 74, 77, 79, 83, 84, 85, 93, 95, 104, 108, 113, 118, 119, 122, 123, 124, 128, 137, 138, 143.
- Keep the broad integrity nets (23 links resolve, 115 D-9, 127 release placeholder, 142 English-only). Drop the narrow/frozen prose-shape guards. **Recommendation:** −~25, replace 118/119 (L1 dev-view/SPI appendix) with the helper scripts run as a single G-1.1 check.

**Aggregate demote/retire candidate count: 118.** A conservative cut (delete only 5a+5b+5d-dead+clear 5c) already removes **~45 rules** to land near the 157→~110 mark; an aggressive cut reaches ~60 rules total, restoring the 18-core + 9 code-keep + the ~30 highest-value meta/corpus integrity nets.

---

## 6. Grandfather / exemption / allowlist inventory (16 files)

| File | Non-comment entries | What it grandfathers / exempts | Used by | Sunset discipline |
|---|---|---|---|---|
| `d9-grandfathered-files.txt` | 54 | Files with pre-existing version/log-metadata tokens at Rule D-9 landing (CI yml, Dockerfile, package-info stubs, many test classes) | Rule 115 | Header declares `sunset_date: 2026-11-21` (single global date, not per-entry). |
| `schema-first-grandfathered.txt` | 10 | Pre-W2 prose-enum sites in ARCHITECTURE.md (RunMode, RunStatus DFA, JoinPolicy, SkillKind, AdmissionDecision, …) | Rule 60 | **Per-entry `\|YYYY-MM-DD\|` sunset, gate-validated**; expired entry FAILS. List CLOSED. |
| `rls-baseline-grandfathered.txt` | 1 | `V2__idempotency_dedup.sql` (tenant_id table predating RLS mandate) | Rule 50 | Pre-Rule-40 baseline; no per-entry date. |
| `inline-dynamic-claims-grandfathered.txt` | 29 | Path globs skipped by inline-dynamic-claims audit (templates, rule cards, ADRs, 7 named release notes, all governance YAMLs) | `gate/lib/audit_inline_dynamic_claims.py` (G-13.c) | Header says entries SHOULD carry sunset (most don't). |
| `historical-release-grandfathered.txt` | 8 | Release notes exempt from byte-identical render check (rc17, rc36-42) | Rule G-13.b render check | ADR-0119 W3; no per-entry date. |
| `active-corpus-name-exemption-markers.txt` | 44 | Regex alternatives = "historical marker" vocabulary (formerly/dissolved/relocated/…) | Rules 80, 94, 98, 103 | Vocabulary, not grandfather; no sunset. |
| `active-corpus-name-exemption-paths.txt` | 41 | Path prefixes the deleted-name scanner skips | Rule 94 | Vocabulary; no sunset. |
| `historical-marker-vocabulary.txt` | 17 | Marker regex for S2cCallbackSignal historical-only check | Rule 80 | Vocabulary; no sunset. |
| `baseline-snapshot-marker-vocabulary.txt` | 11 | Marker regex exempting historical numeric claims from baseline check | Rule 82 | Vocabulary; no sunset. |
| `serial-only-paths.txt` | 13 | Gate scripts exempt from the parallel-execution mandate (bootstrap/diagnostic/generator) | Rule 116 | Exemption list; no sunset. |
| `local-plan-path-exemptions.txt` | 6 | Surfaces allowed to mention `D:\.claude\plans` (G-7/G-26 docs, rule-history, gate/lib, harness) | Rule 143 | Exemption list; no sunset. |
| `fail-open-allowlist.txt` | **0** (empty) | (allowlist for `test_meta_no_fail_open_pipelines` self-test) | self-test | Empty = no fail-open pipeline sanctioned. |
| `frame-shipped-zero-anchor-allowlist.txt` | **0** (empty) | Frames allowed to be `shipped` with 0 FunctionPoints | Rule 140 | Empty → Rule 140 effectively strict. |
| `rule-100-disjunction-allowlist.txt` | **0** (empty) | Rules whose kernel declares EITHER/OR | Rule 100 | **Empty → Rule 100 is vacuous** (iterates zero rules). |
| `requirements.txt` | (pip deps) | Python deps for gate helpers (not a grandfather list) | gate Python helpers | n/a |
| `rule-number-migration.md` | (doc) | Legacy numeric→namespaced rule-id mapping SSOT | Rule 113 | Audit-trail doc, not a list. |

**Observation:** 3 of the allowlists (`fail-open`, `frame-shipped-zero-anchor`, `rule-100-disjunction`) are **empty**, meaning their owning rules (140, 100) are either strict-by-default or vacuous (100 iterates nothing). Only `schema-first-grandfathered.txt` enforces **per-entry** sunset dates; the rest carry at most a single global sunset (d9) or none — consistent with the memory note that closed-but-dateless grandfather lists are an anti-pattern here.

---

## 7. Self-test corpus note

`gate/test_architecture_sync_gate.sh`: **183 `test_*()` functions** emitting **287 unique assertion IDs** (296 `ok` + 300 `fail` invocations; some functions emit a positive + negative pair). Runtime reports `Tests passed: N/TOTAL` where `TOTAL=passed+failed`, and **fails closed** if `passed != TOTAL` (Rule 89 sub-check a). The 287 matches `architecture-status.yaml#baseline_metrics.gate_executable_test_cases: 287`. If the gate rule count is halved, the self-test corpus collapses proportionally (Rule 89 sub-check c requires ≥1 fixture per prevention-wave rule ≥80; Rule 110 requires ≥2 per scope_surfaces rule), so a ~50% rule cut roughly halves the self-test maintenance surface too.

---

## 8. Bottom line

- **157 gate checks confirmed** (= status YAML, = manifest, = parallel runner). 155 numbers + Rule 11/28 double-bodied.
- **18-rule SAFETY CORE** (§3) is the irreducible floor: tenant isolation (28a, 50, 11×2, 16, 12, 24.c), secrets/PII (28b, 28c), liveness (47, 48), plane isolation (105), release/CI truth (4, 7, 19, 89), feature/fact integrity (130, 131). Add 9 `CODE`-keep for a defensible **27-rule hard floor**.
- **~118 demote/retire candidates** (§5): baseline-only number-echoes (collapse 11→2), deleted-name scanners (collapse 10→1), frozen-ADR schema-presence (18, move to schema-lint), armed-vacuous scaffolds (delete the 5 dead, defer the 2 forward), meta over-proliferation (17→6), and the narrow doc-altitude prose guards.
- A conservative cut lands ~157→~110; an aggressive cut reaches ~60, well past the 50% goal, while every safety-core invariant survives.

**Artifact path:** `docs/reviews/2026-06-01-rebalancing-inventory/04-gates.md`
