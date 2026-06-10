package com.huawei.ascend.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InMemoryRunRepositoryTest {

    private final InMemoryRunRepository repository = new InMemoryRunRepository();

    @Test
    void saveBumpsVersionAndFindReturnsPersistedState() {
        Run created = Run.create("tenant-1", "session-1", "task-1", "agent-1");

        Run saved = repository.save(created);

        assertThat(saved.version()).isEqualTo(1);
        assertThat(repository.findById(created.id())).contains(saved);
    }

    /** The CAS contract: a save from a stale version must be rejected, like the Postgres tier. */
    @Test
    void staleSaveIsRejected() {
        Run created = Run.create("tenant-1", "session-1", "task-1", "agent-1");
        Run saved = repository.save(created);
        repository.save(saved.withStatus(RunStatus.RUNNING));

        // `saved` still carries version 1 — the RUNNING save moved persistence to 2.
        assertThatThrownBy(() -> repository.save(saved.withStatus(RunStatus.CANCELLED)))
                .isInstanceOf(RunRepository.OptimisticLockException.class)
                .hasMessageContaining(created.id().toString());
    }

    @Test
    void findByTenantAndTaskIsTenantScoped() {
        Run mine = repository.save(Run.create("tenant-1", "s", "task-9", "agent-1"));
        repository.save(Run.create("tenant-2", "s", "task-9", "agent-1"));

        assertThat(repository.findByTenantAndTask("tenant-1", "task-9"))
                .extracting(Run::id)
                .containsExactly(mine.id());
    }
}
