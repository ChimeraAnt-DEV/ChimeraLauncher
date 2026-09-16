package org.chimeramc.launcher.core.minecraft

/**
 * Pure ABI/bitness decisions, separated from [GamePackageManager] so they can be unit
 * tested without an Android runtime.
 *
 * Why bitness matters: Android fixes a process to one bitness at install time, chosen from
 * the native libraries the APK ships. Native libraries cannot cross that boundary in either
 * direction, so a 32-bit game library set cannot load in a 64-bit process and vice versa.
 * Matching a game version to the launcher is therefore a comparison of two bitnesses, not a
 * capability test of the device.
 */
object AbiBitness {

    const val ABI_ARM64 = "arm64-v8a"
    const val ABI_ARM32 = "armeabi-v7a"

    /** Known 32-bit ABI names. `arm64-`/`x86_64` are the 64-bit counterparts. */
    private val ABI_32 = setOf(ABI_ARM32, "armeabi", "x86")

    fun is32BitAbi(abi: String?): Boolean = abi != null && abi in ABI_32

    /**
     * True when a version declaring [declaredAbi] can have its native libraries loaded by a
     * process of the given bitness.
     *
     * A missing or blank ABI is treated as compatible: some version manifests simply do not
     * state one, and blocking those would be worse than letting the load attempt speak.
     */
    fun compatibleWith(declaredAbi: String?, processIs64Bit: Boolean): Boolean {
        if (declaredAbi.isNullOrBlank()) return true
        return is32BitAbi(declaredAbi) == !processIs64Bit
    }

    /**
     * Picks the ABI a version should be launched with, given every ABI its APK(s) ship.
     *
     * This is what lets one launcher install serve a version whose APK carries more than one
     * ABI. Android records *all* of an APK's architectures in `SUPPORTED_ABIS`, but still
     * runs the process as exactly one of them. A version that ships both arm64-v8a and
     * armeabi-v7a can therefore be served by a 64-bit process, and must not be refused just
     * because one of its ABIs disagrees with the process: the other is in the same APK.
     *
     * Preference order:
     *  1. An ABI the APK ships that matches the process bitness, so the game loads natively.
     *  2. Otherwise the first shipped ABI, so the caller can report a real mismatch naming
     *     an ABI the version actually has (rather than refusing before looking).
     */
    fun selectLaunchAbi(shippedAbis: Collection<String>, processIs64Bit: Boolean): String? {
        if (shippedAbis.isEmpty()) return null
        val matching = shippedAbis.firstOrNull { is32BitAbi(it) != processIs64Bit }
        if (matching != null) return matching
        return shippedAbis.firstOrNull()
    }

    /** True when an APK shipping [shippedAbis] has an ABI this process can actually load. */
    fun hasLoadableAbi(shippedAbis: Collection<String>, processIs64Bit: Boolean): Boolean =
        shippedAbis.any { is32BitAbi(it) == !processIs64Bit }

    /** ABIs worth looking for inside a game APK, in descending order of preference. */
    val ABI_CANDIDATES = listOf(ABI_ARM64, ABI_ARM32, "x86_64", "x86")

    /** Where a game APK keeps its native libraries. */
    private const val NATIVE_LIB_ENTRY = "lib/%s/libminecraftpe.so"

    /**
     * Every ABI present in [apkFiles], read from the archives themselves.
     *
     * Pure JVM (no Android runtime), which is deliberate: this is the authority the launch
     * preflight depends on, so it has to be unit-testable against a real APK. Splits are
     * scanned too, because a game's libraries may live in a split rather than the base APK.
     */
    fun abisInApks(apkFiles: Collection<java.io.File>): List<String> {
        val shipped = mutableListOf<String>()
        for (file in apkFiles) {
            if (!file.isFile) continue
            try {
                java.util.zip.ZipFile(file).use { zip ->
                    for (abi in ABI_CANDIDATES) {
                        if (abi !in shipped &&
                            zip.getEntry(NATIVE_LIB_ENTRY.format(abi)) != null
                        ) {
                            shipped.add(abi)
                        }
                    }
                }
            } catch (_: Exception) {
                // An unreadable archive contributes nothing; the caller falls back.
            }
        }
        return shipped
    }
}