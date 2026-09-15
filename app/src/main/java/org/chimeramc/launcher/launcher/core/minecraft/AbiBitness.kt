package org.chimeramc.launcher.core.minecraft

/**
 * Pure ABI/bitness decisions, separated from [GamePackageManager] so they can be unit
 * tested without an Android runtime.
 *
 * Why bitness matters: Android fixes a process to one bitness at install time, chosen from
 * the native libraries the APK ships. Native libraries cannot cross that boundary in either
 * direction, so a 32-bit game library set cannot load in a 64-bit process and vice versa.
 * Matching a game version to a launcher build is therefore a comparison of two bitnesses,
 * not a capability test of the device.
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
     * The bitness an APK built with [abiFilters] will be installed as.
     *
     * This is the invariant the Gradle flavors rely on: a single 64-bit ABI in the filter
     * list makes the whole install 64-bit, so a "32-bit build" must list only 32-bit ABIs.
     * Listing both does not give a dual-bitness process; it gives a 64-bit process.
     */
    fun buildIs64Bit(abiFilters: Collection<String>): Boolean =
        abiFilters.any { it == ABI_ARM64 || it == "x86_64" }
}