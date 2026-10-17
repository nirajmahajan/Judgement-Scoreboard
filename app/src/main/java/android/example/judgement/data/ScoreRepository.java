package android.example.judgement.data;

import android.content.Context;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import android.example.judgement.domain.Direction;
import android.example.judgement.domain.GameRules;
import android.example.judgement.domain.Names;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** All game mutations pass through one transaction boundary and use stable IDs. */
public final class ScoreRepository implements AutoCloseable {
    private final ScoreDatabase database;
    private final ScoreDao dao;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MutableLiveData<Snapshot> snapshots = new MutableLiveData<>();
    private final MutableLiveData<String> failures = new MutableLiveData<>();

    public ScoreRepository(Context context) { this(ScoreDatabase.open(context)); }
    public ScoreRepository(ScoreDatabase database) {
        this.database = database;
        this.dao = database.dao();
        execute(this::refresh);
    }
    public LiveData<Snapshot> observe() { return snapshots; }
    public LiveData<String> failures() { return failures; }
    /** Commands are synchronous for transactional composition/testing. UI calls run on this executor. */
    public void execute(Runnable command) {
        executor.execute(() -> {
            try { command.run(); }
            catch (RuntimeException error) {
                String detail = error.getMessage();
                failures.postValue("Could not read or save this scorecard. Your database was kept. "
                        + (detail == null ? "Retry or reopen the app." : detail));
            }
        });
    }
    public Snapshot snapshot() { return database.runInTransaction(this::readSnapshot); }
    public void refresh() { snapshots.postValue(snapshot()); }
    @Override public void close() {
        // Queued saves finish before the connection closes. Leaving an Activity must not
        // interrupt an accepted command or block its main thread waiting for disk IO.
        executor.execute(database::close);
        executor.shutdown();
    }

    public long saveDraft(DraftSpec spec) {
        return write(() -> {
            validateConfiguration(spec.direction, spec.trumpMode);
            validateRoster(spec.players, false);
            Records.Game draft;
            if (spec.id == 0) {
                for (Records.Game old : dao.games()) {
                    if ("DRAFT".equals(old.status)) { old.status = "DISCARDED"; dao.updateGame(old); }
                }
                draft = new Records.Game();
                draft.createdAt = System.currentTimeMillis();
                draft.id = dao.insertGame(draft);
            } else {
                draft = requireGame(spec.id);
                if (!"DRAFT".equals(draft.status)) fail("This setup has already started. Open the current setup.");
            }
            List<Records.GamePlayer> previous = activePlayers(draft.id);
            Long requestedDealer = spec.dealerId;
            boolean retainedDealer = false;
            for (PlayerSpec player : spec.players) if (requestedDealer != null && player.id == requestedDealer) retainedDealer = true;
            if (requestedDealer != null && !retainedDealer && requestedDealer.equals(draft.dealerId)) {
                requestedDealer = spec.players.isEmpty() ? null : nextSurvivingDealer(previous, spec.players, requestedDealer);
            }
            List<Records.GamePlayer> stored = applyPlayers(draft.id, spec.players);
            draft.direction = spec.direction;
            draft.trumpMode = spec.trumpMode;
            if (spec.scoringStrategy != null) draft.scoringConfig = spec.scoringStrategy.encode();
            draft.dealerId = resolveDealer(spec.players, stored, requestedDealer);
            dao.updateGame(draft);
            return draft.id;
        });
    }

    /** Null leaves that setting unchanged, so independent queued choices cannot overwrite one another. */
    public void updateDraftSettings(long draftId, String direction, String trumpMode) {
        write(() -> {
            Records.Game draft = requireDraft(draftId);
            String nextDirection = direction == null ? draft.direction : direction;
            String nextTrump = trumpMode == null ? draft.trumpMode : trumpMode;
            validateConfiguration(nextDirection, nextTrump);
            draft.direction = nextDirection; draft.trumpMode = nextTrump; dao.updateGame(draft);
            return null;
        });
    }

    public void chooseDraftDealer(long draftId, long playerId) {
        write(() -> {
            Records.Game draft = requireDraft(draftId);
            Records.GamePlayer player = requirePlayer(playerId);
            if (player.gameId != draftId || !player.active) fail("Choose a dealer from the current setup players.");
            draft.dealerId = playerId; dao.updateGame(draft);
            return null;
        });
    }

    public void updateDraftScoring(long draftId, ScoringStrategy strategy) {
        String config = requireStrategy(strategy).encode();
        write(() -> {
            Records.Game draft = requireDraft(draftId);
            draft.scoringConfig = config; draft.pendingScoringConfig = null; dao.updateGame(draft);
            return null;
        });
    }

