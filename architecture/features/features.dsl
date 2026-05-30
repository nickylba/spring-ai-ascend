// architecture/features/features.dsl
//
// Authority: ADR-0152 (L1 Feature Registry Canonical Schema, W1).
// AUTHORED ZONE. Engineers add new features by editing here directly.
//
// Each FEAT- element MUST satisfy required-properties.yaml#SAA_Feature:
//   common  : saa.id, saa.kind, saa.level, saa.view, saa.status
//   tag     : saa.owner, saa.sourceAdr, saa.capabilityDomain, saa.synopsis,
//             saa.aiBoundary.canModifyCode, saa.aiBoundary.canModifyContracts,
//             saa.aiBoundary.allowedStatusTransitions,
//             saa.aiBoundary.requiresHumanReviewAt,
//             saa.aiBoundary.sandboxPolicyRef
//   optional: saa.devPaths, saa.goals, saa.nonGoals,
//             saa.verificationTestFqns, saa.verificationCommands
//
// Lifecycle states (Rule G-14):
//   proposed -> accepted -> design_only -> ready_for_impl
//            -> implemented_unverified -> test_verified -> shipped
//            -> deprecated -> removed
//
// Pipe (|) is the list separator for properties that encode multiple values.

featRunLifecycleControl = element "Run Lifecycle Control" "Feature" "Public Run lifecycle API + state machine" "SAA Feature" {
    properties {
        "saa.id" "FEAT-RUN-LIFECYCLE-CONTROL"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0020"
        "saa.capabilityDomain" "runtime-run-lifecycle"
        "saa.productClaim" "PC-001|PC-003"
        "saa.requirement" "REQ-001"
        "saa.synopsis" "Owns the public Run lifecycle surface — admission, cancel re-validation, tenant-scoped status polling — and routes every Run state change through the RunRepository SPI as the single sanctioned transition path (Rule R-C.2.b, ADR-0142). Transition legality is governed by the RunStateMachine validator; tenant scoping is enforced at the repository and storage layers (Rule R-J). The concrete route verbs / paths / status codes are owned by openapi-v1.yaml; the atomic single-writer CAS obligation and persistence schema that back the transition are the development.md §5.3 L2 Boundary Contract, not restated at L1."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/runs|agent-service/src/main/java/com/huawei/ascend/service/runtime/state"
        "saa.goals" "Public Run lifecycle API with strict tenant isolation|Atomic CAS-based state transitions with no lost updates|Idempotent admission via IdempotencyStore"
        "saa.nonGoals" "Long-poll or websocket subscriptions (use cursor flow)|Cross-tenant aggregation"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify|bash gate/check_architecture_sync.sh"
    }
}

featEdgeComputeIngress = element "Edge to Compute Ingress" "Feature" "Edge-plane to compute_control ingress routing via IngressGateway" "SAA Feature" {
    properties {
        "saa.id" "FEAT-EDGE-COMPUTE-INGRESS"
        "saa.productClaim" "PC-002"
        "saa.requirement" "REQ-002"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "process"
        "saa.status" "design_only"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0049"
        "saa.capabilityDomain" "edge-compute-routing"
        "saa.synopsis" "Owns the edge-plane to compute_control ingress channel: every edge-originated request reaches the runtime exclusively through IngressGateway SPI, with the wire envelope governed by ingress-envelope.v1.yaml. Rule R-I.1 forbids edge modules from directly importing service/engine/middleware production code; the gateway is the single hop. W1 enforcement is ArchUnit-only at design_only status; full runtime adapter ships when the agent-client SDK lands (post-W3 per ADR-0049)."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|ready_for_impl->implemented_unverified|implemented_unverified->test_verified|test_verified->shipped"
        "saa.aiBoundary.requiresHumanReviewAt" "test_verified|shipped|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-bus/src/main/java/com/huawei/ascend/bus/spi/ingress"
        "saa.goals" "Single ingress hop from edge to compute_control|Wire envelope governed by versioned contract"
        "saa.nonGoals" "Provider-specific transport adapters (SDK concern)"
        "saa.verificationCommands" "./mvnw -pl agent-bus -am verify|bash gate/check_architecture_sync.sh"
    }
}

