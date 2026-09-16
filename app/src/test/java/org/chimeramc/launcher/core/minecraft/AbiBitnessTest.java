package org.chimeramc.launcher.core.minecraft;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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

    @org.junit.Rule
    public final org.junit.rules.TemporaryFolder tmp = new org.junit.rules.TemporaryFolder();


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

    // --- Dual-ABI APKs: the case that made a 64-bit launcher refuse a runnable version ---

    @Test
    public void dualAbiApkOn64BitProcessPicksArm64() {
        // A version whose APK ships both ABIs must launch on the 64-bit build rather than
        // being rejected for "being 32-bit".
        assertEquals("arm64-v8a", AbiBitness.INSTANCE.selectLaunchAbi(
                java.util.Arrays.asList("arm64-v8a", "armeabi-v7a"), true));
    }

    @Test
    public void dualAbiApkOn32BitProcessPicksArm32() {
        assertEquals("armeabi-v7a", AbiBitness.INSTANCE.selectLaunchAbi(
                java.util.Arrays.asList("arm64-v8a", "armeabi-v7a"), false));
    }

    @Test
    public void dualAbiApkIsCompatibleWithBothProcesses() {
        java.util.List<String> shipped = java.util.Arrays.asList("arm64-v8a", "armeabi-v7a");
        assertTrue(AbiBitness.INSTANCE.hasLoadableAbi(shipped, true));
        assertTrue(AbiBitness.INSTANCE.hasLoadableAbi(shipped, false));
    }

    @Test
    public void singleAbiApkIsCompatibleWithMatchingProcessOnly() {
        java.util.List<String> arm64Only = java.util.Collections.singletonList("arm64-v8a");
        assertTrue(AbiBitness.INSTANCE.hasLoadableAbi(arm64Only, true));
        assertFalse(AbiBitness.INSTANCE.hasLoadableAbi(arm64Only, false));

        java.util.List<String> arm32Only = java.util.Collections.singletonList("armeabi-v7a");
        assertTrue(AbiBitness.INSTANCE.hasLoadableAbi(arm32Only, false));
        assertFalse(AbiBitness.INSTANCE.hasLoadableAbi(arm32Only, true));
    }

    @Test
    public void singleAbiApkStillSelectsThatAbiForReporting() {
        // Selection must not return null just because the only ABI mismatches: the caller
        // needs it to name the ABI in the error message.
        assertEquals("armeabi-v7a", AbiBitness.INSTANCE.selectLaunchAbi(
                java.util.Collections.singletonList("armeabi-v7a"), true));
    }

    @Test
    public void noShippedAbisSelectsNothing() {
        assertNull(AbiBitness.INSTANCE.selectLaunchAbi(java.util.Collections.emptyList(), true));
        assertFalse(AbiBitness.INSTANCE.hasLoadableAbi(java.util.Collections.emptyList(), true));
    }

    @Test
    public void selectionPrefersProcessBitnessOverDeclaredOrder() {
        // Order in the APK must not decide: arm32 listed first still loses to arm64 on a
        // 64-bit process. Guards against "first entry wins" regressions.
        assertEquals("arm64-v8a", AbiBitness.INSTANCE.selectLaunchAbi(
                java.util.Arrays.asList("armeabi-v7a", "arm64-v8a"), true));
    }

    @Test
    public void reportedRegressionDualAbiVersionNoLongerRefusedOn64BitBuild() {
        // The exact reported failure: a version whose APK ships both ABIs, previously
        // labelled "armeabi-v7a" from a past extraction, refused on the 64-bit build with
        // "the process is 64 bit cannot run 32bit". The old label-vs-process check did
        // reject it; judging the APK's real contents must not.
        assertFalse("sanity: the old check rejected this case",
                AbiBitness.INSTANCE.compatibleWith("armeabi-v7a", true));

        java.util.List<String> shippedBoth =
                java.util.Arrays.asList("arm64-v8a", "armeabi-v7a");
        assertTrue(AbiBitness.INSTANCE.hasLoadableAbi(shippedBoth, true));
        assertEquals("arm64-v8a", AbiBitness.INSTANCE.selectLaunchAbi(shippedBoth, true));
    }

    @Test
    public void genuine32BitOnlyVersionIsStillRefusedOn64BitBuild() {
        // The fix must not become a blanket "always allow": a version with only 32-bit
        // libraries genuinely cannot load in a 64-bit process.
        java.util.List<String> shipped32 = java.util.Collections.singletonList("armeabi-v7a");
        assertTrue(AbiBitness.INSTANCE.hasLoadableAbi(shipped32, false));
        assertFalse(AbiBitness.INSTANCE.hasLoadableAbi(shipped32, true));
    }

    // --- Reading a real APK off disk, which is what the launch preflight now does ---

    private static java.io.File writeApk(java.io.File dir, String name, String... abis)
            throws java.io.IOException {
        java.io.File apk = new java.io.File(dir, name);
        try (java.util.zip.ZipOutputStream zip =
                     new java.util.zip.ZipOutputStream(new java.io.FileOutputStream(apk))) {
            for (String abi : abis) {
                zip.putNextEntry(new java.util.zip.ZipEntry(
                        "lib/" + abi + "/libminecraftpe.so"));
                zip.write(new byte[]{0x7f, 'E', 'L', 'F'});
                zip.closeEntry();
            }
            // Decoy: a library in an ABI folder we do not consider must not be counted.
            zip.putNextEntry(new java.util.zip.ZipEntry("lib/mips/libminecraftpe.so"));
            zip.write(new byte[]{0x7f, 'E', 'L', 'F'});
            zip.closeEntry();
        }
        return apk;
    }

    @Test
    public void readsBothAbisFromARealDualAbiApk()
            throws java.io.IOException {
        java.io.File apk = writeApk(tmp.getRoot(), "base.apk.chimera",
                "arm64-v8a", "armeabi-v7a");

        java.util.List<String> shipped = AbiBitness.INSTANCE.abisInApks(
                java.util.Collections.singletonList(apk));

        assertTrue("arm64 must be detected", shipped.contains("arm64-v8a"));
        assertTrue("arm32 must be detected", shipped.contains("armeabi-v7a"));
        assertFalse("irrelevant ABIs must be ignored", shipped.contains("mips"));
        // This is the whole point: the reported version is launchable on the 64-bit build.
        assertTrue(AbiBitness.INSTANCE.hasLoadableAbi(shipped, true));
        assertTrue(AbiBitness.INSTANCE.hasLoadableAbi(shipped, false));
    }

    @Test
    public void readsAbiFromASplitApkToo()
            throws java.io.IOException {
        // Game libraries can live in a split rather than the base APK; scanning only the
        // base would report "no ABI" and let the version through untested.
        java.io.File base = writeApk(tmp.getRoot(), "base.apk.chimera", "arm64-v8a");
        java.io.File split = writeApk(tmp.getRoot(), "split_x.apk.chimera", "armeabi-v7a");

        java.util.List<String> shipped = AbiBitness.INSTANCE.abisInApks(
                java.util.Arrays.asList(base, split));

        assertTrue(shipped.contains("arm64-v8a"));
        assertTrue(shipped.contains("armeabi-v7a"));
    }

    @Test
    public void unreadableApkYieldsNoAbisRatherThanThrowing() {
        java.io.File notAnApk = new java.io.File(tmp.getRoot(), "corrupt.apk.chimera");
        try {
            java.nio.file.Files.write(notAnApk.toPath(), "not a zip".getBytes());
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
        // Must degrade to "no information" so the caller can fall back, not crash the launch.
        assertTrue(AbiBitness.INSTANCE.abisInApks(
                java.util.Collections.singletonList(notAnApk)).isEmpty());
    }
}