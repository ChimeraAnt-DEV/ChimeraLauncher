package org.chimeramc.launcher.core.minecraft;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * Covers the ABI/bitness matching that decides whether a Minecraft version can launch.
 *
 * Pure logic, no mocks and no Android runtime: the previous version of this check lived
 * inside GamePackageManager and read Build.SUPPORTED_* / Process.is64Bit() directly, which
 * is exactly why it could not be tested and shipped a bug.
 */
public class AbiBitnessTest {

    @Test
    public void arm64VersionOn64BitProcessIsCompatible() {
        assertTrue(AbiBitness.INSTANCE.compatibleWith("arm64-v8a", true));
    }

    @Test
    public void arm32VersionOn32BitProcessIsCompatible() {
        // The case the old check got wrong: a 64-bit device running the 32-bit launcher
        // build is a 32-bit process, and 32-bit versions must be allowed there.
        assertTrue(AbiBitness.INSTANCE.compatibleWith("armeabi-v7a", false));
    }

    @Test
    public void arm32VersionOn64BitProcessIsIncompatible() {
        assertFalse(AbiBitness.INSTANCE.compatibleWith("armeabi-v7a", true));
    }

    @Test
    public void arm64VersionOn32BitProcessIsIncompatible() {
        // The reverse direction, which the original code did not check at all: it only
        // ever tested whether the *version* was 32-bit, so a 64-bit version on a 32-bit
        // launcher passed preflight and then failed at dlopen.
        assertFalse(AbiBitness.INSTANCE.compatibleWith("arm64-v8a", false));
    }

    @Test
    public void missingAbiIsNotBlocked() {
        // Some manifests carry no ABI; blocking those would break launches that work.
        assertTrue(AbiBitness.INSTANCE.compatibleWith(null, true));
        assertTrue(AbiBitness.INSTANCE.compatibleWith("", true));
        assertTrue(AbiBitness.INSTANCE.compatibleWith("   ", false));
    }

    @Test
    public void allKnown32BitAbisAreClassifiedAs32Bit() {
        for (String abi : new String[]{"armeabi-v7a", "armeabi", "x86"}) {
            assertTrue("expected 32-bit: " + abi, AbiBitness.INSTANCE.is32BitAbi(abi));
        }
    }

    @Test
    public void allKnown64BitAbisAreClassifiedAs64Bit() {
        for (String abi : new String[]{"arm64-v8a", "x86_64", "riscv64"}) {
            assertFalse("expected 64-bit: " + abi, AbiBitness.INSTANCE.is32BitAbi(abi));
            assertTrue(AbiBitness.INSTANCE.compatibleWith(abi, true));
            assertFalse(AbiBitness.INSTANCE.compatibleWith(abi, false));
        }
    }

    @Test
    public void compatibilityIsSymmetricAcrossTheFullMatrix() {
        // Every combination must agree with the plain rule: the version's bitness has to
        // equal the process's bitness.
        String[] abis = {"arm64-v8a", "armeabi-v7a", "x86", "x86_64", "armeabi"};
        for (String abi : abis) {
            for (boolean processIs64 : new boolean[]{true, false}) {
                boolean expected = AbiBitness.INSTANCE.is32BitAbi(abi) != processIs64;
                assertTrue(abi + " / process64=" + processIs64,
                        AbiBitness.INSTANCE.compatibleWith(abi, processIs64) == expected);
            }
        }
    }

    @Test
    public void versionAbiIsDecisiveNotTheEnumOrder() {
        // Guards against a regression where the result is derived from an enum ordinal
        // rather than the ABI string itself.
        assertFalse(AbiBitness.INSTANCE.compatibleWith("arm64-v8a", false));
        assertFalse(AbiBitness.INSTANCE.compatibleWith("armeabi-v7a", true));
    }

    @Test
    public void buildWithOnlyArm64IsA64BitInstall() {
        assertTrue(AbiBitness.INSTANCE.buildIs64Bit(java.util.Collections.singletonList("arm64-v8a")));
    }

    @Test
    public void buildWithOnlyArm32IsA32BitInstall() {
        assertFalse(AbiBitness.INSTANCE.buildIs64Bit(java.util.Collections.singletonList("armeabi-v7a")));
    }

    @Test
    public void listingBothAbisStillProducesA64BitInstall() {
        // The trap the abi32 flavor must avoid: shipping any 64-bit library makes the
        // whole install 64-bit, so listing both ABIs would NOT give a 32-bit process.
        assertTrue(AbiBitness.INSTANCE.buildIs64Bit(java.util.Arrays.asList("arm64-v8a", "armeabi-v7a")));
    }

    @Test
    public void emptyAbiFiltersIsNotA64BitInstall() {
        assertFalse(AbiBitness.INSTANCE.buildIs64Bit(java.util.Collections.emptyList()));
    }
}