featServerClientCallback = element "Server to Client Callback" "Feature" "S2C callback envelope + transport for capability invocation" "SAA Feature" {
    properties {
        "saa.id" "FEAT-SERVER-CLIENT-CALLBACK"
        "saa.productClaim" "PC-004"
        "saa.requirement" "REQ-003"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-bus"
        "saa.sourceAdr" "ADR-0088"
        "saa.capabilityDomain" "s2c-callback-protocol"
        "saa.synopsis" "Owns the server-to-client callback path: when a Run needs a client-side capability invocation, the runtime emits S2cCallbackEnvelope through S2cCallbackTransport SPI; the calling Run suspends via SuspendSignal.forClientCallback and the client response (validated against s2c-callback.v1.yaml) resumes the Run. The transport SPI lives in agent-bus to keep service free of edge-direction transport concerns; the envelope schema is the runtime promise surface."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-bus/src/main/java/com/huawei/ascend/bus/spi/s2c|agent-service/src/main/java/com/huawei/ascend/service/runtime/s2c"
        "saa.goals" "Asynchronous capability invocation without holding client connection|Wire envelope versioned and validated"
        "saa.nonGoals" "Long-poll fallback (cursor flow handles polling)"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featSuspendResumeControl = element "Suspend and Resume Control" "Feature" "SuspendSignal lifecycle and ResumeDispatcher orchestration" "SAA Feature" {
    properties {
        "saa.id" "FEAT-SUSPEND-RESUME-CONTROL"
        "saa.productClaim" "PC-003"
        "saa.requirement" "REQ-004"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "process"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0058"
        "saa.capabilityDomain" "run-suspension-orchestration"
        "saa.synopsis" "Owns Run-level suspension and resume: SuspendSignal sealed-type variants (forClientCallback, forChildRun, forRateLimit, forCheckpoint) cause Run to transition to SUSPENDED with a typed SuspendReason; ResumeDispatcher transitions back to RUNNING when the suspension condition resolves. Child-run spawn is a SuspendSignal variant — parent suspends until child reaches terminal state. The full state machine is encoded in RunStateMachine and validated by every persisted transition."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/suspend"
        "saa.goals" "Typed suspension reasons|Resume without restart|Parent/child Run choreography"
        "saa.nonGoals" "Thread.sleep blocking suspension (Rule R-H forbids it)"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featIdempotencyAndReplay = element "Idempotency and Replay" "Feature" "Idempotency claim + replay on duplicate request" "SAA Feature" {
    properties {
        "saa.id" "FEAT-IDEMPOTENCY-AND-REPLAY"
        "saa.productClaim" "PC-003"
        "saa.requirement" "REQ-005"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0027"
        "saa.capabilityDomain" "idempotency-protocol"
        "saa.synopsis" "Owns idempotency at the public API boundary: the IdempotencyHeaderFilter consults the IdempotencyStore to either claim a fresh slot for a request or replay the previously stored response, so a duplicated request is byte-identical without re-executing the Run. The claim namespace is tenant-scoped and tenant isolation is enforced at the storage layer (Rule R-J). The header semantics and claim/replay posture are owned by openapi-v1.yaml and ADR-0057; the persistence schema and SQL are the development.md §5.3 L2 Boundary Contract, not restated at L1."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/idempotency"
        "saa.goals" "Idempotent admission with byte-identical replay|Tenant-scoped key namespace"
        "saa.nonGoals" "Cross-tenant key sharing"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featTenantIsolation = element "Tenant Isolation" "Feature" "Tenant claim cross-check + storage-engine RLS" "SAA Feature" {
    properties {
        "saa.id" "FEAT-TENANT-ISOLATION"
        "saa.productClaim" "PC-003"
        "saa.requirement" "REQ-006"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "scenarios"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0030"
        "saa.capabilityDomain" "tenant-isolation"
        "saa.synopsis" "Owns tenant isolation across the surface as defense in depth at three layers — HTTP edge, repository, and storage. The edge cross-checks the JWT tenant claim against the request's tenant identity (Rule R-J); every tenant-bearing schema migration carries Row-Level Security so the storage layer fails closed on cross-tenant reads (Rule R-J.a). The cross-tenant response posture at each route is owned by openapi-v1.yaml and ADR-0108; the Row-Level Security realisation and its schema-migration sequence are the development.md §5.3 L2 Boundary Contract, not restated at L1."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/platform/tenant|agent-service/src/main/resources/db/migration"
        "saa.goals" "Defense in depth at HTTP / repo / storage layers"
        "saa.nonGoals" "Cross-tenant aggregation (handled by separate analytics surface)"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featPostureBootstrap = element "Posture Bootstrap" "Feature" "Required-config posture validation at startup" "SAA Feature" {
    properties {
        "saa.id" "FEAT-POSTURE-BOOTSTRAP"
        "saa.productClaim" "PC-003"
        "saa.requirement" "REQ-007"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "physical"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0055"
        "saa.capabilityDomain" "posture-bootstrap"
        "saa.synopsis" "Owns posture-aware startup: PostureBootGuard validates the required-config matrix before the runtime accepts traffic. In research and prod postures the boot fails closed on missing config; in dev posture it logs and allows. Posture is selected from the active runtime profile and defaults to dev when unset, per the fail-loud-on-prod-misconfig discipline of Rule D-6. The required-config matrix and the boot-ordering guarantee that the guard runs before any other wiring are owned by ADR-0058 and ADR-0055; the framework binding is L2 / verification material, not restated at L1."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/platform/posture"
        "saa.goals" "Fail-closed posture in research/prod|Visible misconfig at startup"
        "saa.nonGoals" "Hot-reload of @RequiredConfig values"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

featGraphMemory = element "Graph Memory" "Feature" "Tenant-scoped graph memory store with auto-wiring" "SAA Feature" {
    properties {
        "saa.id" "FEAT-GRAPH-MEMORY"
        "saa.productClaim" "PC-001|PC-005"
        "saa.requirement" "REQ-008"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "logical"
        "saa.status" "design_only"
        "saa.owner" "graphmemory-starter"
        "saa.sourceAdr" "ADR-0064"
        "saa.capabilityDomain" "graph-memory"
        "saa.synopsis" "Owns graph-shaped tenant memory: GraphMemoryRepository provides tenant-scoped CRUD over graph nodes and edges, with semantic-fact extraction for agent-side reasoning. The starter auto-wires the repository when configured; the storage backend is pluggable through GraphMemoryStore SPI. W1 ships the SPI surface as design_only; the production backend (vector index + relational graph) lands in a subsequent wave once the SPI is stable across two consumers."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "design_only->ready_for_impl|ready_for_impl->implemented_unverified|implemented_unverified->test_verified|test_verified->shipped"
        "saa.aiBoundary.requiresHumanReviewAt" "test_verified|shipped|deprecated"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "graphmemory-starter/src/main/java"
        "saa.goals" "Tenant-scoped graph reasoning surface|Pluggable backend"
        "saa.nonGoals" "Cross-tenant graph queries|Single-backend lock-in"
        "saa.verificationCommands" "./mvnw -pl graphmemory-starter -am verify"
    }
}

featEngineDispatchAndHooks = element "Engine Dispatch and Hooks" "Feature" "Typed engine envelope dispatch + middleware hook events" "SAA Feature" {
    properties {
        "saa.id" "FEAT-ENGINE-DISPATCH-AND-HOOKS"
        "saa.productClaim" "PC-004"
        "saa.requirement" "REQ-009"
        "saa.kind" "feature"
        "saa.level" "L1"
        "saa.view" "process"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0088"
        "saa.capabilityDomain" "engine-contract"
        "saa.synopsis" "Owns the engine boundary: EngineRegistry is the single sanctioned dispatch authority for every Run, governed by engine-envelope.v1.yaml, with strict typed matching mandated by Rule R-M.a (the dispatch call chain and the forbidden-subtype-matching shape are the engine-envelope.v1.yaml contract behaviour + the L2 engine-dispatch design, not restated at L1). Cross-cutting policies (model gateway, tool authz, memory governance, tenant policy, quota, observability, sandbox routing, checkpoint, failure handling) are expressed as RuntimeMiddleware on the canonical HookPoint event surface governed by engine-hooks.v1.yaml. The hook contract is the extension surface for new policies without modifying executors."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/engine|agent-execution-engine/src/main/java"
        "saa.goals" "Typed engine dispatch + hook-based middleware|Versioned contracts at the boundary"
        "saa.nonGoals" "Per-executor instanceof checks outside the registry"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify|./mvnw -pl agent-execution-engine -am verify"
    }
}

// Relationship declarations: feature -> functionPoint (requires) — the value axis.
// A Feature drives FunctionPoints (requires) and traverses EngineeringFrames; the
// STRUCTURAL ownership of a FunctionPoint is the EngineeringFrame's `anchors` edge
// (engineering-frames.dsl), not the Feature. Per ADR-0157.
// Capability identifiers (`cap_*`) are defined in features/capabilities.dsl;
// function-point identifiers (`fp*`) are defined in features/function-points.dsl.
// !include order in workspace.dsl ensures both producers come before this consumer.

featRunLifecycleControl -> fpCreateRun "Run lifecycle contains create" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}
featRunLifecycleControl -> fpCancelRun "Run lifecycle contains cancel" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}
featRunLifecycleControl -> fpGetRunStatus "Run lifecycle contains status polling" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}
// featRunLifecycleControl -> fpListRuns removed alongside FP-LIST-RUNS
// (Round-2 Wave A, 2026-05-28 correction P0-1). Re-add when the list
// endpoint actually ships.
featRunLifecycleControl -> fpRunStateTransition "Run lifecycle contains CAS state transition" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}

featEdgeComputeIngress -> fpIngressEnvelope "ingress feature contains envelope routing" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}

featServerClientCallback -> fpS2cCallback "S2C callback feature contains the envelope" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}

featSuspendResumeControl -> fpSuspendResume "suspend/resume feature contains the SuspendSignal lifecycle" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}
featSuspendResumeControl -> fpChildRunSpawn "suspend/resume feature contains child-run choreography" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}

featIdempotencyAndReplay -> fpIdempotencyClaim "idempotency feature contains the claim and replay" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}

featTenantIsolation -> fpTenantCrossCheck "tenant isolation feature contains the cross-check" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}

