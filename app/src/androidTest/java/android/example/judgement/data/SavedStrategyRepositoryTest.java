package android.example.judgement.data;

import static org.junit.Assert.*;

import android.content.Context;
import android.example.judgement.domain.Names;
import android.example.judgement.domain.ScoringStrategy;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SavedStrategyRepositoryTest {
    private Context context;
    private String databaseName;
    private ScoreRepository repository;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        databaseName = "saved-strategies-" + System.nanoTime();
        open();
    }
    private void open() { repository = new ScoreRepository(ScoreDatabase.open(context, databaseName)); }
    private void reopen() { repository.close(); open(); }
    @After public void tearDown() { repository.close(); context.deleteDatabase(databaseName); }

    @Test public void namedCatalogPersistsAndRenameOrDeleteKeepsCurrentAndCompletedRules() {
        ScoringStrategy rules = ScoringStrategy.custom(4, 12, 2, true);
        long id = repository.saveCustomStrategy(0, "  Café | Club 🌟  ", rules);
        reopen();
        Records.SavedStrategy entry = repository.snapshot().savedStrategies.get(0);
        assertEquals(id, entry.id);
        assertEquals("Café | Club 🌟", entry.name);
        assertEquals(Names.key(entry.name), entry.nameKey);
        ScoringStrategy named = ScoringStrategy.decode(entry.scoringConfig);
        assertEquals(entry.name, named.name);
        long draft = draft();
        repository.updateDraftScoring(draft, named);
        repository.startGame(draft);
        Snapshot first = repository.snapshot();
        for (Records.RoundPlayer player : first.roundPlayers) repository.submitBid(first.round.id, player.playerId, 0);
        repository.recordBids(first.round.id);
        repository.setOutcome(first.round.id, first.roundPlayers.get(0).playerId,
                android.example.judgement.domain.Outcome.LOST);
        repository.setOutcome(first.round.id, first.roundPlayers.get(1).playerId,
                android.example.judgement.domain.Outcome.WON);
        repository.completeRound(first.round.id);
        Snapshot completed = repository.snapshot();
        long total = completed.total(first.roundPlayers.get(1).playerId);
        String roundConfig = completed.round.scoringConfig;
        repository.saveCustomStrategy(id, "Evening", ScoringStrategy.custom(1, 2, 3, false));
        repository.deleteCustomStrategy(id);
        reopen();
        Snapshot after = repository.snapshot();
        assertTrue(after.savedStrategies.isEmpty());
        assertEquals(roundConfig, after.round.scoringConfig);
        assertEquals(named.encode(), after.history.get(0).round.scoringConfig);
        assertEquals(named.name, ScoringStrategy.decode(after.game.scoringConfig).name);
        assertEquals(total, after.total(first.roundPlayers.get(1).playerId));
    }

    @Test public void duplicateNamesReservedBuiltinsAndStaleTargetsRollbackTheWholeSave() {
        ScoringStrategy rules = ScoringStrategy.custom(1, 2, 3, false);
        long first = repository.saveCustomStrategy(0, "Café", rules);
        rejects(() -> repository.saveCustomStrategy(0, " CAFE\u0301 ", rules));
        rejects(() -> repository.saveCustomStrategy(0, "Default", rules));
        rejects(() -> repository.saveCustomStrategy(0, "Aggressive", rules));
        rejects(() -> repository.saveCustomStrategy(first, "Other", ScoringStrategy.defaults()));
        rejects(() -> repository.saveCustomStrategy(first + 99, "Missing", rules));
        rejects(() -> repository.deleteCustomStrategy(0));
        long draft = draft();
        String before = repository.snapshot().draft.scoringConfig;
        rejects(() -> repository.saveCustomStrategyForTarget(0, "CAFÉ", rules, draft, null));
        assertEquals(before, repository.snapshot().draft.scoringConfig);
        assertEquals(1, repository.snapshot().savedStrategies.size());
        rejects(() -> repository.saveCustomStrategyForTarget(0, "Stale round", rules, draft, Long.MAX_VALUE));
        assertEquals(1, repository.snapshot().savedStrategies.size());
        assertEquals("Café", repository.snapshot().savedStrategies.get(0).name);
    }

    @Test public void saveAndApplyUsesOneSnapshotAndDefersUpdatesAfterPredictionsStart() {
        long draft = draft();
        ScoringStrategy rules = ScoringStrategy.custom(3, 4, 2, false);
        long id = repository.saveCustomStrategyForTarget(0, "Table", rules, draft, null);
        ScoringStrategy selected = rules.withName("Table");
        assertEquals(selected.encode(), repository.snapshot().draft.scoringConfig);
        repository.startGame(draft);
        Snapshot first = repository.snapshot();
        repository.submitBid(first.round.id, first.roundPlayers.get(0).playerId, 0);
        repository.saveCustomStrategyForTarget(id, "Table revised", ScoringStrategy.custom(4, 5, 3, true),
                0, first.round.id);
        reopen();
        Snapshot after = repository.snapshot();
        assertEquals(selected.encode(), after.round.scoringConfig);
        assertEquals(selected.encode(), after.game.scoringConfig);
        assertEquals("Table revised", ScoringStrategy.decode(after.game.pendingScoringConfig).name);
        assertEquals(after.savedStrategies.get(0).scoringConfig, after.game.pendingScoringConfig);
    }

    private long draft() {
        return repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("Alpha"),
                new PlayerSpec("Beta")), "ASCENDING", "CUSTOM", null));
    }
    private static void rejects(Runnable command) {
        try { command.run(); fail("Expected rejection"); }
        catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
    }
}
