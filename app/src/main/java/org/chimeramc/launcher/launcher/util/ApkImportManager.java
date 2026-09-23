package org.chimeramc.launcher.util;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.chimeramc.launcher.R;
import org.chimeramc.launcher.core.versions.VersionManager;
import org.chimeramc.launcher.ui.dialogs.ApkVersionConfirmDialog;
import org.chimeramc.launcher.ui.dialogs.CustomAlertDialog;
import org.chimeramc.launcher.ui.dialogs.InstallProgressDialog;
import org.chimeramc.launcher.ui.views.MainViewModel;

import java.util.concurrent.Executors;

public class ApkImportManager {
    private final Activity activity;
    private final MainViewModel viewModel;
    private final InstallProgressDialog progressDialog;
    private OnImportCompleteListener importCompleteListener;
    private OnImportFailedListener importFailedListener;

    public interface OnImportCompleteListener {
        void onImportComplete();
    }

    /**
     * Reports a failed import so a caller can offer a retry.
     *
     * The version name is echoed back because the caller that owns the staged package needs
     * it to import the same file again without asking the user to pick it.
     */
    public interface OnImportFailedListener {
        void onImportFailed(String errorMessage, String versionName);
    }

    public ApkImportManager(Activity activity, MainViewModel viewModel) {
        this.activity = activity;
        this.viewModel = viewModel;
        this.progressDialog = new InstallProgressDialog(activity);
    }

    public void setOnImportCompleteListener(OnImportCompleteListener listener) {
        this.importCompleteListener = listener;
    }

    public void setOnImportFailedListener(OnImportFailedListener listener) {
        this.importFailedListener = listener;
    }

    /** Imports [apkUri] directly, using [versionName] instead of asking for it. */
    public void importUri(Uri apkUri, String versionName) {
        if (apkUri == null) return;
        showProgress();
        ApkInstaller installer = new ApkInstaller(activity, Executors.newSingleThreadExecutor(), new ApkInstaller.InstallCallback() {
            @Override
            public void onProgress(int progress) {
                activity.runOnUiThread(() -> progressDialog.setProgress(progress));
            }

            @Override
            public void onSuccess(String installedVersionName) {
                activity.runOnUiThread(() -> {
                    dismissProgress();
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    Toast.makeText(
                            activity,
                            activity.getString(R.string.install_done, installedVersionName),
                            Toast.LENGTH_LONG
                    ).show();
                    VersionManager.get(activity).loadAllVersions();
                    if (importCompleteListener != null) {
                        importCompleteListener.onImportComplete();
                    }
                });
            }

            @Override
            public void onError(String errorMsg) {
                activity.runOnUiThread(() -> {
                    dismissProgress();
                    if (activity.isFinishing() || activity.isDestroyed()) return;
                    // With a listener the caller owns the failure UI (it can offer a retry),
                    // so a toast here would only duplicate the message.
                    if (importFailedListener == null) {
                        Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show();
                    } else {
                        importFailedListener.onImportFailed(errorMsg, versionName);
                    }
                });
            }
        });
        installer.install(apkUri, versionName);
    }

    public void handleApkImportResult(Intent data) {
        Uri apkUri = data.getData();
        if (apkUri == null) return;
        
        String fileName = getFileName(apkUri);
        boolean isBundle = GameBundle.isBundle(fileName);
        
        if (!isBundle && (fileName == null || !fileName.toLowerCase().endsWith(".apk"))) {
            new CustomAlertDialog(activity)
                    .setTitleText(activity.getString(R.string.illegal_apk_title))
                    .setMessage(activity.getString(R.string.not_apk_or_apks))
                    .setPositiveButton(activity.getString(R.string.exit), v -> {})
                    .show();
            return;
        }
        
        String initialVersionName = isBundle
                ? ApkUtils.extractMinecraftVersionNameFromApksUri(activity, apkUri)
                : ApkUtils.extractMinecraftVersionNameFromUri(activity, apkUri);
                
        // Reading the version out of the package manifest fails for some re-signed or
        // older builds, which are still perfectly installable. Fall back to the file name
        // rather than refusing them as "not a Minecraft APK".
        if ("Error Apk".equals(initialVersionName)) {
            initialVersionName = GameBundle.versionNameFromFileName(fileName);
        }
        ApkVersionConfirmDialog dialog = new ApkVersionConfirmDialog()
                .setInitialVersionName(initialVersionName)
                .setCallback(new ApkVersionConfirmDialog.Callback() {
                    @Override
                    public void onInstallClicked(String versionName) {
                        showProgress();
                        ApkInstaller installer = new ApkInstaller(activity, Executors.newSingleThreadExecutor(), new ApkInstaller.InstallCallback() {
                            @Override
                            public void onProgress(int progress) {
                                activity.runOnUiThread(() -> progressDialog.setProgress(progress));
                            }

                            @Override
                            public void onSuccess(String versionName) {
                                activity.runOnUiThread(() -> {
                                    dismissProgress();
                                    Toast.makeText(
                                            activity,
                                            activity.getString(R.string.install_done, versionName),
                                            Toast.LENGTH_LONG
                                    ).show();
                                    VersionManager.get(activity).loadAllVersions();
                                    if (importCompleteListener != null) {
                                        importCompleteListener.onImportComplete();
                                    }
                                });
                            }

                            @Override
                            public void onError(String errorMsg) {
                                activity.runOnUiThread(() -> {
                                    dismissProgress();
                                    Toast.makeText(activity, errorMsg, Toast.LENGTH_LONG).show();
                                });
                            }
                        });
                        installer.install(apkUri, versionName);
                    }

                    @Override
                    public void onCancelled() {
                    }
                });
        dialog.show(((AppCompatActivity) activity).getSupportFragmentManager(), "ApkVersionConfirmDialog");
    }

    void showProgress() {
        progressDialog.setProgress(0);
        if (!progressDialog.isShowing()) progressDialog.show();
    }

    void dismissProgress() {
        if (progressDialog.isShowing()) progressDialog.dismiss();
    }

    public void handleActivityResult(int resultCode, Intent data) {
        if (resultCode == Activity.RESULT_OK && data != null) {
            handleApkImportResult(data);
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex != -1) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getLastPathSegment();
        }
        return result;
    }
}
