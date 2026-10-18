package android.example.judgement.data;

import java.util.List;

/** The earliest highest prediction chooses trump when the table chooses its own suit. */
public final class TrumpCaller {
    private TrumpCaller() { }

    public static Long playerId(String trumpMode, List<Records.RoundPlayer> players) {
        if (!"CUSTOM".equals(trumpMode)) return null;
        Records.RoundPlayer caller = null;
        for (Records.RoundPlayer player : players) {
            if (player.bid == null) continue;
            if (caller == null || player.bid > caller.bid
                    || (player.bid.equals(caller.bid) && player.bidPosition < caller.bidPosition)) {
                caller = player;
            }
        }
        return caller == null ? null : caller.playerId;
    }
}