featPostureBootstrap -> fpPostureBootGuard "posture bootstrap feature contains the boot guard" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}

featGraphMemory -> fpGraphMemoryStore "graph memory feature contains the store" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}

featEngineDispatchAndHooks -> fpEngineDispatch "engine feature contains dispatch" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}
featEngineDispatchAndHooks -> fpHookDispatch "engine feature contains hook dispatch" "SAA Relationship" {
    properties {
        "saa.rel" "requires"
    }
}

// =============================================================================
// W4 — agent-service deep-dive feature catalog (rc55 per-layer features).
// =============================================================================
// agent-service EngineeringFrames — re-tagged from the ADR-0138 Layer features
// per ADR-0157. These are the STRUCTURAL axis for agent-service (module-internal
// responsibility slices that anchor function points), NOT value-thread Features.
// Their Module-contains / EngineeringFrame-anchors edges live in
// features/engineering-frames.dsl. Deep-dive markdown: architecture/docs/L1/agent-service/.

efAccessAdmission = element "Access and Admission Frame" "EngineeringFrame" "Protocol convergence + tenant/auth/idempotency + client capability publication" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-ACCESS-ADMISSION"
        "saa.kind" "engineering_frame"
        "saa.structuralAxis" "true"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138|ADR-0155"
        "saa.cardPath" "architecture/docs/L1/frames/EF-ACCESS-ADMISSION.md"
        "saa.primaryPackage" "com.huawei.ascend.service.platform.web"
        "saa.capabilityDomain" "agent-service-access-layer"
        "saa.synopsis" "Layer 1 of the agent-service per-layer architecture (ADR-0138). Owns protocol convergence (HTTP / gRPC / WebSocket), tenant + auth + idempotency binding at the public boundary, client capability publication via the OpenAPI surface, and ingress for cursor / cancel / resume / S2C callback (the tenant cross-check and idempotency filter behaviour is owned by openapi-v1.yaml + the L2 access-layer design, not restated at L1). Does NOT own Run aggregate state, Task control state, Session context state, engine dispatch, or model/tool translation. The deep-dive feature inventory (AS-L1-F01..F08) lives at architecture/docs/L1/agent-service/features/access-layer.md. + ADR-0155 v1.2 absorption adds F48-F65 design-only items."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/api"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.api.*IT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

