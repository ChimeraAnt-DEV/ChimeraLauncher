package org.chimeramc.launcher.core.curseforge;

import android.content.Context;

import com.google.gson.Gson;

import org.chimeramc.launcher.core.curseforge.models.ContentSearchResponse;
import org.chimeramc.launcher.core.curseforge.models.ModFilesResponse;
import org.chimeramc.launcher.core.curseforge.models.StringResponse;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class CurseForgeClient {
    private static final String BASE_URL = "https://api.curseforge.com";

    public static final int GAME_ID_MINECRAFT = 78022;

    public static final String SORT_POPULARITY = "2";
    public static final String SORT_LAST_UPDATED = "3";
    public static final String SORT_NAME = "4";
    public static final String SORT_TOTAL_DOWNLOADS = "6";

    private final OkHttpClient client;
    private final Gson gson;
    private final Context appContext;

    private static CurseForgeClient instance;

    private CurseForgeClient(Context context) {
        this.appContext = context == null ? null : context.getApplicationContext();
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS);
        org.chimeramc.launcher.settings.LowLatencyNetworkManager.configure(builder);
        this.client = builder.build();
        this.gson = new Gson();
    }

    public static synchronized CurseForgeClient getInstance(Context context) {
        if (instance == null) {
            instance = new CurseForgeClient(context);
        }
        return instance;
    }

    public interface CurseForgeCallback<T> {
        void onSuccess(T result);
        void onError(Throwable t);
    }

    /** Raised when no API key is configured, so callers can point the user at the setting. */
    public static class MissingApiKeyException extends IOException {
        public MissingApiKeyException() {
            super("No CurseForge API key configured");
        }
    }

    private boolean requireKey(CurseForgeCallback<?> callback) {
        if (!CurseForgeKeyStore.hasApiKey(appContext)) {
            callback.onError(new MissingApiKeyException());
            return false;
        }
        return true;
    }

    private Request.Builder authedRequest(String url) {
        return new Request.Builder()
                .url(url)
                .addHeader("x-api-key", CurseForgeKeyStore.getApiKey(appContext))
                .addHeader("Accept", "application/json");
    }

    /** Turns an HTTP status into a message that says what the user can do about it. */
    private static IOException describeFailure(Response response) {
        int code = response.code();
        if (code == 401 || code == 403) {
            return new IOException("CurseForge rejected the API key (HTTP " + code
                    + "). Check the key in Settings > CurseForge.");
        }
        if (code == 429) {
            return new IOException("CurseForge rate limit reached (HTTP 429). Try again shortly.");
        }
        if (code >= 500) {
            return new IOException("CurseForge is having trouble (HTTP " + code + "). Try again later.");
        }
        return new IOException("Unexpected response from CurseForge: HTTP " + code);
    }

    public void searchContent(String query, int classId, String version, int index, int pageSize,
                              String sortField, String sortOrder,
                              CurseForgeCallback<ContentSearchResponse> callback) {
        if (!requireKey(callback)) return;
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/v1/mods/search").newBuilder();
        urlBuilder.addQueryParameter("gameId", String.valueOf(GAME_ID_MINECRAFT));
        urlBuilder.addQueryParameter("sortField", sortField != null ? sortField : SORT_POPULARITY);
        urlBuilder.addQueryParameter("sortOrder", sortOrder != null ? sortOrder : "desc");
        urlBuilder.addQueryParameter("index", String.valueOf(index));
        urlBuilder.addQueryParameter("pageSize", String.valueOf(pageSize));

        if (query != null && !query.isEmpty()) {
            urlBuilder.addQueryParameter("searchFilter", query);
        }
        if (classId > 0) {
            urlBuilder.addQueryParameter("classId", String.valueOf(classId));
        }
        if (version != null && !version.isEmpty() && !"All".equals(version)) {
            urlBuilder.addQueryParameter("gameVersion", version);
        }

        Request request = authedRequest(urlBuilder.build().toString()).build();

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
                    callback.onSuccess(gson.fromJson(json, ContentSearchResponse.class));
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    public void getContentDescription(int contentId, CurseForgeCallback<String> callback) {
        if (!requireKey(callback)) return;
        Request request = authedRequest(BASE_URL + "/v1/mods/" + contentId + "/description").build();

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
                    callback.onSuccess(gson.fromJson(json, StringResponse.class).data);
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }

    public void getModFiles(int modId, int index, int pageSize, CurseForgeCallback<ModFilesResponse> callback) {
        if (!requireKey(callback)) return;
        HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/v1/mods/" + modId + "/files").newBuilder();
        urlBuilder.addQueryParameter("index", String.valueOf(index));
        urlBuilder.addQueryParameter("pageSize", String.valueOf(pageSize));

        Request request = authedRequest(urlBuilder.build().toString()).build();

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
                    callback.onSuccess(gson.fromJson(json, ModFilesResponse.class));
                } catch (Exception e) {
                    callback.onError(e);
                }
            }
        });
    }
}
