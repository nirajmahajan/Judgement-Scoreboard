package android.example.judgement.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.example.judgement.R;
import android.example.judgement.data.GameStatistics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/** Native, wrapping statistics and legends accompany the two selectable graphs. */
final class StatisticsContentAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    private static final int TEXT = 0, PLAYER = 1, SELECT = 2, GRAPH = 3, EVENT = 4;
    private final Context context;
    private final GameStatistics report;
    private final Set<Long> selected = new HashSet<>();
    private final Consumer<Set<Long>> selectionChanged;
    private final Consumer<GameStatistics.TimelineEvent> inspect;
    private final List<Entry> rows = new ArrayList<>();

    StatisticsContentAdapter(Context context, GameStatistics report, Set<Long> selectedIds,
            Consumer<Set<Long>> selectionChanged, Consumer<GameStatistics.TimelineEvent> inspect) {
        this.context = context; this.report = report;
        this.selectionChanged = selectionChanged; this.inspect = inspect;
        for (Long id : selectedIds) if (report.player(id) != null) selected.add(id);
        setHasStableIds(true);
        text(context.getString(R.string.statistics_standings), "");
        for (GameStatistics.PlayerStatistics player : report.players) rows.add(new Entry(PLAYER, player));
        if (!report.awards.isEmpty()) {
            text(context.getString(R.string.statistics_awards), context.getString(R.string.statistics_awards_rule));
            for (GameStatistics.Award award : report.awards) award(award);
        }
        text(context.getString(R.string.statistics_compare), context.getString(R.string.statistics_compare_help));
        for (GameStatistics.PlayerStatistics player : report.players) rows.add(new Entry(SELECT, player));
        rows.add(new Entry(GRAPH, ScoreGraphView.Mode.CUMULATIVE));
        rows.add(new Entry(GRAPH, ScoreGraphView.Mode.POINTS_PER_ROUND));
        text(context.getString(R.string.statistics_round_details), context.getString(R.string.statistics_round_help));
        for (GameStatistics.TimelineEvent event : report.timeline) rows.add(new Entry(EVENT, event));
    }
    private void text(String title, String detail) { rows.add(new Entry(TEXT, new String[]{title, detail})); }
    private void award(GameStatistics.Award award) {
        switch (award.kind) {
            case LARGEST_WIN:
                text(context.getString(R.string.statistics_largest_win), context.getString(
                        R.string.statistics_largest_win_detail, award.name, context.getResources().getQuantityString(
                                R.plurals.score_points, award.value == 1 ? 1 : 2, award.value), award.roundNumber)); break;
            case MOST_EXACT_PREDICTIONS:
                text(context.getString(R.string.statistics_most_exact), context.getString(
                        R.string.statistics_count_award_detail, award.name, award.value)); break;
            case ZERO_MACHINE:
                text(context.getString(R.string.statistics_zero_machine), context.getString(
                        R.string.statistics_count_award_detail, award.name, award.value)); break;
        }
    }
    @Override public int getItemCount() { return rows.size(); }
    @Override public int getItemViewType(int position) { return rows.get(position).kind; }
    @Override public long getItemId(int position) { return position + 1L; }
    @NonNull @Override public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
        if (type == GRAPH) return new GraphHolder(context);
        return new TextHolder(LayoutInflater.from(parent.getContext()).inflate(R.layout.score_row, parent, false));
    }
    @Override public void onBindViewHolder(@NonNull RecyclerView.ViewHolder viewHolder, int position) {
        Entry entry = rows.get(position);
        if (entry.kind == GRAPH) {
            GraphHolder holder = (GraphHolder) viewHolder;
            ScoreGraphView.Mode mode = (ScoreGraphView.Mode) entry.value;
            holder.title.setText(mode == ScoreGraphView.Mode.CUMULATIVE
                    ? R.string.statistics_cumulative : R.string.statistics_per_round);
            holder.help.setText(selected.isEmpty() ? R.string.statistics_select_prompt
                    : mode == ScoreGraphView.Mode.CUMULATIVE
                    ? R.string.statistics_cumulative_help : R.string.statistics_per_round_help);
            holder.graph.setData(report, new HashSet<>(selected), mode, inspect);
            return;
        }
        TextHolder holder = (TextHolder) viewHolder;
        holder.actions.removeAllViews();
        if (entry.kind == TEXT) {
            String[] value = (String[]) entry.value; holder.name.setText(value[0]); detail(holder, value[1]);
        } else if (entry.kind == PLAYER) bindPlayer(holder, (GameStatistics.PlayerStatistics) entry.value);
        else if (entry.kind == SELECT) bindSelection(holder, (GameStatistics.PlayerStatistics) entry.value);
        else bindEvent(holder, (GameStatistics.TimelineEvent) entry.value);
    }
    private void bindPlayer(TextHolder holder, GameStatistics.PlayerStatistics player) {
        holder.name.setText(context.getString(R.string.ranking_format, player.rank, player.name));
        String total = context.getResources().getQuantityString(R.plurals.score_points,
                player.total == 1 ? 1 : 2, player.total);
        if (!player.active) total = context.getString(R.string.removed_player_score, total);
        NumberFormat percentage = NumberFormat.getPercentInstance(); percentage.setMaximumFractionDigits(1);
        String exact = player.exactPercentage == null ? context.getString(R.string.statistics_not_played)
                : context.getString(R.string.statistics_exact_rate, player.exactPredictions,
                        player.playedRounds, percentage.format(player.exactPercentage / 100.0));
        String highest = player.highestSuccessfulBid == null
                ? context.getString(R.string.statistics_no_exact_bid) : player.highestSuccessfulBid.toString();
        detail(holder, total + "\n" + exact + "\n" + context.getString(R.string.statistics_highest_bid, highest)
                + "\n" + context.getResources().getQuantityString(R.plurals.statistics_streak,
                        player.longestExactStreak, player.longestExactStreak));
    }
    private void bindSelection(TextHolder holder, GameStatistics.PlayerStatistics player) {
        holder.name.setText(player.name); detail(holder, ScoreGraphView.playerPattern(player.playerId));
        MaterialCheckBox check = new MaterialCheckBox(context);
        check.setText(R.string.statistics_show_player); check.setMinHeight(dp(48));
        check.setSingleLine(false); check.setMaxLines(Integer.MAX_VALUE);
        check.setButtonTintList(ColorStateList.valueOf(ScoreGraphView.colorForPlayer(player.playerId)));
        check.setChecked(selected.contains(player.playerId));
        check.setContentDescription(context.getString(R.string.statistics_compare_player, player.name));
        check.setOnCheckedChangeListener((button, checked) -> {
            if (checked) selected.add(player.playerId); else selected.remove(player.playerId);
            selectionChanged.accept(new HashSet<>(selected));
            for (int i = 0; i < rows.size(); i++) if (rows.get(i).kind == GRAPH) notifyItemChanged(i);
        });
        holder.actions.addView(check, new LinearLayout.LayoutParams(-1, -2));
    }
    private void bindEvent(TextHolder holder, GameStatistics.TimelineEvent event) {
        if (event.kind == GameStatistics.EventKind.ROUND) {
            GameStatistics.RoundStatistics round = event.round;
            holder.name.setText(context.getResources().getQuantityString(R.plurals.round_heading,
                    round.cards, round.number, round.cards));
            detail(holder, StrategyEditor.label(context, round.strategy)
                    + (round.strategyChanged ? "\n" + context.getString(R.string.statistics_strategy_change) : ""));
        } else {
            holder.name.setText(R.string.statistics_manual_adjustment);
            detail(holder, event.adjustment.name + "\n" + context.getString(R.string.statistics_adjustment_delta,
                    signed(event.adjustment.delta)));
        }
        MaterialButton button = new MaterialButton(context, null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle);
        button.setText(R.string.statistics_inspect); button.setMinHeight(dp(48));
        button.setSingleLine(false); button.setMaxLines(Integer.MAX_VALUE);
        button.setContentDescription(context.getString(R.string.statistics_inspect_named, holder.name.getText()));
        button.setOnClickListener(view -> inspect.accept(event));
        holder.actions.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }
    private static String signed(long value) { return value >= 0 ? "+" + value : Long.toString(value); }
    private void detail(TextHolder holder, String value) {
        holder.detail.setText(value); holder.detail.setVisibility(value.isEmpty() ? View.GONE : View.VISIBLE);
    }
    private int dp(int value) { return Math.round(value * context.getResources().getDisplayMetrics().density); }
    private static final class Entry {
        final int kind; final Object value;
        Entry(int kind, Object value) { this.kind = kind; this.value = value; }
    }
    private static final class TextHolder extends RecyclerView.ViewHolder {
        final TextView name, detail; final LinearLayout actions;
        TextHolder(View view) {
            super(view); name = view.findViewById(R.id.row_name); detail = view.findViewById(R.id.row_detail);
            actions = view.findViewById(R.id.row_actions); ViewCompat.setAccessibilityHeading(name, true);
        }
    }
    private static final class GraphHolder extends RecyclerView.ViewHolder {
        final TextView title, help; final ScoreGraphView graph;
        GraphHolder(Context context) {
            super(new LinearLayout(context));
            LinearLayout root = (LinearLayout) itemView; root.setOrientation(LinearLayout.VERTICAL);
            int padding = Math.round(16 * context.getResources().getDisplayMetrics().density);
            root.setPadding(padding, padding, padding, padding);
            title = new TextView(context); title.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_TitleMedium);
            ViewCompat.setAccessibilityHeading(title, true); root.addView(title, new LinearLayout.LayoutParams(-1, -2));
            graph = new ScoreGraphView(context); root.addView(graph, new LinearLayout.LayoutParams(-1, -2));
            help = new TextView(context); help.setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge);
            root.addView(help, new LinearLayout.LayoutParams(-1, -2));
        }
    }
}
