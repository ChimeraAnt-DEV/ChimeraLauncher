package org.chimeramc.launcher.ui.activities;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.chimeramc.launcher.R;
import org.chimeramc.launcher.core.monster.MonsterMcpeClient;
import org.chimeramc.launcher.core.monster.MonsterMcpeSession;
import org.chimeramc.launcher.core.monster.MonsterMcpeParser;
import org.chimeramc.launcher.core.monster.MonsterMcpeParser.MonsterDownload;
import org.chimeramc.launcher.core.monster.MonsterMcpeParser.MonsterVersion;
import org.chimeramc.launcher.core.versions.GameVersion;
import org.chimeramc.launcher.core.versions.VersionManager;
import org.chimeramc.launcher.ui.adapter.InstallationAdapter;
import org.chimeramc.launcher.ui.dialogs.CustomAlertDialog;
import org.chimeramc.launcher.util.ApkImportManager;
import org.chimeramc.launcher.util.DownloadedApksStore;
import org.chimeramc.launcher.ui.animation.DynamicAnim;
import org.chimeramc.launcher.util.LauncherStorage;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lists the Bedrock versions Monster MCPE publishes and installs a chosen one as an isolated
 * instance.
 *
 * The flow is deliberately: resolve the download link, save the package into the app's
 * private {@code Downloaded_APKs} folder, import from that file, and only delete it once the
 * import reported success. A failed import therefore leaves the package in place so "Retry
 * Import" can run the pipeline again without re-downloading several hundred megabytes.
 *
 * The package source is a third-party mirror, not Mojang, so the screen says so and the
 * user confirms before anything is fetched.
 */
public class InstallationsActivity extends BaseActivity {

    private static final String STATE_PENDING_VERSION = "installations_pending_version";
    private static final String STATE_PENDING_PAGE = "installations_pending_page";
    private static final String STATE_PENDING_FILE = "installations_pending_file";

    private RecyclerView recycler;
    private ProgressBar progress;
    private View emptyContainer;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private TextView refreshButton;
    private TextView stagedSummary;
    private TextView stagedRetry;
    private TextView stagedClear;
    private View stagedContainer;

    private InstallationAdapter adapter;
    private MonsterMcpeClient client;
    private MonsterMcpeSession session;
    private WebView browser;
    private FrameLayout browserContainer;
    private TextView browserHint;
    private DownloadedApksStore store;
    private ApkImportManager importManager;
    private VersionManager versionManager;

    private final List<MonsterVersion> versions = new ArrayList<>();
    private final Map<String, MonsterDownload> resolvedDownloads = new HashMap<>();

