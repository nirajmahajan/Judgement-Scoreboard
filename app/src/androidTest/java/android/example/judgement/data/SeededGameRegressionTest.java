package android.example.judgement.data;

import static org.junit.Assert.*;
import android.content.Context;
import android.example.judgement.domain.Outcome;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Every failure prints a reproducible seed and its complete operation sequence. */
@RunWith(AndroidJUnit4.class)
public class SeededGameRegressionTest {
    @Test public void seededGamesKeepExactScoresAcrossRestartReopenAndRepeatedCompletion() {
        int runs = Integer.parseInt(InstrumentationRegistry.getArguments().getString("sequenceSeeds", "5"));
        for (int iteration = 0; iteration < runs; iteration++) exercise(0x4a554447L + iteration);
    }
    private static void exercise(long seed) {
        Context context = ApplicationProvider.getApplicationContext();
        String file = "seed-" + seed;
        context.deleteDatabase(file);
        Random random = new Random(seed);
        StringBuilder operations = new StringBuilder("seed=").append(seed).append('\n');
        ScoreRepository repository = new ScoreRepository(ScoreDatabase.open(context, file));
        try {
            int count = 2 + random.nextInt(5);
            String direction = random.nextBoolean() ? "ASCENDING" : "DESCENDING";
            List<PlayerSpec> names = new ArrayList<>();
            for (int i = 0; i < count; i++) names.add(new PlayerSpec("Seed player " + i));
            long draft = repository.saveDraft(new DraftSpec(0, names, direction, "FIXED", null));
            repository.startGame(draft);
            operations.append("start ").append(count).append(' ').append(direction).append('\n');
            Map<Long, Long> expected = new LinkedHashMap<>();
            for (Records.GamePlayer player : repository.snapshot().players) expected.put(player.id, 0L);
            while (repository.snapshot().round != null) {
                Snapshot state = repository.snapshot();
                if (random.nextInt(5) == 0) {
                    long old = state.round.id;
                    repository.submitBid(old, state.roundPlayers.get(0).playerId, 0);
                    long replacement = repository.restartRound(old);
                    operations.append("restart ").append(old).append(" -> ").append(replacement).append('\n');
                    assertNotEquals(old, replacement);
                    try { repository.submitBid(old, state.roundPlayers.get(1).playerId, 0); fail("Stale bid accepted"); }
                    catch (IllegalArgumentException expectedFailure) { /* The old callback cannot alter replacement. */ }
                    state = repository.snapshot();
                }
                int[] bids = new int[count];
                int bidTotal = 0;
                for (int i = 0; i < count; i++) {
                    int bid = random.nextInt(state.round.cards + 1);
                    if (i == count - 1 && bidTotal + bid == state.round.cards) bid = (bid + 1) % (state.round.cards + 1);
                    bids[i] = bid; bidTotal += bid;
                    repository.submitBid(state.round.id, state.roundPlayers.get(i).playerId, bid);
                }
                repository.recordBids(state.round.id);
                int remaining = state.round.cards;
                for (int i = 0; i < count; i++) {
                    int tricks = i == count - 1 ? remaining : random.nextInt(remaining + 1);
                    remaining -= tricks;
                    Outcome result = tricks == bids[i] ? Outcome.WON : Outcome.LOST;
                    long id = state.roundPlayers.get(i).playerId;
                    repository.setOutcome(state.round.id, id, result);
                    expected.put(id, expected.get(id) + (result == Outcome.WON ? 10L + bids[i] : 0));
                    operations.append("round ").append(state.round.number).append(" player ").append(id)
                            .append(" bid ").append(bids[i]).append(" tricks ").append(tricks).append('\n');
                }
                repository.completeRound(state.round.id);
                repository.completeRound(state.round.id);
                for (Map.Entry<Long, Long> score : expected.entrySet()) assertEquals(score.getValue().longValue(), repository.snapshot().total(score.getKey()));
                if (random.nextInt(4) == 0) {
                    operations.append("close/reopen\n");
                    repository.close(); repository = new ScoreRepository(ScoreDatabase.open(context, file));
                    for (Map.Entry<Long, Long> score : expected.entrySet()) assertEquals(score.getValue().longValue(), repository.snapshot().total(score.getKey()));
                }
            }
            operations.append("completed close/reopen\n");
            repository.close(); repository = new ScoreRepository(ScoreDatabase.open(context, file));
            assertEquals("COMPLETE", repository.snapshot().game.status);
            assertEquals(52 / count, repository.snapshot().history.size());
            for (Map.Entry<Long, Long> score : expected.entrySet()) assertEquals(score.getValue().longValue(), repository.snapshot().total(score.getKey()));
        } catch (Throwable error) {
            throw new AssertionError(operations.toString(), error);
        } finally { repository.close(); context.deleteDatabase(file); }
    }
}
