package org.levimc.launcher.core.minecraft

import android.util.Log

/**
 * Native companion for [org.chimeramc.client.core.minecraft.MinecraftRuntimePreparer].
 *
 * `libgxcore.so` (a prebuilt, closed-source binary) hardcodes its JNI symbol for
 * `nativeSetupRuntime` to the upstream `org.levimc.launcher.core.minecraft.MinecraftRuntimePreparer`
 * path. During the rebrand the host class was moved to `org.chimeramc.client.core.minecraft`
 * without a levimc-side binding, so the symbol is dead. This retained bridge keeps the
 * prebuilt library's hardcoded JNI lookup working; the implementation forwards to the
 * renamed chimeramc object.


 * Keep this file in `org.levimc.launcher.core.minecraft` — do NOT rename/move it.
 */

 object MinecraftRuntimePreparer {

    private const val TAG = "LeviRuntimePreparer"
    

    @JvmStatic
    @JvmName("nativeSetupRuntime")
    private external fun nativeSetupRuntime(modsPath: String)
    

    @JvmStatic
    fun runNativeSetup(modsPath: String?): Boolean {
        if (modsPath.isNullOrEmpty()) return false
        return try {
            nativeSetupRuntime(modsPath)
            true
        } catch (t: Throwable) {
            Log.w(TAG, "gxcore nativeSetupRuntime failed: ${t.message ?: t.javaClass.simpleName}")
            false
        }
    }
}