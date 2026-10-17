package android.example.judgement.ui;

import android.example.judgement.R;
import android.example.judgement.data.Records;
import android.example.judgement.domain.Outcome;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;

/** One player per row; checked results always come from the persisted round snapshot. */
final class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.Holder> {
    private final ScoreViewModel model;
    private final List<Records.RoundPlayer> players = new ArrayList<>();
    private long roundId, dealerId;

    ResultAdapter(ScoreViewModel model) { this.model = model; setHasStableIds(true); }
    void submit(long roundId, long dealerId, List<Records.RoundPlayer> players) {
        this.roundId = roundId; this.dealerId = dealerId;
        this.players.clear(); this.players.addAll(players); notifyDataSetChanged();
    }
    @Override public long getItemId(int position) { return players.get(position).playerId; }
    @Override public int getItemCount() { return players.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.result_row, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Records.RoundPlayer player = players.get(position);
        long owner = roundId;
        holder.name.setText(player.nameSnapshot); holder.name.setTooltipText(player.nameSnapshot);
        boolean dealer = player.playerId == dealerId;
        holder.tag.setVisibility(dealer ? View.VISIBLE : View.GONE);
        holder.name.setContentDescription(player.nameSnapshot + (dealer ? ", " + holder.itemView.getContext().getString(R.string.dealer_tag) : ""));
        holder.prediction.setText(holder.itemView.getContext().getString(R.string.result_prediction, player.bid));
        holder.choices.setOnCheckedChangeListener(null);
        holder.choices.check(player.outcome == null ? -1 : "WON".equals(player.outcome) ? R.id.result_won : R.id.result_lost);
        for (int id : new int[]{R.id.result_won, R.id.result_lost}) {
            TextView choice = holder.itemView.findViewById(id);
            choice.setContentDescription(holder.itemView.getContext().getString(R.string.result_choice_description,
                    choice.getText(), player.nameSnapshot, player.bid));
        }
        holder.choices.setOnCheckedChangeListener((group, selected) -> {
            if (selected != R.id.result_won && selected != R.id.result_lost) return;
            Outcome outcome = selected == R.id.result_won ? Outcome.WON : Outcome.LOST;
            model.run(() -> model.repository.setOutcome(owner, player.playerId, outcome));
        });
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, prediction;
        final View tag;
        final RadioGroup choices;
        Holder(View view) {
            super(view); name = view.findViewById(R.id.row_name); prediction = view.findViewById(R.id.row_detail);
            tag = view.findViewById(R.id.dealer_tag); choices = view.findViewById(R.id.result_choices);
        }
    }
}
