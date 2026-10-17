package android.example.judgement.data;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** One transactionally consistent read of the database; no row binding performs database queries. */
public final class Snapshot {
    public final Records.Game draft;
    public final List<Records.GamePlayer> draftPlayers;
    public final Records.Game game;
    public final List<Records.GamePlayer> players;
    public final List<Records.GamePlayer> inactivePlayers;
    public final Map<Long, Long> totals;
    public final Records.Round round;
    /** Current round participants in bidding order. */
    public final List<Records.RoundPlayer> roundPlayers;
    public final List<RoundHistory> history;
    public final List<Records.ScoreAdjustment> adjustments;
    public final List<Records.SavedPlayer> savedPlayers;
    public final List<Records.RecoveryRow> recoveries;

    Snapshot(Records.Game draft, List<Records.GamePlayer> draftPlayers, Records.Game game,
             List<Records.GamePlayer> players, List<Records.GamePlayer> inactivePlayers,
             Map<Long, Long> totals, Records.Round round, List<Records.RoundPlayer> roundPlayers,
             List<RoundHistory> history, List<Records.ScoreAdjustment> adjustments,
             List<Records.SavedPlayer> savedPlayers, List<Records.RecoveryRow> recoveries) {
        this.draft = draft;
        this.draftPlayers = Collections.unmodifiableList(draftPlayers);
        this.game = game;
        this.players = Collections.unmodifiableList(players);
        this.inactivePlayers = Collections.unmodifiableList(inactivePlayers);
        this.totals = Collections.unmodifiableMap(totals);
        this.round = round;
        this.roundPlayers = Collections.unmodifiableList(roundPlayers);
        this.history = Collections.unmodifiableList(history);
        this.adjustments = Collections.unmodifiableList(adjustments);
        this.savedPlayers = Collections.unmodifiableList(savedPlayers);
        this.recoveries = Collections.unmodifiableList(recoveries);
    }

    public long total(long playerId) { Long total = totals.get(playerId); return total == null ? 0 : total; }

    public static final class RoundHistory {
        public final Records.Round round;
        public final List<Records.RoundPlayer> players;
        RoundHistory(Records.Round round, List<Records.RoundPlayer> players) {
            this.round = round;
            this.players = Collections.unmodifiableList(players);
        }
    }
}
