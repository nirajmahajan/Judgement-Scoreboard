package android.example.judgement;

import android.content.Context;
import android.os.Bundle;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.example.judgement.data.Records;
import android.example.judgement.data.RoundPlayerEdit;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/** A surviving host verifies PID changes, visible state, and an independent persisted-data read. */
@RunWith(AndroidJUnit4.class)
public class LifecycleFixtureTest {
    private static final ScoringStrategy CUSTOM = ScoringStrategy.custom(17, 23, 2, true);

    @Test public void seedCustomScoring() throws Exception {
        ScoreRepository repository = verifying() ? existing() : started(CUSTOM);
        try {
            if (!verifying()) {
                Snapshot state = repository.snapshot();
                repository.submitBid(state.round.id, state.roundPlayers.get(0).playerId, 0);
            }
            Snapshot state = repository.snapshot();
            assertEquals(CUSTOM.encode(), state.round.scoringConfig);
            assertEquals(CUSTOM.encode(), state.game.scoringConfig);
            assertEquals(Integer.valueOf(0), state.roundPlayers.get(0).bid);
            featureStatus(state, "Scoring: Custom");
        } finally { repository.close(); }
    }

    @Test public void seedPendingStrategy() throws Exception {
        ScoreRepository repository = verifying() ? existing() : started(CUSTOM);
        try {
            if (!verifying()) {
                Snapshot state = repository.snapshot();
                repository.submitBid(state.round.id, state.roundPlayers.get(0).playerId, 0);
                repository.changeScoringStrategy(state.round.id, ScoringStrategy.aggressive());
            }
            Snapshot state = repository.snapshot();
            assertEquals(CUSTOM.encode(), state.round.scoringConfig);
            assertEquals(ScoringStrategy.aggressive().encode(), state.game.pendingScoringConfig);
            featureStatus(state, "Scheduled: Aggressive");
            if (verifying()) {
                finishZeroRound(repository);
                Snapshot next = repository.snapshot();
                assertEquals(state.round.number + 1, next.round.number);
                assertEquals(ScoringStrategy.aggressive().encode(), next.round.scoringConfig);
                assertNull(next.game.pendingScoringConfig);
                assertEquals(CUSTOM.encode(), next.history.get(0).round.scoringConfig);
                assertEquals(17, next.total(state.roundPlayers.get(1).playerId));
                Bundle activated = new Bundle();
                activated.putString("lifecycleActivated", next.round.scoringConfig);
                InstrumentationRegistry.getInstrumentation().sendStatus(2, activated);
            }
        } finally { repository.close(); }
    }

    @Test public void seedCorrectedHistory() throws Exception {
        ScoreRepository repository = verifying() ? existing() : started(ScoringStrategy.aggressive());
        try {
            if (!verifying()) {
                finishZeroRound(repository);
                Snapshot.RoundHistory history = repository.snapshot().history.get(0);
                List<RoundPlayerEdit> edits = new ArrayList<>();
                for (int index = 0; index < history.players.size(); index++) {
                    Records.RoundPlayer player = history.players.get(index);
                    edits.add(new RoundPlayerEdit(player.playerId, 0, index == 1 ? Outcome.LOST : Outcome.WON));
                }
                repository.editRound(history.round.id, 0, edits);
                Snapshot current = repository.snapshot();
                for (Records.RoundPlayer player : current.roundPlayers) repository.submitBid(current.round.id, player.playerId, 0);
                repository.recordBids(current.round.id);
                repository.setOutcome(current.round.id, current.roundPlayers.get(0).playerId, Outcome.LOST);
                repository.setOutcome(current.round.id, current.roundPlayers.get(1).playerId, Outcome.WON);
            }
            Snapshot state = repository.snapshot();
            assertEquals(1, state.history.size()); assertEquals(1, state.history.get(0).round.revision);
            assertEquals(1, state.roundEdits.size()); assertTrue(state.roundEdits.get(0).createdAt > 0);
            assertEquals(7, state.total(state.history.get(0).players.get(0).playerId));
            assertEquals(0, state.total(state.history.get(0).players.get(1).playerId));
            assertEquals("RESULTS", state.round.phase);
            assertEquals("LOST", state.roundPlayers.get(0).outcome);
            assertEquals("WON", state.roundPlayers.get(1).outcome);
            assertNull(state.roundPlayers.get(2).outcome);
            featureStatus(state, "Scoring: Aggressive");
        } finally { repository.close(); }
    }

