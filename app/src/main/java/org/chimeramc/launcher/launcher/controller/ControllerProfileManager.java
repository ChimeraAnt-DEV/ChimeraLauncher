package org.chimeramc.launcher.launcher.controller;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Persists up to {@link ControllerProfile#MAX_SLOTS} profiles per controller
 * type in SharedPreferences as JSON, plus the active slot index per type.
 */
public class ControllerProfileManager {
    private static final String PREFS_NAME = "controller_profiles";
    private static final String KEY_LIST_PREFIX = "profiles_";
    private static final String KEY_ACTIVE_PREFIX = "active_";
    private static final String KEY_BINDING_PREFIX = "instance_binding_";
    private static final Gson GSON = new Gson();
    private static final Type LIST_TYPE = new TypeToken<List<ControllerProfile>>() {}.getType();

    private final SharedPreferences prefs;

    public ControllerProfileManager(Context context) {
        this.prefs = context.getApplicationContext() .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public List<ControllerProfile> getProfiles(ControllerType type) {
        List<ControllerProfile> profiles = loadList(type);
        if (profiles.isEmpty()) {
            profiles.add(new ControllerProfile("Default"));
            saveList(type, profiles);
        }
        return profiles;
    }

    public void saveProfiles(ControllerType type, List<ControllerProfile> profiles) {
        saveList(type, profiles);
    }

    public int getActiveSlot(ControllerType type) {
        return prefs.getInt(KEY_ACTIVE_PREFIX + type.name() , 0);
    }

    public void setActiveSlot(ControllerType type, int slot) {
        int clamped = Math.max(0, Math.min(ControllerProfile.MAX_SLOTS - 1, slot));
        prefs.edit() .putInt(KEY_ACTIVE_PREFIX + type.name() , clamped).apply();
    }

    public ControllerProfile getActiveProfile(ControllerType type) {
        List<ControllerProfile> profiles = getProfiles(type);
        int slot = getActiveSlot(type);
        return profiles.get(Math.min(slot, profiles.size() - 1));
    }

    /** The profile in a specific slot, clamped into range so a stale binding cannot throw. */
    public ControllerProfile getProfile(ControllerType type, int slot) {
        List<ControllerProfile> profiles = getProfiles(type);
        return profiles.get(Math.max(0, Math.min(slot, profiles.size() - 1)));
    }

    /**
     * Binds a controller profile to a Minecraft instance, so selecting that instance can
     * switch the active controller profile automatically.
     *
     * The binding stores the controller <em>type</em> alongside the slot because a slot index
     * is meaningless on its own: slot 1 is a different profile for Xbox than for DualSense.
     * Instance storage ids are stable per version, which is what makes them the right key.
     */
    public void bindInstance(String profileId, ControllerType type, int slot) {
        if (profileId == null || profileId.trim().isEmpty() || type == null) return;
        int clamped = Math.max(0, Math.min(ControllerProfile.MAX_SLOTS - 1, slot));
        prefs.edit().putString(KEY_BINDING_PREFIX + profileId, type.name() + ":" + clamped).apply();
    }

    /** Removes the instance binding, so the instance no longer switches profiles. */
    public void clearBinding(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) return;
        prefs.edit().remove(KEY_BINDING_PREFIX + profileId).apply();
    }

    public boolean hasBinding(String profileId) {
        return getBinding(profileId) != null;
    }

    /**
     * The profile bound to this instance, or null when nothing is bound (or the stored value
     * is unreadable). A stale binding whose slot no longer exists falls back to slot 0 rather
     * than throwing, because deleting a profile must not break launching an instance.
     */
    public Binding getBinding(String profileId) {
        if (profileId == null || profileId.trim().isEmpty()) return null;
        String raw = prefs.getString(KEY_BINDING_PREFIX + profileId, null);
        if (raw == null) return null;
        int separator = raw.lastIndexOf(':');
        if (separator <= 0 || separator == raw.length() - 1) return null;
        ControllerType type = parseType(raw.substring(0, separator));
        if (type == null) return null;
        int slot;
        try {
            slot = Integer.parseInt(raw.substring(separator + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        return new Binding(type, Math.max(0, Math.min(ControllerProfile.MAX_SLOTS - 1, slot)));
    }

    private static ControllerType parseType(String name) {
        for (ControllerType type : ControllerType.values()) {
            if (type.name().equals(name)) return type;
        }
        return null;
    }

    /** A bound controller type plus slot for one instance. */
    public static final class Binding {
        public final ControllerType type;
        public final int slot;

        Binding(ControllerType type, int slot) {
            this.type = type;
            this.slot = slot;
        }
    }

    public int addProfile(ControllerType type, String name) {
        List<ControllerProfile> profiles = getProfiles(type);
        if (profiles.size() >= ControllerProfile.MAX_SLOTS) {
            return -1;
        }
        ControllerProfile profile = new ControllerProfile(sanitizeName(name, profiles));
        profiles.add(profile);
        saveList(type, profiles);
        return profiles.size() - 1;
    }

    public void deleteProfile(ControllerType type, int slot) {
        List<ControllerProfile> profiles = loadList(type);
        if (slot < 0 || slot >= profiles.size()) return;
        profiles.remove(slot);
        if (profiles.isEmpty()) profiles.add(new ControllerProfile("Default"));
        saveList(type, profiles);
        int active = getActiveSlot(type);
        if (active >= profiles.size()) setActiveSlot(type, profiles.size() - 1);
    }

    public void duplicateProfile(ControllerType type, int slot, String newName) {
        List<ControllerProfile> profiles = getProfiles(type);
        if (slot < 0 || slot >= profiles.size() || profiles.size() >= ControllerProfile.MAX_SLOTS) return;
        ControllerProfile copy = profiles.get(slot) .copy();
        copy.setName(newName == null || newName.trim() .isEmpty() ? copy.getName() + " Copy" : newName.trim());
        profiles.add(copy);
        saveList(type, profiles);
    }

    public void renameProfile(ControllerType type, int slot, String newName) {
        List<ControllerProfile> profiles = getProfiles(type);
        if (slot < 0 || slot >= profiles.size() || newName == null || newName.trim() .isEmpty()) return;
        profiles.get(slot) .setName(newName.trim());
        saveList(type, profiles);
    }

    private String sanitizeName(String name, List<ControllerProfile> profiles) {
        String base = name == null || name.trim() .isEmpty() ? "Profile" : name.trim();
        String candidate = base;
        int idx = 2;
        while (true) {
            final String c = candidate;
            boolean taken = profiles.stream() .anyMatch(p -> p.getName() .equals(c));
            if (!taken) return candidate;
            candidate = base + " " + idx++;
        }
    }

    private List<ControllerProfile> loadList(ControllerType type) {
        String json = prefs.getString(KEY_LIST_PREFIX + type.name() , null);
        if (json == null) return new ArrayList();
        try {
            List<ControllerProfile> profiles = GSON.fromJson(json, LIST_TYPE);
            if (profiles == null) return new ArrayList();
            for (ControllerProfile p : profiles) {
                if (p.getName() == null) p.setName("Profile");
            }
            return profiles;
        } catch (JsonSyntaxException e) {
            return new ArrayList();
        }
    }

    private void saveList(ControllerType type, List<ControllerProfile> profiles) {
        prefs.edit() .putString(KEY_LIST_PREFIX + type.name() , GSON.toJson(profiles)).apply();
    }
}