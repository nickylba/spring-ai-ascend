package com.huawei.ascend.runtime.run;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dev-tier {@link RunRepository}: a concurrent map with the same
 * optimistic-lock semantics the Postgres tier enforces with CAS, so code
 * written against this tier already observes stale-save rejection.
 */
public final class InMemoryRunRepository implements RunRepository {

    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();

    @Override
    public Run save(Run run) {
        Objects.requireNonNull(run, "run");
        Run bumped = run.withVersion(run.version() + 1);
        Run result = runs.compute(run.id(), (id, persisted) -> {
            if (persisted != null && persisted.version() != run.version()) {
                throw new OptimisticLockException("Stale save for run " + id
                        + ": persisted version " + persisted.version() + ", saving from " + run.version());
            }
            return bumped;
        });
        return result;
    }

    @Override
    public Optional<Run> findById(UUID id) {
        return Optional.ofNullable(runs.get(id));
    }

    @Override
    public List<Run> findByTenantAndTask(String tenantId, String taskId) {
        return runs.values().stream()
                .filter(run -> run.tenantId().equals(tenantId) && run.taskId().equals(taskId))
                .sorted(Comparator.comparing(Run::attemptId).thenComparing(Run::createdAt))
                .toList();
    }
}