efEngineDispatch = element "Engine Dispatch Frame" "EngineeringFrame" "Engine adapter + executor dispatch (service-side)" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-ENGINE-DISPATCH"
        "saa.kind" "engineering_frame"
        "saa.structuralAxis" "true"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138|ADR-0155"
        "saa.cardPath" "architecture/docs/L1/frames/EF-ENGINE-DISPATCH.md"
        "saa.capabilityDomain" "agent-service-engine-dispatch"
        "saa.synopsis" "Layer 4 of the agent-service per-layer architecture (ADR-0138). Owns engine-adapter dispatch through EngineRegistry — governed by engine-envelope.v1.yaml under Rule R-M.a — and the executor invocation pathway that drives the Run state machine (the resolve-to-adapter dispatch chain is the engine-envelope.v1.yaml contract behaviour + the L2 engine-dispatch design, not restated at L1). The deep-dive inventory lives at architecture/docs/L1/agent-service/features/engine-dispatch-execution.md. Cross-cutting policies expressed as RuntimeMiddleware hooks (see FEAT-ENGINE-DISPATCH-AND-HOOKS for the cross-module Engine Contract feature). + ADR-0155 v1.2 absorption adds F48-F65 design-only items."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/engine|agent-service/src/main/java/com/huawei/ascend/service/runtime/executor/spi"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.engine.*IT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

