package com.huawei.ascend.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RunTest {

    @Test
    void createStartsPendingAttemptOneVersionZeroWithMandatoryTenant() {
        Run run = Run.create("tenant-1", "session-1", "task-1", "agent-1");

        assertThat(run.status()).isEqualTo(RunStatus.PENDING);
        assertThat(run.attemptId()).isEqualTo(1);
        assertThat(run.version()).isZero();
        assertThat(run.tenantId()).isEqualTo("tenant-1");
    }

    @Test
    void tenantIsMandatoryOnEveryRun() {
        assertThatThrownBy(() -> Run.create(null, "s", "t", "a"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("tenantId");
        assertThatThrownBy(() -> Run.create("  ", "s", "t", "a"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tenantId");
    }

    /** withStatus is the only mutation path and it enforces the DFA. */
    @Test
    void withStatusValidatesTheDfa() {
        Run pending = Run.create("tenant-1", "session-1", "task-1", "agent-1");

        Run running = pending.withStatus(RunStatus.RUNNING);
        assertThat(running.status()).isEqualTo(RunStatus.RUNNING);

        assertThatThrownBy(() -> pending.withStatus(RunStatus.SUCCEEDED))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PENDING -> SUCCEEDED");
    }

    /** A FAILED→RUNNING retry is a new attempt. */
    @Test
    void retryIncrementsAttemptCounter() {
        Run failed = Run.create("tenant-1", "session-1", "task-1", "agent-1")
                .withStatus(RunStatus.RUNNING)
                .withStatus(RunStatus.FAILED);

        Run retried = failed.withStatus(RunStatus.RUNNING);

        assertThat(retried.attemptId()).isEqualTo(2);
    }

    @Test
    void terminalStatesReportTerminal() {
        Run succeeded = Run.create("tenant-1", "session-1", "task-1", "agent-1")
                .withStatus(RunStatus.RUNNING)
                .withStatus(RunStatus.SUCCEEDED);

        assertThat(succeeded.isTerminal()).isTrue();
        assertThatThrownBy(() -> succeeded.withStatus(RunStatus.RUNNING))
                .isInstanceOf(IllegalStateException.class);
    }
}
