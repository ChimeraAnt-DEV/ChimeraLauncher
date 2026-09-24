package org.chimeramc.client.core.mods.inbuilt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.chimeramc.client.core.mods.inbuilt.model.ModIds;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The Mod Menu draws one section header per contiguous run of a group id, so PvP modules must be
 * adjacent or the PvP section renders twice. These pin the ordering that guarantees it.
 */
public class PvpModuleGroupingTest {

    private static UnifiedMod mod(String id) {
        return new UnifiedMod(id, id, id, "inbuilt", UnifiedMod.Source.INBUILT,
                false, null, false, "inbuilt", "Inbuilt Features", null, null);
    }

    private static List<String> ids(List<UnifiedMod> mods) {
        List<String> out = new ArrayList<>();
        for (UnifiedMod mod : mods) out.add(mod.getId());
        return out;
    }

    @Test
    public void pvpModulesAreContiguousAtTheEnd() {
        List<UnifiedMod> ordered = InbuiltModuleProvider.groupPvpLast(Arrays.asList(
                mod(ModIds.AIM_SETTINGS),
                mod(ModIds.QUICK_DROP),
                mod(ModIds.CPS_DISPLAY),
                mod(ModIds.ZOOM),
                mod(ModIds.SNAPLOOK)));

        assertEquals(Arrays.asList(
                ModIds.QUICK_DROP, ModIds.ZOOM,
                ModIds.AIM_SETTINGS, ModIds.CPS_DISPLAY, ModIds.SNAPLOOK),
                ids(ordered));
    }

    @Test
    public void everyPvpModuleLandsInOneRun() {
        List<UnifiedMod> ordered = InbuiltModuleProvider.groupPvpLast(Arrays.asList(
                mod(ModIds.AIM_SETTINGS),
                mod(ModIds.QUICK_DROP),
                mod(ModIds.CPS_DISPLAY),
                mod(ModIds.SNAPLOOK),
                mod(ModIds.ZOOM)));

        int runs = 0;
        boolean inPvpRun = false;
        for (UnifiedMod mod : ordered) {
            boolean pvp = ModIds.isPvpModule(mod.getId());
            if (pvp && !inPvpRun) runs++;
            inPvpRun = pvp;
        }
        assertEquals("PvP modules must form a single section", 1, runs);
    }

    @Test
    public void nonPvpOrderIsPreserved() {
        List<UnifiedMod> ordered = InbuiltModuleProvider.groupPvpLast(Arrays.asList(
                mod(ModIds.QUICK_DROP), mod(ModIds.ZOOM), mod(ModIds.FPS_DISPLAY)));
        assertEquals(Arrays.asList(ModIds.QUICK_DROP, ModIds.ZOOM, ModIds.FPS_DISPLAY), ids(ordered));
    }

    @Test
    public void pvpClassificationIsStable() {
        assertTrue(ModIds.isPvpModule(ModIds.AIM_SETTINGS));
        assertTrue(ModIds.isPvpModule(ModIds.CPS_DISPLAY));
        assertTrue(ModIds.isPvpModule(ModIds.SNAPLOOK));
        assertFalse(ModIds.isPvpModule(ModIds.ZOOM));
        assertFalse(ModIds.isPvpModule(ModIds.MOD_MENU));
        assertFalse(ModIds.isPvpModule(null));
    }

    @Test
    public void groupingEmptyAndAllPvpInputsAreSafe() {
        assertTrue(InbuiltModuleProvider.groupPvpLast(new ArrayList<>()).isEmpty());

        List<UnifiedMod> allPvp = InbuiltModuleProvider.groupPvpLast(Arrays.asList(
                mod(ModIds.AIM_SETTINGS), mod(ModIds.CPS_DISPLAY)));
        assertEquals(Arrays.asList(ModIds.AIM_SETTINGS, ModIds.CPS_DISPLAY), ids(allPvp));
    }
}