    /** Only a genuinely new chronological round consumes a scheduled strategy change. */
    public void changeScoringStrategy(long roundId, ScoringStrategy strategy) {
        String config = requireStrategy(strategy).encode();
        write(() -> {
            Records.Round round = requireOpenRound(roundId, null);
            Records.Game game = requireGame(round.gameId);
            if ("BIDDING".equals(round.phase) && bids(dao.roundPlayers(roundId)).isEmpty()) {
                round.scoringConfig = config; dao.updateRound(round);
                game.scoringConfig = config; game.pendingScoringConfig = null;
            } else {
                game.pendingScoringConfig = config.equals(round.scoringConfig) ? null : config;
            }
            dao.updateGame(game);
            return null;
        });
    }

    /** Reuse the table explicitly, while keeping the existing scorecard until Start is confirmed. */
    public long playAgain(long gameId) {
        return write(() -> {
            Records.Game source = requireGame(gameId);
            if (!"ACTIVE".equals(source.status) && !"COMPLETE".equals(source.status)) {
                fail("This scorecard is no longer current. Open the current results.");
            }
            ScoringStrategy.decode(source.scoringConfig);
            List<Records.GamePlayer> previous = activePlayers(gameId);
            validateConfiguration(source.direction, source.trumpMode);
            GameRules.validatePlayerCount(previous.size());
            for (Records.Game other : dao.games()) {
                if ("DRAFT".equals(other.status)) { other.status = "DISCARDED"; dao.updateGame(other); }
            }
            Records.Game draft = new Records.Game();
            draft.direction = source.direction; draft.trumpMode = "CUSTOM";
            draft.scoringConfig = source.scoringConfig; draft.createdAt = System.currentTimeMillis();
            draft.id = dao.insertGame(draft);
            List<PlayerSpec> specs = new ArrayList<>();
            for (Records.GamePlayer player : previous) {
                Long profile = player.savedPlayerId != null && dao.savedPlayer(player.savedPlayerId) != null
                        ? player.savedPlayerId : null;
                specs.add(new PlayerSpec(-player.id, player.name, profile));
            }
            applyPlayers(draft.id, specs);
            draft.dealerId = null;
            dao.updateGame(draft);
            return draft.id;
        });
    }

    public void renameDraftPlayer(long draftId, long playerId, String rawName) {
        write(() -> {
            requireDraft(draftId);
            Records.GamePlayer player = requirePlayer(playerId);
            if (player.gameId != draftId || !player.active) fail("This setup player was removed. Review the latest roster.");
            String name = Names.displayName(rawName), key = Names.key(name);
            for (Records.GamePlayer other : activePlayers(draftId)) {
                if (other.id != playerId && other.nameKey.equals(key)) fail("Player names must be unique.");
            }
            player.name = name; player.nameKey = key; dao.updatePlayer(player);
            return null;
        });
    }

    public void removeDraftPlayer(long draftId, long playerId) {
        write(() -> {
            Records.Game draft = requireDraft(draftId);
            Records.GamePlayer removed = requirePlayer(playerId);
            if (removed.gameId != draftId || !removed.active) fail("This setup player was already removed. Review the latest roster.");
            List<Records.GamePlayer> previous = activePlayers(draftId);
            List<PlayerSpec> remaining = new ArrayList<>();
            for (Records.GamePlayer player : previous) {
                if (player.id == playerId) continue;
                remaining.add(new PlayerSpec(player.id, player.name, player.savedPlayerId));
                player.seat = remaining.size() - 1; dao.updatePlayer(player);
            }
            removed.active = false; dao.updatePlayer(removed);
            if (draft.dealerId != null && draft.dealerId == playerId) {
                draft.dealerId = remaining.isEmpty() ? null : nextSurvivingDealer(previous, remaining, playerId);
                dao.updateGame(draft);
            }
            return null;
        });
    }

    /** A stale drag cannot remove a participant added after the gesture's initial snapshot. */
    public void reorderDraft(long draftId, List<Long> orderedIds) {
        write(() -> {
            requireDraft(draftId);
            List<Records.GamePlayer> current = activePlayers(draftId);
            Set<Long> requested = new HashSet<>(orderedIds);
            Set<Long> existing = new HashSet<>();
            for (Records.GamePlayer player : current) existing.add(player.id);
            if (requested.size() != orderedIds.size() || !requested.equals(existing)) {
                fail("The player list changed during this move. Review the latest order and try again.");
            }
            for (int seat = 0; seat < orderedIds.size(); seat++) {
                Records.GamePlayer player = requirePlayer(orderedIds.get(seat)); player.seat = seat; dao.updatePlayer(player);
            }
            return null;
        });
    }

