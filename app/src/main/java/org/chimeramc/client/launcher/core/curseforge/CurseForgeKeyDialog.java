package org.chimeramc.client.core.curseforge;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import org.chimeramc.client.R;

/**
 * Prompts for the CurseForge API key when one is missing or rejected.
 *
 * The launcher cannot ship a usable key (a key in a public repo is a revoked key), so the
 * first CurseForge request from a fresh install will always need this. Callers pass a
 * {@code onSaved} runnable to retry whatever request failed.
 */
public final class CurseForgeKeyDialog {

    private CurseForgeKeyDialog() {
    }

    public static void show(Activity activity, Runnable onSaved) {
        if (activity == null || activity.isFinishing()) return;

        LinearLayout box = new LinearLayout(activity);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * activity.getResources().getDisplayMetrics().density);
        box.setPadding(pad, pad / 3, pad, 0);

        EditText input = new EditText(activity);
        input.setHint(R.string.curseforge_api_key_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        input.setSingleLine(true);
        String existing = CurseForgeKeyStore.getApiKey(activity);
        input.setText(existing);
        box.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle(R.string.curseforge_api_key_required_title)
                .setMessage(R.string.curseforge_api_key_required_message)
                .setView(box)
                .setPositiveButton(android.R.string.ok, null)
                .setNeutralButton(R.string.curseforge_open_console, (d, which) -> {
                    try {
                        activity.startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://console.curseforge.com/?#/api-keys")));
                    } catch (Exception ignored) {
                    }
                    show(activity, onSaved);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(v -> {
                    String value = input.getText().toString().trim();
                    if (value.isEmpty()) {
                        Toast.makeText(activity, R.string.curseforge_api_key_invalid,
                                Toast.LENGTH_SHORT).show();
                        return;
                    }
                    CurseForgeKeyStore.setApiKey(activity, value);
                    Toast.makeText(activity, R.string.curseforge_api_key_saved,
                            Toast.LENGTH_SHORT).show();
                    dialog.dismiss();
                    if (onSaved != null) onSaved.run();
                }));
        dialog.show();
    }
}