package org.chimeramc.launcher.launcher.controller;

import android.content.Context;
import android.view.InputDevice;
import android.view.MotionEvent;

import org.chimeramc.launcher.settings.FeatureSettings;

/**
 * Applies the active controller profile to the real gameplay input pipeline.
 *
 * This class sits directly in {@code MinecraftActivity.dispatchKeyEvent} and
 * {@code dispatchGenericMotionEvent}, so it runs on every controller event, on the UI
 * thread, before the game processes the input. Any work done here is added straight to
 * input-to-photon latency, which is why:
 *
 * - No method on the read path is {@code synchronized}. The active response is published
 *   as one immutable, {@code volatile} reference, so readers never take a lock and never
 *   see a half-applied profile. The previous per-method locking contended with the UI
 *   thread on every single event.
 * - All profile-derived math (dead-zone range, sensitivity, remap table) is precomputed
 *   once in {@link ControllerResponse} instead of per event.
 *
 * With "Low Input Delay" enabled the dead zone is tightened and an ease-out response curve
 * is applied, so the same physical stick travel produces more look/turn output. That is a
 * real reduction in how far the stick must move before the game reacts.
 */
public final class ControllerInputProcessor {
    private static volatile ControllerResponse active;
    private static volatile ControllerType activeType;

    /**
     * Dead zone used when Low Input Delay is on. The profile's own dead zone is still the
     * floor; this only tightens the default so drifting sticks do not cancel it out.
     */
    private static final float LOW_LATENCY_DEAD_ZONE = 0.06f;

    private ControllerInputProcessor() {
    }

    public static void setActiveProfile(ControllerType type, ControllerProfile profile) {
        setActiveProfile(type, profile, isLowInputDelayEnabled());
    }

    public static void setActiveProfile(ControllerType type, ControllerProfile profile, boolean lowInputDelay) {
        activeType = type;
        if (profile == null) {
            active = null;
            return;
        }
        ControllerProfile effective = lowInputDelay ? tightenDeadZone(profile) : profile;
        // Single volatile publication: the response is fully built before it is visible.
        active = new ControllerResponse(effective, lowInputDelay);
    }

    /**
     * Rebuilds the active response after the user flips a setting that affects it. Cheap:
     * it only runs on an explicit toggle/profile change, never per input event.
     */
    public static void refresh(ControllerProfile profile) {
        if (profile == null) return;
        setActiveProfile(activeType, profile, isLowInputDelayEnabled());
    }

    /**
     * Reloads the active profile for the current controller and rebuilds its response.
     * Call after toggling Low Input Delay or editing a profile so a running session picks
     * the change up without a restart.
     */
    public static void reload(Context context) {
        ControllerType type = activeType;
        if (type == null) {
            detectAndLoad(context);
            return;
        }
        ControllerProfile profile = new ControllerProfileManager(context).getActiveProfile(type);
        refresh(profile);
    }

    private static boolean isLowInputDelayEnabled() {
        FeatureSettings settings = FeatureSettings.getInstance();
        return settings != null && settings.isLowInputDelayEnabled();
    }

    private static ControllerProfile tightenDeadZone(ControllerProfile profile) {
        ControllerProfile effective = profile.copy();
        effective.setLeftDeadZone(Math.min(profile.getLeftDeadZone(), LOW_LATENCY_DEAD_ZONE));
        effective.setRightDeadZone(Math.min(profile.getRightDeadZone(), LOW_LATENCY_DEAD_ZONE));
        return effective;
    }

    public static ControllerType getActiveType() {
        return activeType;
    }

    public static boolean isActive() {
        return active != null;
    }