    public long startGame(long draftId) {
        return write(() -> {
            Records.Game draft = requireGame(draftId);
            if (!"DRAFT".equals(draft.status)) fail("This setup has already started.");
            List<Records.GamePlayer> players = activePlayers(draft.id);
            GameRules.validatePlayerCount(players.size());
            validateConfiguration(draft.direction, draft.trumpMode);
            ScoringStrategy.decode(draft.scoringConfig);
            long dealer = draft.dealerId == null ? players.get(players.size() - 1).id : draft.dealerId;
            GameRules.bidOrder(ids(players), dealer);
            for (Records.Game game : dao.games()) {
                if (game.id != draft.id && ("ACTIVE".equals(game.status) || "COMPLETE".equals(game.status))) {
                    game.status = "REPLACED"; dao.updateGame(game);
                }
            }
            // Removed setup entries never participated in this game. Historical inactive
            // participants are retained only after a game has actually started.
            dao.deleteInactiveDraftPlayers(draft.id);
            draft.status = "ACTIVE";
            draft.maximumCards = GameRules.maximumCards(players.size());
            draft.dealerId = dealer;
            dao.updateGame(draft);
            createRound(draft.id, 1, GameRules.firstCards(players.size(), Direction.valueOf(draft.direction)), dealer, players, draft.scoringConfig);
            return draft.id;
        });
    }

    public void submitBid(long roundId, long playerId, int bid) {
        write(() -> {
            Records.Round round = requireOpenRound(roundId, "BIDDING");
            List<Records.RoundPlayer> rows = dao.roundPlayers(roundId);
            Map<Long, Integer> bids = bids(rows);
            long[] order = roundIds(rows);
            if (bids.containsKey(playerId)) GameRules.validateBidEdit(round.cards, order, round.dealerId, bids, playerId, bid);
            else GameRules.validateBidSubmission(round.cards, order, round.dealerId, bids, playerId, bid);
            Records.RoundPlayer row = requireRoundPlayer(rows, playerId);
            row.bid = bid;
            dao.updateRoundPlayer(row);
            return null;
        });
    }

    public void recordBids(long roundId) {
        write(() -> {
            Records.Round round = requireOpenRound(roundId, "BIDDING");
            List<Records.RoundPlayer> rows = dao.roundPlayers(roundId);
            GameRules.validateBidsComplete(round.cards, roundIds(rows), round.dealerId, bids(rows));
            round.phase = "RESULTS";
            dao.updateRound(round);
            return null;
        });
    }

    public void setOutcome(long roundId, long playerId, Outcome outcome) {
        write(() -> {
            requireOpenRound(roundId, "RESULTS");
            Records.RoundPlayer row = requireRoundPlayer(dao.roundPlayers(roundId), playerId);
            row.outcome = outcome == null ? null : outcome.name();
            dao.updateRoundPlayer(row);
            return null;
        });
    }

    /** A repeated completion of an already completed round succeeds without a second effect. */
    public void completeRound(long roundId) {
        write(() -> {
            Records.Round round = dao.round(roundId);
            if (round == null) fail("This round no longer exists.");
            if ("COMPLETE".equals(round.phase)) return null;
            requireOpenRound(roundId, "RESULTS");
            List<Records.RoundPlayer> rows = dao.roundPlayers(roundId);
            int[] bids = new int[rows.size()];
            Outcome[] outcomes = new Outcome[rows.size()];
            ScoringStrategy strategy = ScoringStrategy.decode(round.scoringConfig);
            Map<Long, Long> totals = totals(round.gameId);
            for (int index = 0; index < rows.size(); index++) {
                Records.RoundPlayer row = rows.get(index);
                if (row.bid == null || row.outcome == null) fail("Choose Won or Lost for every player.");
                bids[index] = row.bid;
                outcomes[index] = Outcome.valueOf(row.outcome);
                GameRules.addScore(totals.get(row.playerId), strategy.points(round.cards, row.bid, outcomes[index]));
            }
            if (!GameRules.areOutcomesFeasible(round.cards, bids, outcomes)) {
                fail("These results are impossible for " + round.cards + " tricks. Check the Won/Lost choices.");
            }
            Records.Game game = requireGame(round.gameId);
            List<Records.GamePlayer> players = activePlayers(game.id);
            long nextDealer = GameRules.nextDealer(ids(players), round.dealerId);
            Integer nextCards = GameRules.nextCards(round.cards, players.size(), Direction.valueOf(game.direction));
            round.phase = "COMPLETE";
            round.completedAt = System.currentTimeMillis();
            dao.updateRound(round);
            game.dealerId = nextDealer;
            if (nextCards == null) game.status = "COMPLETE";
            else {
                if (game.pendingScoringConfig != null) {
                    ScoringStrategy.decode(game.pendingScoringConfig);
                    game.scoringConfig = game.pendingScoringConfig; game.pendingScoringConfig = null;
                }
                createRound(game.id, round.number + 1, nextCards, nextDealer, players, game.scoringConfig);
            }
            dao.updateGame(game);
            return null;
        });
    }

    public long restartRound(long roundId) {
        return write(() -> {
            Records.Round round = requireOpenRound(roundId, null);
            return replaceRound(round, round.dealerId, activePlayers(round.gameId));
        });
    }

    public RoundEditPreview previewRoundEdit(long roundId, long expectedRevision, List<RoundPlayerEdit> edits) {
        return database.runInTransaction(() -> validateRoundEdit(roundId, expectedRevision, edits).preview);
    }

