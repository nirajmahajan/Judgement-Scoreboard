package android.example.judgement.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.RoundEditPreview;
import android.example.judgement.data.RoundPlayerEdit;
import android.example.judgement.domain.Outcome;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.List;
import java.util.function.Consumer;

/** Historical participant inputs remain ID-based and preserve keyboard focus during previews. */
final class RoundEditorAdapter extends RecyclerView.Adapter<RoundEditorAdapter.Holder> {
    private static final Object STATE = new Object();
    private final Consumer<RoundEditRequest> changed;
    private RoundEditRequest request;
    private RoundEditPreview preview;
    private boolean busy;

    RoundEditorAdapter(Consumer<RoundEditRequest> changed) {
        this.changed = changed;
        setHasStableIds(true);
    }

    @SuppressLint("NotifyDataSetChanged") // Only a new editor's participant set requires a full reset.
    void submit(RoundEditRequest next, @Nullable RoundEditPreview preview, boolean busy) {
        boolean sameParticipants = request != null && request.players.size() == next.players.size();
        if (sameParticipants) {
            for (int index = 0; index < request.players.size(); index++) {
                if (request.players.get(index).playerId != next.players.get(index).playerId) {
                    sameParticipants = false;
                    break;
                }
            }
        }
        request = next;
        this.preview = matches(preview, next) ? preview : null;
        this.busy = busy;
        if (sameParticipants) notifyItemRangeChanged(0, getItemCount(), STATE);
        else notifyDataSetChanged();
    }

