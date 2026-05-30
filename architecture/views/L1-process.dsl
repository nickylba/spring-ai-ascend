// architecture/views/L1-process.dsl
//
// Authority: ADR-0151 (W3 of L1 Feature Registry plan).
// 4+1 process view — runtime/process model rendered as a container view
// with description text NAMING the key flows at the layer-interaction
// altitude only.
//
// Altitude discipline (L1): this description names which containers
// participate in each flow and which boundary owns the resolution. It is
// delegated downstream — route verbs, HTTP status codes, admission-chain
// ordering, and runtime state-transition sequences live in the L2 /
// contract surfaces (architecture/docs/L1/agent-service/process.md
// dynamic-view sequences + the route / engine / S2C / RunEvent contracts
// under docs/contracts), NOT in this view description. This mirrors the
// 'Altitude discipline (L1)' header of the Markdown twin
// architecture/docs/L1/agent-service/process.md.

container springAiAscend "L1-Process" "Process view — runtime/process model" {
    include *
    autoLayout lr
    title "Spring AI Ascend — L1 Process View"
    description "Process flows (layer-interaction names; wire steps delegated to architecture/docs/L1/agent-service/process.md + docs/contracts): (1) Run admission — agent-service inbound boundary admits a Run through its admission chain and dispatches to the agent-execution-engine EngineRegistry boundary. (2) Suspend / Resume — the SuspendSignal boundary suspends an in-flight Run and the ResumeDispatcher boundary resumes it. (3) S2C Callback — agent-bus delivers the callback envelope and agent-service resumes the suspended Run via the ResumeDispatcher boundary. Containers above the line are runtime-active; everything is non-blocking I/O (Rule R-G + R-H)."
}