    public void editRound(long roundId, long expectedRevision, List<RoundPlayerEdit> edits) {
        write(() -> {
            ValidatedEdit validated = validateRoundEdit(roundId, expectedRevision, edits);
            if (!validated.preview.changed) return null;
            Records.Round round = validated.round;
            Records.RoundEdit audit = new Records.RoundEdit();
            audit.gameId = round.gameId; audit.roundId = round.id;
            audit.previousRevision = round.revision; audit.revision = Math.addExact(round.revision, 1);
            audit.previousValues = auditValues(round, validated.before);
            audit.newValues = auditValues(round, validated.after);
            audit.createdAt = System.currentTimeMillis();
            for (Records.RoundPlayer row : validated.after) dao.updateRoundPlayer(row);
            round.revision = audit.revision; dao.updateRound(round);
            dao.insertRoundEdit(audit);
            return null;
        });
    }

    private ValidatedEdit validateRoundEdit(long roundId, long expectedRevision, List<RoundPlayerEdit> edits) {
        Records.Round round = dao.round(roundId);
        if (round == null || !"COMPLETE".equals(round.phase)) fail("Choose a completed round to edit.");
        Records.Game game = requireGame(round.gameId);
        if (!"ACTIVE".equals(game.status) && !"COMPLETE".equals(game.status)) fail("This scorecard is no longer current.");
        if (round.revision != expectedRevision) fail("This round was edited. Reopen it before saving another correction.");
        List<Records.RoundPlayer> before = dao.roundPlayers(roundId), after = new ArrayList<>();
        if (edits == null || edits.size() != before.size()) fail("Enter a prediction and result for every recorded participant.");
        Map<Long, RoundPlayerEdit> requested = new HashMap<>();
        for (RoundPlayerEdit edit : edits) {
            if (edit == null || requested.put(edit.playerId, edit) != null) fail("Each recorded participant must appear once.");
        }
        ScoringStrategy strategy = ScoringStrategy.decode(round.scoringConfig);
        List<RoundEditPreview.PlayerChange> changes = new ArrayList<>();
        Map<Long, Long> deltas = new LinkedHashMap<>();
        int[] correctedBids = new int[before.size()];
        Outcome[] correctedOutcomes = new Outcome[before.size()];
        boolean changed = false;
        for (int index = 0; index < before.size(); index++) {
            Records.RoundPlayer original = before.get(index);
            RoundPlayerEdit edit = requested.get(original.playerId);
            if (edit == null) fail("A corrected result belongs to another participant.");
            GameRules.validateBid(round.cards, edit.bid);
            if (edit.outcome == null) fail("Choose Won or Lost for every participant.");
            Records.RoundPlayer row = new Records.RoundPlayer();
            row.roundId = original.roundId; row.playerId = original.playerId;
            row.bidPosition = original.bidPosition; row.nameSnapshot = original.nameSnapshot;
            row.bid = edit.bid; row.outcome = edit.outcome.name(); after.add(row);
            correctedBids[index] = edit.bid; correctedOutcomes[index] = edit.outcome;
            RoundEditPreview.PlayerChange change = new RoundEditPreview.PlayerChange(row.playerId,
                    row.nameSnapshot, original.bid, edit.bid, Outcome.valueOf(original.outcome), edit.outcome,
                    strategy.points(round.cards, original.bid, Outcome.valueOf(original.outcome)),
                    strategy.points(round.cards, edit.bid, edit.outcome));
            changes.add(change); deltas.put(row.playerId, change.delta);
            changed |= original.bid != edit.bid || !original.outcome.equals(row.outcome);
        }
        GameRules.validateBidsComplete(round.cards, roundIds(after), round.dealerId, bids(after));
        if (!GameRules.areOutcomesFeasible(round.cards, correctedBids, correctedOutcomes)) {
            fail("These results are impossible for " + round.cards + " tricks. Check the predictions and Won/Lost choices.");
        }
        return new ValidatedEdit(round, before, after, new RoundEditPreview(round.id, expectedRevision,
                changes, totals(round.gameId, round.id, after), deltas, changed));
    }

    private static String auditValues(Records.Round round, List<Records.RoundPlayer> rows) {
        JSONArray values = new JSONArray();
        ScoringStrategy strategy = ScoringStrategy.decode(round.scoringConfig);
        try {
            for (Records.RoundPlayer row : rows) {
                JSONObject value = new JSONObject();
                value.put("playerId", row.playerId); value.put("name", row.nameSnapshot);
                value.put("bid", row.bid); value.put("outcome", row.outcome);
                value.put("points", strategy.points(round.cards, row.bid, Outcome.valueOf(row.outcome)));
                values.put(value);
            }
        } catch (JSONException error) { throw new IllegalStateException("Cannot record the round correction.", error); }
        return values.toString();
    }

