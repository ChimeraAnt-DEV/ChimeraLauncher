package org.chimeramc.launcher.util;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.chimeramc.launcher.util.DisplayModePreference.Mode;
import org.junit.Test;

/**
 * Selection rules for a high-refresh display mode. The important property under test is that
 * a faster mode is only ever chosen when it keeps the current resolution, so "unlocking" a
 * refresh rate cannot silently downscale the picture.
 */
public class DisplayModePreferenceTest {

    private static Mode mode(int id, float hz, int width, int height) {
        return new Mode(id, hz, width, height);
    }

    @Test
    public void picksFastestSameResolutionMode() {
        List<Mode> modes = Arrays.asList(
                mode(0, 60f, 1080, 2400),
                mode(1, 90f, 1080, 2400),
                mode(2, 120f, 1080, 2400));
        assertEquals(2, DisplayModePreference.selectHighRefreshModeId(modes, 1080, 2400));
    }

    @Test
    public void ignoresFasterModeThatChangesResolution() {
        // This is the trap the class exists for: mode 9 is the fastest but is 720p.
        List<Mode> modes = Arrays.asList(
                mode(0, 60f, 1080, 2400),
                mode(9, 144f, 720, 1600),
                mode(3, 120f, 1080, 2400));
        assertEquals(3, DisplayModePreference.selectHighRefreshModeId(modes, 1080, 2400));
    }

    @Test
    public void onlyFastModeAtOtherResolutionYieldsNoMode() {
        List<Mode> modes = Arrays.asList(
                mode(0, 60f, 1080, 2400),
                mode(9, 144f, 720, 1600));
        assertEquals(DisplayModePreference.NO_MODE,
                DisplayModePreference.selectHighRefreshModeId(modes, 1080, 2400));
    }

    @Test
    public void modesBelowThresholdAreNotWorthRequesting() {
        List<Mode> modes = Arrays.asList(
                mode(0, 48f, 1080, 2400),
                mode(1, 60f, 1080, 2400),
                mode(2, 89f, 1080, 2400));
        assertEquals(DisplayModePreference.NO_MODE,
                DisplayModePreference.selectHighRefreshModeId(modes, 1080, 2400));
    }

    @Test
    public void exactlyNinetyHertzQualifies() {
        List<Mode> modes = Arrays.asList(mode(5, 90f, 1080, 2400));
        assertEquals(5, DisplayModePreference.selectHighRefreshModeId(modes, 1080, 2400));
    }

    @Test
    public void nullOrEmptyModeListYieldsNoMode() {
        assertEquals(DisplayModePreference.NO_MODE,
                DisplayModePreference.selectHighRefreshModeId(null, 1080, 2400));
        assertEquals(DisplayModePreference.NO_MODE,
                DisplayModePreference.selectHighRefreshModeId(new ArrayList<>(), 1080, 2400));
    }

    @Test
    public void unknownCurrentSizeYieldsNoMode() {
        List<Mode> modes = Arrays.asList(mode(1, 120f, 1080, 2400));
        assertEquals(DisplayModePreference.NO_MODE,
                DisplayModePreference.selectHighRefreshModeId(modes, 0, 0));
    }

    @Test
    public void nullEntriesInModeListAreSkipped() {
        List<Mode> modes = Arrays.asList(null, mode(2, 120f, 1080, 2400));
        assertEquals(2, DisplayModePreference.selectHighRefreshModeId(modes, 1080, 2400));
    }
}
