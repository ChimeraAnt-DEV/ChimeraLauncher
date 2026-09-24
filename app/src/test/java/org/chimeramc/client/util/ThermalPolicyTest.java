package org.chimeramc.client.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Thermal severity mapping and the work-gating decisions derived from it.
 */
public class ThermalPolicyTest {

    @Test
    public void coolAndLightStatusesDoNotThrottle() {
        assertEquals(ThermalPolicy.SEVERITY_NONE, ThermalPolicy.severityFor(ThermalPolicy.STATUS_NONE));
        assertEquals(ThermalPolicy.SEVERITY_WARM, ThermalPolicy.severityFor(ThermalPolicy.STATUS_LIGHT));
        assertEquals(ThermalPolicy.SEVERITY_WARM, ThermalPolicy.severityFor(ThermalPolicy.STATUS_MODERATE));
    }

    @Test
    public void severeAndWorseMapToHot() {
        assertEquals(ThermalPolicy.SEVERITY_HOT, ThermalPolicy.severityFor(ThermalPolicy.STATUS_SEVERE));
        assertEquals(ThermalPolicy.SEVERITY_HOT, ThermalPolicy.severityFor(ThermalPolicy.STATUS_CRITICAL));
        assertEquals(ThermalPolicy.SEVERITY_HOT, ThermalPolicy.severityFor(ThermalPolicy.STATUS_EMERGENCY));
        assertEquals(ThermalPolicy.SEVERITY_HOT, ThermalPolicy.severityFor(ThermalPolicy.STATUS_SHUTDOWN));
    }

    @Test
    public void unknownStatusIsTreatedAsCoolSoNewLevelsCannotDisableWork() {
        assertEquals(ThermalPolicy.SEVERITY_NONE, ThermalPolicy.severityFor(99));
        assertEquals(ThermalPolicy.SEVERITY_NONE, ThermalPolicy.severityFor(-1));
    }

    @Test
    public void speculativeWorkOnlyRunsWhenCool() {
        assertTrue(ThermalPolicy.allowsSpeculativeWork(ThermalPolicy.SEVERITY_NONE));
        assertFalse(ThermalPolicy.allowsSpeculativeWork(ThermalPolicy.SEVERITY_WARM));
        assertFalse(ThermalPolicy.allowsSpeculativeWork(ThermalPolicy.SEVERITY_HOT));
    }

    @Test
    public void backgroundWorkSurvivesWarmButNotHot() {
        assertTrue(ThermalPolicy.allowsBackgroundWork(ThermalPolicy.SEVERITY_NONE));
        assertTrue(ThermalPolicy.allowsBackgroundWork(ThermalPolicy.SEVERITY_WARM));
        assertFalse(ThermalPolicy.allowsBackgroundWork(ThermalPolicy.SEVERITY_HOT));
    }

    @Test
    public void userInitiatedWorkIsNeverBlocked() {
        assertTrue(ThermalPolicy.allowsUserInitiatedWork(ThermalPolicy.SEVERITY_NONE));
        assertTrue(ThermalPolicy.allowsUserInitiatedWork(ThermalPolicy.SEVERITY_WARM));
        assertTrue(ThermalPolicy.allowsUserInitiatedWork(ThermalPolicy.SEVERITY_HOT));
    }
}