    private static final class ValidatedEdit {
        final Records.Round round;
        final List<Records.RoundPlayer> before, after;
        final RoundEditPreview preview;
        ValidatedEdit(Records.Round round, List<Records.RoundPlayer> before,
                      List<Records.RoundPlayer> after, RoundEditPreview preview) {
            this.round = round; this.before = before; this.after = after; this.preview = preview;
        }
    }

    /** Validate the complete staged roster before writing any participant or round. */
    public void applyRoster(long roundId, List<PlayerSpec> specs, long dealerId, boolean confirmedRestart) {
        write(() -> { applyRosterInTransaction(roundId, specs, dealerId, confirmedRestart); return null; });
    }

    public void restoreParticipant(long roundId, long playerId, boolean confirmedRestart) {
        write(() -> {
            Records.Round round = requireOpenRound(roundId, null);
            Records.GamePlayer player = requirePlayer(playerId);
            if (player.gameId != round.gameId || player.active) fail("Choose a removed player from this game.");
            List<PlayerSpec> roster = new ArrayList<>();
            for (Records.GamePlayer active : activePlayers(round.gameId)) roster.add(new PlayerSpec(active.id, active.name, active.savedPlayerId));
            roster.add(new PlayerSpec(player.id, player.name, player.savedPlayerId));
            applyRosterInTransaction(roundId, roster, round.dealerId, confirmedRestart);
            return null;
        });
    }

    public void renamePlayer(long playerId, String rawName) {
        write(() -> {
            Records.GamePlayer player = requirePlayer(playerId);
            String name = Names.displayName(rawName);
            String key = Names.key(name);
            for (Records.GamePlayer other : activePlayers(player.gameId)) {
                if (other.id != player.id && other.nameKey.equals(key)) fail("A player with this name is already in the game.");
            }
            player.name = name; player.nameKey = key; dao.updatePlayer(player);
            for (Records.Round round : dao.rounds(player.gameId)) {
                if ("BIDDING".equals(round.phase) || "RESULTS".equals(round.phase)) {
                    for (Records.RoundPlayer row : dao.roundPlayers(round.id)) {
                        if (row.playerId == player.id) { row.nameSnapshot = name; dao.updateRoundPlayer(row); }
                    }
                }
            }
            return null;
        });
    }

    public void correctScore(long playerId, long expectedTotal, long newTotal) {
        write(() -> {
            GameRules.validateScoreTotal(newTotal);
            Records.GamePlayer player = requirePlayer(playerId);
            Records.Game game = requireGame(player.gameId);
            if (!"ACTIVE".equals(game.status) && !"COMPLETE".equals(game.status)) fail("This scorecard is no longer current.");
            long current = totals(player.gameId).get(playerId);
            if (current != expectedTotal) fail("This score changed. Reopen the correction and try again.");
            if (newTotal != current) {
                Records.ScoreAdjustment adjustment = new Records.ScoreAdjustment();
                adjustment.gameId = player.gameId; adjustment.playerId = player.id;
                adjustment.previousTotal = current; adjustment.delta = Math.subtractExact(newTotal, current);
                adjustment.nameSnapshot = player.name; adjustment.createdAt = System.currentTimeMillis();
                dao.insertAdjustment(adjustment);
            }
            return null;
        });
    }

    public long addSavedPlayer(String rawName) {
        return write(() -> insertSavedProfile(rawName));
    }

    /** Saving an optional profile and adding its game participant is one atomic command. */
    public long addPlayerToDraft(long draftId, String rawName, boolean saveForFuture) {
        return write(() -> {
            Records.Game draft = requireGame(draftId);
            if (!"DRAFT".equals(draft.status)) fail("This setup has already started.");
            String name = Names.displayName(rawName);
            List<PlayerSpec> specs = new ArrayList<>();
            for (Records.GamePlayer existing : activePlayers(draftId)) specs.add(new PlayerSpec(existing.id, existing.name, existing.savedPlayerId));
            specs.add(new PlayerSpec(name));
            validateRoster(specs, false);
            Long profileId = saveForFuture ? insertSavedProfile(name) : null;
            specs.set(specs.size() - 1, new PlayerSpec(0, name, profileId));
            List<Records.GamePlayer> stored = applyPlayers(draftId, specs);
            return stored.get(stored.size() - 1).id;
        });
    }

    private long insertSavedProfile(String rawName) {
        String name = Names.displayName(rawName);
        String key = Names.key(name);
        Records.SavedPlayer existing = dao.savedPlayerByKey(key);
        if (existing != null) throw new DuplicateSavedPlayerException(existing.id);
        Records.SavedPlayer player = new Records.SavedPlayer(); player.name = name; player.nameKey = key;
        return dao.insertSavedPlayer(player);
    }

