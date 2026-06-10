package com.huawei.ascend.runtime.run;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RunStateMachineTest {

    /** Every edge of the §4 #20 DFA, exhaustively — legal edges pass, all others throw. */
    @Test
    void dfaIsExactlyTheSpecifiedEdgeSet() {
        Set<String> legal = Set.of(
                "PENDING->RUNNING", "PENDING->CANCELLED",
                "RUNNING->SUSPENDED", "RUNNING->SUCCEEDED", "RUNNING->FAILED", "RUNNING->CANCELLED",
                "SUSPENDED->RUNNING", "SUSPENDED->EXPIRED", "SUSPENDED->FAILED", "SUSPENDED->CANCELLED",
                "FAILED->RUNNING");
        for (RunStatus from : RunStatus.values()) {
            for (RunStatus to : RunStatus.values()) {
                String edge = from + "->" + to;
                if (legal.contains(edge)) {
                    RunStateMachine.validate(from, to);
                    assertThat(RunStateMachine.allowedTransitions(from)).contains(to);
                } else {
                    assertThatThrownBy(() -> RunStateMachine.validate(from, to))
                            .as(edge)
                            .isInstanceOf(IllegalStateException.class)
                            .hasMessageContaining(from.name());
                }
            }
        }
    }

    @Test
    void exactlyThreeStatesAreTerminal() {
        assertThat(java.util.Arrays.stream(RunStatus.values())
                .filter(RunStateMachine::isTerminal))
                .containsExactlyInAnyOrder(RunStatus.SUCCEEDED, RunStatus.CANCELLED, RunStatus.EXPIRED);
    }
}
