package android.example.judgement.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.PlayerSpec;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ConcatAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/** A staged, ID-based roster with explicit accessible seat controls. */
final class RosterAdapter extends RecyclerView.Adapter<RosterAdapter.Holder> {
    interface Listener {
        void onChanged(List<PlayerSpec> players);
        void rename(PlayerSpec player);
        void remove(PlayerSpec player);
    }
    private static final AtomicLong NEXT_TEMPORARY_ID = new AtomicLong(-2L);
    private final Context context;
    private final Listener listener;
    private final List<PlayerSpec> players = new ArrayList<>();
    private RecyclerView recyclerView;
    private Long dealerId;

    RosterAdapter(Context context, List<PlayerSpec> players, Long dealerId, Listener listener) {
        this.context = context;
        this.listener = listener;
        setHasStableIds(true);
        update(players, dealerId);
    }
    void attachTo(RecyclerView view) { attachTo(view, true); }
    void attachTo(RecyclerView view, boolean installAdapter) {
        recyclerView = view;
        if (installAdapter) view.setAdapter(this);
    }
    void detach() { recyclerView = null; }

    @SuppressLint("NotifyDataSetChanged") // At most 52 rows; dealer labels and available moves change together.
    void update(List<PlayerSpec> next, Long dealerId) {
        players.clear();
        for (PlayerSpec player : next) {
            players.add(player.id == 0L ? new PlayerSpec(NEXT_TEMPORARY_ID.getAndDecrement(),
                    player.name, player.savedPlayerId) : player);
        }
        this.dealerId = dealerId;
        notifyDataSetChanged();
    }
    @Override public long getItemId(int position) { return players.get(position).id; }
    @Override public int getItemCount() { return players.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.score_row, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        PlayerSpec player = players.get(position);
        holder.name.setText(player.name);
        holder.detail.setText(context.getString(dealerId != null && dealerId == player.id
                ? R.string.seat_dealer_format : R.string.seat_format, position + 1));
        holder.actions.removeAllViews();
        addAction(holder, R.string.move_up, position > 0, () -> move(holder, -1));
        addAction(holder, R.string.move_down, position < players.size() - 1, () -> move(holder, 1));
        addAction(holder, R.string.rename, true, () -> {
            PlayerSpec current = current(holder); if (current != null) listener.rename(current);
        });
        addAction(holder, R.string.remove, true, () -> {
            PlayerSpec current = current(holder); if (current != null) listener.remove(current);
        });
    }
    private PlayerSpec current(Holder holder) {
        int position = holder.getBindingAdapterPosition();
        return position == RecyclerView.NO_POSITION ? null : players.get(position);
    }
    private void addAction(Holder holder, int label, boolean enabled, Runnable command) {
        MaterialButton button = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(label);
        button.setMinHeight(dp(48));
        button.setSingleLine(false);
        button.setEnabled(enabled);
        button.setContentDescription(context.getString(label) + " " + holder.name.getText());
        button.setOnClickListener(view -> command.run());
        LinearLayout.LayoutParams parameters = new LinearLayout.LayoutParams(-1, -2);
        if (holder.actions.getChildCount() > 0) parameters.topMargin = dp(8);
        holder.actions.addView(button, parameters);
    }
    private void move(Holder holder, int delta) {
        int from = holder.getBindingAdapterPosition();
        int destination = from + delta;
        if (from == RecyclerView.NO_POSITION || destination < 0 || destination >= players.size()) return;
        PlayerSpec moved = players.get(from);
        Collections.swap(players, from, destination);
        notifyItemMoved(from, destination);
        notifyItemRangeChanged(Math.min(from, destination), 2);
        listener.onChanged(new ArrayList<>(players));
        if (recyclerView == null) return;
        recyclerView.scrollToPosition(contentOffset(recyclerView.getAdapter()) + destination);
        RecyclerView target = recyclerView;
        target.post(() -> {
            if (target != recyclerView) return;
            for (int child = 0; child < target.getChildCount(); child++) {
                RecyclerView.ViewHolder candidate = target.getChildViewHolder(target.getChildAt(child));
                if (candidate.getBindingAdapter() != this) continue;
                int position = candidate.getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION && players.get(position).id == moved.id) {
                    View action = ((Holder) candidate).actions.getChildAt(delta < 0 ? 0 : 1);
                    action.requestFocus();
                    action.announceForAccessibility(context.getString(R.string.move_announcement,
                            moved.name, position + 1));
                    return;
                }
            }
        });
    }
    private int contentOffset(RecyclerView.Adapter<?> adapter) {
        if (adapter == this) return 0;
        if (adapter instanceof ConcatAdapter) {
            int offset = 0;
            for (RecyclerView.Adapter<?> child : ((ConcatAdapter) adapter).getAdapters()) {
                if (child == this) return offset;
                offset += child.getItemCount();
            }
        }
        throw new IllegalStateException("Roster is not attached to its RecyclerView.");
    }
    private int dp(int value) { return Math.round(context.getResources().getDisplayMetrics().density * value); }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, detail;
        final LinearLayout actions;
        Holder(View view) {
            super(view);
            name = view.findViewById(R.id.row_name);
            detail = view.findViewById(R.id.row_detail);
            actions = view.findViewById(R.id.row_actions);
        }
    }
}
