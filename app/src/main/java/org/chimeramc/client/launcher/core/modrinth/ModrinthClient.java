package org.chimeramc.client.core.modrinth;

import android.content.Context;

import com.google.gson.Gson;

import org.chimeramc.client.core.modrinth.models.ModrinthSearchResponse;
import org.chimeramc.client.core.modrinth.models.ModrinthVersion;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Read-only Modrinth API client. Unlike CurseForge this needs no API key: Modrinth's v2 API
 * is open, with a documented expectation that clients send a descriptive User-Agent.
 *
 * Note that Modrinth indexes Java Edition projects. A Bedrock launcher cannot install most
 * of it, so the browser surfaces the project type rather than pretending otherwise.
 */
public class ModrinthClient {
    private static final String BASE_URL = "https://api.modrinth.com/v2";

    // Modrinth asks for a UA that identifies the app; omitting one risks being throttled.
    private static final String USER_AGENT = "ChimeraClient/1.0 (ChimeraClient)";

    public static final String TYPE_MOD = "mod";
    public static final String TYPE_RESOURCEPACK = "resourcepack";
    public static final String TYPE_DATAPACK = "datapack";
    public static final String TYPE_MODPACK = "modpack";

    public static final String INDEX_RELEVANCY = "relevance";
    public static final String INDEX_DOWNLOADS = "downloads";
    public static final String INDEX_FOLLOWS = "follows";
    public static final String INDEX_NEWEST = "newest";
    public static final String INDEX_UPDATED = "updated";

    private final OkHttpClient client;
    private final Gson gson;

    private static ModrinthClient instance;

    private ModrinthClient(Context context) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);
        org.chimeramc.client.settings.LowLatencyNetworkManager.configure(builder);
        this.client = builder.build();
        this.gson = new Gson();
    }

    public static synchronized ModrinthClient getInstance(Context context) {
        if (instance == null) {
            instance = new ModrinthClient(context == null ? null : context.getApplicationContext());
        }
        return instance;
    }

    public interface ModrinthCallback<T> {
        void onSuccess(T result);

        void onError(Throwable t);
    }

    private Request.Builder request(String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("User-Agent", USER_AGENT)
                .addHeader("Accept", "application/json");
    }

    /** Turns an HTTP status into a message that says what the user can do about it. */
    private static IOException describeFailure(Response response) {
        int code = response.code();
        if (code == 429) {
            return new IOException("Modrinth rate limit reached (HTTP 429). Try again shortly.");
        }
        if (code == 404) {
            return new IOException("Modrinth has no such project (HTTP 404).");
        }
        if (code >= 500) {
            return new IOException("Modrinth is having trouble (HTTP " + code + "). Try again later.");
        }
        return new IOException("Unexpected response from Modrinth: HTTP " + code);
    }

    /**
     * Searches projects.
     *
     * @param projectType one of the {@code TYPE_*} constants, or null/empty for all types.
     * @param gameVersion an exact game version to filter on, or null/empty for all.
     */
    public void search(String query, String projectType, String gameVersion,
                       int offset, int limit, String index,
                       ModrinthCallback<ModrinthSearchResponse> callback) {
        Request request = request(buildSearchUrl(query, projectType, gameVersion, offset, limit, index))
                .build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    if (!closeable.isSuccessful()) {
                        callback.onError(describeFailure(closeable));
                        return;
                    }
                    String json = closeable.body().string();
                    callback.onSuccess(gson.fromJson(json, ModrinthSearchResponse.class));
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    /** Package-visible so the facet encoding can be asserted without a network round trip. */
    static String buildSearchUrl(String query, String projectType, String gameVersion,
                                 int offset, int limit, String index) {
        HttpUrl.Builder builder = HttpUrl.parse(BASE_URL + "/search").newBuilder();
        builder.addQueryParameter("limit", String.valueOf(clamp(limit, 1, 100)));
        builder.addQueryParameter("offset", String.valueOf(Math.max(0, offset)));
        builder.addQueryParameter("index", index != null && !index.isEmpty() ? index : INDEX_RELEVANCY);
        if (query != null && !query.isEmpty()) {
            builder.addQueryParameter("query", query);
        }

        StringBuilder facets = new StringBuilder();
        if (projectType != null && !projectType.isEmpty()) {
            appendFacet(facets, "project_type:" + projectType);
        }
        if (gameVersion != null && !gameVersion.isEmpty()) {
            appendFacet(facets, "versions:" + gameVersion);
        }
        if (facets.length() > 0) {
            // Each facet is its own OR-group; groups are AND-ed by Modrinth.
            builder.addQueryParameter("facets", "[" + facets + "]");
        }
        return builder.build().toString();
    }

    /**
     * Lists a project's versions.
     *
     * @param gameVersions exact game versions to filter on; may be null or empty.
     */
    public void getVersions(String projectIdOrSlug, java.util.List<String> gameVersions,
                            ModrinthCallback<ModrinthVersion[]> callback) {
        HttpUrl.Builder builder =
                HttpUrl.parse(BASE_URL + "/project/" + projectIdOrSlug + "/version").newBuilder();
        if (gameVersions != null && !gameVersions.isEmpty()) {
            StringBuilder json = new StringBuilder("[");
            for (int i = 0; i < gameVersions.size(); i++) {
                if (i > 0) {
                    json.append(',');
                }
                json.append('"').append(gameVersions.get(i)).append('"');
            }
            json.append(']');
            builder.addQueryParameter("game_versions", json.toString());
        }

        Request request = request(builder.build().toString()).build();
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                callback.onError(e);
            }

            @Override
            public void onResponse(Call call, Response response) {
                try (Response closeable = response) {
                    if (!closeable.isSuccessful()) {
                        callback.onError(describeFailure(closeable));
                        return;
                    }
                    String json = closeable.body().string();
                    callback.onSuccess(gson.fromJson(json, ModrinthVersion[].class));
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    /** Appends one facet group, e.g. {@code ["project_type:resourcepack"]}, ready for wrapping. */
    private static void appendFacet(StringBuilder facets, String facet) {
        if (facets.length() > 0) {
            facets.append(',');
        }
        facets.append("[\"").append(facet).append("\"]");
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
