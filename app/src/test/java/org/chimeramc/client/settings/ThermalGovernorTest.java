package org.chimeramc.client.settings;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.lang.reflect.Field;

/**
 * Guards the class-loading invariant in {@link ThermalGovernor}.
 *
 * The platform's thermal API only exists from API 29, while minSdk is 28. A static field
 * whose declared type is an API-29 class must be resolved when the class is initialized, so
 * leaving {@code PowerManager.OnThermalStatusChangedListener} on the outer class made every
 * API 28 launch die with {@code NoClassDefFoundError} wrapped in
 * {@code ExceptionInInitializerError} — before any method body, and therefore before any
 * {@code SDK_INT} check could run. These tests fail if that type is ever moved back.
 */
public class ThermalGovernorTest {

    /**
     * The decisive assertion. If this type reappears on the outer class, the app cannot start
     * on Android 9, which is exactly the crash this change fixes.
     */
    @Test
    public void outerClassDeclaresNoApi29PowerManagerTypes() {
        for (Field field : ThermalGovernor.class.getDeclaredFields()) {
            String type = field.getType().getName();
            assertFalse(
                    "ThermalGovernor must not declare a field of API-29 type " + type
                            + "; it would fail to initialize on API 28",
                    type.startsWith("android.os.PowerManager"));
        }
    }

    /** The API-29 surface has to live somewhere, or thermal throttling silently stops working. */
    @Test
    public void api29SurfaceIsIsolatedInItsOwnHolder() {
        Class<?>[] nested = ThermalGovernor.class.getDeclaredClasses();
        Class<?> holder = null;
        for (Class<?> candidate : nested) {
            if (candidate.getSimpleName().equals("Api29")) {
                holder = candidate;
            }
        }
        assertNotNull("expected a nested Api29 holder for the version-gated thermal API", holder);

        boolean holdsListener = false;
        for (Field field : holder.getDeclaredFields()) {
            if (field.getType().getName()
                    .equals("android.os.PowerManager$OnThermalStatusChangedListener")) {
                holdsListener = true;
            }
        }
        assertTrue("Api29 holder should own the thermal status listener", holdsListener);
    }

    /**
     * Loading the outer class must be harmless on its own. Touching the API-29 holder from a
     * non-gated context is what the nested-holder split is designed to prevent, so the public
     * entry points stay callable without a registered context.
     */
    @Test
    public void publicSurfaceIsSafeToLoadWithoutInit() {
        assertNotNull(ThermalGovernor.class);
        // No init() call: these must not throw, and must not report throttling.
        assertFalse(ThermalGovernor.shouldPauseBackgroundWork());
        assertFalse(ThermalGovernor.shouldPauseSpeculativeWork());
    }
}