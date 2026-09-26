package org.levimc.launcher.util

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import org.chimeramc.client.core.minecraft.LauncherApplication
import androidx.core.content.edit
import android.widget.Toast
import android.view.Gravity
import org.chimeramc.client.R

object NativeBridgeHelper {
    @Volatile
    private var gxcoreLoaded = false

    @JvmStatic
    fun getAppContext(): Context {
        return LauncherApplication.context
    }

    @JvmStatic
    fun ensureGxCoreLoaded(): Boolean {
        if (gxcoreLoaded) {
            return true
        }
        return synchronized(this) {
            if (gxcoreLoaded) {
                true
            } else {
                try {
                    System.loadLibrary("gxcore")
                    gxcoreLoaded = true
                    true
                } catch (_: Throwable) {
                    false
                }
            }
        }
    }

    @JvmStatic
    fun scanImage(inputPath: String): Boolean {
        if (!ensureGxCoreLoaded()) {
            return false
        }
        return nativeScanImage(inputPath)
    }

    @JvmStatic
    fun rewriteImage(inputPath: String, outputPath: String): Boolean {
        if (!ensureGxCoreLoaded()) {
            return false
        }
        return nativeRewriteImage(inputPath, outputPath)
    }

    private const val TAG = "NativeBridgeHelper"

    @JvmStatic
    fun bootstrapGxCore(): Boolean {
        if (!ensureGxCoreLoaded()) {
            android.util.Log.w(TAG, "bootstrapGxCore skipped: gxcore failed to load")
            return false
        }
        return try {
            nativeBootstrapGxCore()
            android.util.Log.i(TAG, "gxcore bootstrap completed")
            true
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "gxcore bootstrap threw: ${t.message ?: t.javaClass.simpleName}", t)
            false
        }
    }

    @JvmStatic
    fun getPrefs(name: String): SharedPreferences {
        return getAppContext().getSharedPreferences(name, Context.MODE_PRIVATE)
    }

    @JvmStatic
    fun getBoolean(prefsName: String, key: String, defaultValue: Boolean = false): Boolean {
        return try {
            getPrefs(prefsName).getBoolean(key, defaultValue)
        } catch (t: Throwable) {
            defaultValue
        }
    }

    @JvmStatic
    fun putBoolean(prefsName: String, key: String, value: Boolean) {
        try {
            getPrefs(prefsName).edit { putBoolean(key, value) }
        } catch (_: Throwable) {
        }
    }

    @JvmStatic
    fun getString(prefsName: String, key: String, defaultValue: String? = null): String? {
        return try {
            getPrefs(prefsName).getString(key, defaultValue)
        } catch (t: Throwable) {
            defaultValue
        }
    }

    @JvmStatic
    fun putString(prefsName: String, key: String, value: String) {
        try {
            getPrefs(prefsName).edit { putString(key, value) }
        } catch (_: Throwable) {
        }
    }

    /**
     * Whether gxcore's native verification/bootstrap stack should run.
     *
     * On devices where the closed-source libgxcore cannot complete its license/anti-tamper
     * verification (e.g. no Google Play Services / XAL auth), its ELF init silently
     * calls `_exit(0)` — killing the process with no tombstone or logcat entry, right in the
     * middle of `System.loadLibrary("gxcore")`。 Disabling this flag makes the launcher skip
     * the gxcore bootstrap and native-image guard ( verify/rewrite( entirely — mirroring the
     * `LeviLaunchroid_no_verification` approach — so the game still launches ( unverified(.
     */
    @JvmStatic
    fun isGxCoreEnabled(): Boolean {
        return try {
            org.chimeramc.client.settings.FeatureSettings.getInstance().isGxCoreEnabled()
        } catch (t: Throwable) {
            false
        }
    }

    @JvmStatic
    fun isLauncherManagedLoginEnabled(): Boolean {
        return try {
            val json = getString("feature_settings", "settings_json", null)
            if (!json.isNullOrEmpty()) {
                val obj = JSONObject(json)
                obj.optBoolean("launcherManagedMcLoginEnabled", false)
            } else {
                false
            }
        } catch (t: Throwable) {
            false
        }
    }

    @JvmStatic
    fun showInvalidLicenseOverlay() {
        val ctx = getAppContext()
        try {
            val handler = Handler(Looper.getMainLooper())
            handler.post {
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.invalid_license_detected_toast),
                    Toast.LENGTH_LONG
                ).apply {
                    setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 100)
                    show()
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
    }

    @JvmStatic
    private external fun nativeScanImage(inputPath: String): Boolean

    @JvmStatic
    private external fun nativeRewriteImage(inputPath: String, outputPath: String): Boolean

    @JvmStatic
    private external fun nativeBootstrapGxCore()
}
