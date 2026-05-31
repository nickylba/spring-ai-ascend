# The AI Knowledge System

This tree is the project's **AI knowledge system** — the searchable corpus that helps humans and AI
agents understand the repository. It is deliberately **outside the engineering main-path** and is
**not governed**: no ADR governs it, no blocking gate enforces it. It is maintained only by
**advisory integrity scripts** that keep it from drifting into self-contradiction.

> **The principle.** Governance constrains the engineering main-path (product code, architecture-of-record,
> runtime contracts, and the small set of current governed invariants). Knowledge is everything an agent
> *could* want to know. Conflating the two — making every piece of knowledge a binding precondition or a
> gate — is the defect this system exists to undo. Knowledge is **available unless needed**; governance
> **applies only when justified**.

## What lives here

| Subtree | Holds |
|---|---|
| `knowledge/adr/` | ADR history — the decision record, consolidated by topic. Historical and current rationale, kept for recall, **not** as binding authority. |
| `knowledge/architecture/` | Architecture rationale, the A2D delivery corpus, design narratives (the "why"), relocated from the old `docs/architecture/l0,l1` trees. |
| `knowledge/maps/` | Orientation maps and indexes (architecture graph, facet map, reading guides) — reference, not mandatory pre-work. |
| `knowledge/governance-history/` | The history of governance waves (rc-wave records, retired rules, defect-family narratives) — changelog, not active rules. |
| `knowledge/lessons/` | Distilled lessons from past defects and reviews. |
| `knowledge/_tools/` | The advisory maintenance tooling (integrity check + search). Not a gate. |

## How to use it

### Search
```
knowledge/_tools/search.sh "<query>"        # ripgrep across the knowledge tree, ranked by path
knowledge/_tools/search.sh --titles "<q>"   # match document titles/headings only
```
Load the *smallest* slice that answers your task. You do **not** need to read the whole tree to start
work — that broad-reading tax is exactly what this system removes.

### Add or update knowledge
1. Drop a markdown or YAML file in the matching subtree (or edit an existing one).
2. Give it a clear `# Title` (H1) and, for decision/lesson docs, a short front-matter block
   (`id:`, `topic:`, `status:`, `supersedes:` where relevant).
3. Run the integrity check (below). Fix anything it flags.
4. Commit. **No ADR, no gate, no review-proposal is required** to maintain knowledge.

### Check integrity (advisory)
```
knowledge/_tools/check_integrity.py          # parse + links + unique-IDs + contradiction heuristics
```
This is **advisory** — it returns non-zero only to help you catch corruption (unparseable files,
broken intra-knowledge links, duplicate IDs, contradictory status). It is **never** wired into the
blocking architecture gate. **Red line:** if this script is ever made a blocking/coverage gate, the
knowledge↔governance conflation has returned — do not do it.

## The bridge to governance (promotion)

Knowledge is the default home for everything learned. A piece of knowledge becomes **governance** only
when it is a *current governed invariant* — something whose violation causes real product, security,
compatibility, tenant-isolation, or release risk. Promotion is deliberate and rare:

1. State the invariant and the concrete failure it prevents.
2. Name an owner and a review date.
3. Express it as a rule/contract in the main-path, with a machine-checkable enforcer **only if** it
   meets the gate-admission bar.

Most knowledge is never promoted. That is correct.

## What does NOT belong here

- Runtime contracts, the architecture-of-record, current governed invariants → those are governed
  main-path (`docs/contracts/`, `architecture/`, `docs/governance/`).
- New blocking gates or rules → governance, not knowledge.
- Anything that must be *obeyed* to do work safely → governance.