    @Test public void seedSetup() {
        ScoreRepository repository = fresh();
        repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("Lifecycle player A")), "DESCENDING", "CUSTOM", null));
        repository.close(); marker("Lifecycle player A");
    }
    @Test public void seedBidding() {
        ScoreRepository repository = started();
        Snapshot state = repository.snapshot();
        repository.submitBid(state.round.id, state.roundPlayers.get(0).playerId, 0);
        repository.close(); marker("Prediction: 0");
    }
    @Test public void seedResults() {
        ScoreRepository repository = started();
        Snapshot state = repository.snapshot();
        for (Records.RoundPlayer player : state.roundPlayers) repository.submitBid(state.round.id, player.playerId, 0);
        repository.recordBids(state.round.id);
        repository.setOutcome(state.round.id, state.roundPlayers.get(0).playerId, Outcome.LOST);
        repository.setOutcome(state.round.id, state.roundPlayers.get(1).playerId, Outcome.WON);
        repository.close(); marker("0 · Lost");
    }
    @Test public void seedCompleted() {
        ScoreRepository repository = fresh();
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("Lifecycle player A"), new PlayerSpec("Lifecycle player B")), "ASCENDING", "FIXED", null));
        repository.startGame(draft);
        while (repository.snapshot().round != null) {
            Snapshot state = repository.snapshot();
            for (Records.RoundPlayer player : state.roundPlayers) repository.submitBid(state.round.id, player.playerId, 0);
            repository.recordBids(state.round.id);
            for (int index = 0; index < state.roundPlayers.size(); index++) repository.setOutcome(state.round.id, state.roundPlayers.get(index).playerId, index == 0 ? Outcome.LOST : Outcome.WON);
            repository.completeRound(state.round.id);
        }
        repository.close(); marker("Game complete");
    }
    private static ScoreRepository fresh() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(ScoreDatabase.NAME);
        return new ScoreRepository(context);
    }
    private static ScoreRepository started() {
        return started(ScoringStrategy.defaults());
    }
    private static ScoreRepository started(ScoringStrategy scoring) {
        ScoreRepository repository = fresh();
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("Lifecycle player A"), new PlayerSpec("Lifecycle player B"), new PlayerSpec("Lifecycle player C")), "ASCENDING", "FIXED", null, scoring));
        repository.startGame(draft); return repository;
    }
    private static ScoreRepository existing() { return new ScoreRepository(ApplicationProvider.getApplicationContext()); }
    private static boolean verifying() {
        return "true".equals(InstrumentationRegistry.getArguments().getString("lifecycleVerify"));
    }
    private static void finishZeroRound(ScoreRepository repository) {
        Snapshot state = repository.snapshot();
        for (Records.RoundPlayer player : state.roundPlayers) if (player.bid == null) repository.submitBid(state.round.id, player.playerId, 0);
        repository.recordBids(state.round.id);
        for (int index = 0; index < state.roundPlayers.size(); index++) repository.setOutcome(
                state.round.id, state.roundPlayers.get(index).playerId, index == 0 ? Outcome.LOST : Outcome.WON);
        repository.completeRound(state.round.id);
    }
    private static void featureStatus(Snapshot state, String visibleMarker) throws Exception {
        JSONObject data = new JSONObject();
        data.put("gameId", state.game.id); data.put("active", state.game.scoringConfig);
        data.put("pending", state.game.pendingScoringConfig == null ? JSONObject.NULL : state.game.pendingScoringConfig);
        data.put("roundId", state.round.id); data.put("number", state.round.number);
        data.put("cards", state.round.cards); data.put("dealerId", state.round.dealerId);
        data.put("phase", state.round.phase); data.put("scoring", state.round.scoringConfig);
        data.put("players", recordedPlayers(state.roundPlayers));
        JSONObject totals = new JSONObject();
        for (java.util.Map.Entry<Long, Long> total : state.totals.entrySet()) totals.put(Long.toString(total.getKey()), total.getValue());
        data.put("totals", totals);
        JSONArray history = new JSONArray();
        for (Snapshot.RoundHistory entry : state.history) {
            JSONObject item = new JSONObject(); item.put("roundId", entry.round.id);
            item.put("number", entry.round.number); item.put("cards", entry.round.cards);
            item.put("dealerId", entry.round.dealerId);
            item.put("revision", entry.round.revision); item.put("scoring", entry.round.scoringConfig);
            item.put("players", recordedPlayers(entry.players)); history.put(item);
        }
        data.put("history", history);
        JSONArray audits = new JSONArray();
        for (Records.RoundEdit edit : state.roundEdits) {
            JSONObject item = new JSONObject(); item.put("id", edit.id); item.put("roundId", edit.roundId);
            item.put("revision", edit.revision); item.put("previous", edit.previousValues);
            item.put("updated", edit.newValues); item.put("createdAt", edit.createdAt); audits.put(item);
        }
        data.put("audits", audits);
        Bundle status = new Bundle(); status.putString("lifecycleMarker", visibleMarker);
        status.putString("lifecycleData", data.toString());
        InstrumentationRegistry.getInstrumentation().sendStatus(2, status);
    }
    private static JSONArray recordedPlayers(List<Records.RoundPlayer> players) throws Exception {
        JSONArray result = new JSONArray();
        for (Records.RoundPlayer player : players) {
            JSONObject item = new JSONObject(); item.put("id", player.playerId);
            item.put("name", player.nameSnapshot); item.put("bid", player.bid == null ? JSONObject.NULL : player.bid);
            item.put("outcome", player.outcome == null ? JSONObject.NULL : player.outcome); result.put(item);
        }
        return result;
    }
    private static void marker(String text) {
        Bundle status = new Bundle(); status.putString("lifecycleMarker", text);
        InstrumentationRegistry.getInstrumentation().sendStatus(2, status);
    }
}