    public void renameSavedPlayer(long id, String rawName) {
        write(() -> {
            Records.SavedPlayer player = dao.savedPlayer(id);
            if (player == null) fail("This saved player was removed.");
            String name = Names.displayName(rawName);
            String key = Names.key(name);
            Records.SavedPlayer existing = dao.savedPlayerByKey(key);
            if (existing != null && existing.id != id) throw new DuplicateSavedPlayerException(existing.id);
            player.name = name; player.nameKey = key; dao.updateSavedPlayer(player);
            return null;
        });
    }
    public void removeSavedPlayer(long id) { removeSavedPlayers(java.util.Collections.singletonList(id)); }
    public void removeSavedPlayers(List<Long> ids) {
        write(() -> { for (long id : ids) dao.deleteSavedPlayer(id); return null; });
    }

    /** Adds the whole selection or nothing. Existing seats retain their IDs and order. */
    public void addSavedPlayersToDraft(long draftId, List<Long> profileIds) {
        write(() -> {
            Records.Game game = requireGame(draftId);
            if (!"DRAFT".equals(game.status)) fail("Open setup before adding saved players.");
            Set<Long> requested = new HashSet<>(profileIds);
            if (requested.size() != profileIds.size()) fail("Select each saved player once.");
            List<PlayerSpec> specs = new ArrayList<>();
            for (Records.GamePlayer player : activePlayers(draftId)) specs.add(new PlayerSpec(player.id, player.name, player.savedPlayerId));
            for (Records.SavedPlayer profile : dao.savedPlayers()) {
                if (requested.remove(profile.id)) specs.add(new PlayerSpec(0, profile.name, profile.id));
            }
            if (!requested.isEmpty()) fail("A selected saved player was removed. Open the picker again.");
            validateRoster(specs, false);
            applyPlayers(draftId, specs);
            return null;
        });
    }

    public static final class DuplicateSavedPlayerException extends IllegalArgumentException {
        public final long existingId;
        DuplicateSavedPlayerException(long id) { super("This name already has a saved player. Use the existing profile."); existingId = id; }
    }

    private void applyRosterInTransaction(long roundId, List<PlayerSpec> specs, long requestedDealer, boolean confirmedRestart) {
        Records.Round round = requireOpenRound(roundId, null);
        validateRoster(specs, true);
        GameRules.validateCards(round.cards, specs.size());
        List<Records.GamePlayer> original = activePlayers(round.gameId);
        Set<Long> uniqueIds = new HashSet<>();
        boolean structural = original.size() != specs.size();
        for (int index = 0; index < specs.size(); index++) {
            PlayerSpec spec = specs.get(index);
            if (spec.id > 0) {
                Records.GamePlayer stored = requirePlayer(spec.id);
                if (stored.gameId != round.gameId) fail("A roster row belongs to another game.");
            }
            if (spec.id != 0 && !uniqueIds.add(spec.id)) fail("The roster contains the same participant twice.");
            if (index >= original.size() || spec.id != original.get(index).id) structural = true;
        }
        long dealer = requestedDealer;
        if (dealer == 0) dealer = nextSurvivingDealer(original, specs, round.dealerId);
        boolean dealerPresent = false;
        for (PlayerSpec spec : specs) if (spec.id == dealer) dealerPresent = true;
        if (!dealerPresent) fail("Choose a dealer from the prospective roster.");
        structural |= dealer != round.dealerId;
        boolean started = !bids(dao.roundPlayers(roundId)).isEmpty() || "RESULTS".equals(round.phase);
        if (structural && started && !confirmedRestart) fail("Confirm restarting the current round before applying player or dealer changes.");
        List<Records.GamePlayer> players = applyPlayers(round.gameId, specs);
        Records.Game game = requireGame(round.gameId);
        Long persistedDealer = resolveDealer(specs, players, dealer);
        game.dealerId = persistedDealer; dao.updateGame(game);
        if (structural) replaceRound(round, persistedDealer, players);
        else {
            for (Records.RoundPlayer row : dao.roundPlayers(roundId)) {
                row.nameSnapshot = requirePlayer(row.playerId).name; dao.updateRoundPlayer(row);
            }
        }
    }

    private List<Records.GamePlayer> applyPlayers(long gameId, List<PlayerSpec> specs) {
        List<Records.GamePlayer> previous = dao.players(gameId);
        Set<Long> retained = new HashSet<>();
        List<Records.GamePlayer> result = new ArrayList<>();
        for (int seat = 0; seat < specs.size(); seat++) {
            PlayerSpec spec = specs.get(seat);
            Records.GamePlayer player = spec.id > 0 ? requirePlayer(spec.id) : new Records.GamePlayer();
            if (spec.id > 0 && player.gameId != gameId) fail("A player belongs to another setup.");
            if (spec.id > 0 && !retained.add(player.id)) fail("The roster contains the same participant twice.");
            if (spec.savedPlayerId != null && (spec.id <= 0 || !spec.savedPlayerId.equals(player.savedPlayerId))) {
                if (dao.savedPlayer(spec.savedPlayerId) == null) fail("This saved player was removed. Open the picker again.");
            }
            Long profileId = spec.savedPlayerId;
            if (spec.id <= 0 && spec.saveForFuture) profileId = insertSavedProfile(spec.name);
            player.gameId = gameId; player.name = Names.displayName(spec.name); player.nameKey = Names.key(spec.name);
            player.seat = seat; player.active = true; player.savedPlayerId = profileId;
            if (player.id == 0) { player.id = dao.insertPlayer(player); retained.add(player.id); }
            else dao.updatePlayer(player);
            result.add(player);
        }
        for (Records.GamePlayer player : previous) {
            if (!retained.contains(player.id) && player.active) { player.active = false; dao.updatePlayer(player); }
        }
        return result;
    }

