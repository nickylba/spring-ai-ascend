package com.huawei.ascend.runtime.engine.spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/** Verifies the factory methods and default values on {@link TrajectorySettings}. */
class TrajectorySettingsTest {

    @Test
    void offHasSampleRateOneAndDisabled() {
        TrajectorySettings settings = TrajectorySettings.off();
        assertThat(settings.enabled()).isFalse();
        assertThat(settings.sampleRate()).isEqualTo(1.0);
    }

    @Test
    void basicHasSampleRateOne() {
        TrajectorySettings settings = TrajectorySettings.basic(
                true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256);
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.sampleRate()).isEqualTo(1.0);
        assertThat(settings.truncateChars()).isEqualTo(256);
        assertThat(settings.maskKeyPattern()).isNotNull();
    }

    @Test
    void fourArgConstructorCarriesSampleRate() {
        TrajectorySettings settings = new TrajectorySettings(
                true, Pattern.compile(TrajectoryMasking.DEFAULT_KEY_PATTERN), 256, 0.5, null);
        assertThat(settings.sampleRate()).isEqualTo(0.5);
    }

    // --- TrajectorySettings.from(...) tests ---

    @Test
    void fromDisabledYieldsOff() {
        TrajectorySettings settings = TrajectorySettings.from(
                false, TrajectoryMasking.DEFAULT_KEY_PATTERN, 256, 1.0, null, null, 0, Set.of());
        assertThat(settings.enabled()).isFalse();
    }

    @Test
    void fromEnabledCarriesMaskAndTruncate() {
        TrajectorySettings settings = TrajectorySettings.from(
                true, TrajectoryMasking.DEFAULT_KEY_PATTERN, 256, 1.0, null, null, 0, Set.of());
        assertThat(settings.enabled()).isTrue();
        assertThat(settings.truncateChars()).isEqualTo(256);
        assertThat(settings.maskKeyPattern()).isNotNull();
    }

    @Test
    void fromEnabledCarriesSampleRate() {
        TrajectorySettings settings = TrajectorySettings.from(
                true, TrajectoryMasking.DEFAULT_KEY_PATTERN, 256, 0.5, null, null, 0, Set.of());
        assertThat(settings.sampleRate()).isEqualTo(0.5);
    }

    @Test
    void fromInvalidMaskPatternFailsSafeToDefaultNotNull() {
        TrajectorySettings settings = TrajectorySettings.from(
                true, "(unbalanced", 256, 1.0, null, null, 0, Set.of());
        // Must never crash, must never produce a null pattern (silently disabling redaction).
        assertThat(settings.maskKeyPattern()).isNotNull();
        assertThat(settings.maskKeyPattern().pattern()).isEqualTo(TrajectoryMasking.DEFAULT_KEY_PATTERN);
    }
}
