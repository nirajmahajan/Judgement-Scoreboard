package android.example.judgement.ui;

import android.example.judgement.R;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.DiffUtil;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.List;

/** Binds a complete snapshot. Rows never query storage or own game state. */
final class ScoreAdapter extends RecyclerView.Adapter<ScoreAdapter.Holder> {
    static final class Action {
        final String label;
        final boolean enabled;
        final Runnable run;
        Action(String label, boolean enabled, Runnable run) { this.label = label; this.enabled = enabled; this.run = run; }
    }
    static final class Row {
        final long id;
        final String name;
        final String detail;
        final List<Action> actions = new ArrayList<>();
        Row(long id, String name, String detail) { this.id = id; this.name = name; this.detail = detail; }
        Row action(String label, Runnable run) { return action(label, true, run); }
        Row action(String label, boolean enabled, Runnable run) { actions.add(new Action(label, enabled, run)); return this; }
    }
    private final List<Row> rows = new ArrayList<>();
    ScoreAdapter() { setHasStableIds(true); }
    void submit(List<Row> next) {
        List<Row> previous = new ArrayList<>(rows);
        DiffUtil.DiffResult difference = DiffUtil.calculateDiff(new DiffUtil.Callback() {
            @Override public int getOldListSize() { return previous.size(); }
            @Override public int getNewListSize() { return next.size(); }
            @Override public boolean areItemsTheSame(int oldIndex, int newIndex) { return previous.get(oldIndex).id == next.get(newIndex).id; }
            @Override public boolean areContentsTheSame(int oldIndex, int newIndex) {
                Row old = previous.get(oldIndex), current = next.get(newIndex);
                // Action callbacks capture the current round and must always be rebound.
                return old.actions.isEmpty() && current.actions.isEmpty() && old.name.equals(current.name) && old.detail.equals(current.detail);
            }
        });
        rows.clear(); rows.addAll(next); difference.dispatchUpdatesTo(this);
    }
    @Override public long getItemId(int position) { return rows.get(position).id; }
    @Override public int getItemCount() { return rows.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.score_row, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Row row = rows.get(position);
        holder.name.setText(row.name);
        androidx.core.view.ViewCompat.setAccessibilityHeading(holder.name, true);
        holder.detail.setText(row.detail);
        holder.detail.setVisibility(row.detail.isEmpty() ? View.GONE : View.VISIBLE);
        holder.actions.removeAllViews();
        for (Action action : row.actions) {
            MaterialButton button = new MaterialButton(holder.itemView.getContext(), null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
            button.setText(action.label);
            button.setContentDescription(action.label + ", " + row.name);
            button.setMinHeight(dp(holder.itemView, 48));
            button.setMaxLines(20);
            button.setSingleLine(false);
            button.setEnabled(action.enabled);
            button.setOnClickListener(v -> action.run.run());
            holder.actions.addView(button, new LinearLayout.LayoutParams(-1, -2));
        }
    }
    static int dp(View view, int value) { return Math.round(value * view.getResources().getDisplayMetrics().density); }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, detail;
        final LinearLayout actions;
        Holder(View view) { super(view); name = view.findViewById(R.id.row_name); detail = view.findViewById(R.id.row_detail); actions = view.findViewById(R.id.row_actions); }
    }
}