efInternalEventQueue = element "Internal Event Queue Frame" "EngineeringFrame" "Internal event queue infrastructure (design-only)" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-INTERNAL-EVENT-QUEUE"
        "saa.kind" "engineering_frame"
        "saa.structuralAxis" "true"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138|ADR-0155"
        "saa.cardPath" "architecture/docs/L1/frames/EF-INTERNAL-EVENT-QUEUE.md"
        "saa.capabilityDomain" "agent-service-event-queue"
        "saa.synopsis" "Layer 5 of the agent-service per-layer architecture (ADR-0138). Owns in-process event queue infrastructure used by the runtime to decouple emit-side from consume-side concerns. The deep-dive lives at architecture/docs/L1/agent-service/features/internal-event-queue.md. This is the runtime's internal eventing primitive; cross-service eventing flows through agent-bus three-track channels (control/data/rhythm). + ADR-0155 v1.2 absorption adds F48-F65 design-only items."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/events"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.events.*Test"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

efSessionTaskState = element "Session Task State Frame" "EngineeringFrame" "Run aggregate + Session + Task lifecycle state" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-SESSION-TASK-STATE"
        "saa.kind" "engineering_frame"
        "saa.structuralAxis" "true"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138|ADR-0155"
        "saa.cardPath" "architecture/docs/L1/frames/EF-SESSION-TASK-STATE.md"
        "saa.primaryPackage" "com.huawei.ascend.service.session"
        "saa.capabilityDomain" "agent-service-session-task-manager"
        "saa.synopsis" "Layer 3 of the agent-service per-layer architecture (ADR-0138). Owns Session and Task aggregate lifecycles — the entities above Run that group runs into user-visible interactions. Task state machine governs admission / suspension / completion at the user-interaction level; Session state machine groups tasks into a conversation context. The deep-dive lives at architecture/docs/L1/agent-service/features/session-task-manager.md. + ADR-0155 v1.2 absorption adds F48-F65 design-only items."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/session"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.session.*IT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

