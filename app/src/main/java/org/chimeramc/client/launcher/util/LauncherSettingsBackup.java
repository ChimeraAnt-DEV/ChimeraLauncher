package org.chimeramc.client.util;

import android.content.Context;
import android.content.SharedPreferences;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.chimeramc.client.launcher.controller.ControllerProfile;
import org.chimeramc.client.launcher.controller.ControllerProfileCodec;
import org.chimeramc.client.launcher.controller.ControllerProfileManager;
import org.chimeramc.client.launcher.controller.ControllerType;
import org.chimeramc.client.settings.FeatureSettings;
import org.chimeramc.client.settings.SettingsStorage;

import java.util.List;
import java.util.Map;

/**
 * Exports and imports the launcher's own configuration: feature settings, personalization and
 * controller profiles.
 *
 * This is deliberately not the instance backup. {@link InstanceBackupManager} moves one
 * instance's game data (worlds, resource packs, options) between devices; this moves the
 * launcher's preferences between installs, so a device or a reinstall does not mean retuning
 * dead zones and re-picking an accent colour.
 *
 * What is intentionally excluded: account tokens and any API key. Those are credentials, and a
 * settings file that people share in chat is exactly the wrong place for them. The export says
 * so in its manifest rather than silently dropping fields.
 */
public final class LauncherSettingsBackup {

    public static final String FORMAT_ID = "org.chimeramc.client.settings";
    public static final int SCHEMA_VERSION = 1;

    private static final Gson GSON = new Gson();

    private LauncherSettingsBackup() {
    }

    /** Human-readable summary of what an imported file contained. */
    public static final class ImportResult {
        public final boolean success;
        public final String message;
        public final int profileCount;

        private ImportResult(boolean success, String message, int profileCount) {
            this.success = success;
            this.message = message;
            this.profileCount = profileCount;
        }
    }

    /**
     * Builds the export document.
     *
     * @param includePersonalization when false the personalization block is omitted, which is
     *                               useful when moving profiles between devices whose screens
     *                               differ enough that the same transparency looks wrong.
     */
    public static String export(Context context, boolean includePersonalization,
                                boolean includeControllerProfiles) {
        JsonObject root = new JsonObject();
        root.addProperty("format", FORMAT_ID);
        root.addProperty("schemaVersion", SCHEMA_VERSION);
        root.addProperty("createdAt", System.currentTimeMillis());
        root.addProperty("containsCredentials", false);

        root.add("featureSettings", featureSettingsJson(context));
        if (includePersonalization) {
            root.add("personalization", sharedPrefsJson(context, PersonalizationManager.PREFS_NAME));
        }
        if (includeControllerProfiles) {
            root.add("controllerProfiles", controllerProfilesJson(context));
        }
        return GSON.toJson(root);
    }

