package org.chimeramc.launcher.core.monster;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.chimeramc.launcher.core.monster.MonsterMcpeParser.MonsterDownload;
import org.chimeramc.launcher.core.monster.MonsterMcpeParser.MonsterVersion;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Reads the version list and package links from Monster MCPE, and downloads a chosen
 * package into a caller-supplied file.
 *
 * Page reads go through {@link MonsterMcpeSession} because the site is behind a Cloudflare
 * browser check: a plain HTTP request is answered with the "Just a moment..." interstitial
 * and HTTP 403, never the page. Once the browser has cleared the check, its {@code cf_clearance}
 * cookie is replayed here so the package download streams over OkHttp (which gives real
 * progress and a file on disk) instead of through the WebView.
 */
public class MonsterMcpeClient {

    private static final int BUFFER_SIZE = 131072;

    private final OkHttpClient client;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static MonsterMcpeClient instance;

    private MonsterMcpeClient(Context context) {
        OkHttpClient.Builder builder = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true);
        org.chimeramc.launcher.settings.LowLatencyNetworkManager.configure(builder);
        this.client = builder.build();
    }

    public static synchronized MonsterMcpeClient getInstance(Context context) {
        if (instance == null) {
            instance = new MonsterMcpeClient(context == null ? null : context.getApplicationContext());
        }
        return instance;
    }

    public interface ListingCallback {
        void onSuccess(List<MonsterVersion> versions);

        /** The site wants a browser check; the caller should surface the WebView. */
        void onChallenge();

        void onError(Throwable error);
    }

    public interface DownloadLinkCallback {
        void onSuccess(MonsterDownload download);

        void onChallenge();

        void onError(Throwable error);
    }

    public interface FileDownloadCallback {
        void onSuccess();

        void onError(Throwable error);
    }

    /** Progress of a package download, reported on the main thread. */
    public interface ProgressListener {
        void onProgress(int percent);
    }

    /** Fetches the listing page through the browser session and extracts the versions. */
    public void fetchVersions(MonsterMcpeSession session, ListingCallback callback) {
        session.fetchHtml(MonsterMcpeParser.LISTING_URL, new MonsterMcpeSession.HtmlCallback() {
            @Override
            public void onHtml(String html) {
                List<MonsterVersion> versions = MonsterMcpeParser.parseListing(html);
                if (versions.isEmpty()) {
                    callback.onError(new IOException("No versions found on the download page"));
                } else {
                    callback.onSuccess(versions);
                }
            }

            @Override
            public void onChallenge() {
                callback.onChallenge();
            }

            @Override
            public void onError(Throwable error) {
                callback.onError(error);
            }
        });
    }

    /** Fetches a version page through the browser session and resolves its package link. */
    public void fetchDownloadLink(MonsterMcpeSession session, String pageUrl,
                                  DownloadLinkCallback callback) {
        session.fetchHtml(pageUrl, new MonsterMcpeSession.HtmlCallback() {
            @Override
            public void onHtml(String html) {
                List<MonsterDownload> downloads = MonsterMcpeParser.parseDownloadPage(html);
                MonsterDownload download = MonsterMcpeParser.firstPackage(downloads);
                if (download == null) {
                    callback.onError(new IOException("This version has no downloadable package"));
                } else {
                    callback.onSuccess(download);
                }
            }

            @Override
            public void onChallenge() {
                callback.onChallenge();
            }

            @Override
            public void onError(Throwable error) {
                callback.onError(error);
            }
        });
    }

    /**
     * Streams [url] into [destination].
     *
     * Runs off the main thread; progress is posted back on it. [cookies] and [userAgent] come
     * from the browser session that cleared the site's check, so the request is treated as the
     * same client. The caller owns the file and deletes a partial download on failure.
     */
    public void downloadPackage(String url, File destination, String cookies, String userAgent,
                                ProgressListener listener, FileDownloadCallback callback) {
        new Thread(() -> {
            try (Response response = client.newCall(buildDownloadRequest(url, cookies, userAgent))
                    .execute()) {
                if (!response.isSuccessful() || response.body() == null) {
                    throw describeFailure(response);
                }
                long total = response.body().contentLength();
                long read = 0;
                byte[] buffer = new byte[BUFFER_SIZE];
                try (InputStream in = response.body().byteStream();
                     OutputStream out = new java.io.FileOutputStream(destination)) {
                    int count;
                    while ((count = in.read(buffer)) != -1) {
                        out.write(buffer, 0, count);
                        read += count;
                        if (listener != null && total > 0) {
                            int percent = (int) Math.min(100, (read * 100) / total);
                            handler.post(() -> listener.onProgress(percent));
                        }
                    }
                }
                handler.post(callback::onSuccess);
            } catch (Exception e) {
                handler.post(() -> callback.onError(e));
            }
        }, "monster-mcpe-download").start();
    }

    private Request buildDownloadRequest(String url, String cookies, String userAgent) {
        Request.Builder builder = new Request.Builder()
                .url(url)
                .addHeader("Accept", "*/*")
                .addHeader("Referer", MonsterMcpeParser.LISTING_URL);
        if (userAgent != null && !userAgent.isEmpty()) {
            builder.addHeader("User-Agent", userAgent);
        }
        if (cookies != null && !cookies.isEmpty()) {
            builder.addHeader("Cookie", cookies);
        }
        return builder.build();
    }

    /** Turns an HTTP status into a message that says what the user can do about it. */
    private static IOException describeFailure(Response response) {
        int code = response.code();
        if (code == 403) {
            return new IOException("Monster MCPE blocked the download (HTTP 403). "
                    + "The browser check may have expired; reload the list and try again.");
        }
        if (code == 404) {
            return new IOException("Monster MCPE has no such file (HTTP 404).");
        }
        if (code >= 500) {
            return new IOException("Monster MCPE is having trouble (HTTP " + code + "). Try again later.");
        }
        return new IOException("Unexpected response from Monster MCPE: HTTP " + code);
    }
}