    /** The version currently downloading or importing, so a retry targets the same instance. */
    private String pendingVersionName;
    private String pendingPageUrl;
    private File pendingFile;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_installations);
        setActiveNavTab(R.id.nav_tab_installations);

        if (savedInstanceState != null) {
            pendingVersionName = savedInstanceState.getString(STATE_PENDING_VERSION);
            pendingPageUrl = savedInstanceState.getString(STATE_PENDING_PAGE);
            String path = savedInstanceState.getString(STATE_PENDING_FILE);
            if (path != null) pendingFile = new File(path);
        }

        client = MonsterMcpeClient.getInstance(this);
        store = new DownloadedApksStore(this);
        versionManager = VersionManager.get(this);
        importManager = new ApkImportManager(this, null);
        importManager.setOnImportCompleteListener(this::onImportSucceeded);
        importManager.setOnImportFailedListener(this::onImportFailed);

        bindViews();
        session = new MonsterMcpeSession(browser);
        setupRecycler();
        refreshStagedPackages();
        loadVersions();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(STATE_PENDING_VERSION, pendingVersionName);
        outState.putString(STATE_PENDING_PAGE, pendingPageUrl);
        outState.putString(STATE_PENDING_FILE, pendingFile == null ? null : pendingFile.getAbsolutePath());
    }

    private void bindViews() {
        recycler = findViewById(R.id.installations_recycler);
        progress = findViewById(R.id.installations_progress);
        emptyContainer = findViewById(R.id.installations_empty);
        emptyTitle = findViewById(R.id.installations_empty_title);
        emptyMessage = findViewById(R.id.installations_empty_message);
        refreshButton = findViewById(R.id.installations_refresh);
        stagedContainer = findViewById(R.id.installations_staged_container);
        stagedSummary = findViewById(R.id.installations_staged_summary);
        stagedRetry = findViewById(R.id.installations_staged_retry);
        stagedClear = findViewById(R.id.installations_staged_clear);
        browser = findViewById(R.id.installations_browser);
        browserContainer = findViewById(R.id.installations_browser_container);
        browserHint = findViewById(R.id.installations_browser_hint);
        findViewById(R.id.installations_browser_cancel).setOnClickListener(v -> cancelBrowserCheck());

        refreshButton.setOnClickListener(v -> loadVersions());
        stagedRetry.setOnClickListener(v -> retryStagedImport());
        stagedClear.setOnClickListener(v -> confirmClearStaged());
        DynamicAnim.applyPressScale(refreshButton);
        DynamicAnim.applyPressScale(stagedRetry);
        DynamicAnim.applyPressScale(stagedClear);
    }

    /**
     * Shows the WebView so the user can clear Monster MCPE's browser check.
     *
     * The check needs a real browser engine, so it cannot be done headlessly; the session
     * keeps polling in the background and the flow continues once it clears.
     */
    private void showBrowser(int hintRes) {
        browserHint.setText(hintRes);
        browserContainer.setVisibility(View.VISIBLE);
        browserContainer.bringToFront();
    }

    private void hideBrowser() {
        browserContainer.setVisibility(View.GONE);
    }

    /**
     * Abandons the browser check.
     *
     * Reloading here would re-request the listing and immediately re-open the check, so the
     * screen settles on an explanatory message instead and the user can press Refresh.
     */
    private void cancelBrowserCheck() {
        hideBrowser();
        if (session != null) session.release();
        showLoading(false);
        showEmpty(getString(R.string.installations_error_title),
                getString(R.string.installations_challenge_cancelled));
    }

    @Override
    protected void onDestroy() {
        if (session != null) session.release();
        if (browser != null) browser.destroy();
        super.onDestroy();
    }

    private void setupRecycler() {
        adapter = new InstallationAdapter();
        adapter.setOnDownloadListener(this::confirmAndDownload);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setAdapter(adapter);
    }

    private void loadVersions() {
        showLoading(true);
        client.fetchVersions(session, new MonsterMcpeClient.ListingCallback() {
            @Override
            public void onSuccess(List<MonsterVersion> fetched) {
                if (isFinishing() || isDestroyed()) return;
                showLoading(false);
                hideBrowser();
                resolvedDownloads.clear();
                versions.clear();
                versions.addAll(fetched);
                adapter.setVersions(versions);
                markInstalledVersions();
                emptyContainer.setVisibility(versions.isEmpty() ? View.VISIBLE : View.GONE);
                recycler.setVisibility(versions.isEmpty() ? View.GONE : View.VISIBLE);
            }

            @Override
            public void onChallenge() {
                showBrowser(R.string.installations_challenge_hint);
            }

            @Override
            public void onError(Throwable error) {
                if (isFinishing() || isDestroyed()) return;
                showLoading(false);
                hideBrowser();
                showEmpty(getString(R.string.installations_error_title), describe(error));
            }
        });
    }

    /** Flags the rows whose version already exists as an instance. */
    private void markInstalledVersions() {
        for (MonsterVersion version : versions) {
            adapter.markInstalled(version.versionCode, isInstalled(version.versionCode));
        }
    }

    private boolean isInstalled(String versionCode) {
        if (versionCode == null || versionCode.isEmpty()) return false;
        return matches(versionManager.getInstalledVersions(), versionCode)
                || matches(versionManager.getCustomVersions(), versionCode);
    }

    private boolean matches(List<GameVersion> installed, String versionCode) {
        if (installed == null) return false;
        for (GameVersion version : installed) {
            if (versionCode.equals(version.versionCode)) return true;
            if (version.directoryName != null && version.directoryName.contains(versionCode)) return true;
        }
        return false;
    }

    private void confirmAndDownload(MonsterVersion version) {
        if (version == null) return;
        if (adapter.stateOf(version.pageUrl) == InstallationAdapter.State.DOWNLOADING) {
            Toast.makeText(this, R.string.installations_download_in_progress, Toast.LENGTH_SHORT).show();
            return;
        }

        new CustomAlertDialog(this)
                .setTitleText(getString(R.string.installations_confirm_title, version.displayLabel()))
                .setMessage(getString(R.string.installations_confirm_message))
                .setPositiveButton(getString(R.string.installations_download),
                        v -> resolveAndDownload(version))
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void resolveAndDownload(MonsterVersion version) {
        adapter.setState(version.pageUrl, InstallationAdapter.State.DOWNLOADING);
        adapter.setProgress(version.pageUrl, 0);

        MonsterDownload cached = resolvedDownloads.get(version.pageUrl);
        if (cached != null) {
            startDownload(version, cached);
            return;
        }

        client.fetchDownloadLink(session, version.pageUrl, new MonsterMcpeClient.DownloadLinkCallback() {
            @Override
            public void onSuccess(MonsterDownload download) {
                if (isFinishing() || isDestroyed()) return;
                resolvedDownloads.put(version.pageUrl, download);
                startDownload(version, download);
            }

            @Override
            public void onChallenge() {
                // The session keeps polling, so clearing the check resumes this same request.
                showBrowser(R.string.installations_challenge_hint);
            }

            @Override
            public void onError(Throwable error) {
                if (isFinishing() || isDestroyed()) return;
                adapter.setError(version.pageUrl, describe(error));
            }
        });
    }

    private void startDownload(MonsterVersion version, MonsterDownload download) {
        File destination = store.newFile(download.fileName);
        pendingVersionName = uniqueVersionName(versionNameFor(version));
        pendingPageUrl = version.pageUrl;
        pendingFile = destination;

        client.downloadPackage(download.url, destination,
                session.cookiesFor(MonsterMcpeParser.LISTING_URL), session.userAgent(),
                percent -> adapter.setProgress(version.pageUrl, percent),
                new MonsterMcpeClient.FileDownloadCallback() {
                    @Override
                    public void onSuccess() {
                        if (isFinishing() || isDestroyed()) return;
                        adapter.setState(version.pageUrl, InstallationAdapter.State.IMPORTING);
                        refreshStagedPackages();
                        importManager.importUri(Uri.fromFile(destination), pendingVersionName);
                    }

                    @Override
                    public void onError(Throwable error) {
                        if (isFinishing() || isDestroyed()) return;
                        // A partial file would be imported as a corrupt package, so drop it.
                        store.delete(destination);
                        pendingFile = null;
                        pendingPageUrl = null;
                        adapter.setError(version.pageUrl, describe(error));
                        refreshStagedPackages();
                    }
                });
    }

    private void onImportSucceeded() {
        // Only a completed import may remove the package; that ordering is the point of staging.
        boolean deleted = pendingFile != null && store.delete(pendingFile);
        pendingFile = null;
        pendingVersionName = null;
        pendingPageUrl = null;
        resolvedDownloads.clear();
        refreshStagedPackages();
        markInstalledVersions();
        Toast.makeText(this,
                deleted ? R.string.installations_cleanup_done : R.string.installations_cleanup_skipped,
                Toast.LENGTH_SHORT).show();
    }

    private void onImportFailed(String message, String versionName) {
        // The package is kept on purpose so the user can retry without downloading again.
        pendingVersionName = versionName;
        refreshStagedPackages();
        if (pendingPageUrl != null) {
            adapter.setError(pendingPageUrl, message);
        }
        new CustomAlertDialog(this)
                .setTitleText(getString(R.string.installations_import_failed_title))
                .setMessage(getString(R.string.installations_import_failed_message, message))
                .setPositiveButton(getString(R.string.installations_retry_import),
                        v -> retryStagedImport())
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    /**
     * Re-runs the import on the staged package without downloading it again.
     *
     * The file on disk is preferred over the remembered one because the activity may have
     * been recreated; whatever is actually staged is what the pipeline should read.
     */
    private void retryStagedImport() {
        File file = pendingFile != null && pendingFile.isFile() ? pendingFile : null;
        if (file == null) {
            List<File> staged = store.listPackages();
            file = staged.isEmpty() ? null : staged.get(0);
        }
        if (file == null) {
            Toast.makeText(this, R.string.installations_nothing_staged, Toast.LENGTH_LONG).show();
            refreshStagedPackages();
            return;
        }

        pendingFile = file;
        if (pendingVersionName == null || pendingVersionName.isEmpty()) {
            pendingVersionName = MonsterMcpeParser.versionNameFrom(file.getName());
        }
        String pageUrl = pendingPageUrl != null ? pendingPageUrl : pageUrlFor(file.getName());
        pendingPageUrl = pageUrl;
        if (pageUrl != null) {
            adapter.setState(pageUrl, InstallationAdapter.State.IMPORTING);
        }
        importManager.importUri(Uri.fromFile(file), pendingVersionName);
    }

    /** Finds the row whose version code appears in a staged file's name. */
    private String pageUrlFor(String fileName) {
        String code = MonsterMcpeParser.versionNameFrom(fileName);
        if (code == null || "unknown".equals(code)) return null;
        for (MonsterVersion version : versions) {
            if (code.equals(version.versionCode)) return version.pageUrl;
        }
        return null;
    }

    private void confirmClearStaged() {
        new CustomAlertDialog(this)
                .setTitleText(getString(R.string.installations_clear_staged_title))
                .setMessage(getString(R.string.installations_clear_staged_message))
                .setPositiveButton(getString(R.string.installations_clear_staged), v -> {
                    int deleted = store.deleteAll();
                    pendingFile = null;
                    pendingVersionName = null;
                    pendingPageUrl = null;
                    refreshStagedPackages();
                    Toast.makeText(this, getString(R.string.installations_cleared, deleted),
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    private void refreshStagedPackages() {
        List<File> staged = store.listPackages();
        if (staged.isEmpty()) {
            stagedContainer.setVisibility(View.GONE);
            return;
        }
        stagedContainer.setVisibility(View.VISIBLE);
        long bytes = 0;
        for (File file : staged) bytes += file.length();
        stagedSummary.setText(getString(R.string.installations_staged_summary,
                staged.size(), formatSize(bytes)));
    }

    private void showLoading(boolean loading) {
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        if (loading) {
            emptyContainer.setVisibility(View.GONE);
            recycler.setVisibility(View.GONE);
        }
    }

    private void showEmpty(String title, String message) {
        emptyTitle.setText(title);
        emptyMessage.setText(message);
        emptyContainer.setVisibility(View.VISIBLE);
        recycler.setVisibility(View.GONE);
    }

    /**
     * Picks an instance name that no existing instance uses.
     *
     * The import pipeline deletes the target directory before extracting, so importing onto
     * an existing name would silently destroy that instance. A suffix keeps both, which is
     * also what makes "Reinstall" create a second copy rather than overwrite the first.
     */
    private String uniqueVersionName(String base) {
        String name = base == null || base.isEmpty() ? "unknown" : base;
        File root = LauncherStorage.getMinecraftRoot(this);
        if (!new File(root, name).exists()) return name;
        for (int i = 2; i < 100; i++) {
            String candidate = name + "_" + i;
            if (!new File(root, candidate).exists()) return candidate;
        }
        return name + "_" + System.currentTimeMillis();
    }

    private static String versionNameFor(MonsterVersion version) {
        String code = version.versionCode;
        if (code == null || code.isEmpty()) {
            code = MonsterMcpeParser.stripTags(version.title);
        }
        return MonsterMcpeParser.versionNameFrom(code);
    }

    private String describe(Throwable error) {
        if (error == null) return getString(R.string.installations_unknown_error);
        String message = error.getMessage();
        return message == null || message.isEmpty()
                ? getString(R.string.installations_unknown_error)
                : message;
    }

    private static String formatSize(long bytes) {
        if (bytes >= 1024L * 1024L * 1024L) {
            return String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
        if (bytes >= 1024L * 1024L) {
            return String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
        }
        if (bytes >= 1024L) {
            return String.format(Locale.US, "%.0f KB", bytes / 1024.0);
        }
        return bytes + " B";
    }
}
