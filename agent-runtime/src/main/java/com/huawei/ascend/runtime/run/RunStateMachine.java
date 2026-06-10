package com.huawei.ascend.runtime.run;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The formal transition DFA for {@link RunStatus} (architecture constraint
 * §4 #20). Every status change MUST flow through {@link #validate} — a Run
 * whose status jumps outside these edges is corrupted state, not a variant.
 *
 * <p>Legal transitions:
 * <pre>
 * PENDING   → RUNNING | CANCELLED
 * RUNNING   → SUSPENDED | SUCCEEDED | FAILED | CANCELLED
 * SUSPENDED → RUNNING | EXPIRED | FAILED | CANCELLED
 * FAILED    → RUNNING            (retry — new attempt)
 * SUCCEEDED / CANCELLED / EXPIRED are terminal.
 * </pre>
 *
 * Authority: ADR-0020.
 */
public final class RunStateMachine {

    private static final Map<RunStatus, Set<RunStatus>> LEGAL = new EnumMap<>(RunStatus.class);

    static {
        LEGAL.put(RunStatus.PENDING, EnumSet.of(RunStatus.RUNNING, RunStatus.CANCELLED));
        LEGAL.put(RunStatus.RUNNING, EnumSet.of(
                RunStatus.SUSPENDED, RunStatus.SUCCEEDED, RunStatus.FAILED, RunStatus.CANCELLED));
        LEGAL.put(RunStatus.SUSPENDED, EnumSet.of(
                RunStatus.RUNNING, RunStatus.EXPIRED, RunStatus.FAILED, RunStatus.CANCELLED));
        LEGAL.put(RunStatus.FAILED, EnumSet.of(RunStatus.RUNNING));
        LEGAL.put(RunStatus.SUCCEEDED, EnumSet.noneOf(RunStatus.class));
        LEGAL.put(RunStatus.CANCELLED, EnumSet.noneOf(RunStatus.class));
        LEGAL.put(RunStatus.EXPIRED, EnumSet.noneOf(RunStatus.class));
    }

    private RunStateMachine() {
    }

    /**
     * Throws {@link IllegalStateException} when {@code from → to} is not a
     * legal DFA edge. Self-transitions are illegal (they would mask lost
     * updates); idempotent operations must be handled at the caller (e.g.
     * cancel of an already-cancelled Run answers 200 without re-transitioning).
     */
    public static void validate(RunStatus from, RunStatus to) {
        if (from == null || to == null) {
            throw new IllegalStateException("Run status transition with null state: " + from + " -> " + to);
        }
        if (!LEGAL.get(from).contains(to)) {
            throw new IllegalStateException("Illegal run status transition: " + from + " -> " + to);
        }
    }

    /** The set of states reachable from {@code from} in one legal transition. */
    public static Set<RunStatus> allowedTransitions(RunStatus from) {
        return Set.copyOf(LEGAL.get(from));
    }

    public static boolean isTerminal(RunStatus status) {
        return LEGAL.get(status).isEmpty();
    }
}
