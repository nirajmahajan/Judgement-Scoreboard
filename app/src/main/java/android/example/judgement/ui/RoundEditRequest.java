package android.example.judgement.ui;

import android.example.judgement.data.Records;
import android.example.judgement.data.RoundPlayerEdit;
import android.example.judgement.domain.GameRules;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Saved raw inputs and immutable historical targets; no callbacks retain an old screen. */
final class RoundEditRequest {
    private static final int BUNDLE_VERSION = 1;

    final String token;
    final long generation;
    final long gameId;
    final long roundId;
    final long expectedRevision;
    final long dealerId;
    final int roundNumber;
    final int cards;
    final ScoringStrategy scoring;
    final List<PlayerInput> players;
    final boolean validate;
    final String error;

    RoundEditRequest(Records.Round round, List<Records.RoundPlayer> participants) {
        this(UUID.randomUUID().toString(), 0L, round.gameId, round.id, round.revision,
                round.dealerId, round.number, round.cards, ScoringStrategy.decode(round.scoringConfig),
                originals(round, participants), false, null);
        if (!"COMPLETE".equals(round.phase)) {
            throw new IllegalArgumentException("Choose a completed round to edit.");
        }
    }

    private RoundEditRequest(String token, long generation, long gameId, long roundId,
            long expectedRevision, long dealerId, int roundNumber, int cards,
            ScoringStrategy scoring, List<PlayerInput> players, boolean validate, String error) {
        this.token = Objects.requireNonNull(token);
        this.generation = generation;
        this.gameId = gameId;
        this.roundId = roundId;
        this.expectedRevision = expectedRevision;
        this.dealerId = dealerId;
        this.roundNumber = roundNumber;
        this.cards = cards;
        this.scoring = scoring;
        this.players = Collections.unmodifiableList(new ArrayList<>(players));
        this.validate = validate;
        this.error = error;
    }

    RoundEditRequest editBid(long playerId, String raw) {
        PlayerInput original = player(playerId);
        if (original.bidText.equals(raw)) return this;
        return replace(original.withInput(Objects.requireNonNull(raw), original.outcome));
    }

    RoundEditRequest editOutcome(long playerId, Outcome outcome) {
        PlayerInput original = player(playerId);
        if (original.outcome == outcome) return this;
        return replace(original.withInput(original.bidText, outcome));
    }

    RoundEditRequest validated() {
        return copy(generation, players, true, error);
    }

    RoundEditRequest withError(String message) {
        return copy(generation, players, true, message);
    }

    PlayerInput player(long playerId) {
        for (PlayerInput player : players) if (player.playerId == playerId) return player;
        throw new IllegalArgumentException("This participant does not belong to the edited round.");
    }

    long originalPoints(long playerId) {
        PlayerInput player = player(playerId);
        return scoring.points(cards, player.originalBid, player.originalOutcome, players.size());
    }

    String bidError(long playerId) {
        if (!validate) return null;
        try { bid(player(playerId).bidText, cards); return null; }
        catch (IllegalArgumentException invalid) { return invalid.getMessage(); }
    }

    String outcomeError(long playerId) {
        return validate && player(playerId).outcome == null ? "Choose Won or Lost." : null;
    }

    /** Syntax and bounds are local; the repository validates the whole corrected allocation. */
    List<RoundPlayerEdit> edits() {
        List<RoundPlayerEdit> edits = new ArrayList<>();
        for (PlayerInput player : players) {
            int bid = bid(player.bidText, cards);
            if (player.outcome == null) throw new IllegalArgumentException("Choose Won or Lost.");
            edits.add(new RoundPlayerEdit(player.playerId, bid, player.outcome));
        }
        return Collections.unmodifiableList(edits);
    }

    Bundle toBundle() {
        Bundle value = new Bundle();
        value.putInt("version", BUNDLE_VERSION);
        value.putString("token", token); value.putLong("generation", generation);
        value.putLong("game", gameId); value.putLong("round", roundId);
        value.putLong("revision", expectedRevision); value.putLong("dealer", dealerId);
        value.putInt("number", roundNumber); value.putInt("cards", cards);
        value.putString("scoring", scoring.encode()); value.putBoolean("validate", validate);
        value.putString("error", error);
        ArrayList<Bundle> rows = new ArrayList<>();
        for (PlayerInput player : players) {
            Bundle row = new Bundle();
            row.putLong("id", player.playerId); row.putString("name", player.name);
            row.putInt("position", player.bidPosition); row.putInt("beforeBid", player.originalBid);
            row.putString("beforeOutcome", player.originalOutcome.name());
            row.putString("bid", player.bidText);
            row.putString("outcome", player.outcome == null ? null : player.outcome.name());
            rows.add(row);
        }
        value.putParcelableArrayList("players", rows);
        return value;
    }

