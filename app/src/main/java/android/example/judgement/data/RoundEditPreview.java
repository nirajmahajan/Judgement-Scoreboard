package android.example.judgement.data;

import android.example.judgement.domain.Outcome;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Validated within one read transaction. Save revalidates the same revision against latest totals. */
public final class RoundEditPreview {
    public final long roundId;
    public final long expectedRevision;
    public final List<PlayerChange> players;
    public final Map<Long, Long> totals;
    public final Map<Long, Long> scoreDeltas;
    public final boolean changed;
    RoundEditPreview(long roundId, long expectedRevision, List<PlayerChange> players,
                     Map<Long, Long> totals, Map<Long, Long> scoreDeltas, boolean changed) {
        this.roundId = roundId; this.expectedRevision = expectedRevision;
        this.players = Collections.unmodifiableList(players);
        this.totals = Collections.unmodifiableMap(totals);
        this.scoreDeltas = Collections.unmodifiableMap(scoreDeltas); this.changed = changed;
    }
    public static final class PlayerChange {
        public final long playerId;
        public final String name;
        public final int beforeBid, afterBid;
        public final Outcome beforeOutcome, afterOutcome;
        public final long beforePoints, afterPoints, delta;
        PlayerChange(long playerId, String name, int beforeBid, int afterBid, Outcome beforeOutcome,
                     Outcome afterOutcome, long beforePoints, long afterPoints) {
            this.playerId = playerId; this.name = name; this.beforeBid = beforeBid;
            this.afterBid = afterBid; this.beforeOutcome = beforeOutcome; this.afterOutcome = afterOutcome;
            this.beforePoints = beforePoints; this.afterPoints = afterPoints;
            this.delta = Math.subtractExact(afterPoints, beforePoints);
        }
    }
}
