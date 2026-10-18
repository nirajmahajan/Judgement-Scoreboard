package android.example.judgement.data;

import static org.junit.Assert.*;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.room.Room;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.example.judgement.domain.Outcome;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Real, file-backed SQLite regressions; exercised on both oldest and newest supported CI APIs. */
@RunWith(AndroidJUnit4.class)
public class RepositoryRegressionTest {
    private Context context;
    private String name;
    private ScoreRepository repository;

    @Before public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        name = "regression-" + System.nanoTime();
        repository = new ScoreRepository(ScoreDatabase.open(context, name));
    }
    @After public void tearDown() {
        if (repository != null) repository.close();
        context.deleteDatabase(name);
    }

    @Test public void partialDraftAndActiveRoundSurviveCloseReopenIndependently() {
        start(8);
        Snapshot active = repository.snapshot();
        repository.submitBid(active.round.id, active.roundPlayers.get(0).playerId, 0);
        long draftId = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("Next table")), "DESCENDING", "CUSTOM", null));
        reopen();
        Snapshot restored = repository.snapshot();
        assertEquals(draftId, restored.draft.id);
        assertEquals("Next table", restored.draftPlayers.get(0).name);
        assertEquals("DESCENDING", restored.draft.direction);
        assertEquals("CUSTOM", restored.draft.trumpMode);
        assertEquals(active.game.id, restored.game.id);
        assertEquals(8, restored.players.size());
        assertEquals(Integer.valueOf(0), restored.roundPlayers.get(0).bid);
        assertNull(restored.roundPlayers.get(1).bid);
    }

    @Test public void eightOffscreenResultsPersistAndCompletionDoesNotDependOnViews() {
        start(8);
        fillBidsAndResults();
        reopen();
        Snapshot snapshot = repository.snapshot();
        for (Records.RoundPlayer row : snapshot.roundPlayers) assertNotNull(row.outcome);
        repository.completeRound(snapshot.round.id);
        Snapshot finished = repository.snapshot();
        assertEquals(1, finished.history.size());
        assertEquals(2, finished.round.cards);
        assertEquals(0, finished.total(snapshot.roundPlayers.get(0).playerId));
        assertEquals(10, finished.total(snapshot.roundPlayers.get(7).playerId));
    }

    @Test public void impossibleAllZeroAllLostResultsRejectWithoutAdvancingAnything() {
        start(3);
        Snapshot before = repository.snapshot();
        for (Records.RoundPlayer row : before.roundPlayers) repository.submitBid(before.round.id, row.playerId, 0);
        repository.recordBids(before.round.id);
        for (Records.RoundPlayer row : before.roundPlayers) repository.setOutcome(before.round.id, row.playerId, Outcome.LOST);
        try {
            repository.completeRound(before.round.id);
            fail("Impossible results must be rejected");
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("predicted 0"));
            assertTrue(error.getMessage().contains("1 hand remains"));
            for (Records.RoundPlayer row : before.roundPlayers) assertTrue(error.getMessage().contains(row.nameSnapshot));
            assertFalse(error.getMessage().toLowerCase(java.util.Locale.ROOT).contains("trick"));
        }
        Snapshot after = repository.snapshot();
        assertEquals(before.round.id, after.round.id);
        assertEquals(before.round.dealerId, after.round.dealerId);
        assertTrue(after.history.isEmpty());
        for (long total : after.totals.values()) assertEquals(0, total);
    }

    @Test public void concurrentRepeatedCompletionHasExactlyOneEffect() throws Exception {
        start(3); fillBidsAndResults();
        Snapshot before = repository.snapshot();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> tasks = new ArrayList<>();
        for (int index = 0; index < 8; index++) tasks.add(pool.submit(() -> { start.await(); repository.completeRound(before.round.id); return null; }));
        start.countDown();
        try { for (Future<?> task : tasks) task.get(30, TimeUnit.SECONDS); }
        finally { pool.shutdownNow(); }
        Snapshot after = repository.snapshot();
        assertEquals(1, after.history.size());
        assertEquals(2, after.round.number);
        assertEquals(before.roundPlayers.get(0).playerId, after.round.dealerId);
        assertEquals(10, after.total(before.roundPlayers.get(1).playerId));
    }

    @Test public void scoreOverflowRollsBackWholeCompletionAndExpectedTotalRejectsStaleEdits() {
        start(2); fillBidsAndResults();
        Snapshot before = repository.snapshot();
        long winner = before.roundPlayers.get(1).playerId;
        repository.correctScore(winner, 0, Long.MAX_VALUE);
        rejects(() -> repository.correctScore(winner, 0, 3));
        rejects(() -> repository.completeRound(before.round.id));
        Snapshot after = repository.snapshot();
        assertEquals(Long.MAX_VALUE, after.total(winner));
        assertEquals(before.round.id, after.round.id);
        assertEquals("RESULTS", after.round.phase);
        assertTrue(after.history.isEmpty());
        repository.correctScore(winner, Long.MAX_VALUE, 0);
        repository.completeRound(before.round.id);
        assertEquals(10, repository.snapshot().total(winner));
    }

    @Test public void latestBidOnlyDealerLastAndForbiddenTotalAreEnforcedInRepository() {
        start(3);
        Snapshot s = repository.snapshot();
        long first = s.roundPlayers.get(0).playerId, second = s.roundPlayers.get(1).playerId, dealer = s.round.dealerId;
        rejects(() -> repository.submitBid(s.round.id, dealer, 0));
        repository.submitBid(s.round.id, first, 0);
        repository.submitBid(s.round.id, first, 1);
        repository.submitBid(s.round.id, first, 0);
        repository.submitBid(s.round.id, second, 0);
        rejects(() -> repository.submitBid(s.round.id, first, 1));
        rejects(() -> repository.submitBid(s.round.id, dealer, 1));
        repository.submitBid(s.round.id, dealer, 0);
        repository.recordBids(s.round.id);
        rejects(() -> repository.submitBid(s.round.id, dealer, 0));
    }

    @Test public void restartChangesIdentityAndRejectsAllOldRoundCallbacks() {
        start(3);
        Snapshot before = repository.snapshot();
        repository.submitBid(before.round.id, before.roundPlayers.get(0).playerId, 0);
        long replacement = repository.restartRound(before.round.id);
        assertNotEquals(before.round.id, replacement);
        rejects(() -> repository.submitBid(before.round.id, before.roundPlayers.get(1).playerId, 0));
        rejects(() -> repository.setOutcome(before.round.id, before.roundPlayers.get(0).playerId, Outcome.WON));
        rejects(() -> repository.completeRound(before.round.id));
        Snapshot after = repository.snapshot();
        assertEquals(before.round.number, after.round.number);
        assertEquals(before.round.cards, after.round.cards);
        assertEquals(before.round.dealerId, after.round.dealerId);
        for (Records.RoundPlayer row : after.roundPlayers) { assertNull(row.bid); assertNull(row.outcome); }
    }

    @Test public void wildcardNamesRenameByIdAndCompletedSnapshotsRetainOldNames() {
        start(2); fillBidsAndResults();
        Snapshot before = repository.snapshot();
        long first = before.players.get(0).id;
        repository.completeRound(before.round.id);
        repository.renamePlayer(first, "A renamed person with a very long full name % _ e\u0301");
        Snapshot after = repository.snapshot();
        assertEquals(before.players.get(0).id, after.players.get(0).id);
        assertEquals(before.players.get(1).name, after.players.get(1).name);
        assertEquals(before.roundPlayers.get(0).nameSnapshot, after.history.get(0).players.get(0).nameSnapshot);
        assertEquals(before.roundPlayers.get(0).outcome, after.history.get(0).players.get(0).outcome);
    }

    @Test public void prospectiveRosterIsAtomicAndConfirmedChangesPreserveDealerAndScores() {
        start(3);
        Snapshot before = repository.snapshot();
        repository.submitBid(before.round.id, before.roundPlayers.get(0).playerId, 0);
        List<PlayerSpec> reordered = specs(before.players);
        java.util.Collections.swap(reordered, 0, 1);
        rejects(() -> repository.applyRoster(before.round.id, reordered, before.round.dealerId, false));
        assertEquals(before.players.get(0).id, repository.snapshot().players.get(0).id);
        repository.applyRoster(before.round.id, reordered, before.round.dealerId, true);
        Snapshot after = repository.snapshot();
        assertNotEquals(before.round.id, after.round.id);
        assertEquals(before.round.dealerId, after.round.dealerId);
        assertEquals(before.players.get(1).id, after.players.get(0).id);
        List<PlayerSpec> duplicate = specs(after.players);
        duplicate.set(1, new PlayerSpec(duplicate.get(1).id, duplicate.get(0).name));
        rejects(() -> repository.applyRoster(after.round.id, duplicate, after.round.dealerId, true));
        assertEquals(after.round.id, repository.snapshot().round.id);
        rejects(() -> repository.applyRoster(after.round.id, Arrays.asList(duplicate.get(0)), 0, true));
        repository.applyRoster(after.round.id, Arrays.asList(new PlayerSpec("Fresh one"), new PlayerSpec("Fresh two")), 0, true);
        Snapshot replacement = repository.snapshot();
        assertEquals(2, replacement.players.size()); assertEquals(3, replacement.inactivePlayers.size());
        assertEquals(replacement.players.get(0).id, replacement.round.dealerId);
        assertEquals("Fresh one", replacement.players.get(0).name);
    }

    @Test public void descendingRosterImmediatelyStepsDownAndRemovingPlayersNeverRaisesCurrentDeal() {
        List<PlayerSpec> initial = new ArrayList<>();
        for (int index = 0; index < 8; index++) initial.add(new PlayerSpec("Player " + index));
        long draft = repository.saveDraft(new DraftSpec(0, initial, "DESCENDING", "FIXED", null));
        repository.startGame(draft);
        Snapshot before = repository.snapshot();
        repository.submitBid(before.round.id, before.roundPlayers.get(0).playerId, 0);
        List<PlayerSpec> more = specs(before.players); more.add(new PlayerSpec("Ninth player"));
        rejects(() -> repository.applyRoster(before.round.id, more, before.round.dealerId, false));
        assertEquals(6, repository.snapshot().round.cards);
        assertEquals(8, repository.snapshot().players.size());
        repository.applyRoster(before.round.id, more, before.round.dealerId, true);
        Snapshot after = repository.snapshot();
        assertEquals(5, after.round.cards); assertNotEquals(before.round.id, after.round.id);
        assertEquals(before.round.number, after.round.number);
        assertEquals(before.round.scoringConfig, after.round.scoringConfig);
        assertEquals(before.round.dealerId, after.round.dealerId);
        assertEquals(6, after.game.maximumCards); assertEquals(9, after.players.size());
        for (Records.RoundPlayer row : after.roundPlayers) { assertNull(row.bid); assertNull(row.outcome); }
        rejects(() -> repository.submitBid(before.round.id, before.roundPlayers.get(0).playerId, 0));
        rejects(() -> repository.completeRound(before.round.id));
        reopen();
        Snapshot persisted = repository.snapshot();
        assertEquals(after.round.id, persisted.round.id); assertEquals(5, persisted.round.cards);
        List<PlayerSpec> reduced = specs(persisted.players); reduced.remove(8);
        repository.applyRoster(persisted.round.id, reduced, persisted.round.dealerId, true);
        assertEquals(5, repository.snapshot().round.cards);
        fillBidsAndResults(); repository.completeRound(repository.snapshot().round.id);
        Snapshot next = repository.snapshot();
        assertEquals(4, next.round.cards); assertEquals(2, next.round.number);
        assertEquals(6, next.game.maximumCards);
        assertEquals(1, next.history.size()); assertEquals(5, next.history.get(0).round.cards);
    }

    @Test public void ascendingRosterPastNewLimitFinishesWithoutScoringUnfinishedRoundAndSurvivesReopen() {
        start(8);
        for (int index = 0; index < 5; index++) {
            fillBidsAndResults(); repository.completeRound(repository.snapshot().round.id);
        }
        Snapshot before = repository.snapshot();
        assertEquals(6, before.round.cards); assertEquals(5, before.history.size());
        fillBidsAndResults();
        List<PlayerSpec> more = specs(before.players); more.add(new PlayerSpec(-100, "Ninth player", null, true));
        rejects(() -> repository.applyRoster(before.round.id, more, before.round.dealerId, false));
        assertEquals("ACTIVE", repository.snapshot().game.status);
        ScoreDatabase faultConnection = ScoreDatabase.open(context, name);
        SupportSQLiteDatabase sql = faultConnection.getOpenHelper().getWritableDatabase();
        sql.execSQL("CREATE TRIGGER reject_roster_finish BEFORE UPDATE ON games WHEN NEW.status='COMPLETE' BEGIN SELECT RAISE(ABORT, 'Injected finish failure'); END");
        try {
            try { repository.applyRoster(before.round.id, more, before.round.dealerId, true); fail("Expected SQL failure"); }
            catch (android.database.sqlite.SQLiteException expected) { assertTrue(expected.getMessage().contains("Injected finish")); }
            Snapshot unchanged = repository.snapshot();
            assertEquals("ACTIVE", unchanged.game.status); assertEquals(8, unchanged.players.size());
            assertEquals(before.round.id, unchanged.round.id); assertEquals("RESULTS", unchanged.round.phase);
            assertEquals(before.totals, unchanged.totals); assertTrue(unchanged.savedPlayers.isEmpty());
            assertEquals(5, unchanged.history.size());
        } finally { sql.execSQL("DROP TRIGGER reject_roster_finish"); faultConnection.close(); }
        repository.applyRoster(before.round.id, more, before.round.dealerId, true);
        Snapshot finished = repository.snapshot();
        assertEquals("COMPLETE", finished.game.status); assertNull(finished.round);
        assertEquals(9, finished.players.size()); assertEquals(6, finished.game.maximumCards);
        assertEquals(1, finished.savedPlayers.size());
        assertEquals(5, finished.history.size());
        for (Records.GamePlayer player : before.players) assertEquals(before.total(player.id), finished.total(player.id));
        assertEquals(0, finished.total(finished.players.get(8).id));
        for (int index = 0; index < before.history.size(); index++) {
            assertEquals(before.history.get(index).round.id, finished.history.get(index).round.id);
        }
        rejects(() -> repository.completeRound(before.round.id));
        rejects(() -> repository.setOutcome(before.round.id, before.roundPlayers.get(0).playerId, Outcome.WON));
        rejects(() -> repository.applyRoster(before.round.id, more, before.round.dealerId, true));
        reopen();
        Snapshot persisted = repository.snapshot();
        assertEquals("COMPLETE", persisted.game.status); assertNull(persisted.round);
        assertEquals(finished.totals, persisted.totals); assertEquals(5, persisted.history.size());
        ScoreDatabase connection = ScoreDatabase.open(context, name);
        try { assertEquals("ABANDONED", connection.dao().round(before.round.id).phase); }
        finally { connection.close(); }
    }

    @Test public void ascendingRemovalKeepsCurrentDealAndExtendsLastDealWithoutChangingStartingMaximum() {
        start(8);
        for (int index = 0; index < 5; index++) {
            fillBidsAndResults(); repository.completeRound(repository.snapshot().round.id);
        }
        Snapshot before = repository.snapshot();
        List<PlayerSpec> reduced = specs(before.players); reduced.remove(0);
        repository.applyRoster(before.round.id, reduced, 0, true);
        Snapshot changed = repository.snapshot();
        assertEquals(6, changed.round.cards); assertEquals(6, changed.game.maximumCards);
        assertEquals(before.round.number, changed.round.number); assertEquals(7, changed.players.size());
        fillBidsAndResults(); repository.completeRound(changed.round.id);
        Snapshot last = repository.snapshot();
        assertEquals(7, last.round.cards); assertEquals("ACTIVE", last.game.status);
        fillBidsAndResults(); repository.completeRound(last.round.id);
        assertEquals("COMPLETE", repository.snapshot().game.status);
        assertEquals(7, repository.snapshot().history.size());
    }

    @Test public void removingDealerAdvancesToNextSurvivorAndInsertedSeatJoinsDealerCycle() {
        start(4);
        Snapshot before = repository.snapshot();
        long removedDealer = before.round.dealerId;
        repository.correctScore(removedDealer, 0, 42);
        List<PlayerSpec> reordered = specs(before.players); reordered.remove(3);
        reordered.add(1, new PlayerSpec("Inserted after next dealer"));
        repository.applyRoster(before.round.id, reordered, removedDealer, true);
        Snapshot changed = repository.snapshot();
        long inserted = changed.players.get(1).id;
        assertEquals(before.players.get(0).id, changed.round.dealerId);
        assertEquals(removedDealer, changed.inactivePlayers.get(0).id);
        assertEquals(42, changed.total(removedDealer)); assertEquals(0, changed.total(inserted));
        assertEquals(inserted, changed.roundPlayers.get(0).playerId);
        fillBidsAndResults(); repository.completeRound(changed.round.id);
        assertEquals(inserted, repository.snapshot().round.dealerId);
    }

    @Test public void removedParticipantsRetainPointsAndExplicitRestoreUsesSameIdentity() {
        start(3); fillBidsAndResults();
        Snapshot first = repository.snapshot(); repository.completeRound(first.round.id);
        Snapshot second = repository.snapshot();
        long removed = second.players.get(1).id;
        List<PlayerSpec> reduced = specs(second.players); reduced.remove(1);
        repository.applyRoster(second.round.id, reduced, 0, true);
        Snapshot after = repository.snapshot();
        assertEquals(10, after.total(removed));
        assertEquals(removed, after.inactivePlayers.get(0).id);
        repository.restoreParticipant(after.round.id, removed, true);
        Snapshot restored = repository.snapshot();
        assertTrue(restored.inactivePlayers.isEmpty());
        assertEquals(removed, restored.players.get(2).id); assertEquals(10, restored.total(removed));
    }

    @Test public void savedProfilesAreSeparateAndSelectionRejectsNormalizedDuplicatesAtomically() {
        long profile = repository.addSavedPlayer("  E\u0301lodie\u00a0");
        try { repository.addSavedPlayer("éLODIE"); fail(); }
        catch (ScoreRepository.DuplicateSavedPlayerException error) { assertEquals(profile, error.existingId); }
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("Table only")), "ASCENDING", "FIXED", null));
        repository.addSavedPlayersToDraft(draft, Arrays.asList(profile));
        Snapshot withProfile = repository.snapshot();
        String snapshotName = withProfile.draftPlayers.get(1).name;
        repository.renameSavedPlayer(profile, "A new profile name");
        assertEquals(snapshotName, repository.snapshot().draftPlayers.get(1).name);
        repository.removeSavedPlayer(profile);
        Snapshot afterDelete = repository.snapshot();
        assertEquals(2, afterDelete.draftPlayers.size()); assertTrue(afterDelete.savedPlayers.isEmpty());
        assertNull(afterDelete.game);
    }

    @Test public void removingSetupDealerPreselectsNextSurvivingSeatAndAllowsEmptySetup() {
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("One"), new PlayerSpec("Two"), new PlayerSpec("Three")), "ASCENDING", "FIXED", null));
        Snapshot first = repository.snapshot();
        long dealer = first.draftPlayers.get(1).id;
        repository.saveDraft(new DraftSpec(draft, specs(first.draftPlayers), "ASCENDING", "FIXED", dealer));
        List<PlayerSpec> reduced = specs(first.draftPlayers); reduced.remove(1);
        repository.saveDraft(new DraftSpec(draft, reduced, "ASCENDING", "FIXED", dealer));
        Snapshot after = repository.snapshot();
        assertEquals(Long.valueOf(first.draftPlayers.get(2).id), after.draft.dealerId);
        repository.saveDraft(new DraftSpec(draft, new ArrayList<>(), "ASCENDING", "FIXED", after.draft.dealerId));
        assertNull(repository.snapshot().draft.dealerId);
    }

    @Test public void addingOptionalSavedPlayerIsAtomicAndDoesNotSaveOnRejectedDuplicate() {
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("One")), "ASCENDING", "FIXED", null));
        rejects(() -> repository.addPlayerToDraft(draft, " ONE ", true));
        assertTrue(repository.snapshot().savedPlayers.isEmpty());
        long participant = repository.addPlayerToDraft(draft, "Two", true);
        Snapshot after = repository.snapshot();
        assertEquals(2, after.draftPlayers.size()); assertEquals(1, after.savedPlayers.size());
        assertEquals(participant, after.draftPlayers.get(1).id);
        assertEquals(Long.valueOf(after.savedPlayers.get(0).id), after.draftPlayers.get(1).savedPlayerId);
    }

    @Test public void closingRepositoryDrainsQueuedSaveWithoutBlockingOrDroppingIt() throws Exception {
        CountDownLatch release = new CountDownLatch(1), saved = new CountDownLatch(1);
        ScoreRepository closing = repository;
        closing.execute(() -> {
            try { release.await(); }
            catch (InterruptedException error) { throw new AssertionError(error); }
            closing.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("Queued")), "ASCENDING", "FIXED", null));
            saved.countDown();
        });
        long before = android.os.SystemClock.uptimeMillis();
        repository.close(); repository = null;
        assertTrue(android.os.SystemClock.uptimeMillis() - before < 500);
        release.countDown(); assertTrue(saved.await(10, TimeUnit.SECONDS));
        repository = new ScoreRepository(ScoreDatabase.open(context, name));
        assertEquals("Queued", repository.snapshot().draftPlayers.get(0).name);
    }

    @Test public void renamingDuringResultsPreservesBidOutcomeScoreAndDealerIdentity() {
        start(3); fillBidsAndResults();
        Snapshot before = repository.snapshot();
        long id = before.round.dealerId;
        repository.correctScore(id, 0, 42);
        repository.renamePlayer(id, "Renamed dealer %_ with a full Unicode name العربية");
        Snapshot after = repository.snapshot();
        assertEquals(before.round.id, after.round.id);
        assertEquals(id, after.round.dealerId);
        assertEquals(42, after.total(id));
        for (int index = 0; index < before.roundPlayers.size(); index++) {
            assertEquals(before.roundPlayers.get(index).playerId, after.roundPlayers.get(index).playerId);
            assertEquals(before.roundPlayers.get(index).bid, after.roundPlayers.get(index).bid);
            assertEquals(before.roundPlayers.get(index).outcome, after.roundPlayers.get(index).outcome);
        }
        assertTrue(after.roundPlayers.get(after.roundPlayers.size() - 1).nameSnapshot.startsWith("Renamed dealer"));
    }

    @Test public void failedNextRoundInsertRollsBackScoresHistoryDealerAndPhase() {
        start(3); fillBidsAndResults();
        Snapshot before = repository.snapshot();
        ScoreDatabase faultConnection = ScoreDatabase.open(context, name);
        androidx.sqlite.db.SupportSQLiteDatabase sql = faultConnection.getOpenHelper().getWritableDatabase();
        sql.execSQL("CREATE TRIGGER reject_next_round BEFORE INSERT ON rounds BEGIN SELECT RAISE(ABORT, 'Injected next-round insert failure'); END");
        try {
            try { repository.completeRound(before.round.id); fail("Expected injected SQL failure"); }
            catch (android.database.sqlite.SQLiteException expected) { assertTrue(expected.getMessage().contains("Injected next-round")); }
            Snapshot after = repository.snapshot();
            assertEquals(before.round.id, after.round.id);
            assertEquals("RESULTS", after.round.phase);
            assertEquals(before.round.dealerId, after.game.dealerId.longValue());
            assertTrue(after.history.isEmpty());
            for (long total : after.totals.values()) assertEquals(0, total);
            for (int index = 0; index < before.roundPlayers.size(); index++) assertEquals(before.roundPlayers.get(index).outcome, after.roundPlayers.get(index).outcome);
        } finally {
            sql.execSQL("DROP TRIGGER reject_next_round");
            faultConnection.close();
        }
        repository.completeRound(before.round.id);
        assertEquals(1, repository.snapshot().history.size());
    }

    @Test public void independentConcurrentSetupChoicesMergeWithoutLosingDirectionOrTrump() throws Exception {
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("One"), new PlayerSpec("Two")), "ASCENDING", "FIXED", null));
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch begin = new CountDownLatch(1);
        try {
            Future<?> direction = pool.submit(() -> { begin.await(); repository.updateDraftSettings(draft, "DESCENDING", null); return null; });
            Future<?> trump = pool.submit(() -> { begin.await(); repository.updateDraftSettings(draft, null, "CUSTOM"); return null; });
            begin.countDown(); direction.get(10, TimeUnit.SECONDS); trump.get(10, TimeUnit.SECONDS);
            Snapshot after = repository.snapshot();
            assertEquals("DESCENDING", after.draft.direction); assertEquals("CUSTOM", after.draft.trumpMode);
            assertEquals(2, after.draftPlayers.size());
        } finally { pool.shutdownNow(); }
    }

    @Test public void staleDraftReorderCannotDropAPlayerAddedAfterGestureStarted() {
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("One"), new PlayerSpec("Two")), "ASCENDING", "FIXED", null));
        Snapshot before = repository.snapshot();
        long first = before.draftPlayers.get(0).id, dealer = before.draftPlayers.get(1).id;
        repository.chooseDraftDealer(draft, dealer);
        List<Long> stale = Arrays.asList(dealer, first);
        long added = repository.addPlayerToDraft(draft, "Added during drag", false);
        rejects(() -> repository.reorderDraft(draft, stale));
        assertEquals(3, repository.snapshot().draftPlayers.size());
        assertEquals(first, repository.snapshot().draftPlayers.get(0).id);
        repository.reorderDraft(draft, Arrays.asList(added, dealer, first));
        Snapshot reordered = repository.snapshot();
        assertEquals(added, reordered.draftPlayers.get(0).id);
        assertEquals(Long.valueOf(dealer), reordered.draft.dealerId);
        assertEquals(0, reordered.draftPlayers.get(0).seat); assertEquals(2, reordered.draftPlayers.get(2).seat);
    }

    @Test public void setupRenameAndRemoveUseIdsAndPreserveNewlyAddedParticipantsAndSettings() {
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("One%"), new PlayerSpec("Two_"), new PlayerSpec("Three")), "ASCENDING", "FIXED", null));
        Snapshot before = repository.snapshot();
        long first = before.draftPlayers.get(0).id, dealer = before.draftPlayers.get(1).id, next = before.draftPlayers.get(2).id;
        repository.chooseDraftDealer(draft, dealer);
        long added = repository.addPlayerToDraft(draft, "Added while rename was open", false);
        repository.updateDraftSettings(draft, "DESCENDING", "CUSTOM");
        repository.renameDraftPlayer(draft, first, "Full renamed %_ player");
        repository.removeDraftPlayer(draft, dealer);
        Snapshot after = repository.snapshot();
        assertEquals(3, after.draftPlayers.size()); assertEquals(added, after.draftPlayers.get(2).id);
        assertEquals("Full renamed %_ player", after.draftPlayers.get(0).name);
        assertEquals(Long.valueOf(next), after.draft.dealerId);
        assertEquals("DESCENDING", after.draft.direction); assertEquals("CUSTOM", after.draft.trumpMode);
        rejects(() -> repository.renameDraftPlayer(draft, dealer, "Too late"));
        rejects(() -> repository.removeDraftPlayer(draft, dealer));
    }

    @Test public void stagedSaveForFutureCreatesProfileOnlyWhenConfirmedRosterApplyCommits() {
        start(3);
        Snapshot before = repository.snapshot();
        repository.submitBid(before.round.id, before.roundPlayers.get(0).playerId, 0);
        List<PlayerSpec> staged = specs(before.players);
        staged.add(new PlayerSpec(-100, "New remembered player", null, true));
        rejects(() -> repository.applyRoster(before.round.id, staged, before.round.dealerId, false));
        assertTrue(repository.snapshot().savedPlayers.isEmpty());
        assertEquals(3, repository.snapshot().players.size());
        assertEquals(before.round.id, repository.snapshot().round.id);
        repository.applyRoster(before.round.id, staged, before.round.dealerId, true);
        Snapshot applied = repository.snapshot();
        assertEquals(4, applied.players.size()); assertEquals(1, applied.savedPlayers.size());
        assertEquals(Long.valueOf(applied.savedPlayers.get(0).id), applied.players.get(3).savedPlayerId);
        assertNotEquals(before.round.id, applied.round.id);
    }

    @Test public void duplicateSavedIntentRollsBackEarlierProfileAndParticipantInsertions() {
        start(3);
        repository.addSavedPlayer("Existing profile");
        Snapshot before = repository.snapshot();
        List<PlayerSpec> staged = specs(before.players);
        staged.add(new PlayerSpec(-100, "First proposed profile", null, true));
        staged.add(new PlayerSpec(-101, "Existing profile", null, true));
        rejects(() -> repository.applyRoster(before.round.id, staged, before.round.dealerId, true));
        Snapshot after = repository.snapshot();
        assertEquals(3, after.players.size()); assertEquals(1, after.savedPlayers.size());
        assertEquals("Existing profile", after.savedPlayers.get(0).name);
        assertEquals(before.round.id, after.round.id);
    }

    @Test public void failedDescendingStepDownRollsBackRosterSavedProfileAndOldPredictions() {
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("One"), new PlayerSpec("Two")), "DESCENDING", "FIXED", null));
        repository.startGame(draft);
        Snapshot before = repository.snapshot();
        repository.submitBid(before.round.id, before.roundPlayers.get(0).playerId, 0);
        List<PlayerSpec> staged = specs(before.players);
        staged.add(new PlayerSpec(-100, "Remembered new player", null, true));
        ScoreDatabase connection = ScoreDatabase.open(context, name);
        SupportSQLiteDatabase sql = connection.getOpenHelper().getWritableDatabase();
        sql.execSQL("CREATE TRIGGER reject_replacement BEFORE INSERT ON rounds BEGIN SELECT RAISE(ABORT, 'Injected replacement failure'); END");
        try {
            try { repository.applyRoster(before.round.id, staged, before.round.dealerId, true); fail("Expected SQL failure"); }
            catch (android.database.sqlite.SQLiteException expected) { assertTrue(expected.getMessage().contains("Injected replacement")); }
            Snapshot unchanged = repository.snapshot();
            assertTrue(unchanged.savedPlayers.isEmpty()); assertEquals(2, unchanged.players.size());
            assertEquals(before.round.id, unchanged.round.id); assertEquals(26, unchanged.round.cards);
            assertEquals(Integer.valueOf(0), unchanged.roundPlayers.get(0).bid);
            assertEquals("ACTIVE", unchanged.game.status);
        } finally { sql.execSQL("DROP TRIGGER reject_replacement"); connection.close(); }
        repository.applyRoster(before.round.id, staged, before.round.dealerId, true);
        assertEquals(17, repository.snapshot().round.cards);
        assertEquals(1, repository.snapshot().savedPlayers.size());
    }

    @Test public void startingGameExcludesRemovedSetupEntriesButKeepsTheirSavedProfiles() {
        long draft = repository.saveDraft(new DraftSpec(0, Arrays.asList(new PlayerSpec("One"), new PlayerSpec("Two")), "ASCENDING", "FIXED", null));
        long removed = repository.addPlayerToDraft(draft, "Removed before start", true);
        repository.chooseDraftDealer(draft, removed);
        repository.removeDraftPlayer(draft, removed);
        repository.startGame(draft);
        Snapshot started = repository.snapshot();
        assertEquals(2, started.players.size()); assertTrue(started.inactivePlayers.isEmpty());
        assertEquals(2, started.totals.size()); assertFalse(started.totals.containsKey(removed));
        assertEquals(1, started.savedPlayers.size());
        assertEquals("Removed before start", started.savedPlayers.get(0).name);
        assertEquals(2, started.roundPlayers.size());
    }

    private void start(int count) {
        List<PlayerSpec> players = new ArrayList<>();
        for (int index = 0; index < count; index++) players.add(new PlayerSpec("Player " + index + (index == 0 ? "%" : "_")));
        long draft = repository.saveDraft(new DraftSpec(0, players, "ASCENDING", "FIXED", null));
        repository.startGame(draft);
    }
    private void fillBidsAndResults() {
        Snapshot s = repository.snapshot();
        for (Records.RoundPlayer row : s.roundPlayers) repository.submitBid(s.round.id, row.playerId, 0);
        repository.recordBids(s.round.id);
        for (int index = 0; index < s.roundPlayers.size(); index++) repository.setOutcome(s.round.id, s.roundPlayers.get(index).playerId, index == 0 ? Outcome.LOST : Outcome.WON);
    }
    private static List<PlayerSpec> specs(List<Records.GamePlayer> players) {
        List<PlayerSpec> specs = new ArrayList<>();
        for (Records.GamePlayer player : players) specs.add(new PlayerSpec(player.id, player.name, player.savedPlayerId));
        return specs;
    }
    private void reopen() { repository.close(); repository = new ScoreRepository(ScoreDatabase.open(context, name)); }
    private static void rejects(Runnable command) {
        try { command.run(); fail("Expected validation rejection"); }
        catch (IllegalArgumentException expected) { assertNotNull(expected.getMessage()); }
    }
}
