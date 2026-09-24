package org.chimeramc.client.core.modrinth.models;

import com.google.gson.annotations.SerializedName;

import java.io.Serializable;
import java.util.List;

public class ModrinthSearchResponse implements Serializable {
    @SerializedName("hits")
    public List<ModrinthProject> hits;
    @SerializedName("offset")
    public int offset;
    @SerializedName("limit")
    public int limit;
    @SerializedName("total_hits")
    public long totalHits;
}
