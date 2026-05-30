// architecture/views/L1-scenarios.dsl
//
// Authority: ADR-0151 (W3 of L1 Feature Registry plan).
// 4+1 scenarios view — key user/system scenarios as a container view with
// description text listing 5 anchor scenarios. Detailed dynamic-view
// sequences live in Markdown narrative
// (architecture/docs/L1/<module>/scenarios.md + architecture/docs/L1/<module>/features/README.md).
//
// Altitude discipline (L1). The description below names each anchor scenario
// at the VALUE axis only — the actor, the boundaries traversed, the feature it
// realises, and the boundary identities (SPI / envelope / gateway) it touches.
// Wire-level realisation — concrete HTTP status codes, route x verb, header
// behaviour, method descriptors, and runtime CAS / sequence detail — is L2 /
// contract material: it is owned by the route + SPI contracts
// (docs/contracts/*.v1.yaml) and the per-scenario L2 process narrative
// (architecture/docs/L2/<frame>/), and is the same altitude-discipline boundary
// the cleaned markdown rendering of this view
// (architecture/docs/L1/agent-service/scenarios.md) already holds. This view
// names only the scenario identity, never its wire shape.

container springAiAscend "L1-Scenarios" "Scenarios view — anchor user/system scenarios" {
    include *
    autoLayout lr
    title "Spring AI Ascend — L1 Scenarios View"
    description "Anchor scenarios (value-axis identity; wire realisation delegated to docs/contracts/*.v1.yaml + the L2 process narrative). S1 — Create Run (FEAT-RUN-LIFECYCLE-CONTROL): a client creates a Run via the run-creation boundary; admission traverses the agent-service idempotency + posture guards and the agent-execution-engine EngineRegistry boundary, returning a Run handle. S2 — Cancel Run (FEAT-TENANT-ISOLATION): a cancel request is re-authorised by the JWT tenant cross-check and admitted through the Run lifecycle state-machine guard. S3 — S2C Callback (FEAT-SERVER-CLIENT-CALLBACK): agent-bus delivers an S2cCallbackEnvelope; the client response resumes the suspended Run across the SuspendSignal boundary. S4 — Edge Ingress (FEAT-EDGE-COMPUTE-INGRESS): an IngressEnvelope enters in a single hop via the IngressGateway boundary. S5 — Graph Memory Read (FEAT-GRAPH-MEMORY): tenant-scoped graph-memory read access."
}