    /** Applies an exported document over the current configuration. */
    public static ImportResult importFrom(Context context, String json) {
        if (context == null) {
            return new ImportResult(false, "No context", 0);
        }
        if (json == null || json.trim().isEmpty()) {
            return new ImportResult(false, "The file is empty.", 0);
        }
        JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                return new ImportResult(false, "The file is not a launcher settings export.", 0);
            }
            root = parsed.getAsJsonObject();
        } catch (Exception e) {
            return new ImportResult(false, "The file is not valid JSON.", 0);
        }
        if (!root.has("format") || !FORMAT_ID.equals(root.get("format").getAsString())) {
            return new ImportResult(false, "The file is not a launcher settings export.", 0);
        }
        int applied = 0;
        int profiles = 0;
        try {
            if (root.has("personalization") && root.get("personalization").isJsonObject()) {
                applySharedPrefs(context, PersonalizationManager.PREFS_NAME,
                        root.getAsJsonObject("personalization"));
            }
            if (root.has("controllerProfiles") && root.get("controllerProfiles").isJsonObject()) {
                profiles = applyControllerProfiles(context, root.getAsJsonObject("controllerProfiles"));
            }
            if (root.has("featureSettings") && root.get("featureSettings").isJsonObject()) {
                applyFeatureSettings(context, root.getAsJsonObject("featureSettings"));
            }
            applied = 1;
        } catch (Exception e) {
            return new ImportResult(false,
                    e.getMessage() == null ? "Import failed." : e.getMessage(), 0);
        }
        return new ImportResult(true, "Settings restored.", profiles);
    }

    private static JsonObject featureSettingsJson(Context context) {
        String raw = context.getSharedPreferences(SettingsStorage.SP_NAME, Context.MODE_PRIVATE)
                .getString(SettingsStorage.KEY_SETTINGS_JSON, null);
        if (raw == null) return new JsonObject();
        try {
            JsonElement parsed = JsonParser.parseString(raw);
            return parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            return new JsonObject();
        }
    }

    private static void applyFeatureSettings(Context context, JsonObject settings) {
        // Written through the same store the app reads, then the in-memory singleton is
        // refreshed so the running session does not keep the pre-import values.
        SettingsStorage.save(context, GSON.fromJson(settings, FeatureSettings.class));
        FeatureSettings.reload(context);
    }

    private static JsonObject sharedPrefsJson(Context context, String prefsName) {
        SharedPreferences prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE);
        JsonObject result = new JsonObject();
        for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
            Object value = entry.getValue();
            if (value instanceof Boolean) {
                result.addProperty(entry.getKey(), (Boolean) value);
            } else if (value instanceof Number) {
                result.addProperty(entry.getKey(), (Number) value);
            } else if (value != null) {
                result.addProperty(entry.getKey(), String.valueOf(value));
            }
        }
        return result;
    }

    private static void applySharedPrefs(Context context, String prefsName, JsonObject values) {
        SharedPreferences.Editor editor = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).edit();
        for (Map.Entry<String, JsonElement> entry : values.entrySet()) {
            JsonElement value = entry.getValue();
            if (!value.isJsonPrimitive()) continue;
            if (value.getAsJsonPrimitive().isBoolean()) {
                editor.putBoolean(entry.getKey(), value.getAsBoolean());
            } else if (value.getAsJsonPrimitive().isNumber()) {
                // SharedPreferences is typed per key, and reading an int with getInt after an
                // import written as a float throws ClassCastException at the read site. Gson
                // keeps the original literal, so a decimal point is what separates an int pref
                // from a float one.
                String literal = value.getAsString();
                if (literal.indexOf('.') >= 0 || literal.indexOf('e') >= 0 || literal.indexOf('E') >= 0) {
                    editor.putFloat(entry.getKey(), value.getAsFloat());
                } else {
                    editor.putInt(entry.getKey(), value.getAsInt());
                }
            } else {
                editor.putString(entry.getKey(), value.getAsString());
            }
        }
        editor.apply();
    }

    private static JsonObject controllerProfilesJson(Context context) {
        JsonObject result = new JsonObject();
        ControllerProfileManager manager = new ControllerProfileManager(context);
        for (ControllerType type : ControllerType.values()) {
            JsonObject block = new JsonObject();
            // Reuse the profile codec so a profile file and this settings file are validated
            // identically on the way back in (slot limit, curve ranges, null entries).
            block.addProperty("bundle",
                    ControllerProfileCodec.export(type, manager.getProfiles(type)));
            block.addProperty("activeSlot", manager.getActiveSlot(type));
            result.add(type.name(), block);
        }
        return result;
    }

    private static int applyControllerProfiles(Context context, JsonObject block) {
        ControllerProfileManager manager = new ControllerProfileManager(context);
        int count = 0;
        for (ControllerType type : ControllerType.values()) {
            if (!block.has(type.name()) || !block.get(type.name()).isJsonObject()) continue;
            JsonObject typeBlock = block.getAsJsonObject(type.name());
            if (!typeBlock.has("bundle")) continue;
            List<ControllerProfile> profiles;
            try {
                profiles = ControllerProfileCodec.importFrom(typeBlock.get("bundle").getAsString()).profiles;
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (profiles.isEmpty()) continue;
            manager.saveProfiles(type, profiles);
            count += profiles.size();
            if (typeBlock.has("activeSlot")) {
                int slot = typeBlock.get("activeSlot").getAsInt();
                manager.setActiveSlot(type, Math.max(0, Math.min(profiles.size() - 1, slot)));
            }
        }
        return count;
    }
}
