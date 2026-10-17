package android.example.judgement.data;

import android.example.judgement.domain.Outcome;

/** A complete proposed result for one historical participant; identity and name remain unchanged. */
public final class RoundPlayerEdit {
    public final long playerId;
    public final int bid;
    public final Outcome outcome;
    public RoundPlayerEdit(long playerId, int bid, Outcome outcome) {
        this.playerId = playerId; this.bid = bid; this.outcome = outcome;
    }
}