    @SuppressWarnings("deprecation") // The typed overload requires API33; this app supports API26.
    static RoundEditRequest fromBundle(Bundle value) {
        if (value == null) return null;
        if (value.getInt("version") != BUNDLE_VERSION) {
            throw new IllegalArgumentException("This saved round editor format is unsupported.");
        }
        ArrayList<Bundle> rows = value.getParcelableArrayList("players");
        if (rows == null) throw new IllegalArgumentException("The saved round participants are missing.");
        List<PlayerInput> players = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (Bundle row : rows) {
            long id = row.getLong("id");
            if (!ids.add(id)) throw new IllegalArgumentException("A round participant appears twice.");
            String outcome = row.getString("outcome");
            players.add(new PlayerInput(id, Objects.requireNonNull(row.getString("name")),
                    row.getInt("position"), row.getInt("beforeBid"),
                    Outcome.valueOf(row.getString("beforeOutcome")),
                    Objects.requireNonNull(row.getString("bid")),
                    outcome == null ? null : Outcome.valueOf(outcome)));
        }
        return new RoundEditRequest(value.getString("token"), value.getLong("generation"),
                value.getLong("game"), value.getLong("round"), value.getLong("revision"),
                value.getLong("dealer"), value.getInt("number"), value.getInt("cards"),
                ScoringStrategy.decode(value.getString("scoring")), players,
                value.getBoolean("validate"), value.getString("error"));
    }

    private RoundEditRequest replace(PlayerInput changed) {
        List<PlayerInput> next = new ArrayList<>();
        for (PlayerInput player : players) next.add(player.playerId == changed.playerId ? changed : player);
        return copy(Math.addExact(generation, 1L), next, validate, null);
    }

    private RoundEditRequest copy(long generation, List<PlayerInput> players,
            boolean validate, String error) {
        return new RoundEditRequest(token, generation, gameId, roundId, expectedRevision,
                dealerId, roundNumber, cards, scoring, players, validate, error);
    }

    private static int bid(String raw, int cards) {
        try {
            String trimmed = raw.trim();
            if (!trimmed.matches("[0-9]+")) throw new NumberFormatException();
            int parsed = Integer.parseInt(trimmed);
            GameRules.validateBid(cards, parsed);
            return parsed;
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("Enter a whole prediction from 0 to " + cards + ".");
        }
    }

    private static List<PlayerInput> originals(Records.Round round,
            List<Records.RoundPlayer> participants) {
        GameRules.validatePlayerCount(participants.size());
        List<Records.RoundPlayer> ordered = new ArrayList<>(participants);
        ordered.sort(Comparator.comparingInt((Records.RoundPlayer row) -> row.bidPosition)
                .thenComparingLong(row -> row.playerId));
        List<PlayerInput> result = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        for (Records.RoundPlayer row : ordered) {
            if (row.roundId != round.id || !ids.add(row.playerId) || row.bid == null
                    || row.outcome == null) {
                throw new IllegalArgumentException("The completed round participant data is invalid.");
            }
            GameRules.validateBid(round.cards, row.bid);
            Outcome outcome = Outcome.valueOf(row.outcome);
            result.add(new PlayerInput(row.playerId, row.nameSnapshot, row.bidPosition,
                    row.bid, outcome, Integer.toString(row.bid), outcome));
        }
        return result;
    }

    static final class PlayerInput {
        final long playerId;
        final String name;
        final int bidPosition;
        final int originalBid;
        final Outcome originalOutcome;
        final String bidText;
        final Outcome outcome;

        private PlayerInput(long playerId, String name, int bidPosition, int originalBid,
                Outcome originalOutcome, String bidText, Outcome outcome) {
            this.playerId = playerId; this.name = name; this.bidPosition = bidPosition;
            this.originalBid = originalBid; this.originalOutcome = originalOutcome;
            this.bidText = bidText; this.outcome = outcome;
        }

        private PlayerInput withInput(String text, Outcome outcome) {
            return new PlayerInput(playerId, name, bidPosition, originalBid, originalOutcome,
                    text, outcome);
        }
    }
}
