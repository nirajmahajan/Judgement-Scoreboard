package android.example.judgement.data;

import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;

import org.junit.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GameStatisticsTest {
    @Test
    public void finalMetricsUseRecordedRulesParticipationAndFullNames() {
        Fixture fixture = exampleGame();
        GameStatistics report = GameStatistics.from(fixture.snapshot());
        GameStatistics.PlayerStatistics alpha = report.player(11L);
        assertEquals("Renamed player — निरज 🎴 with a complete long name", alpha.name);
        assertEquals(64L, alpha.total);
        assertEquals(1, alpha.rank);
        assertEquals(5, alpha.playedRounds);
        assertEquals(4, alpha.exactPredictions);
        assertEquals(80.0, alpha.exactPercentage, 0.0);
        assertEquals(Integer.valueOf(4), alpha.highestSuccessfulBid);
        assertEquals(3, alpha.longestExactStreak);
        assertEquals(1, alpha.zeroBids);
        assertEquals(26L, report.player(22L).total);
        assertEquals(12L, report.player(33L).total);
        assertFalse(report.player(33L).active);
        assertEquals(0, report.player(44L).playedRounds);
        assertNull(report.player(44L).exactPercentage);
        assertNull(report.player(44L).highestSuccessfulBid);
        assertNull(report.player(999L));
        assertEquals(5, report.completedRounds);
        assertEquals("Original complete Alpha name", report.rounds.get(0).player(11L).name);
        assertEquals(10L, report.rounds.get(0).player(11L).points);
        assertEquals(15L, report.rounds.get(2).player(11L).points);
        assertEquals(28L, report.rounds.get(4).player(11L).points);
        assertEquals(2L, report.rounds.get(4).player(22L).points);
    }

    @Test
    public void chronologicalTimelineKeepsManualCorrectionsSeparateFromRoundPerformance() {
        GameStatistics report = GameStatistics.from(exampleGame().snapshot());
        assertEquals(7, report.timeline.size());
        assertFalse(report.rounds.get(0).strategyChanged);
        assertFalse(report.rounds.get(1).strategyChanged);
        assertTrue(report.rounds.get(2).strategyChanged);
        assertFalse(report.rounds.get(3).strategyChanged);
        assertTrue(report.rounds.get(4).strategyChanged);
        GameStatistics.TimelineEvent correction = report.timeline.get(3);
        assertEquals(GameStatistics.EventKind.ADJUSTMENT, correction.kind);
        assertNull(correction.round);
        assertEquals(5L, correction.adjustment.delta);
        assertEquals("C before rename", correction.adjustment.name);
        assertEquals(BigInteger.valueOf(15L), correction.cumulative.get(33L));
        assertEquals(BigInteger.valueOf(10L), report.timeline.get(2).cumulative.get(33L));
        assertEquals(BigInteger.valueOf(12L), report.timeline.get(6).cumulative.get(33L));
        assertEquals(0L, report.rounds.get(4).player(33L).points);
        assertEquals(1, report.player(33L).exactPredictions);
    }

    @Test
    public void removedAndRestoredParticipationDoesNotInventRoundsOrBreakAnExactStreak() {
        Fixture fixture = new Fixture();
        fixture.addPlayer(11L, "A", 0, true);
        fixture.addPlayer(22L, "B", 1, true);
        fixture.addPlayer(33L, "Removed C", 2, false);
        fixture.round(101L, 1, 1, ScoringStrategy.defaults(), result(11L, 0, true),
                result(22L, 0, false), result(33L, 0, true));
        fixture.round(102L, 2, 2, ScoringStrategy.defaults(), result(11L, 0, true),
                result(22L, 0, false));
        fixture.round(103L, 3, 3, ScoringStrategy.defaults(), result(11L, 1, false),
                result(22L, 0, false), result(33L, 0, true));
        GameStatistics report = GameStatistics.from(fixture.snapshot());
        assertEquals(2, report.player(33L).playedRounds);
        assertEquals(100.0, report.player(33L).exactPercentage, 0.0);
        assertEquals(Integer.valueOf(0), report.player(33L).highestSuccessfulBid);
        assertEquals(2, report.player(33L).longestExactStreak);
        assertNull(report.rounds.get(1).player(33L));
        assertEquals(BigInteger.TEN, report.timeline.get(1).cumulative.get(33L));
        assertEquals(2, report.player(11L).longestExactStreak);
    }

    @Test
    public void signedAndBeyondLongPrefixesRemainExactAfterRetroactiveCorrections() {
        Fixture negative = twoPlayers();
        negative.round(101L, 1, 1, ScoringStrategy.custom(0L, 0L, 0L, false),
                result(11L, 0, true), result(22L, 0, false));
        negative.adjust(1L, 11L, -10L, 150L);
        negative.round(102L, 2, 2, ScoringStrategy.defaults(), result(11L, 0, true),
                result(22L, 0, false));
        GameStatistics negativeReport = GameStatistics.from(negative.snapshot());
        assertEquals(BigInteger.valueOf(-10L), negativeReport.timeline.get(1).cumulative.get(11L));
        assertEquals(0L, negativeReport.player(11L).total);

        Fixture huge = twoPlayers();
        ScoringStrategy hugeZero = ScoringStrategy.custom(Long.MAX_VALUE, 0L, 0L, false);
        huge.round(101L, 1, 1, hugeZero, result(11L, 0, true), result(22L, 0, false));
        huge.round(102L, 2, 2, hugeZero, result(11L, 0, true), result(22L, 0, false));
        huge.adjust(1L, 11L, -Long.MAX_VALUE, 250L);
        GameStatistics hugeReport = GameStatistics.from(huge.snapshot());
        assertEquals(BigInteger.valueOf(Long.MAX_VALUE).multiply(BigInteger.TWO),
                hugeReport.timeline.get(1).cumulative.get(11L));
        assertEquals(Long.MAX_VALUE, hugeReport.player(11L).total);
    }

    @Test
    public void sameTimestampEventsPutRoundsFirstThenSortEachKindByStableId() {
        Fixture fixture = twoPlayers();
        fixture.round(102L, 2, 2, ScoringStrategy.defaults(), result(11L, 0, true),
                result(22L, 0, false));
        fixture.round(101L, 1, 1, ScoringStrategy.defaults(), result(11L, 0, true),
                result(22L, 0, false));
        for (Snapshot.RoundHistory entry : fixture.history) entry.round.completedAt = 500L;
        fixture.adjust(9L, 11L, 3L, 500L);
        fixture.adjust(3L, 11L, -2L, 500L);
        GameStatistics report = GameStatistics.from(fixture.snapshot());
        assertEquals(101L, report.timeline.get(0).id);
        assertEquals(102L, report.timeline.get(1).id);
        assertEquals(GameStatistics.EventKind.ROUND, report.timeline.get(1).kind);
        assertEquals(3L, report.timeline.get(2).id);
        assertEquals(9L, report.timeline.get(3).id);
        assertEquals(GameStatistics.EventKind.ADJUSTMENT, report.timeline.get(2).kind);
        assertEquals(21L, report.player(11L).total);
    }

    @Test
    public void correctionRecalculatesMetricsGraphsAwardsAndRankWithoutStaleCaches() {
        Fixture fixture = exampleGame();
        fixture.adjust(3L, 22L, 20L, 600L);
        GameStatistics before = GameStatistics.from(fixture.snapshot());
        assertNotNull(award(before, GameStatistics.AwardKind.LARGEST_WIN));
        Snapshot.RoundHistory fifth = fixture.history.get(4);
        fifth.players.get(0).outcome = Outcome.LOST.name();
        GameStatistics after = GameStatistics.from(fixture.snapshot());
        assertEquals(36L, after.player(11L).total);
        assertEquals(1, before.player(11L).rank);
        assertEquals(2, after.player(11L).rank);
        assertEquals(1, after.player(22L).rank);
        assertEquals(3, after.player(11L).exactPredictions);
        assertEquals(60.0, after.player(11L).exactPercentage, 0.0);
        assertEquals(Integer.valueOf(2), after.player(11L).highestSuccessfulBid);
        assertEquals(0L, after.rounds.get(4).player(11L).points);
        assertEquals(BigInteger.valueOf(36L), after.timeline.get(6).cumulative.get(11L));
        assertEquals(22L, award(after, GameStatistics.AwardKind.MOST_EXACT_PREDICTIONS).playerId);
        assertEquals(3, award(after, GameStatistics.AwardKind.LARGEST_WIN).roundNumber.intValue());
        // The earlier immutable report remains a historical calculation, not a view of mutable rows.
        assertEquals(64L, before.player(11L).total);
        assertEquals(28L, before.rounds.get(4).player(11L).points);
    }

    @Test
    public void denseRanksRetainActiveSeatOrderAndThenFormerSeatOrderForTies() {
        Fixture fixture = new Fixture();
        fixture.addPlayer(11L, "A", 1, true);
        fixture.addPlayer(22L, "B", 0, true);
        fixture.addPlayer(33L, "C", 0, false);
        fixture.round(101L, 1, 1, ScoringStrategy.defaults(), result(11L, 0, true),
                result(22L, 0, true), result(33L, 0, false));
        fixture.adjust(1L, 33L, 10L, 150L);
        GameStatistics report = GameStatistics.from(fixture.snapshot());
        assertEquals(22L, report.players.get(0).playerId);
        assertEquals(11L, report.players.get(1).playerId);
        assertEquals(33L, report.players.get(2).playerId);
        for (GameStatistics.PlayerStatistics player : report.players) assertEquals(1, player.rank);
        fixture.addPlayer(44L, "D", 2, true);
        report = GameStatistics.from(fixture.snapshot());
        assertEquals(2, report.player(44L).rank);
    }

    @Test
    public void awardsNeedFiveRoundsUniqueLeadersAndThreeCountOccurrences() {
        Fixture fixture = twoPlayers();
        for (int number = 1; number <= 4; number++) {
            fixture.round(100L + number, number, number, ScoringStrategy.defaults(),
                    result(11L, 0, true), result(22L, number == 1 ? 0 : 1, false));
        }
        assertTrue(GameStatistics.from(fixture.snapshot()).awards.isEmpty());
        fixture.round(105L, 5, 5, ScoringStrategy.defaults(), result(11L, 0, true),
                result(22L, 1, false));
        GameStatistics report = GameStatistics.from(fixture.snapshot());
        assertEquals(3, report.awards.size());
        assertEquals(5L, award(report, GameStatistics.AwardKind.ZERO_MACHINE).value);
        assertEquals(5L, award(report, GameStatistics.AwardKind.MOST_EXACT_PREDICTIONS).value);
        GameStatistics.Award largest = award(report, GameStatistics.AwardKind.LARGEST_WIN);
        assertEquals(10L, largest.value);
        assertEquals(Long.valueOf(101L), largest.roundId);
        assertEquals(Integer.valueOf(1), largest.roundNumber);

        Fixture scarce = twoPlayers();
        for (int number = 1; number <= 5; number++) {
            scarce.round(100L + number, number, number, ScoringStrategy.defaults(),
                    result(11L, number <= 2 ? 0 : 1, number <= 2),
                    result(22L, number == 1 ? 0 : 1, false));
        }
        report = GameStatistics.from(scarce.snapshot());
        assertNull(award(report, GameStatistics.AwardKind.ZERO_MACHINE));
        assertNull(award(report, GameStatistics.AwardKind.MOST_EXACT_PREDICTIONS));
        assertNotNull(award(report, GameStatistics.AwardKind.LARGEST_WIN));
    }

    @Test
    public void tiesHideEachAwardAndZeroMachineIncludesMissedZeroBids() {
        Fixture fixture = twoPlayers();
        for (int number = 1; number <= 5; number++) {
            fixture.round(100L + number, number, number, ScoringStrategy.defaults(),
                    result(11L, 0, number % 2 == 1), result(22L, 0, number % 2 == 0));
        }
        GameStatistics report = GameStatistics.from(fixture.snapshot());
        assertNull(award(report, GameStatistics.AwardKind.LARGEST_WIN));
        assertNull(award(report, GameStatistics.AwardKind.ZERO_MACHINE));
        assertEquals(11L, award(report, GameStatistics.AwardKind.MOST_EXACT_PREDICTIONS).playerId);
        fixture.history.get(4).players.get(0).bid = 1;
        report = GameStatistics.from(fixture.snapshot());
        GameStatistics.Award zero = award(report, GameStatistics.AwardKind.ZERO_MACHINE);
        assertEquals(22L, zero.playerId);
        assertEquals(5L, zero.value);
        assertEquals(2, report.player(22L).exactPredictions);
    }

    @Test
    public void inProgressRoundsAreExcludedAndCollectionsCannotMutateTheReport() {
        Fixture fixture = twoPlayers();
        fixture.round(101L, 1, 1, ScoringStrategy.defaults(), result(11L, 0, true),
                result(22L, 0, false));
        fixture.round(102L, 2, 2, ScoringStrategy.aggressive(), result(11L, 2, true),
                result(22L, 0, false));
        fixture.history.get(1).round.phase = "RESULTS";
        GameStatistics report = GameStatistics.from(fixture.snapshot());
        assertEquals(1, report.completedRounds);
        assertEquals(10L, report.player(11L).total);
        immutable(() -> report.players.clear());
        immutable(() -> report.rounds.clear());
        immutable(() -> report.rounds.get(0).players.clear());
        immutable(() -> report.timeline.get(0).cumulative.clear());
        immutable(() -> report.awards.clear());
    }

    private static Fixture exampleGame() {
        Fixture fixture = new Fixture();
        fixture.addPlayer(11L, "Renamed player — निरज 🎴 with a complete long name", 0, true);
        fixture.addPlayer(22L, "B", 1, true);
        fixture.addPlayer(33L, "C", 2, false);
        fixture.addPlayer(44L, "New, no completed participation", 3, true);
        fixture.round(101L, 1, 1, ScoringStrategy.defaults(), result(11L, 0, true),
                result(22L, 0, false), result(33L, 0, true));
        fixture.history.get(0).players.get(0).nameSnapshot = "Original complete Alpha name";
        fixture.round(102L, 2, 2, ScoringStrategy.defaults(), result(11L, 1, true),
                result(22L, 0, true), result(33L, 0, false));
        fixture.round(103L, 3, 3, ScoringStrategy.aggressive(), result(11L, 2, true),
                result(22L, 0, true), result(33L, 2, false));
        fixture.round(104L, 4, 4, ScoringStrategy.aggressive(), result(11L, 3, false),
                result(22L, 0, true), result(33L, 0, false));
        fixture.round(105L, 5, 5, ScoringStrategy.custom(2L, 20L, 2L, false),
                result(11L, 4, true), result(22L, 0, true), result(33L, 0, false));
        fixture.adjust(1L, 33L, 5L, 350L);
        fixture.adjust(2L, 33L, -3L, 550L);
        return fixture;
    }

    private static Fixture twoPlayers() {
        Fixture fixture = new Fixture();
        fixture.addPlayer(11L, "A", 0, true);
        fixture.addPlayer(22L, "B", 1, true);
        return fixture;
    }

    private static Result result(long id, int bid, boolean won) {
        return new Result(id, bid, won ? Outcome.WON : Outcome.LOST);
    }

    private static GameStatistics.Award award(GameStatistics report, GameStatistics.AwardKind kind) {
        for (GameStatistics.Award award : report.awards) if (award.kind == kind) return award;
        return null;
    }

    private static void immutable(Runnable mutation) {
        try { mutation.run(); fail("Expected an immutable result"); }
        catch (UnsupportedOperationException expected) { }
    }

    private static final class Result {
        final long id;
        final int bid;
        final Outcome outcome;
        Result(long id, int bid, Outcome outcome) { this.id = id; this.bid = bid; this.outcome = outcome; }
    }

    private static final class Fixture {
        final List<Records.GamePlayer> players = new ArrayList<>();
        final List<Records.GamePlayer> inactive = new ArrayList<>();
        final List<Snapshot.RoundHistory> history = new ArrayList<>();
        final List<Records.ScoreAdjustment> adjustments = new ArrayList<>();

        void addPlayer(long id, String name, int seat, boolean active) {
            Records.GamePlayer player = new Records.GamePlayer();
            player.id = id; player.name = name; player.seat = seat; player.active = active;
            (active ? players : inactive).add(player);
        }

        void round(long id, int number, int cards, ScoringStrategy strategy, Result... results) {
            Records.Round round = new Records.Round();
            round.id = id; round.number = number; round.cards = cards; round.phase = "COMPLETE";
            round.completedAt = number * 100L; round.scoringConfig = strategy.encode();
            List<Records.RoundPlayer> rows = new ArrayList<>();
            for (int index = 0; index < results.length; index++) {
                Result result = results[index];
                Records.RoundPlayer row = new Records.RoundPlayer();
                row.roundId = id; row.playerId = result.id; row.nameSnapshot = "Recorded " + result.id;
                row.bidPosition = index; row.bid = result.bid; row.outcome = result.outcome.name();
                rows.add(row);
            }
            history.add(new Snapshot.RoundHistory(round, rows));
        }

        void adjust(long id, long playerId, long delta, long at) {
            Records.ScoreAdjustment adjustment = new Records.ScoreAdjustment();
            adjustment.id = id; adjustment.playerId = playerId; adjustment.delta = delta;
            adjustment.nameSnapshot = "C before rename"; adjustment.createdAt = at;
            adjustments.add(adjustment);
        }

        Snapshot snapshot() {
            List<Snapshot.RoundHistory> reverse = new ArrayList<>(history);
            Collections.reverse(reverse);
            return new Snapshot(null, Collections.emptyList(), new Records.Game(), players, inactive,
                    Collections.emptyMap(), null, Collections.emptyList(), reverse, adjustments,
                    Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
        }
    }
}