    private Long resolveDealer(List<PlayerSpec> specs, List<Records.GamePlayer> stored, Long requested) {
        if (requested == null) return null;
        for (int index = 0; index < specs.size(); index++) {
            if (specs.get(index).id == requested) return stored.get(index).id;
        }
        fail("Choose a dealer from the setup players."); return null;
    }

    private static long nextSurvivingDealer(List<Records.GamePlayer> original, List<PlayerSpec> specs, long dealerId) {
        Set<Long> surviving = new HashSet<>(); for (PlayerSpec spec : specs) surviving.add(spec.id);
        int oldSeat = 0;
        for (int index = 0; index < original.size(); index++) if (original.get(index).id == dealerId) oldSeat = index;
        for (int offset = 0; offset < original.size(); offset++) {
            long candidate = original.get((oldSeat + offset) % original.size()).id;
            if (surviving.contains(candidate)) return candidate;
        }
        return specs.get(0).id;
    }

    private void validateRoster(List<PlayerSpec> specs, boolean requireComplete) {
        if (specs == null) fail("The player list is missing.");
        if (requireComplete) GameRules.validatePlayerCount(specs.size());
        else if (specs.size() > GameRules.MAX_PLAYERS) fail("A game supports at most 52 players.");
        Set<String> names = new HashSet<>();
        Set<Long> profiles = new HashSet<>();
        for (PlayerSpec spec : specs) {
            if (spec == null) fail("A player row is missing.");
            Names.displayName(spec.name);
            if (spec.saveForFuture && (spec.id > 0 || spec.savedPlayerId != null)) fail("Only a new player without a saved profile can be saved here.");
            if (!names.add(Names.key(spec.name))) fail("Player names must be unique.");
            if (spec.savedPlayerId != null && !profiles.add(spec.savedPlayerId)) fail("This saved player is already in the roster.");
        }
    }

    private static void validateConfiguration(String direction, String trumpMode) {
        try { Direction.valueOf(direction); } catch (IllegalArgumentException | NullPointerException error) { fail("Choose ascending or descending cards."); }
        if (!"FIXED".equals(trumpMode) && !"CUSTOM".equals(trumpMode)) fail("Choose fixed or custom trump.");
    }

    private long replaceRound(Records.Round old, long dealer, List<Records.GamePlayer> players) {
        old.phase = "ABANDONED"; dao.updateRound(old);
        return createRound(old.gameId, old.number, old.cards, dealer, players, old.scoringConfig);
    }

    private long createRound(long gameId, int number, int cards, long dealer, List<Records.GamePlayer> players, String scoringConfig) {
        GameRules.validateCards(cards, players.size());
        long[] order = GameRules.bidOrder(ids(players), dealer);
        Records.Round round = new Records.Round();
        round.gameId = gameId; round.number = number; round.cards = cards; round.dealerId = dealer;
        round.scoringConfig = scoringConfig;
        round.createdAt = System.currentTimeMillis(); round.id = dao.insertRound(round);
        for (int index = 0; index < order.length; index++) {
            Records.RoundPlayer row = new Records.RoundPlayer();
            row.roundId = round.id; row.playerId = order[index]; row.bidPosition = index;
            row.nameSnapshot = requirePlayer(order[index]).name;
            dao.insertRoundPlayer(row);
        }
        return round.id;
    }

