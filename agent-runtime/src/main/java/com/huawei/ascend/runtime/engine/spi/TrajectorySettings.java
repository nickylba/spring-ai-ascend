package com.huawei.ascend.runtime.engine.spi;

import java.util.regex.Pattern;

/**
 * Resolved per-invocation trajectory settings handed to a {@link TrajectorySource}.
 * The runtime computes these from global configuration plus any per-request override
 * before opening the trajectory, so the adapter base never reads configuration itself.
 * When enabled, every supported kind is emitted with masked and truncated payloads,
 * unless {@code sampleRate} drops the whole invocation (head sampling: one Bernoulli
 * draw per invocation keeps or drops the entire trajectory, never a partial span tree).
 */
public record TrajectorySettings(boolean enabled, Pattern maskKeyPattern, int truncateChars, double sampleRate) {

    /**
     * Full-sampling convenience constructor ({@code sampleRate = 1.0}) — keeps call sites that
     * predate per-invocation sampling working unchanged.
     */
    public TrajectorySettings(boolean enabled, Pattern maskKeyPattern, int truncateChars) {
        this(enabled, maskKeyPattern, truncateChars, 1.0);
    }

    public static TrajectorySettings off() {
        return new TrajectorySettings(false, null, 0, 1.0);
    }
}
