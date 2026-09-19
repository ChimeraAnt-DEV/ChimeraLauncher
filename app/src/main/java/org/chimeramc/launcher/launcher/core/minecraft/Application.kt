package org.chimeramc.launcher.core.minecraft

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.preference.PreferenceManager
import org.chimeramc.launcher.core.crash.CrashReporter
import org.chimeramc.launcher.core.news.NewsNotificationHelper
import org.chimeramc.launcher.settings.FeatureSettings
import org.chimeramc.launcher.settings.LowLatencyNetworkManager
import org.chimeramc.launcher.settings.ThermalGovernor
import org.chimeramc.launcher.ui.dialogs.LogcatOverlayManager

class LauncherApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        context = applicationContext
        FeatureSettings.init(applicationContext)
        LowLatencyNetworkManager.init(applicationContext)
        ThermalGovernor.init(applicationContext)
        CrashReporter.init(this)
        // Mirror the persisted haptics preference into the static feedback layer so the
        // very first interaction honours it, before Settings is ever opened.
        org.chimeramc.launcher.ui.animation.UiTouchFeedback.setEnabled(
            org.chimeramc.launcher.util.PersonalizationManager(applicationContext)
                .isHapticFeedbackEnabled()
        )
        val processName = Application.getProcessName()
        if (processName.endsWith(":crash")) return

        NewsNotificationHelper.initialize(this)
        LogcatOverlayManager.init(this)
        PlaytimeManager.init(applicationContext)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
    }

    companion object {
        @JvmStatic
        lateinit var context: Context
            private set

        @JvmStatic
        lateinit var preferences: SharedPreferences
            private set
    }
}
