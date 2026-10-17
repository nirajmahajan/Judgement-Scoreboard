package android.example.judgement;

import android.content.Context;
import android.os.Bundle;
import android.example.judgement.data.DraftSpec;
import android.example.judgement.data.PlayerSpec;
import android.example.judgement.data.ScoreDatabase;
import android.example.judgement.data.ScoreRepository;
import android.example.judgement.data.Snapshot;
import android.example.judgement.data.Records;
import android.example.judgement.domain.Outcome;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Seeds only. A host process kills/relaunches the target and verifies its PID and visible state. */
@RunWith(AndroidJUnit4.class)
public class LifecycleFixtureTest {
    @Test public void seedSetup() {
        ScoreRepository repository = fresh();
        repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("Lifecycle player A")), "DESCENDING", "CUSTOM", null));
        repository.close(); marker("Lifecycle player A");
    }
    @Test public void seedBidding() {
        ScoreRepository repository = started();
        Snapshot state = repository.snapshot();
        repository.submitBid(state.round.id, state.roundPlayers.get(0).playerId, 0);
        repository.close(); marker("Bid: 0");
    }
    @Test public void seedResults() {
        ScoreRepository repository = started();
        Snapshot state = repository.snapshot();
        for (Records.RoundPlayer player : state.roundPlayers) repository.submitBid(state.round.id, player.playerId, 0);
        repository.recordBids(state.round.id);
        repository.setOutcome(state.round.id, state.roundPlayers.get(0).playerId, Outcome.LOST);
        repository.setOutcome(state.round.id, state.roundPlayers.get(1).playerId, Outcome.WON);
        repository.close(); marker("Bid 0 · Lost");
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
        ScoreRepository repository = fresh();
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("Lifecycle player A"), new PlayerSpec("Lifecycle player B"), new PlayerSpec("Lifecycle player C")), "ASCENDING", "FIXED", null));
        repository.startGame(draft); return repository;
    }
    private static void marker(String text) {
        Bundle status = new Bundle(); status.putString("lifecycleMarker", text);
        InstrumentationRegistry.getInstrumentation().sendStatus(2, status);
    }
}
