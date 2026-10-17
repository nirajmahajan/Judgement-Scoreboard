package android.example.judgement.ui;

import android.example.judgement.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongConsumer;

/** A compact selection list. The owning screen keeps the selected profile IDs. */
final class SavedPlayerPickerAdapter extends RecyclerView.Adapter<SavedPlayerPickerAdapter.Holder> {
    static final class Item {
        final long id;
        final String name;
        final boolean checked, enabled;
        final String disabledReason;

        Item(long id, String name, boolean checked, boolean enabled, String disabledReason) {
            this.id = id;
            this.name = name;
            this.checked = checked;
            this.enabled = enabled;
            this.disabledReason = disabledReason;
        }
    }

    private final List<Item> items = new ArrayList<>();
    private final LongConsumer toggleSelection;

    SavedPlayerPickerAdapter(LongConsumer toggleSelection) {
        this.toggleSelection = toggleSelection;
        setHasStableIds(true);
    }

    void submit(List<Item> next) {
        List<Item> previous = new ArrayList<>(items);
        DiffUtil.DiffResult changes = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return previous.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int before, int after) {
                return previous.get(before).id == next.get(after).id;
            }
            @Override public boolean areContentsTheSame(int before, int after) {
                Item old = previous.get(before), current = next.get(after);
                return old.name.equals(current.name) && old.checked == current.checked
                        && old.enabled == current.enabled && old.disabledReason.equals(current.disabledReason);
            }
        });
        items.clear();
        items.addAll(next);
        changes.dispatchUpdatesTo(this);
    }

    @Override public long getItemId(int position) { return items.get(position).id; }
    @Override public int getItemCount() { return items.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.saved_player_picker_row, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Item item = items.get(position);
        holder.checkbox.setOnCheckedChangeListener(null);
        holder.checkbox.setChecked(item.checked);
        holder.checkbox.setEnabled(item.enabled);
        holder.name.setText(item.name); holder.name.setTooltipText(item.name);
        holder.name.setAlpha(item.enabled ? 1f : 0.5f);
        String description = item.name + (item.disabledReason.isEmpty() ? "" : ", " + item.disabledReason);
        holder.checkbox.setContentDescription(description);
        holder.row.setTooltipText(description);
        holder.row.setEnabled(item.enabled);
        holder.row.setOnClickListener(view -> holder.checkbox.performClick());
        holder.checkbox.setOnCheckedChangeListener((checkbox, checked) -> {
            if (item.enabled) toggleSelection.accept(item.id);
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View row;
        final CheckBox checkbox;
        final TextView name;
        Holder(View view) {
            super(view);
            row = view.findViewById(R.id.saved_player_picker_body);
            checkbox = view.findViewById(R.id.saved_player_checkbox);
            name = view.findViewById(R.id.row_name);
        }
    }
}
