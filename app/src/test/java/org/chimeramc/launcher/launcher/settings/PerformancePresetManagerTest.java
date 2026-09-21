package org.chimeramc.launcher.launcher.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.chimeramc.launcher.util.DisplayModePreference;
import org.chimeramc.launcher.settings.PerformancePresetManager;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Covers the preset-to-display-mode decision, which is the only part of a preset that can add
 * frames and therefore the part whose gating must not regress.
 *
 * The launch path asks {@link PerformancePresetManager#wantsHighRefreshMode} before touching
 * {@code preferredDisplayModeId}; before this was gated the request was unconditional, so every
 * launch switched a high-refresh panel even when the user had chosen Battery or Balanced. The
 * assertions here pin that gating down.
 */
public class PerformancePresetManagerTest {

    private static DisplayModePreference.Mode mode(int id, float rate, int w, int h) {
        return new DisplayModePreference.Mode(id, rate, w, h);
    }

    @Test
    public void onlyPerformanceWantsHighRefresh() {
        assertTrue(PerformancePresetManager.wantsHighRefreshMode(
                PerformancePresetManager.Preset.PERFORMANCE));
        assertFalse(PerformancePresetManager.wantsHighRefreshMode(
                PerformancePresetManager.Preset.BALANCED));
        assertFalse(PerformancePresetManager.wantsHighRefreshMode(
                PerformancePresetManager.Preset.BATTERY));
    }

    @Test
    public void nonPerformancePresetsNeverSelectAMode() {
        List<DisplayModePreference.Mode> modes = Arrays.asList(
                mode(1, 120f, 1080, 2400),
                mode(2, 90f, 1080, 2400));

        assertEquals(DisplayModePreference.NO_MODE, PerformancePresetManager.displayModeFor(
                PerformancePresetManager.Preset.BALANCED, modes, 1080, 2400));
        assertEquals(DisplayModePreference.NO_MODE, PerformancePresetManager.displayModeFor(
                PerformancePresetManager.Preset.BATTERY, modes, 1080, 2400));
    }

    @Test
    public void performanceSelectsFastestSameResolutionMode() {
        List<DisplayModePreference.Mode> modes = Arrays.asList(
                mode(1, 60f, 1080, 2400),
                mode(2, 120f, 1080, 2400),
                mode(3, 90f, 1080, 2400));

        assertEquals(2, PerformancePresetManager.displayModeFor(
                PerformancePresetManager.Preset.PERFORMANCE, modes, 1080, 2400));
    }

    /**
     * The fast mode advertises a lower resolution, so taking it would trade sharpness for
     * frames without telling the user. Only the same-resolution mode may be chosen.
     */
    @Test
    public void performanceIgnoresFasterModesAtADifferentResolution() {
        List<DisplayModePreference.Mode> modes = Arrays.asList(
                mode(1, 60f, 1080, 2400),
                mode(2, 144f, 720, 1600),
                mode(3, 90f, 1080, 2400));

        assertEquals(3, PerformancePresetManager.displayModeFor(
                PerformancePresetManager.Preset.PERFORMANCE, modes, 1080, 2400));
    }

    @Test
    public void performanceReturnsNoModeWhenNoneIsFastEnough() {
        List<DisplayModePreference.Mode> modes = Collections.singletonList(mode(1, 60f, 1080, 2400));

        assertEquals(DisplayModePreference.NO_MODE, PerformancePresetManager.displayModeFor(
                PerformancePresetManager.Preset.PERFORMANCE, modes, 1080, 2400));
    }

    @Test
    public void performanceToleratesEmptyOrNullModeLists() {
        assertEquals(DisplayModePreference.NO_MODE, PerformancePresetManager.displayModeFor(
                PerformancePresetManager.Preset.PERFORMANCE, new ArrayList<>(), 1080, 2400));
        assertEquals(DisplayModePreference.NO_MODE, PerformancePresetManager.displayModeFor(
                PerformancePresetManager.Preset.PERFORMANCE, null, 1080, 2400));
    }

    @Test
    public void everyPresetDescribesItself() {
        for (PerformancePresetManager.Preset preset : PerformancePresetManager.Preset.values()) {
            String description = PerformancePresetManager.describe(preset);
            assertNotNull(description);
            assertFalse(description.trim().isEmpty());
        }
    }
}
