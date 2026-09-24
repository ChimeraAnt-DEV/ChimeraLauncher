package org.chimeramc.client.core.modrinth.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class ModrinthVersion implements Serializable {
    @SerializedName("id")
    public String id;
    @SerializedName("project_id")
    public String projectId;
    @SerializedName("name")
    public String name;
    @SerializedName("version_number")
    public String versionNumber;
    /** "release", "beta" or "alpha". */
    @SerializedName("version_type")
    public String versionType;
    @SerializedName("date_published")
    public String datePublished;
    @SerializedName("downloads")
    public long downloads;
    @SerializedName("game_versions")
    public List<String> gameVersions;
    @SerializedName("loaders")
    public List<String> loaders;
    @SerializedName("files")
    public List<ModrinthFile> files;

    /** The file Modrinth marks primary, falling back to the first one listed. */
    public ModrinthFile primaryFile() {
        if (files == null || files.isEmpty()) {
            return null;
        }
        for (ModrinthFile file : files) {
            if (file.primary) {
                return file;
            }
        }
        return files.get(0);
    }
}
