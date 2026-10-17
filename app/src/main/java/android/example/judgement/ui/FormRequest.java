package android.example.judgement.ui;

import android.os.Bundle;
import java.util.UUID;

/** A saved form retains its original command target, never a callback into an old Activity. */
final class FormRequest {
    enum Kind { ADD_PLAYER, RENAME_PLAYER, BID, SCORE }
    final String token;
    final Kind kind;
    final long gameId, playerId, roundId, expectedScore;
    final int maximumBid;
    final boolean manage;
    final String text, error;

    FormRequest(Kind kind, String text, boolean manage, long gameId, long playerId,
            long roundId, long expectedScore, int maximumBid) {
        this(UUID.randomUUID().toString(), kind, text, manage, gameId, playerId,
                roundId, expectedScore, maximumBid, null);
    }
    private FormRequest(String token, Kind kind, String text, boolean manage, long gameId,
            long playerId, long roundId, long expectedScore, int maximumBid, String error) {
        this.token = token; this.kind = kind; this.text = text; this.manage = manage;
        this.gameId = gameId; this.playerId = playerId; this.roundId = roundId;
        this.expectedScore = expectedScore; this.maximumBid = maximumBid; this.error = error;
    }
    FormRequest withInput(String value) {
        return new FormRequest(token, kind, value, manage, gameId, playerId, roundId,
                expectedScore, maximumBid, null);
    }
    FormRequest withError(String message) {
        return new FormRequest(token, kind, text, manage, gameId, playerId, roundId,
                expectedScore, maximumBid, message);
    }
    Bundle toBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("token", token); bundle.putString("kind", kind.name());
        bundle.putString("text", text); bundle.putBoolean("manage", manage);
        bundle.putLong("game", gameId); bundle.putLong("player", playerId);
        bundle.putLong("round", roundId); bundle.putLong("expected", expectedScore);
        bundle.putInt("maximumBid", maximumBid); bundle.putString("error", error);
        return bundle;
    }
    static FormRequest fromBundle(Bundle bundle) {
        if (bundle == null) return null;
        return new FormRequest(bundle.getString("token"), Kind.valueOf(bundle.getString("kind")),
                bundle.getString("text", ""), bundle.getBoolean("manage"),
                bundle.getLong("game"), bundle.getLong("player"), bundle.getLong("round"),
                bundle.getLong("expected"), bundle.getInt("maximumBid"), bundle.getString("error"));
    }
}
