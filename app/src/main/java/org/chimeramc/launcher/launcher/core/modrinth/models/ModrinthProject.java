package org.chimeramc.launcher.core.modrinth.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

/** One Modrinth search hit. Modrinth is a Java Edition index; see {@link #projectType}. */
public class ModrinthProject implements Serializable {
    @SerializedName("project_id")
    public String projectId;
    @SerializedName("slug")
    public String slug;
    @SerializedName("title")
    public String title;
    @SerializedName("description")
    public String description;
    @SerializedName("author")
    public String author;
    @SerializedName("project_type")
    public String projectType;
    @SerializedName("downloads")
    public long downloads;
    @SerializedName("follows")
    public long follows;
    @SerializedName("icon_url")
    public String iconUrl;
    @SerializedName("date_modified")
    public String dateModified;
    @SerializedName("latest_version")
    public String latestVersion;
    @SerializedName("categories")
    public List<String> categories;
    @SerializedName("display_categories")
    public List<String> displayCategories;
    @SerializedName("versions")
    public List<String> gameVersions;
    @SerializedName("license")
    public String license;
    /** "required" / "optional" / "unsupported" / "unknown", from Modrinth's environment block. */
    @SerializedName("client_side")
    public String clientSide;
    @SerializedName("server_side")
    public String serverSide;

    public String displayName() {
        return title != null && !title.isEmpty() ? title : slug;
    }
}
