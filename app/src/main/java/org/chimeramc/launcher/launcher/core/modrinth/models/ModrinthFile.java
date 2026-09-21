package org.chimeramc.launcher.core.modrinth.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;

public class ModrinthFile implements Serializable {
    @SerializedName("url")
    public String url;
    @SerializedName("filename")
    public String filename;
    @SerializedName("size")
    public long size;
    @SerializedName("primary")
    public boolean primary;
}
