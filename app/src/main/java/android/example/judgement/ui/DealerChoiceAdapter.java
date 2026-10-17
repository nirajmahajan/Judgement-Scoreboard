package android.example.judgement.ui;

import android.example.judgement.R;
import android.example.judgement.data.PlayerSpec;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RadioButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.util.function.LongConsumer;

final class DealerChoiceAdapter extends RecyclerView.Adapter<DealerChoiceAdapter.Holder> {
    private final List<PlayerSpec> players;
    private final Long selected;
    private final LongConsumer choose;
    DealerChoiceAdapter(List<PlayerSpec> players, Long selected, LongConsumer choose) {
        this.players = players; this.selected = selected; this.choose = choose; setHasStableIds(true);
    }
    @Override public long getItemId(int position) { return players.get(position).id; }
    @Override public int getItemCount() { return players.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.dealer_choice_row, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        PlayerSpec player = players.get(position);
        holder.name.setText(player.name); holder.name.setTooltipText(player.name);
        holder.radio.setChecked(selected != null && selected == player.id);
        holder.radio.setContentDescription(player.name);
        holder.radio.setOnClickListener(view -> choose.accept(player.id));
        holder.itemView.setOnClickListener(view -> holder.radio.performClick());
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name; final RadioButton radio;
        Holder(View view) { super(view); name = view.findViewById(R.id.row_name); radio = view.findViewById(R.id.dealer_choice_radio); }
    }
}