efTaskControl = element "Task Control Frame" "EngineeringFrame" "Task-centric control: orchestrator loop, suspend/resume, cancel re-auth" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-TASK-CONTROL"
        "saa.kind" "engineering_frame"
        "saa.structuralAxis" "true"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "shipped"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138|ADR-0155"
        "saa.cardPath" "architecture/docs/L1/frames/EF-TASK-CONTROL.md"
        "saa.primaryPackage" "com.huawei.ascend.service.runtime.runs"
        "saa.capabilityDomain" "agent-service-task-control"
        "saa.synopsis" "Layer 2 of the agent-service per-layer architecture (ADR-0138). Owns task-centric control: cursor flow + cancel re-authorization + resume/replay against Run state. This is the layer that translates client-side task semantics into runtime Run lifecycle operations. The deep-dive lives at architecture/docs/L1/agent-service/features/task-centric-control.md. + ADR-0155 v1.2 absorption adds F48-F65 design-only items."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/runs"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.runs.*IT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}

efTranslationIntercept = element "Translation Tool Intercept Frame" "EngineeringFrame" "Model/tool translation + intercept hooks" "SAA EngineeringFrame" {
    properties {
        "saa.id" "EF-TRANSLATION-INTERCEPT"
        "saa.kind" "engineering_frame"
        "saa.structuralAxis" "true"
        "saa.level" "L1"
        "saa.view" "development"
        "saa.status" "design_only"
        "saa.owner" "agent-service"
        "saa.sourceAdr" "ADR-0138|ADR-0155"
        "saa.cardPath" "architecture/docs/L1/frames/EF-TRANSLATION-INTERCEPT.md"
        "saa.capabilityDomain" "agent-service-translation-tool"
        "saa.synopsis" "Layer 6 of the agent-service per-layer architecture (ADR-0138). Owns model/tool translation hooks: ModelGateway, tool authz boundary, prompt shaping, response normalisation. Intercepts model invocations to enforce platform policy before they reach provider SDKs. The deep-dive lives at architecture/docs/L1/agent-service/features/translation-tool-intercept.md. + ADR-0155 v1.2 absorption adds F48-F65 design-only items."
        "saa.aiBoundary.canModifyCode" "true"
        "saa.aiBoundary.canModifyContracts" "false"
        "saa.aiBoundary.allowedStatusTransitions" "shipped->deprecated"
        "saa.aiBoundary.requiresHumanReviewAt" "deprecated|removed"
        "saa.aiBoundary.sandboxPolicyRef" "docs/governance/sandbox-policies.yaml#default_policy"
        "saa.devPaths" "agent-service/src/main/java/com/huawei/ascend/service/runtime/translation|agent-service/src/main/java/com/huawei/ascend/service/runtime/intercept/spi"
        "saa.verificationTestFqns" "com.huawei.ascend.service.runtime.translation.*IT"
        "saa.verificationCommands" "./mvnw -pl agent-service -am verify"
    }
}
