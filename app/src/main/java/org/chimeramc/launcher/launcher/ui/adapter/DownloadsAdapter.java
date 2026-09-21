package org.chimeramc.launcher.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.chimeramc.launcher.R;
import org.chimeramc.launcher.core.downloads.DownloadsScanner;

import java.util.ArrayList;
import java.util.List;

public class DownloadsAdapter extends RecyclerView.Adapter<DownloadsAdapter.ViewHolder> {

    private final List<DownloadsScanner.Found> items = new ArrayList<>();
    private final OnFileClickListener listener;
    private final OnImportClickListener importListener;

    public interface OnFileClickListener {
        void onFileClick(DownloadsScanner.Found found);
    }

    public interface OnImportClickListener {
        void onImportClick(DownloadsScanner.Found found);
    }

    public DownloadsAdapter(OnFileClickListener listener, OnImportClickListener importListener) {
        this.listener = listener;
        this.importListener = importListener;
    }

    public void setItems(List<DownloadsScanner.Found> found) {
        items.clear();
        if (found != null) {
            items.addAll(found);
        }
        notifyDataSetChanged();
    }

    public int importableCount() {
        int count = 0;
        for (DownloadsScanner.Found item : items) {
            if (item.importable) {
                count++;
            }
        }
        return count;
    }

    public List<DownloadsScanner.Found> importableItems() {
        List<DownloadsScanner.Found> out = new ArrayList<>();
        for (DownloadsScanner.Found item : items) {
            if (item.importable) {
                out.add(item);
            }
        }
        return out;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_download_file, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(items.get(position), listener, importListener);
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView name;
        final TextView details;
        final android.widget.Button importButton;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.download_file_name);
            details = itemView.findViewById(R.id.download_file_details);
            importButton = itemView.findViewById(R.id.download_file_import);
        }

        void bind(final DownloadsScanner.Found found, final OnFileClickListener listener,
                  final OnImportClickListener importListener) {
            android.content.Context ctx = itemView.getContext();
            name.setText(found.name);
            details.setText(ctx.getString(R.string.downloads_file_details,
                    DownloadsScanner.describeExtension(found.extension), formatSize(found.size)));

            // A file the launcher cannot import stays listed but is plainly unavailable,
            // so a just-downloaded item is never silently missing.
            importButton.setEnabled(found.importable);
            importButton.setAlpha(found.importable ? 1f : 0.4f);
            importButton.setText(found.importable ? R.string.downloads_import : R.string.downloads_unsupported);
            if (found.importable) {
                importButton.setOnClickListener(v -> importListener.onImportClick(found));
            } else {
                importButton.setOnClickListener(null);
            }

            itemView.setOnClickListener(v -> listener.onFileClick(found));
            new org.chimeramc.launcher.util.PersonalizationManager(ctx).applyGlassToView(itemView);
        }

        private static String formatSize(long bytes) {
            if (bytes >= 1024L * 1024L) {
                return String.format(java.util.Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0));
            }
            if (bytes >= 1024L) {
                return String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0);
            }
            return bytes + " B";
        }
    }
}
