package com.huawei.ascend.runtime.run;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence SPI for {@link Run} records. The dev tier is in-memory
 * ({@link InMemoryRunRepository}); the durable Postgres tier replaces it via
 * the same interface and arms the optimistic-lock CAS on {@link #save}
 * (ADR-0106).
 */
public interface RunRepository {

    /**
     * Persist the run, bumping its optimistic-lock version. Implementations
     * MUST reject a stale save — one whose {@code version} does not match the
     * currently persisted version — with {@link OptimisticLockException}.
     *
     * @return the persisted run carrying the bumped version
     */
    Run save(Run run);

    Optional<Run> findById(UUID id);

    /** All runs of one tenant for the given transport task, newest attempt last. */
    List<Run> findByTenantAndTask(String tenantId, String taskId);

    /** Thrown when a save loses the optimistic-lock race (§4 #20 CAS contract). */
    class OptimisticLockException extends RuntimeException {
        public OptimisticLockException(String message) {
            super(message);
        }
    }
}
