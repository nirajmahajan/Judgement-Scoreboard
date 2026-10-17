package android.example.judgement.data;

import static org.junit.Assert.*;

import android.content.Context;
import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** File-backed tests of strategy snapshots, deferred changes, and audited historical corrections. */
@RunWith(AndroidJUnit4.class)
public class ScoringHistoryRepositoryTest {
    private Context context;
    private String name;
    private ScoreDatabase database;
    private ScoreRepository repository;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        name = "strategies-" + System.nanoTime();
        open();
    }
    @After public void tearDown() {
        if (repository != null) repository.close();
        context.deleteDatabase(name);
    }
    private void open() { database = ScoreDatabase.open(context, name); repository = new ScoreRepository(database); }
    private void reopen() { repository.close(); open(); }

    @Test public void partialDraftStrategyPersistsAndUnrelatedSetupSavesDoNotResetIt() {
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("First")), "ASCENDING", "FIXED", null));
        ScoringStrategy custom = ScoringStrategy.custom(13, 19, 3, true);
        repository.updateDraftScoring(draft, custom);
        repository.addPlayerToDraft(draft, "Second", false);
        Snapshot saved = repository.snapshot();
        List<PlayerSpec> same = new ArrayList<>();
        for (Records.GamePlayer player : saved.draftPlayers) same.add(new PlayerSpec(player.id, player.name));
        repository.saveDraft(new DraftSpec(draft, same, "DESCENDING", "CUSTOM", saved.draftPlayers.get(0).id));
        reopen();
        assertEquals(custom.encode(), repository.snapshot().draft.scoringConfig);
        repository.startGame(draft);
        assertEquals(custom.encode(), repository.snapshot().round.scoringConfig);
        assertEquals(26, repository.snapshot().round.cards);
        rejects(() -> repository.updateDraftScoring(draft, ScoringStrategy.defaults()));
    }

    @Test public void firstBidSchedulesStrategyAndRestartDoesNotConsumePendingChoice() {
        start(2, ScoringStrategy.defaults());
        Snapshot original = repository.snapshot();
        repository.changeScoringStrategy(original.round.id, ScoringStrategy.aggressive());
        assertEquals(ScoringStrategy.aggressive().encode(), repository.snapshot().round.scoringConfig);
        assertNull(repository.snapshot().game.pendingScoringConfig);
        repository.changeScoringStrategy(original.round.id, ScoringStrategy.defaults());
        repository.submitBid(original.round.id, original.roundPlayers.get(0).playerId, 0);
        repository.changeScoringStrategy(original.round.id, ScoringStrategy.aggressive());
        reopen();
        Snapshot pending = repository.snapshot();
        assertEquals(Records.DEFAULT_SCORING, pending.round.scoringConfig);
        assertEquals(ScoringStrategy.aggressive().encode(), pending.game.pendingScoringConfig);
        long restarted = repository.restartRound(pending.round.id);
        assertNotEquals(pending.round.id, restarted);
        assertEquals(pending.round.number, repository.snapshot().round.number);
        assertEquals(pending.round.cards, repository.snapshot().round.cards);
        assertEquals(Records.DEFAULT_SCORING, repository.snapshot().round.scoringConfig);
        assertEquals(ScoringStrategy.aggressive().encode(), repository.snapshot().game.pendingScoringConfig);
        rejects(() -> repository.changeScoringStrategy(pending.round.id, ScoringStrategy.defaults()));
        completeZeroRound();
        Snapshot next = repository.snapshot();
        assertEquals(ScoringStrategy.aggressive().encode(), next.round.scoringConfig);
        assertEquals(ScoringStrategy.aggressive().encode(), next.game.scoringConfig);
        assertNull(next.game.pendingScoringConfig);
        assertEquals(Records.DEFAULT_SCORING, next.history.get(0).round.scoringConfig);
        assertEquals(10, next.total(original.roundPlayers.get(1).playerId));
    }

    @Test public void structuralRestartPreservesStrategyAndPendingUntilNewOrdinal() {
        start(2, ScoringStrategy.aggressive());
        Snapshot before = repository.snapshot();
        repository.submitBid(before.round.id, before.roundPlayers.get(0).playerId, 0);
        ScoringStrategy custom = ScoringStrategy.custom(20, 30, 2, false);
        repository.changeScoringStrategy(before.round.id, custom);
        List<PlayerSpec> roster = new ArrayList<>();
        for (Records.GamePlayer player : before.players) roster.add(new PlayerSpec(player.id, player.name));
        roster.add(new PlayerSpec("Third"));
        repository.applyRoster(before.round.id, roster, before.round.dealerId, true);
        Snapshot restarted = repository.snapshot();
        assertEquals(before.round.number, restarted.round.number);
        assertEquals(ScoringStrategy.aggressive().encode(), restarted.round.scoringConfig);
        assertEquals(custom.encode(), restarted.game.pendingScoringConfig);
        completeZeroRound();
        assertEquals(custom.encode(), repository.snapshot().round.scoringConfig);
        assertEquals(ScoringStrategy.aggressive().encode(), repository.snapshot().history.get(0).round.scoringConfig);
    }

    @Test public void choosingCurrentStrategyCancelsPendingChangeWithoutRewritingRound() {
        start(2, ScoringStrategy.defaults());
        Snapshot before = repository.snapshot();
        repository.submitBid(before.round.id, before.roundPlayers.get(0).playerId, 0);
        repository.changeScoringStrategy(before.round.id, ScoringStrategy.aggressive());
        repository.changeScoringStrategy(before.round.id, ScoringStrategy.defaults());
        assertNull(repository.snapshot().game.pendingScoringConfig);
        assertEquals(Integer.valueOf(0), repository.snapshot().roundPlayers.get(0).bid);
        completeZeroRound();
        assertEquals(Records.DEFAULT_SCORING, repository.snapshot().round.scoringConfig);
    }

    @Test public void finalRoundRetainsUnappliedRequestAndPlayAgainUsesActiveStrategy() {
        start(52, ScoringStrategy.aggressive());
        Snapshot first = repository.snapshot();
        repository.submitBid(first.round.id, first.roundPlayers.get(0).playerId, 0);
        repository.changeScoringStrategy(first.round.id, ScoringStrategy.custom(30, 40, 2, false));
        completeZeroRound();
        Snapshot finished = repository.snapshot();
        assertEquals("COMPLETE", finished.game.status);
        assertNull(finished.round);
        assertNotNull(finished.game.pendingScoringConfig);
        assertEquals(ScoringStrategy.aggressive().encode(), finished.game.scoringConfig);
        long draftId = repository.playAgain(finished.game.id);
        Snapshot replay = repository.snapshot();
        assertEquals(draftId, replay.draft.id);
        assertEquals(ScoringStrategy.aggressive().encode(), replay.draft.scoringConfig);
        assertEquals("CUSTOM", replay.draft.trumpMode);
        assertEquals("FIXED", replay.game.trumpMode);
        assertNull(replay.draft.pendingScoringConfig);
        assertEquals(finished.game.id, replay.game.id);
        assertEquals(1, replay.history.size());
        assertEquals(finished.totals, replay.totals);
    }

    @Test public void playAgainReusesOnlyActiveRosterAndExistingProfilesWithFreshIdentities() {
        long profile = repository.addSavedPlayer("Linked");
        long draftId = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec(0, "Linked", profile), new PlayerSpec("Second"), new PlayerSpec("Removed")), "ASCENDING", "CUSTOM", null, ScoringStrategy.aggressive()));
        repository.startGame(draftId); completeZeroRound();
        Snapshot before = repository.snapshot();
        List<PlayerSpec> roster = Arrays.asList(new PlayerSpec(before.players.get(1).id, "Second renamed"), new PlayerSpec(before.players.get(0).id, "Linked", profile));
        repository.applyRoster(before.round.id, roster, before.players.get(1).id, true);
        repository.removeSavedPlayer(profile);
        Snapshot active = repository.snapshot();
        long replayId = repository.playAgain(active.game.id);
        reopen();
        Snapshot replay = repository.snapshot();
        assertEquals(active.game.id, replay.game.id); assertEquals(active.totals, replay.totals);
        assertEquals(2, replay.draftPlayers.size()); assertEquals("Second renamed", replay.draftPlayers.get(0).name);
        assertEquals("Linked", replay.draftPlayers.get(1).name);
        assertNull(replay.draftPlayers.get(1).savedPlayerId);
        assertNull("Play again requires a fresh dealer choice", replay.draft.dealerId);
        for (Records.GamePlayer player : replay.draftPlayers) for (Records.GamePlayer old : active.players) assertNotEquals(old.id, player.id);
        assertTrue(replay.savedPlayers.isEmpty());
        repository.chooseDraftDealer(replayId, replay.draftPlayers.get(0).id);
        repository.startGame(replayId);
        assertEquals(1, repository.snapshot().round.number);
        assertTrue(repository.snapshot().history.isEmpty());
        for (long total : repository.snapshot().totals.values()) assertEquals(0, total);
        rejects(() -> repository.playAgain(active.game.id));
    }

    @Test public void historicalPreviewIsReadOnlyAndSaveUsesOriginalStrategyWithImmutableAudit() throws Exception {
        start(2, ScoringStrategy.aggressive()); completeZeroRound();
        Snapshot before = repository.snapshot();
        Snapshot.RoundHistory historical = before.history.get(0);
        long first = historical.players.get(0).playerId, second = historical.players.get(1).playerId;
        repository.correctScore(second, 7, 12);
        repository.changeScoringStrategy(before.round.id, ScoringStrategy.defaults());
        repository.submitBid(before.round.id, before.roundPlayers.get(0).playerId, 0);
        Snapshot current = repository.snapshot();
        List<RoundPlayerEdit> edits = swapped(historical);
        RoundEditPreview preview = repository.previewRoundEdit(historical.round.id, 0, edits);
        assertTrue(preview.changed); assertEquals(7, preview.players.get(0).afterPoints);
        assertEquals(7, preview.totals.get(first).longValue()); assertEquals(5, preview.totals.get(second).longValue());
        assertEquals(0, repository.snapshot().history.get(0).round.revision);
        assertTrue(repository.snapshot().roundEdits.isEmpty());
        repository.editRound(historical.round.id, 0, edits);
        Snapshot after = repository.snapshot();
        assertEquals(preview.totals, after.totals);
        assertEquals(current.round.id, after.round.id); assertEquals(current.round.cards, after.round.cards);
        assertEquals(current.round.dealerId, after.round.dealerId); assertEquals(current.roundPlayers.get(0).bid, after.roundPlayers.get(0).bid);
        assertEquals(1, after.history.get(0).round.revision);
        assertEquals(ScoringStrategy.aggressive().encode(), after.history.get(0).round.scoringConfig);
        assertEquals(1, after.adjustments.size()); assertEquals(5, after.adjustments.get(0).delta);
        assertEquals(1, after.roundEdits.size());
        Records.RoundEdit audit = after.roundEdits.get(0);
        assertEquals(0, audit.previousRevision); assertEquals(1, audit.revision); assertTrue(audit.createdAt > 0);
        assertEquals("LOST", new JSONArray(audit.previousValues).getJSONObject(0).getString("outcome"));
        assertEquals(7, new JSONArray(audit.newValues).getJSONObject(0).getLong("points"));
        String recorded = audit.newValues;
        repository.renamePlayer(first, "Renamed after correction"); reopen();
        assertEquals(recorded, repository.snapshot().roundEdits.get(0).newValues);
        rejects(() -> repository.editRound(historical.round.id, 0, edits));
        GameStatistics report = GameStatistics.from(repository.snapshot());
        assertEquals(7, report.player(first).total); assertEquals(5, report.player(second).total);
    }

    @Test public void invalidHistoricalBidsAndOutcomesLeaveEveryRecordUnchanged() {
        start(2, ScoringStrategy.defaults()); completeZeroRound();
        Snapshot.RoundHistory history = repository.snapshot().history.get(0);
        long first = history.players.get(0).playerId, second = history.players.get(1).playerId;
        rejects(() -> repository.editRound(history.round.id, 0, Arrays.asList(new RoundPlayerEdit(first, 1, Outcome.WON), new RoundPlayerEdit(second, 0, Outcome.WON))));
        try {
            repository.editRound(history.round.id, 0, Arrays.asList(new RoundPlayerEdit(first, 0, Outcome.WON), new RoundPlayerEdit(second, 0, Outcome.WON)));
            fail("Impossible historical results must be rejected");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("sum to 0 hands"));
            assertTrue(error.getMessage().contains("this round has 1 hand"));
            assertFalse(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("trick"));
        }
        rejects(() -> repository.editRound(history.round.id, 0, Arrays.asList(new RoundPlayerEdit(first, 0, Outcome.LOST), new RoundPlayerEdit(first, 0, Outcome.WON))));
        rejects(() -> repository.editRound(history.round.id, 0, Arrays.asList(new RoundPlayerEdit(first, -1, Outcome.LOST), new RoundPlayerEdit(second, 0, Outcome.WON))));
        rejects(() -> repository.editRound(history.round.id, 0, Arrays.asList(new RoundPlayerEdit(first, 0, null), new RoundPlayerEdit(second, 0, Outcome.WON))));
        rejects(() -> repository.editRound(repository.snapshot().round.id, 0, swapped(history)));
        Snapshot after = repository.snapshot();
        assertEquals(0, after.history.get(0).round.revision); assertTrue(after.roundEdits.isEmpty());
        assertEquals(0, after.total(first)); assertEquals(10, after.total(second));
    }

    @Test public void negativeAndOverflowRecalculatedTotalsRejectAtomically() {
        start(2, ScoringStrategy.defaults()); completeZeroRound();
        Snapshot.RoundHistory history = repository.snapshot().history.get(0);
        long first = history.players.get(0).playerId, second = history.players.get(1).playerId;
        repository.correctScore(second, 10, 0);
        rejects(() -> repository.previewRoundEdit(history.round.id, 0, swapped(history)));
        rejects(() -> repository.editRound(history.round.id, 0, swapped(history)));
        assertEquals(0, repository.snapshot().total(second));
        repository.correctScore(second, 0, 10);
        repository.correctScore(first, 0, Long.MAX_VALUE);
        rejects(() -> repository.editRound(history.round.id, 0, swapped(history)));
        assertEquals(Long.MAX_VALUE, repository.snapshot().total(first));
        assertEquals(0, repository.snapshot().history.get(0).round.revision);
        assertTrue(repository.snapshot().roundEdits.isEmpty());
    }

    @Test public void auditInsertFailureRollsBackCorrectedRowsRevisionAndTotals() {
        start(2, ScoringStrategy.defaults()); completeZeroRound();
        Snapshot before = repository.snapshot(); Snapshot.RoundHistory history = before.history.get(0);
        database.getOpenHelper().getWritableDatabase().execSQL("CREATE TRIGGER refuse_audit BEFORE INSERT ON round_edits BEGIN SELECT RAISE(ABORT,'audit unavailable'); END");
        try { repository.editRound(history.round.id, 0, swapped(history)); fail("Expected SQLite failure"); }
        catch (android.database.sqlite.SQLiteException expected) { assertTrue(expected.getMessage().contains("audit unavailable")); }
        Snapshot after = repository.snapshot();
        assertEquals(before.totals, after.totals); assertEquals(0, after.history.get(0).round.revision);
        assertEquals(history.players.get(0).outcome, after.history.get(0).players.get(0).outcome);
        assertTrue(after.roundEdits.isEmpty());
        database.getOpenHelper().getWritableDatabase().execSQL("DROP TRIGGER refuse_audit");
        repository.editRound(history.round.id, 0, swapped(history));
        assertEquals(1, repository.snapshot().roundEdits.size());
    }

    @Test public void concurrentHistoricalCorrectionsAcceptExactlyOneExpectedRevision() throws Exception {
        start(2, ScoringStrategy.defaults()); completeZeroRound();
        Snapshot.RoundHistory history = repository.snapshot().history.get(0);
        List<RoundPlayerEdit> first = swapped(history);
        List<RoundPlayerEdit> second = Arrays.asList(new RoundPlayerEdit(history.players.get(0).playerId, 1, Outcome.LOST), new RoundPlayerEdit(history.players.get(1).playerId, 1, Outcome.WON));
        ExecutorService pool = Executors.newFixedThreadPool(2); CountDownLatch begin = new CountDownLatch(1);
        AtomicInteger saved = new AtomicInteger(), stale = new AtomicInteger();
        try {
            List<Future<?>> tasks = new ArrayList<>();
            for (List<RoundPlayerEdit> edits : Arrays.asList(first, second)) tasks.add(pool.submit(() -> {
                begin.await();
                try { repository.editRound(history.round.id, 0, edits); saved.incrementAndGet(); }
                catch (IllegalArgumentException error) { assertTrue(error.getMessage().contains("was edited")); stale.incrementAndGet(); }
                return null;
            }));
            begin.countDown(); for (Future<?> task : tasks) task.get(30, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertEquals(1, saved.get()); assertEquals(1, stale.get());
        assertEquals(1, repository.snapshot().roundEdits.size()); assertEquals(1, repository.snapshot().history.get(0).round.revision);
    }

    @Test public void unchangedHistoricalSaveHasNoAuditAndSignedGraphPrefixesRemainExact() {
        start(2, ScoringStrategy.defaults()); completeZeroRound();
        Snapshot.RoundHistory first = repository.snapshot().history.get(0);
        List<RoundPlayerEdit> unchanged = new ArrayList<>();
        for (Records.RoundPlayer row : first.players) unchanged.add(new RoundPlayerEdit(row.playerId, row.bid, Outcome.valueOf(row.outcome)));
        assertFalse(repository.previewRoundEdit(first.round.id, 0, unchanged).changed);
        repository.editRound(first.round.id, 0, unchanged);
        assertTrue(repository.snapshot().roundEdits.isEmpty());
        long player = first.players.get(1).playerId;
        repository.correctScore(player, 10, 0);
        completeRoundWithLostPlayer(first.players.get(0).playerId);
        database.getOpenHelper().getWritableDatabase().execSQL("UPDATE rounds SET completedAt=number*100 WHERE phase='COMPLETE'");
        database.getOpenHelper().getWritableDatabase().execSQL("UPDATE score_adjustments SET createdAt=150");
        repository.editRound(first.round.id, 0, swapped(first));
        GameStatistics statistics = GameStatistics.from(repository.snapshot());
        assertEquals(0, statistics.player(player).total);
        assertTrue(statistics.timeline.stream().anyMatch(event -> event.cumulative.get(player).signum() < 0));
    }

    private void start(int count, ScoringStrategy strategy) {
        List<PlayerSpec> players = new ArrayList<>(); for (int index = 0; index < count; index++) players.add(new PlayerSpec("Player " + index));
        long draft = repository.saveDraft(new DraftSpec(0, players, "ASCENDING", "FIXED", null, strategy));
        repository.startGame(draft);
    }
    private void completeZeroRound() { completeRoundWithLostPlayer(repository.snapshot().roundPlayers.get(0).playerId); }
    private void completeRoundWithLostPlayer(long lostId) {
        Snapshot current = repository.snapshot();
        for (Records.RoundPlayer row : current.roundPlayers) if (row.bid == null) repository.submitBid(current.round.id, row.playerId, 0);
        repository.recordBids(current.round.id);
        for (Records.RoundPlayer row : current.roundPlayers) repository.setOutcome(current.round.id, row.playerId, row.playerId == lostId ? Outcome.LOST : Outcome.WON);
        repository.completeRound(current.round.id);
    }
    private static List<RoundPlayerEdit> swapped(Snapshot.RoundHistory history) {
        List<RoundPlayerEdit> edits = new ArrayList<>();
        for (Records.RoundPlayer row : history.players) edits.add(new RoundPlayerEdit(row.playerId, row.bid, "WON".equals(row.outcome) ? Outcome.LOST : Outcome.WON));
        return edits;
    }
    private static void rejects(Runnable command) {
        try { command.run(); fail("Expected rejected command"); }
        catch (IllegalArgumentException | ArithmeticException expected) { assertNotNull(expected.getMessage()); }
    }
}
