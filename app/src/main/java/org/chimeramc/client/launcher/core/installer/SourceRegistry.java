package org.chimeramc.client.core.installer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The package sources the launcher knows about, and which one is currently in use.
 *
 * Registration is deliberately a plain list rather than reflection or a service loader: the
 * set is tiny, it must be obvious at a glance which mirrors ship in the app, and an
 * unreadable preference should fall back to the first entry rather than to nothing.
 *
 * Adding a mirror is therefore two steps — implement {@link BedrockSource} and add it to
 * {@link #all()} — with no change to the UI, the download client or the import pipeline.
 *
 * The order matters: {@link #active()} is the first entry, so the first source is the one the
 * Installations tab uses out of the box.
 */
public final class SourceRegistry {

    private static final List<BedrockSource> SOURCES = List.of(
            new McpePlanetSource(),
            new McpedlSource()
    );

    private static final String PREF_FILE = "installer_sources";
    private static final String KEY_ACTIVE = "active_source_id";

    private SourceRegistry() {
    }

    /** Every registered source, in the order the settings screen should offer them. */
    public static List<BedrockSource> all() {
        return Collections.unmodifiableList(new ArrayList<>(SOURCES));
    }

    /** The source used for new downloads; the first registered one when unset or unknown. */
    public static BedrockSource active() {
        return SOURCES.get(0);
    }

    /** Looks a source up by {@link BedrockSource#id()}, falling back to {@link #active()}. */
    public static BedrockSource byId(String id) {
        if (id != null) {
            for (BedrockSource source : SOURCES) {
                if (id.equals(source.id())) return source;
            }
        }
        return active();
    }

    /** Preference file and key, exposed so a settings screen can persist a choice later. */
    public static String preferenceFile() {
        return PREF_FILE;
    }

    public static String activeKey() {
        return KEY_ACTIVE;
    }
}
