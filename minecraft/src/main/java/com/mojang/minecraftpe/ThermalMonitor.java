package com.mojang.minecraftpe;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

/**
 * Reports whether the device is under enough thermal pressure that the game should reduce
 * its own load.
 *
 * Previously this returned {@code false} unconditionally, which made it a no-op the native
 * layer could trust for nothing. It now reflects the platform's thermal status on API 29+.
 *
 * The mapping is deliberately conservative in one direction: an unknown or unavailable
 * reading reports "not throttled". Wrongly telling the game to slow down costs frames on a
 * perfectly healthy device, which is a worse failure than being late to react to heat.
 */
public class ThermalMonitor {

    private final Context appContext;

    public ThermalMonitor() {
        this(null);
    }

    public ThermalMonitor(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    /**
     * True when the platform reports the device is at least SEVERE thermal status, i.e. it is
     * actively throttling. Light/moderate pressure is normal under load and is not reported.
     */
    public boolean getLowPowerModeEnabled() {
        int status = getThermalStatus();
        return status >= PowerManager.THERMAL_STATUS_SEVERE;
    }

    /** Raw platform thermal status, or {@link PowerManager#THERMAL_STATUS_NONE} if unavailable. */
    public int getThermalStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return PowerManager.THERMAL_STATUS_NONE;
        }
        if (appContext == null) {
            return PowerManager.THERMAL_STATUS_NONE;
        }
        try {
            PowerManager powerManager =
                    (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
            if (powerManager == null) return PowerManager.THERMAL_STATUS_NONE;
            return powerManager.getCurrentThermalStatus();
        } catch (Throwable ignored) {
            return PowerManager.THERMAL_STATUS_NONE;
        }
    }
}
