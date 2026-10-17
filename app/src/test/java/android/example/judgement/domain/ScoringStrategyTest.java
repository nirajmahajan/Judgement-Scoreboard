package android.example.judgement.domain;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ScoringStrategyTest {
    @Test
    public void defaultPreservesExistingScoresIncludingExactZeroAndAllMisses() {
        ScoringStrategy scoring = ScoringStrategy.defaults();
        for (int cards = 1; cards <= GameRules.DECK_SIZE; cards++) {
            for (int bid = 0; bid <= cards; bid++) {
                assertEquals(GameRules.score(bid, Outcome.WON),
                        scoring.points(cards, bid, Outcome.WON));
                assertEquals(0L, scoring.points(cards, bid, Outcome.LOST));
            }
        }
    }

    @Test
    public void aggressiveMatchesEveryRequestedExampleAndDoesNotCapTheTotal() {
        int[][] examples = {
                {1, 1, 12}, {2, 1, 11}, {2, 2, 14}, {12, 0, 7}, {12, 6, 16},
                {12, 7, 27}, {12, 8, 28}, {16, 8, 18}, {16, 9, 29}
        };
        for (int[] example : examples) {
            assertEquals("cards=" + example[0] + " bid=" + example[1], example[2],
                    ScoringStrategy.aggressive().points(example[0], example[1], Outcome.WON));
        }
        assertEquals(72L, ScoringStrategy.aggressive().points(52, 52, Outcome.WON));
    }

    @Test
    public void strictMajorityChangesOnlyAfterHalfForEverySupportedHand() {
        for (int cards = 1; cards <= GameRules.DECK_SIZE; cards++) {
            int belowOrEqualHalf = cards / 2;
            int aboveHalf = belowOrEqualHalf + 1;
            if (belowOrEqualHalf > 0) {
                assertEquals(10L + belowOrEqualHalf, ScoringStrategy.aggressive()
                        .points(cards, belowOrEqualHalf, Outcome.WON));
            }
            assertEquals(10L + aboveHalf + Math.min(cards, 10), ScoringStrategy.aggressive()
                    .points(cards, aboveHalf, Outcome.WON));
        }
    }

    @Test
    public void majorityBonusGrowsToTenThenStaysTenAcrossAllLargerHands() {
        for (int cards = 1; cards <= GameRules.DECK_SIZE; cards++) {
            long additional = ScoringStrategy.aggressive().points(cards, cards, Outcome.WON)
                    - ScoringStrategy.defaults().points(cards, cards, Outcome.WON);
            assertEquals(cards < 10 ? cards : 10, additional);
        }
    }

    @Test
    public void aggressiveNeverRewardsAMissedPredictionEvenAboveHalf() {
        for (int cards = 1; cards <= GameRules.DECK_SIZE; cards++) {
            for (int bid = 0; bid <= cards; bid++) {
                assertEquals(0L, ScoringStrategy.aggressive().points(cards, bid, Outcome.LOST));
            }
            assertEquals(7L, ScoringStrategy.aggressive().points(cards, 0, Outcome.WON));
        }
    }

    @Test
    public void customUsesIndependentZeroBaseTrickAndOptionalMajoritySettings() {
        ScoringStrategy withoutBonus = ScoringStrategy.custom(31L, 4L, 3L, false);
        ScoringStrategy withBonus = ScoringStrategy.custom(31L, 4L, 3L, true);
        assertEquals(31L, withoutBonus.points(12, 0, Outcome.WON));
        assertEquals(31L, withBonus.points(12, 0, Outcome.WON));
        assertEquals(22L, withBonus.points(12, 6, Outcome.WON));
        assertEquals(25L, withoutBonus.points(12, 7, Outcome.WON));
        assertEquals(35L, withBonus.points(12, 7, Outcome.WON));
        assertEquals(8L, withBonus.points(1, 1, Outcome.WON));
        assertEquals(0L, withBonus.points(12, 7, Outcome.LOST));
        assertEquals(0L, ScoringStrategy.custom(0L, 0L, 0L, false)
                .points(12, 7, Outcome.WON));
    }

    @Test
    public void customConfigurationsValidateOverflowBeforeAGameCanStart() {
        rejects(() -> ScoringStrategy.custom(0L, Long.MAX_VALUE, 1L, false));
        rejects(() -> ScoringStrategy.custom(0L, 0L, Long.MAX_VALUE, false));
        rejects(() -> ScoringStrategy.custom(0L, Long.MAX_VALUE - 9L, 0L, true));
        rejects(() -> ScoringStrategy.custom(0L, Long.MAX_VALUE - 25L, 1L, false));
        rejects(() -> ScoringStrategy.custom(0L, Long.MAX_VALUE - 35L, 1L, true));
        assertEquals(Long.MAX_VALUE, ScoringStrategy.custom(0L, Long.MAX_VALUE - 26L, 1L,
                false).points(26, 26, Outcome.WON));
        assertEquals(Long.MAX_VALUE, ScoringStrategy.custom(0L, Long.MAX_VALUE - 36L, 1L,
                true).points(26, 26, Outcome.WON));
        ScoringStrategy maximumLegal = ScoringStrategy.custom(0L, Long.MAX_VALUE - 26L,
                1L, false);
        rejects(() -> maximumLegal.points(52, 52, Outcome.WON));
    }

    @Test
    public void exactZeroAndBaseCanUseTheFullNonnegativeLongRange() {
        ScoringStrategy scoring = ScoringStrategy.custom(Long.MAX_VALUE, Long.MAX_VALUE,
                0L, false);
        assertEquals(Long.MAX_VALUE, scoring.points(1, 0, Outcome.WON));
        assertEquals(Long.MAX_VALUE, scoring.points(52, 52, Outcome.WON));
        assertEquals(0L, scoring.points(1, 0, Outcome.LOST));
        rejects(() -> ScoringStrategy.custom(-1L, 0L, 0L, false));
        rejects(() -> ScoringStrategy.custom(0L, -1L, 0L, false));
        rejects(() -> ScoringStrategy.custom(0L, 0L, -1L, false));
    }

    @Test
    public void scoringRejectsMissingOutcomesAndInvalidCardsOrBidsBeforeReturningAnyPoints() {
        for (ScoringStrategy scoring : new ScoringStrategy[] {
                ScoringStrategy.defaults(), ScoringStrategy.aggressive(),
                ScoringStrategy.custom(0L, 2L, 0L, true)}) {
            rejects(() -> scoring.points(0, 0, Outcome.WON));
            rejects(() -> scoring.points(53, 0, Outcome.WON));
            rejects(() -> scoring.points(2, -1, Outcome.LOST));
            rejects(() -> scoring.points(2, 3, Outcome.LOST));
            rejects(() -> scoring.points(2, 0, null));
        }
    }

    @Test
    public void versionedPersistencePreservesRulesAndRecognizablePresetIdentity() {
        assertEquals("v1|DEFAULT|10|10|1|0", ScoringStrategy.defaults().encode());
        assertEquals("v1|AGGRESSIVE|7|10|1|1", ScoringStrategy.aggressive().encode());
        for (ScoringStrategy scoring : new ScoringStrategy[] {
                ScoringStrategy.defaults(), ScoringStrategy.aggressive(),
                ScoringStrategy.custom(10L, 10L, 1L, false),
                ScoringStrategy.custom(Long.MAX_VALUE, Long.MAX_VALUE - 36L, 1L, true)}) {
            ScoringStrategy reopened = ScoringStrategy.decode(scoring.encode());
            assertEquals(scoring, reopened);
            assertEquals(scoring.hashCode(), reopened.hashCode());
            assertEquals(scoring.preset, reopened.preset);
            for (int cards = 1; cards <= 26; cards++) {
                assertEquals(scoring.points(cards, cards, Outcome.WON),
                        reopened.points(cards, cards, Outcome.WON));
            }
        }
        assertNotEquals(ScoringStrategy.defaults(),
                ScoringStrategy.custom(10L, 10L, 1L, false));
    }

    @Test
    public void persistenceRejectsUnknownVersionsCorruptFieldsAndAlteredNamedPresets() {
        String[] corrupt = {
                null, "", "v2|DEFAULT|10|10|1|0", "v1|UNKNOWN|10|10|1|0",
                "v1|DEFAULT|10|10|1", "v1|DEFAULT|10|10|1|0|extra",
                "v1|DEFAULT|11|10|1|0", "v1|AGGRESSIVE|10|10|1|1",
                "v1|AGGRESSIVE|7|10|1|0", "v1|CUSTOM|-1|10|1|0",
                "v1|CUSTOM|0|+1|1|0", "v1|CUSTOM|0|1|01|0",
                "v1|CUSTOM|0|1|1|true", "v1|CUSTOM|0|1|1|",
                "v1|CUSTOM|0|1.5|1|0", "v1|CUSTOM|0|1|1| 0",
                "v1|CUSTOM|9223372036854775808|0|0|0",
                "v1|CUSTOM|0|9223372036854775807|1|0"
        };
        for (String encoded : corrupt) {
            rejects(() -> ScoringStrategy.decode(encoded));
        }
    }

    private static void rejects(Runnable command) {
        try {
            command.run();
            fail("Expected invalid scoring rules to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue("Validation must explain the problem", expected.getMessage() != null
                    && !expected.getMessage().isEmpty());
        }
    }
}
