package org.chimeramc.launcher.ui.adapter;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.ItemTouchHelper;

import org.chimeramc.launcher.R;
import org.chimeramc.launcher.core.mods.Mod;
import org.chimeramc.launcher.core.mods.ModLoadDiagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ModsAdapter extends RecyclerView.Adapter<ModsAdapter.ModViewHolder> {

    private List<Mod> mods = new ArrayList<>();
    private Map<String, ModLoadDiagnostics.Record> loadFailures = Collections.emptyMap();
    private OnModEnableChangeListener onModEnableChangeListener;
    private OnModReorderListener onModReorderListener;
    private OnModClickListener onModClickListener;
    private ItemTouchHelper itemTouchHelper;


    public interface OnModEnableChangeListener {
        void onEnableChanged(Mod mod, boolean enabled);
    }

    public interface OnModReorderListener {
        void onModsReordered(List<Mod> reorderedMods);
    }

    public interface OnModClickListener {
        void onModClick(Mod mod, int position, View sharedView);
    }

    public ModsAdapter(List<Mod> mods) {
        this.mods = mods;
    }


    public void setOnModEnableChangeListener(OnModEnableChangeListener l) {
        this.onModEnableChangeListener = l;
    }

    public void setOnModReorderListener(OnModReorderListener listener) {
        this.onModReorderListener = listener;
    }

    public void setOnModClickListener(OnModClickListener listener) {
        this.onModClickListener = listener;
    }

    public void setItemTouchHelper(ItemTouchHelper itemTouchHelper) {
        this.itemTouchHelper = itemTouchHelper;
    }

    /** Supplies the last-launch failures so each row can flag a mod that did not load. */
    public void setLoadFailures(Map<String, ModLoadDiagnostics.Record> failures) {
        this.loadFailures = failures != null ? failures : Collections.emptyMap();
        notifyDataSetChanged();
    }

    /**
     * Maps a failure category to the short line shown on the row. The user-facing wording is a
     * category, not the raw native message, because a raw {@code dlopen} error does not tell
     * the user whether to install a dependency or fetch a matching mod build.
     */
    public static int reasonResForKind(String kind) {
        if (kind == null) {
            return R.string.mod_load_diagnostics_unknown;
        }
        switch (kind) {
            case ModLoadDiagnostics.KIND_INCOMPATIBLE:
                return R.string.mod_load_diagnostics_incompatible;
            case ModLoadDiagnostics.KIND_DLOPEN:
                return R.string.mod_load_diagnostics_dlopen;
            case ModLoadDiagnostics.KIND_SYMBOL:
                return R.string.mod_load_diagnostics_symbol;
            case ModLoadDiagnostics.KIND_MISSING_DEPENDENCY:
                return R.string.mod_load_diagnostics_missing_dependency;
            default:
                return R.string.mod_load_diagnostics_unknown;
        }
    }

    @NonNull
    @Override
    public ModViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_mod, parent, false);
        return new ModViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ModViewHolder holder, int position) {
        Mod mod = mods.get(position);
        holder.name.setText(mod.getDisplayName());
        holder.authorText.setText(holder.itemView.getContext().getString(
                R.string.mod_author_byline,
                fallbackText(mod.getAuthor(), holder.itemView.getContext().getString(R.string.mod_unknown_author))));
        holder.versionText.setText(holder.itemView.getContext().getString(
                R.string.mod_version_chip,
                fallbackText(mod.getVersion(), holder.itemView.getContext().getString(R.string.mod_unknown_version))));

        if (holder.orderText != null) {
            holder.orderText.setText(holder.itemView.getContext().getString(R.string.mod_load_order, position + 1));
            holder.orderText.setVisibility(View.VISIBLE);
        }

        if (holder.configBadge != null) {
            holder.configBadge.setVisibility(View.GONE);
        }

        if (holder.failureBadge != null) {
            ModLoadDiagnostics.Record failure = loadFailures.get(mod.getId());
            if (failure == null) {
                holder.failureBadge.setVisibility(View.GONE);
            } else {
                holder.failureBadge.setText(reasonResForKind(failure.kind));
                holder.failureBadge.setVisibility(View.VISIBLE);
            }
        }

        holder.switchBtn.setOnCheckedChangeListener(null);
        holder.switchBtn.setChecked(mod.isEnabled());

        holder.switchBtn.setOnCheckedChangeListener((btn, isChecked) -> {
            if (isChecked == mod.isEnabled()) return;

            mod.setEnabled(isChecked);

            if (onModEnableChangeListener != null) {
                onModEnableChangeListener.onEnableChanged(mod, isChecked);
            }
        });

        if (holder.dragHandle != null) {
            // applyPressScale is idempotent per view: it attaches once and the delegate
            // closes over the recycled holder, so re-binding must not reset the listener.
            org.chimeramc.launcher.ui.animation.DynamicAnim.applyPressScale(
                    holder.dragHandle,
                    (v, event) -> {
                        if (event.getActionMasked() == android.view.MotionEvent.ACTION_DOWN
                                && itemTouchHelper != null) {
                            itemTouchHelper.startDrag(holder);
                        }
                        return false;
                    });
        }

        ViewCompat.setTransitionName(holder.itemView, "mod_card_" + mod.getId());

        holder.itemView.setOnClickListener(v -> {
            if (onModClickListener != null) {
                onModClickListener.onModClick(mod, position, holder.itemView);
            }
        });
        org.chimeramc.launcher.ui.animation.DynamicAnim.applyPressScale(holder.itemView);

        android.content.Context context = holder.itemView.getContext();
        org.chimeramc.launcher.util.PersonalizationManager pm = new org.chimeramc.launcher.util.PersonalizationManager(context);
        pm.applyGlassToView(holder.itemView);
        pm.applyAccentToView(holder.itemView, context);
    }

    @Override
    public int getItemCount() {
        return mods != null ? mods.size() : 0;
    }

    public Mod getItem(int pos) {
        return mods.get(pos);
    }

    public void removeAt(int pos) {
        mods.remove(pos);
        notifyItemRemoved(pos);
    }

    public void updateMods(List<Mod> list) {
        this.mods = list;
        notifyDataSetChanged();
    }

    public void moveItem(int fromPosition, int toPosition) {
        if (mods == null || mods.isEmpty()) return;
        if (fromPosition < 0 || toPosition < 0) return;
        if (fromPosition >= mods.size() || toPosition >= mods.size()) return;
        if (fromPosition == toPosition) return;

        if (fromPosition < toPosition) {
            for (int i = fromPosition; i < toPosition; i++) {
                Collections.swap(mods, i, i + 1);
            }
        } else {
            for (int i = fromPosition; i > toPosition; i--) {
                Collections.swap(mods, i, i - 1);
            }
        }
        notifyItemMoved(fromPosition, toPosition);
    }

    public void commitReorder() {
        if (onModReorderListener != null) {
            onModReorderListener.onModsReordered(new ArrayList<>(mods));
        }
    }

    private String fallbackText(String value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    static class ModViewHolder extends RecyclerView.ViewHolder {
        TextView name;
        TextView orderText;
        TextView configBadge;
        TextView failureBadge;
        TextView authorText;
        TextView versionText;
        Switch switchBtn;
        android.widget.ImageView dragHandle;

        public ModViewHolder(@NonNull View itemView) {
            super(itemView);
            name = itemView.findViewById(R.id.mod_name);
            orderText = itemView.findViewById(R.id.mod_order);
            configBadge = itemView.findViewById(R.id.mod_config_badge);
            failureBadge = itemView.findViewById(R.id.mod_failure_badge);
            authorText = itemView.findViewById(R.id.mod_author);
            versionText = itemView.findViewById(R.id.mod_version);
            switchBtn = itemView.findViewById(R.id.mod_switch);
            dragHandle = itemView.findViewById(R.id.drag_handle);
        }
    }
}