    public static void detectAndLoad(Context context) {
        int[] ids = InputDevice.getDeviceIds();
        for (int id : ids) {
            InputDevice device = InputDevice.getDevice(id);
            if (device == null) continue;
            int sources = device.getSources();
            if ((sources & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (sources & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                ControllerType type = ControllerType.from(device);
                if (type != null) {
                    ControllerProfile profile = new ControllerProfileManager(context).getActiveProfile(type);
                    setActiveProfile(type, profile);
                    return;
                }
            }
        }
    }

    /**
     * Applies a per-instance controller profile binding, if the instance has one.
     *
     * Called when a session starts so an instance can bring its own controller setup. The
     * bound controller type wins over whatever is currently connected: a player who binds a
     * profile to an instance expects that instance to use it, and the alternative (silently
     * ignoring the binding when a different pad is plugged in) makes the binding look broken.
     *
     * @return true when a binding was applied
     */
    public static boolean applyInstanceBinding(Context context, String profileId) {
        if (context == null || profileId == null || profileId.trim().isEmpty()) return false;
        ControllerProfileManager manager = new ControllerProfileManager(context);
        ControllerProfileManager.Binding binding = manager.getBinding(profileId);
        if (binding == null) return false;
        ControllerProfile profile = manager.getProfile(binding.type, binding.slot);
        if (profile == null) return false;
        setActiveProfile(binding.type, profile);
        return true;
    }

    /**
     * Applies instance bindings for any instance id that matches, used when the bound
     * instance is already known at selection time rather than at launch.
     */
    public static void reloadForInstance(Context context, String profileId) {
        if (context == null) return;
        if (!applyInstanceBinding(context, profileId)) {
            detectAndLoad(context);
        }
    }

    public static int remapKey(int keyCode) {
        ControllerResponse response = active;
        if (response == null || keyCode <= 0) {
            return keyCode;
        }
        return response.remapKey(keyCode);
    }

    public static float adjustAxisValue(int axis, float value) {
        ControllerResponse response = active;
        if (response == null) {
            return value;
        }
        return response.adjustAxis(axis, value);
    }

    /**
     * Rewrites a motion event's stick and trigger axes through the active profile and returns
     * the event the game should see.
     *
     * This is what makes the response-curve and trigger-curve editors observable in gameplay.
     * Swallowing in-dead-zone events (see {@link #isWithinDeadZone}) is not enough on its own:
     * the game reads the axis values off the event, so without rewriting them a curve would
     * change nothing the player can feel.
     *
     * Allocates only when an axis actually changes, so an all-default profile (and every
     * non-joystick event) stays on the zero-allocation path. When it does allocate, the caller
     * owns the returned event; the original is returned untouched otherwise, so callers must
     * compare identity before recycling.
     */
    public static MotionEvent transformMotionEvent(MotionEvent event) {
        ControllerResponse response = active;
        if (response == null || event == null) {
            return event;
        }
        int sources = event.getSource();
        if ((sources & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                && (sources & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) {
            return event;
        }

        int pointerCount = event.getPointerCount();
        if (pointerCount <= 0) return event;

        // Read through PointerCoords rather than MotionEvent.getAxisValue(axis, pointerIndex),
        // which only exists from API 29; this project supports API 28.
        boolean changed = false;
        float[][] rewritten = new float[pointerCount][TRANSFORM_AXES.length];
        MotionEvent.PointerCoords scratch = new MotionEvent.PointerCoords();
        for (int p = 0; p < pointerCount; p++) {
            event.getPointerCoords(p, scratch);
            for (int a = 0; a < TRANSFORM_AXES.length; a++) {
                int axis = TRANSFORM_AXES[a];
                float original = scratch.getAxisValue(axis);
                float updated = response.adjustAxisOrTrigger(axis, original);
                rewritten[p][a] = updated;
                if (updated != original) {
                    changed = true;
                }
            }
        }
        if (!changed) return event;

        MotionEvent.PointerProperties[] properties = new MotionEvent.PointerProperties[pointerCount];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[pointerCount];
        for (int p = 0; p < pointerCount; p++) {
            properties[p] = new MotionEvent.PointerProperties();
            event.getPointerProperties(p, properties[p]);
            coords[p] = new MotionEvent.PointerCoords();
            event.getPointerCoords(p, coords[p]);
            for (int a = 0; a < TRANSFORM_AXES.length; a++) {
                coords[p].setAxisValue(TRANSFORM_AXES[a], rewritten[p][a]);
            }
        }

        return MotionEvent.obtain(
                event.getDownTime(),
                event.getEventTime(),
                event.getAction(),
                pointerCount,
                properties,
                coords,
                event.getMetaState(),
                event.getButtonState(),
                event.getXPrecision(),
                event.getYPrecision(),
                event.getDeviceId(),
                event.getEdgeFlags(),
                event.getSource(),
                event.getFlags());
    }

    /** Axes the profile rewrites on the way to the game. */
    private static final int[] TRANSFORM_AXES = {
            MotionEvent.AXIS_X,
            MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z,
            MotionEvent.AXIS_RZ,
            MotionEvent.AXIS_LTRIGGER,
            MotionEvent.AXIS_RTRIGGER,
            MotionEvent.AXIS_BRAKE,
            MotionEvent.AXIS_GAS,
    };

    public static float adjustTriggerValue(int axis, float value) {
        ControllerResponse response = active;
        if (response == null) {
            return value;
        }
        return response.adjustTrigger(axis, value);
    }

    /**
     * Whether the event's stick axes are all inside the dead zone, meaning the game should
     * not see the event at all. Uses the precomputed per-axis dead zone directly instead of
     * re-running the full curve for every axis.
     */
    public static boolean isWithinDeadZone(MotionEvent event) {
        ControllerResponse response = active;
        if (response == null || event == null) {
            return false;
        }
        int sources = event.getSource();
        if ((sources & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK
                && (sources & InputDevice.SOURCE_GAMEPAD) != InputDevice.SOURCE_GAMEPAD) {
            return false;
        }
        // Only report "within dead zone" when an axis actually carries movement that the
        // dead zone flattens. Returning true for an all-zero event would swallow controller
        // button presses, which also arrive through dispatchGenericMotionEvent with no stick
        // deflection.
        return isFlattened(response, event, MotionEvent.AXIS_X)
                || isFlattened(response, event, MotionEvent.AXIS_Y)
                || isFlattened(response, event, MotionEvent.AXIS_Z)
                || isFlattened(response, event, MotionEvent.AXIS_RZ);
    }

    private static boolean isFlattened(ControllerResponse response, MotionEvent event, int axis) {
        float value = event.getAxisValue(axis);
        return value != 0f && !response.isOutsideDeadZone(axis, value);
    }

    public static int processKeyEvent(int keyCode) {
        ControllerResponse response = active;
        if (response == null || keyCode <= 0) {
            return keyCode;
        }
        return response.remapKey(keyCode);
    }
}