    private Records.Round requireOpenRound(long id, String phase) {
        Records.Round round = dao.round(id);
        if (round == null) fail("This round no longer exists.");
        Records.Game game = requireGame(round.gameId);
        if (!"ACTIVE".equals(game.status) || (!"BIDDING".equals(round.phase) && !"RESULTS".equals(round.phase))) fail("This round has changed. Return to the current round.");
        if (phase != null && !phase.equals(round.phase)) fail("This action belongs to another stage of the round.");
        return round;
    }
    private Records.Game requireDraft(long id) {
        Records.Game draft = requireGame(id);
        if (!"DRAFT".equals(draft.status)) fail("This setup has already started. Open the current setup.");
        return draft;
    }
    private Records.Game requireGame(long id) {
        Records.Game game = dao.game(id); if (game == null) fail("This game no longer exists."); return game;
    }
    private Records.GamePlayer requirePlayer(long id) {
        Records.GamePlayer player = dao.player(id); if (player == null) fail("This participant no longer exists."); return player;
    }
    private static Records.RoundPlayer requireRoundPlayer(List<Records.RoundPlayer> rows, long id) {
        for (Records.RoundPlayer row : rows) if (row.playerId == id) return row;
        fail("This player is not in the current round."); return null;
    }
    private List<Records.GamePlayer> activePlayers(long gameId) {
        List<Records.GamePlayer> players = new ArrayList<>();
        for (Records.GamePlayer player : dao.players(gameId)) if (player.active) players.add(player);
        return players;
    }
    private static long[] ids(List<Records.GamePlayer> players) {
        long[] ids = new long[players.size()]; for (int index = 0; index < ids.length; index++) ids[index] = players.get(index).id; return ids;
    }
    private static long[] roundIds(List<Records.RoundPlayer> players) {
        long[] ids = new long[players.size()]; for (int index = 0; index < ids.length; index++) ids[index] = players.get(index).playerId; return ids;
    }
    private static Map<Long, Integer> bids(List<Records.RoundPlayer> players) {
        Map<Long, Integer> bids = new LinkedHashMap<>(); for (Records.RoundPlayer player : players) if (player.bid != null) bids.put(player.playerId, player.bid); return bids;
    }
    private Map<Long, Long> totals(long gameId) {
        return totals(gameId, -1, null);
    }
    private Map<Long, Long> totals(long gameId, long replacementRoundId, List<Records.RoundPlayer> replacement) {
        Map<Long, BigInteger> exact = new LinkedHashMap<>();
        for (Records.GamePlayer player : dao.players(gameId)) exact.put(player.id, BigInteger.ZERO);
        for (Records.Round round : dao.rounds(gameId)) {
            if (!"COMPLETE".equals(round.phase)) continue;
            ScoringStrategy strategy = ScoringStrategy.decode(round.scoringConfig);
            for (Records.RoundPlayer player : round.id == replacementRoundId ? replacement : dao.roundPlayers(round.id)) {
                long award = strategy.points(round.cards, player.bid, Outcome.valueOf(player.outcome));
                exact.put(player.playerId, exact.get(player.playerId).add(BigInteger.valueOf(award)));
            }
        }
        for (Records.ScoreAdjustment adjustment : dao.adjustments(gameId)) {
            exact.put(adjustment.playerId, exact.get(adjustment.playerId).add(BigInteger.valueOf(adjustment.delta)));
        }
        Map<Long, Long> totals = new LinkedHashMap<>();
        for (Map.Entry<Long, BigInteger> entry : exact.entrySet()) {
            BigInteger exactValue = entry.getValue();
            if (exactValue.signum() < 0 || exactValue.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                fail("This score exceeds the supported total.");
            }
            long value = exactValue.longValue();
            GameRules.validateScoreTotal(value);
            totals.put(entry.getKey(), value);
        }
        return totals;
    }

    private Snapshot readSnapshot() {
        Records.Game draft = null, current = null;
        for (Records.Game game : dao.games()) {
            if (draft == null && "DRAFT".equals(game.status)) draft = game;
            if (current == null && ("ACTIVE".equals(game.status) || "COMPLETE".equals(game.status))) current = game;
        }
        List<Records.GamePlayer> players = new ArrayList<>(), inactive = new ArrayList<>();
        List<Snapshot.RoundHistory> history = new ArrayList<>();
        List<Records.ScoreAdjustment> adjustments = new ArrayList<>();
        Map<Long, Long> totals = new HashMap<>();
        Records.Round open = null;
        List<Records.RoundPlayer> roundPlayers = new ArrayList<>();
        if (current != null) {
            for (Records.GamePlayer player : dao.players(current.id)) { if (player.active) players.add(player); else inactive.add(player); }
            totals = totals(current.id);
            adjustments = dao.adjustments(current.id);
            for (Records.Round round : dao.rounds(current.id)) {
                if ("COMPLETE".equals(round.phase)) history.add(new Snapshot.RoundHistory(round, dao.roundPlayers(round.id)));
                if ("BIDDING".equals(round.phase) || "RESULTS".equals(round.phase)) open = round;
            }
            if (open != null) roundPlayers = dao.roundPlayers(open.id);
        }
        return new Snapshot(draft, draft == null ? new ArrayList<>() : activePlayers(draft.id), current,
                players, inactive, totals, open, roundPlayers, history, adjustments, dao.savedPlayers(), dao.recovery(),
                current == null ? new ArrayList<>() : dao.roundEdits(current.id));
    }

    private static ScoringStrategy requireStrategy(ScoringStrategy strategy) {
        if (strategy == null) fail("Choose a scoring strategy.");
        return strategy;
    }

    private <T> T write(Callable<T> action) {
        T result = database.runInTransaction(action);
        refresh();
        return result;
    }
    private static void fail(String message) { throw new IllegalArgumentException(message); }
}
