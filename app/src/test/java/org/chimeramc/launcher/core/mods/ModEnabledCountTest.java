package org.chimeramc.launcher.core.mods;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Covers the "N active" count shown on the home hero card. The count is per instance: it is
 * derived from the mod list the {@link ModManager} discovers for the currently selected version,
 * so an instance with no mods must read zero rather than inheriting another instance's count.
 */
public class ModEnabledCountTest {

    private static Mod mod(String id, boolean enabled) {
        return new Mod(id, id + ".so", "lib/" + id + ".so", id, enabled, 0);
    }

    @Test
    public void nullListCountsZero() {
        assertEquals(0, Mod.countEnabled(null));
    }

    @Test
    public void emptyListCountsZero() {
        assertEquals(0, Mod.countEnabled(Collections.emptyList()));
    }

    @Test
    public void instanceWithoutModsReadsZero() {
        assertEquals(0, Mod.countEnabled(new ArrayList<>()));
    }

    @Test
    public void countsOnlyEnabledMods() {
        List<Mod> mods = Arrays.asList(
                mod("a", true),
                mod("b", false),
                mod("c", true),
                mod("d", false));
        assertEquals(2, Mod.countEnabled(mods));
    }

    @Test
    public void allDisabledCountsZero() {
        List<Mod> mods = Arrays.asList(mod("a", false), mod("b", false));
        assertEquals(0, Mod.countEnabled(mods));
    }

    @Test
    public void allEnabledCountsAll() {
        List<Mod> mods = Arrays.asList(mod("a", true), mod("b", true), mod("c", true));
        assertEquals(3, Mod.countEnabled(mods));
    }

    @Test
    public void togglingChangesCount() {
        Mod a = mod("a", true);
        Mod b = mod("b", true);
        List<Mod> mods = Arrays.asList(a, b);
        assertEquals(2, Mod.countEnabled(mods));

        b.setEnabled(false);
        assertEquals(1, Mod.countEnabled(mods));

        a.setEnabled(false);
        assertEquals(0, Mod.countEnabled(mods));
    }

    @Test
    public void nullEntryIsSkippedNotCounted() {
        assertEquals(1, Mod.countEnabled(Arrays.asList(mod("a", true), null)));
    }
}
