package com.huawei.ascend.runtime.run;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Execution record — the contract spine of the runtime (Rule R-C.2). One Run
 * is one execution attempt of an agent against the platform; every persisted
 * Run carries a non-null tenant and its status only ever changes along the
 * {@link RunStateMachine} DFA.
 *
 * <p>{@code version} is the optimistic-lock field staged ahead of the
 * Postgres CAS tier (ADR-0106): it defaults to 0 and is bumped by the
 * repository on save, so pre-CAS rows already carry a usable version when the
 * durable tier arms the check.
 *
 * @param id          run identity, never null
 * @param tenantId    owning tenant, never null/blank (Rule R-C.2.a)
 * @param sessionId   conversation the run belongs to, never null/blank
 * @param taskId      transport-level task correlation (A2A taskId), never null/blank
 * @param agentId     agent that executes the run, never null/blank
 * @param status      current DFA state, never null
 * @param attemptId   1-based attempt counter; a FAILED→RUNNING retry creates attempt N+1
 * @param version     optimistic-lock counter maintained by the repository
 * @param createdAt   creation instant, never null
 * @param updatedAt   last transition instant, never null
 */
public record Run(
        UUID id,
        String tenantId,
        String sessionId,
        String taskId,
        String agentId,
        RunStatus status,
        int attemptId,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public Run {
        Objects.requireNonNull(id, "id");
        requireNonBlank(tenantId, "tenantId");
        requireNonBlank(sessionId, "sessionId");
        requireNonBlank(taskId, "taskId");
        requireNonBlank(agentId, "agentId");
        Objects.requireNonNull(status, "status");
        if (attemptId < 1) {
            throw new IllegalArgumentException("attemptId must be >= 1, got: " + attemptId);
        }
        if (version < 0) {
            throw new IllegalArgumentException("version must be >= 0, got: " + version);
        }
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
    }

    /** A fresh PENDING run, attempt 1, version 0. */
    public static Run create(String tenantId, String sessionId, String taskId, String agentId) {
        Instant now = Instant.now();
        return new Run(UUID.randomUUID(), tenantId, sessionId, taskId, agentId,
                RunStatus.PENDING, 1, 0, now, now);
    }

    /**
     * Copy with the new status — the ONLY way to change a Run's state.
     * Validates the DFA edge (Rule R-C.2.b); a FAILED→RUNNING retry also
     * increments the attempt counter.
     */
    public Run withStatus(RunStatus newStatus) {
        RunStateMachine.validate(status, newStatus);
        int nextAttempt = status == RunStatus.FAILED && newStatus == RunStatus.RUNNING
                ? attemptId + 1 : attemptId;
        return new Run(id, tenantId, sessionId, taskId, agentId,
                newStatus, nextAttempt, version, createdAt, Instant.now());
    }

    /** Repository-internal copy with the bumped optimistic-lock version. */
    Run withVersion(long newVersion) {
        return new Run(id, tenantId, sessionId, taskId, agentId,
                status, attemptId, newVersion, createdAt, updatedAt);
    }

    public boolean isTerminal() {
        return RunStateMachine.isTerminal(status);
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
