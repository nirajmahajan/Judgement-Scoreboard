package android.example.judgement.ui;

import android.content.Context;
import android.example.judgement.R;
import android.example.judgement.data.Records;
import android.example.judgement.data.Snapshot;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Renders persisted historical values without looking up current player names or scoring rules. */
final class RoundDetails {
    private RoundDetails() { }
    static List<ScoreAdapter.Row> rows(Context context, Snapshot.RoundHistory history,
            List<Records.RoundEdit> edits) {
        List<ScoreAdapter.Row> rows = new ArrayList<>();
        ScoringStrategy scoring = ScoringStrategy.decode(history.round.scoringConfig);
        for (Records.RoundPlayer player : history.players) {
            rows.add(new ScoreAdapter.Row(player.playerId, player.nameSnapshot, performance(context,
                    player.bid, player.outcome, scoring.points(history.round.cards, player.bid,
                            Outcome.valueOf(player.outcome)))));
        }
        for (Records.RoundEdit edit : edits) if (edit.roundId == history.round.id) {
            String title = context.getString(R.string.history_edit_record, edit.revision,
                    DateFormat.getDateTimeInstance().format(new Date(edit.createdAt)));
            String detail = context.getString(R.string.history_before) + "\n" + audit(context, edit.previousValues)
                    + "\n\n" + context.getString(R.string.history_after) + "\n" + audit(context, edit.newValues);
            rows.add(new ScoreAdapter.Row(-edit.id - 1, title, detail));
        }
        return rows;
    }
    private static String audit(Context context, String encoded) {
        try {
            JSONArray values = new JSONArray(encoded); StringBuilder text = new StringBuilder();
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.getJSONObject(i);
                if (text.length() > 0) text.append("\n\n");
                text.append(value.getString("name")).append('\n').append(performance(context,
                        value.getInt("bid"), value.getString("outcome"), value.getLong("points")));
            }
            return text.toString();
        } catch (JSONException exception) {
            // A damaged audit remains visible in its original form instead of silently disappearing.
            return encoded;
        }
    }
    private static String performance(Context context, int bid, String outcome, long points) {
        return context.getString(R.string.history_recorded_performance, bid,
                context.getString("WON".equals(outcome) ? R.string.won : R.string.lost),
                context.getResources().getQuantityString(R.plurals.score_points, points == 1 ? 1 : 2, points));
    }
}
