package android.example.judgement.ui;

import android.example.judgement.R;
import android.graphics.Rect;
import android.view.TouchDelegate;
import android.example.judgement.data.Records;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Compact bidding table; drafts live in the ViewModel and commands carry stable IDs. */
final class BidAdapter extends RecyclerView.Adapter<BidAdapter.Holder> {
    private final ScoreViewModel model;
    private final Runnable refresh;
    private final List<Records.RoundPlayer> players = new ArrayList<>();
    private long roundId, dealerId;
    private Long trumpCallerId;
    private int current, cards;
    BidAdapter(ScoreViewModel model, Runnable refresh) {
        this.model = model; this.refresh = refresh; setHasStableIds(true);
    }
    void submit(long roundId, long dealerId, int cards, List<Records.RoundPlayer> players, int current, Long trumpCallerId) {
        this.dealerId = dealerId; this.roundId = roundId; this.cards = cards;
        this.trumpCallerId = trumpCallerId;
        this.players.clear(); this.players.addAll(players); this.current = current;
        notifyDataSetChanged();
    }
    @Override public long getItemId(int position) { return players.get(position).playerId; }
    @Override public int getItemCount() { return players.size(); }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.bid_row, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Records.RoundPlayer player = players.get(position);
        final long owner = roundId;
        boolean latest = position == current - 1;
        boolean editing = position == current || (latest && model.editingBid(owner, player.playerId));
        holder.name.setText(player.nameSnapshot);
        boolean dealer = player.playerId == dealerId;
        boolean trumpCaller = Objects.equals(trumpCallerId, player.playerId);
        holder.itemView.findViewById(R.id.dealer_tag).setVisibility(dealer ? View.VISIBLE : View.GONE);
        holder.itemView.findViewById(R.id.round_trump_tag).setVisibility(trumpCaller ? View.VISIBLE : View.GONE);
        holder.name.setContentDescription(player.nameSnapshot
                + (dealer ? ", " + holder.itemView.getContext().getString(R.string.dealer_tag) : "")
                + (trumpCaller ? ", " + holder.itemView.getContext().getString(R.string.trump_tag) : ""));
        holder.name.setTooltipText(player.nameSnapshot);
        holder.name.setTypeface(null, position == current ? android.graphics.Typeface.BOLD : android.graphics.Typeface.NORMAL);
        holder.detail.setText(player.bid == null ? "—" : player.bid.toString());
        holder.detail.setVisibility(editing ? View.GONE : View.VISIBLE);
        if (holder.watcher != null) holder.input.removeTextChangedListener(holder.watcher);
        holder.input.setVisibility(editing ? View.VISIBLE : View.GONE);
        String input = model.bidInput(owner, player.playerId, player.bid);
        if (!holder.input.getText().toString().equals(input)) holder.input.setText(input);
        Integer remaining = dealer ? remainingHands() : null;
        String hint = remaining == null ? null : remaining < 0
                ? holder.itemView.getContext().getString(R.string.dealer_prediction_any)
                : holder.itemView.getContext().getString(R.string.dealer_prediction_hint, remaining);
        android.text.SpannableString styledHint = hint == null ? null : new android.text.SpannableString(hint);
        if (styledHint != null) styledHint.setSpan(new android.text.style.AbsoluteSizeSpan(hintSize(holder.input.getContext())),
                0, styledHint.length(), android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        holder.input.setHint(styledHint);
        holder.input.setHintTextColor(com.google.android.material.color.MaterialColors.getColor(holder.input,
                com.google.android.material.R.attr.colorOnSurfaceVariant));
        holder.input.setContentDescription(player.nameSnapshot + ", " + holder.itemView.getContext().getString(R.string.table_bid)
                + (hint == null ? "" : ", " + hint));
        holder.watcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                model.bidInput(owner, player.playerId, s.toString());
            }
            @Override public void afterTextChanged(Editable s) { }
        };
        holder.input.addTextChangedListener(holder.watcher);
        Runnable submit = () -> model.submitInlineBid(owner, player.playerId, holder.input.getText().toString());
        holder.input.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_DONE) return false;
            submit.run(); return true;
        });
        holder.confirm.setVisibility(editing || latest ? View.VISIBLE : View.INVISIBLE);
        holder.confirm.setEnabled(editing || latest);
        holder.expandActionTarget();
        holder.confirm.setImageResource(editing ? R.drawable.ic_bid_confirm : R.drawable.ic_roster_edit);
        holder.confirm.setContentDescription(holder.itemView.getContext().getString(editing ? R.string.enter_bid : R.string.edit_bid)
                + ", " + player.nameSnapshot);
        holder.confirm.setOnClickListener(view -> {
            if (editing) submit.run();
            else { model.editBid(owner, player.playerId); refresh.run(); }
        });
    }
    private Integer remainingHands() {
        int total = 0;
        for (Records.RoundPlayer player : players) if (player.playerId != dealerId) {
            if (player.bid == null) return null;
            total += player.bid;
        }
        return cards - total;
    }
    private static int hintSize(android.content.Context context) {
        return Math.round(android.util.TypedValue.applyDimension(android.util.TypedValue.COMPLEX_UNIT_SP,
                12, context.getResources().getDisplayMetrics()));
    }
    static int inputWidth(android.content.Context context) {
        TextView sample = new TextView(context);
        sample.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
        float numberWidth = sample.getPaint().measureText("52");
        sample.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, hintSize(context));
        float hintWidth = sample.getPaint().measureText(context.getString(R.string.dealer_prediction_hint, 52));
        return Math.max(Math.round(48 * context.getResources().getDisplayMetrics().density),
                (int) Math.ceil(Math.max(numberWidth, hintWidth))
                        + Math.round(8 * context.getResources().getDisplayMetrics().density));
    }
    static final class Holder extends RecyclerView.ViewHolder {
        final TextView name, detail;
        final EditText input;
        final ImageButton confirm;
        TextWatcher watcher;
        Holder(View view) {
            super(view); name = view.findViewById(R.id.row_name); detail = view.findViewById(R.id.row_detail);
            input = view.findViewById(R.id.bid_input); confirm = view.findViewById(R.id.bid_confirm);
            input.setHorizontallyScrolling(false);
            View numberCell = (View) input.getParent();
            ViewGroup.LayoutParams numberLayout = numberCell.getLayoutParams();
            numberLayout.width = inputWidth(view.getContext()); numberCell.setLayoutParams(numberLayout);
            confirm.addOnLayoutChangeListener((button, l, t, r, b, ol, ot, or, ob) -> expandActionTarget());
        }
        void expandActionTarget() {
            View parent = (View) confirm.getParent();
            if (confirm.getVisibility() != View.VISIBLE || confirm.getWidth() == 0) {
                parent.setTouchDelegate(null); return;
            }
            Rect area = new Rect(); confirm.getHitRect(area);
            int targetWidth = Math.round(48 * confirm.getResources().getDisplayMetrics().density);
            area.inset(-Math.max(0, targetWidth - area.width()) / 2, 0);
            parent.setTouchDelegate(new TouchDelegate(area, confirm));
        }
    }
}
