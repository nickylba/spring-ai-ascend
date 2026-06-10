package com.huawei.ascend.runtime.run;

/**
 * Lifecycle states of a {@link Run}. The legal transitions between them form
 * the DFA enforced by {@link RunStateMachine}; {@code SUCCEEDED},
 * {@code CANCELLED}, and {@code EXPIRED} are terminal.
 */
public enum RunStatus {
    PENDING,
    RUNNING,
    SUSPENDED,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    EXPIRED
}