    @Override public int getItemCount() { return request == null ? 0 : request.players.size(); }
    @Override public long getItemId(int position) { return request.players.get(position).playerId; }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.score_row, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        bind(holder, request.players.get(position));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position,
            @NonNull List<Object> payloads) {
        // Reusing the same holder avoids change animations replacing a focused numeric input.
        bind(holder, request.players.get(position));
    }

    private void bind(Holder holder, RoundEditRequest.PlayerInput player) {
        holder.binding = true;
        holder.playerId = player.playerId;
        Context context = holder.itemView.getContext();
        holder.name.setText(player.name);
        holder.original.setText(context.getString(R.string.round_edit_original,
                player.originalBid, context.getString(player.originalOutcome == Outcome.WON
                        ? R.string.won : R.string.lost), points(context, request.originalPoints(player.playerId))));
        holder.bid.setContentDescription(context.getString(R.string.round_edit_bid_description, player.name));
        String displayed = holder.bid.getText() == null ? "" : holder.bid.getText().toString();
        if (!displayed.equals(player.bidText)) {
            int selection = holder.bid.getSelectionStart();
            boolean focused = holder.bid.hasFocus();
            holder.bid.setText(player.bidText);
            holder.bid.setSelection(focused && selection >= 0
                    ? Math.min(selection, player.bidText.length()) : player.bidText.length());
        }
        holder.bidField.setError(request.bidError(player.playerId));
        holder.bid.setEnabled(!busy);
        holder.won.setEnabled(!busy); holder.lost.setEnabled(!busy);
        holder.won.setContentDescription(context.getString(R.string.round_edit_outcome_description,
                context.getString(R.string.won), player.name));
        holder.lost.setContentDescription(context.getString(R.string.round_edit_outcome_description,
                context.getString(R.string.lost), player.name));
        if (player.outcome == null) holder.outcomes.clearChecked();
        else holder.outcomes.check(player.outcome == Outcome.WON ? holder.won.getId() : holder.lost.getId());
        String outcomeError = request.outcomeError(player.playerId);
        holder.outcomeError.setText(outcomeError);
        holder.outcomeError.setVisibility(outcomeError == null ? View.GONE : View.VISIBLE);
        RoundEditPreview.PlayerChange change = previewPlayer(player.playerId);
        if (change == null) {
            holder.preview.setText("");
            holder.preview.setVisibility(View.GONE);
        } else {
            long newTotal = preview.totals.get(player.playerId);
            String delta = context.getString(change.delta >= 0
                    ? R.string.round_edit_positive_delta : R.string.round_edit_negative_delta, change.delta);
            holder.preview.setText(context.getString(R.string.round_edit_preview_points,
                    points(context, change.afterPoints), delta, points(context, newTotal)));
            holder.preview.setVisibility(View.VISIBLE);
        }
        holder.binding = false;
    }

    private RoundEditPreview.PlayerChange previewPlayer(long playerId) {
        if (preview == null) return null;
        for (RoundEditPreview.PlayerChange player : preview.players) {
            if (player.playerId == playerId) return player;
        }
        return null;
    }

    private void input(RoundEditRequest next) {
        if (next == request) return;
        request = next;
        preview = null;
        notifyItemRangeChanged(0, getItemCount(), STATE);
        changed.accept(next);
    }

    private static boolean matches(RoundEditPreview preview, RoundEditRequest request) {
        if (preview == null || preview.roundId != request.roundId
                || preview.expectedRevision != request.expectedRevision
                || preview.players.size() != request.players.size()) return false;
        try {
            List<RoundPlayerEdit> edits = request.edits();
            for (RoundPlayerEdit edit : edits) {
                boolean matched = false;
                for (RoundEditPreview.PlayerChange change : preview.players) {
                    if (change.playerId == edit.playerId && change.afterBid == edit.bid
                            && change.afterOutcome == edit.outcome) {
                        matched = true;
                        break;
                    }
                }
                if (!matched) return false;
            }
            return true;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static String points(Context context, long value) {
        return context.getResources().getQuantityString(R.plurals.score_points,
                value == 1L ? 1 : 2, value);
    }

    final class Holder extends RecyclerView.ViewHolder {
        final TextView name, original, preview, outcomeError;
        final TextInputLayout bidField;
        final TextInputEditText bid;
        final MaterialButtonToggleGroup outcomes;
        final MaterialButton won, lost;
        long playerId;
        boolean binding;

        Holder(View view) {
            super(view);
            Context context = view.getContext();
            name = view.findViewById(R.id.row_name);
            original = view.findViewById(R.id.row_detail);
            ViewCompat.setAccessibilityHeading(name, true);
            LinearLayout controls = view.findViewById(R.id.row_actions);
            bidField = new TextInputLayout(context);
            bidField.setHint(context.getString(R.string.round_edit_bid_hint));
            bid = new TextInputEditText(context);
            bid.setId(View.generateViewId());
            bid.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
            bid.setImeOptions(EditorInfo.IME_ACTION_NEXT);
            bid.setSingleLine(true);
            bid.setMinHeight(dp(context, 48));
            bidField.addView(bid, new LinearLayout.LayoutParams(-1, -2));
            controls.addView(bidField, new LinearLayout.LayoutParams(-1, -2));
            outcomes = new MaterialButtonToggleGroup(context);
            outcomes.setSingleSelection(true); outcomes.setSelectionRequired(true);
            won = outcomeButton(context, R.string.won);
            lost = outcomeButton(context, R.string.lost);
            outcomes.addView(won, new LinearLayout.LayoutParams(0, -2, 1f));
            outcomes.addView(lost, new LinearLayout.LayoutParams(0, -2, 1f));
            controls.addView(outcomes, new LinearLayout.LayoutParams(-1, -2));
            outcomeError = new TextView(context);
            outcomeError.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium);
            outcomeError.setTextColor(com.google.android.material.color.MaterialColors.getColor(
                    view, androidx.appcompat.R.attr.colorError));
            ViewCompat.setAccessibilityLiveRegion(outcomeError, ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
            controls.addView(outcomeError, new LinearLayout.LayoutParams(-1, -2));
            preview = new TextView(context);
            preview.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            controls.addView(preview, new LinearLayout.LayoutParams(-1, -2));
            bid.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
                @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
                @Override public void afterTextChanged(Editable value) {
                    if (!binding && request != null) input(request.editBid(playerId, value.toString()));
                }
            });
            outcomes.addOnButtonCheckedListener((group, checkedId, checked) -> {
                if (!binding && checked && request != null) {
                    input(request.editOutcome(playerId, checkedId == won.getId() ? Outcome.WON : Outcome.LOST));
                }
            });
        }
    }

    private static MaterialButton outcomeButton(Context context, int label) {
        MaterialButton button = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setId(View.generateViewId()); button.setText(label);
        button.setMinHeight(dp(context, 48)); button.setMaxLines(Integer.MAX_VALUE);
        button.setSingleLine(false); button.setMinWidth(0); button.setMinimumWidth(0);
        return button;
    }

    private static int dp(Context context, int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
