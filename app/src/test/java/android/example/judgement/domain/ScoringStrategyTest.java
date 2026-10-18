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
                        scoring.points(cards, bid, Outcome.WON, 2));
                assertEquals(0L, scoring.points(cards, bid, Outcome.LOST, 2));
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
                    ScoringStrategy.aggressive().points(example[0], example[1], Outcome.WON, 2));
        }
        assertEquals(72L, ScoringStrategy.aggressive().points(52, 52, Outcome.WON, 2));
    }

    @Test
    public void strictMajorityChangesOnlyAfterHalfForEverySupportedHand() {
        for (int cards = 1; cards <= GameRules.DECK_SIZE; cards++) {
            int belowOrEqualHalf = cards / 2;
            int aboveHalf = belowOrEqualHalf + 1;
            if (belowOrEqualHalf > 0) {
                assertEquals(10L + belowOrEqualHalf, ScoringStrategy.aggressive()
                        .points(cards, belowOrEqualHalf, Outcome.WON, 2));
            }
            assertEquals(10L + aboveHalf + Math.min(cards, 10), ScoringStrategy.aggressive()
                    .points(cards, aboveHalf, Outcome.WON, 2));
        }
    }

    @Test
    public void majorityBonusGrowsToTenThenStaysTenAcrossAllLargerHands() {
        for (int cards = 1; cards <= GameRules.DECK_SIZE; cards++) {
            long additional = ScoringStrategy.aggressive().points(cards, cards, Outcome.WON, 2)
                    - ScoringStrategy.defaults().points(cards, cards, Outcome.WON, 2);
            assertEquals(cards < 10 ? cards : 10, additional);
        }
    }

    @Test
    public void aggressiveNeverRewardsAMissedPredictionEvenAboveHalf() {
        for (int cards = 1; cards <= GameRules.DECK_SIZE; cards++) {
            for (int bid = 0; bid <= cards; bid++) {
                assertEquals(0L, ScoringStrategy.aggressive().points(cards, bid, Outcome.LOST, 2));
            }
            assertEquals(cards < 4 ? 10L : 7L,
                    ScoringStrategy.aggressive().points(cards, 0, Outcome.WON, 2));
        }
    }

    @Test
    public void aggressiveZeroPenaltyStartsAtFourCardsRegardlessOfPlayerCount() {
        ScoringStrategy scoring = ScoringStrategy.aggressive();
        assertEquals(10L, scoring.points(1, 0, Outcome.WON, 6));
        assertEquals(10L, scoring.points(2, 0, Outcome.WON, 6));
        assertEquals(10L, scoring.points(3, 0, Outcome.WON, 2));
        assertEquals(7L, scoring.points(4, 0, Outcome.WON, 6));
        assertEquals(7L, scoring.points(5, 0, Outcome.WON, 6));
        for (int players = 1; players <= GameRules.MAX_PLAYERS; players++) {
            for (int cards = 1; cards <= GameRules.DECK_SIZE; cards++) {
                assertEquals("cards=" + cards + " players=" + players,
                        cards < 4 ? 10L : 7L,
                        scoring.points(cards, 0, Outcome.WON, players));
                assertEquals(0L, scoring.points(cards, 0, Outcome.LOST, players));
            }
        }
    }

    @Test
    public void zeroThresholdDoesNotChangeDefaultCustomOrOtherAggressiveRewards() {
        for (int cards : new int[] {1, 2, 3, 4, 5}) {
            assertEquals(10L, ScoringStrategy.defaults().points(cards, 0, Outcome.WON, 6));
            assertEquals(31L, ScoringStrategy.custom(31L, 4L, 3L, true)
                    .points(cards, 0, Outcome.WON, 6));
            assertEquals(7L, ScoringStrategy.custom(7L, 10L, 1L, true)
                    .points(cards, 0, Outcome.WON, 6));
            assertEquals(ScoringStrategy.aggressive().points(cards, 1, Outcome.WON, 1),
                    ScoringStrategy.aggressive().points(cards, 1, Outcome.WON, 6));
        }
    }

    @Test
    public void customUsesIndependentZeroBaseTrickAndOptionalMajoritySettings() {
        ScoringStrategy withoutBonus = ScoringStrategy.custom(31L, 4L, 3L, false);
        ScoringStrategy withBonus = ScoringStrategy.custom(31L, 4L, 3L, true);
        assertEquals(31L, withoutBonus.points(12, 0, Outcome.WON, 2));
        assertEquals(31L, withBonus.points(12, 0, Outcome.WON, 2));
        assertEquals(22L, withBonus.points(12, 6, Outcome.WON, 2));
        assertEquals(25L, withoutBonus.points(12, 7, Outcome.WON, 2));
        assertEquals(35L, withBonus.points(12, 7, Outcome.WON, 2));
        assertEquals(8L, withBonus.points(1, 1, Outcome.WON, 2));
        assertEquals(0L, withBonus.points(12, 7, Outcome.LOST, 2));
        assertEquals(0L, ScoringStrategy.custom(0L, 0L, 0L, false)
                .points(12, 7, Outcome.WON, 2));
    }

    @Test
    public void customConfigurationsValidateOverflowBeforeAGameCanStart() {
        rejects(() -> ScoringStrategy.custom(0L, Long.MAX_VALUE, 1L, false));
        rejects(() -> ScoringStrategy.custom(0L, 0L, Long.MAX_VALUE, false));
        rejects(() -> ScoringStrategy.custom(0L, Long.MAX_VALUE - 9L, 0L, true));
        rejects(() -> ScoringStrategy.custom(0L, Long.MAX_VALUE - 25L, 1L, false));
        rejects(() -> ScoringStrategy.custom(0L, Long.MAX_VALUE - 35L, 1L, true));
        assertEquals(Long.MAX_VALUE, ScoringStrategy.custom(0L, Long.MAX_VALUE - 26L, 1L,
                false).points(26, 26, Outcome.WON, 2));
        assertEquals(Long.MAX_VALUE, ScoringStrategy.custom(0L, Long.MAX_VALUE - 36L, 1L,
                true).points(26, 26, Outcome.WON, 2));
        ScoringStrategy maximumLegal = ScoringStrategy.custom(0L, Long.MAX_VALUE - 26L,
                1L, false);
        rejects(() -> maximumLegal.points(52, 52, Outcome.WON, 2));
    }

    @Test
    public void exactZeroAndBaseCanUseTheFullNonnegativeLongRange() {
        ScoringStrategy scoring = ScoringStrategy.custom(Long.MAX_VALUE, Long.MAX_VALUE,
                0L, false);
        assertEquals(Long.MAX_VALUE, scoring.points(1, 0, Outcome.WON, 2));
        assertEquals(Long.MAX_VALUE, scoring.points(52, 52, Outcome.WON, 2));
        assertEquals(0L, scoring.points(1, 0, Outcome.LOST, 2));
        rejects(() -> ScoringStrategy.custom(-1L, 0L, 0L, false));
        rejects(() -> ScoringStrategy.custom(0L, -1L, 0L, false));
        rejects(() -> ScoringStrategy.custom(0L, 0L, -1L, false));
    }

    @Test
    public void scoringRejectsMissingOutcomesAndInvalidCardsOrBidsBeforeReturningAnyPoints() {
        for (ScoringStrategy scoring : new ScoringStrategy[] {
                ScoringStrategy.defaults(), ScoringStrategy.aggressive(),
                ScoringStrategy.custom(0L, 2L, 0L, true)}) {
            rejects(() -> scoring.points(0, 0, Outcome.WON, 2));
            rejects(() -> scoring.points(53, 0, Outcome.WON, 2));
            rejects(() -> scoring.points(2, -1, Outcome.LOST, 2));
            rejects(() -> scoring.points(2, 3, Outcome.LOST, 2));
            rejects(() -> scoring.points(2, 0, null, 2));
            rejects(() -> scoring.points(2, 0, Outcome.WON, 0));
            rejects(() -> scoring.points(2, 0, Outcome.LOST, -1));
            rejects(() -> scoring.points(2, 0, Outcome.LOST, 53));
        }
    }

    @Test
    public void versionedPersistencePreservesRulesAndRecognizablePresetIdentity() {
        assertEquals("v1|DEFAULT|10|10|1|0", ScoringStrategy.defaults().encode());
        assertEquals("v1|AGGRESSIVE|7|10|1|1", ScoringStrategy.aggressive().encode());
        ScoringStrategy existingAggressive = ScoringStrategy.decode("v1|AGGRESSIVE|7|10|1|1");
        assertEquals(10L, existingAggressive.points(3, 0, Outcome.WON, 2));
        assertEquals(7L, existingAggressive.points(4, 0, Outcome.WON, 6));
        for (ScoringStrategy scoring : new ScoringStrategy[] {
                ScoringStrategy.defaults(), ScoringStrategy.aggressive(),
                ScoringStrategy.custom(10L, 10L, 1L, false),
                ScoringStrategy.custom(Long.MAX_VALUE, Long.MAX_VALUE - 36L, 1L, true)}) {
            ScoringStrategy reopened = ScoringStrategy.decode(scoring.encode());
            assertEquals(scoring, reopened);
            assertEquals(scoring.hashCode(), reopened.hashCode());
            assertEquals(scoring.preset, reopened.preset);
            for (int cards = 1; cards <= 26; cards++) {
                assertEquals(scoring.points(cards, cards, Outcome.WON, 2),
                        reopened.points(cards, cards, Outcome.WON, 2));
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

    @Test public void namedCustomStrategiesRoundTripUnicodeDelimitersAndKeepLegacyEncoding() {
        String name = "Équipe | Weekend 🌟\nLeague";
        ScoringStrategy strategy = ScoringStrategy.custom(name, 5, 8, 2, true);
        assertEquals(name, strategy.name);
        assertEquals(strategy, ScoringStrategy.decode(strategy.encode()));
        assertEquals(name, ScoringStrategy.decode(strategy.encode()).name);
        assertEquals(ScoringStrategy.custom(5, 8, 2, true).points(6, 4, Outcome.WON, 2),
                strategy.points(6, 4, Outcome.WON, 2));
        assertEquals("v1|CUSTOM|5|8|2|1", ScoringStrategy.custom(5, 8, 2, true).encode());
        assertNotEquals(strategy, strategy.withName("Another"));
        rejects(() -> ScoringStrategy.defaults().withName("New"));
        rejects(() -> ScoringStrategy.custom("  DEFAULT  ", 0, 0, 0, false));
        rejects(() -> ScoringStrategy.custom("Aggressive", 0, 0, 0, false));
        rejects(() -> ScoringStrategy.custom("\u200B", 0, 0, 0, false));
        rejects(() -> ScoringStrategy.decode("v2|CUSTOM|0|0|0|0|_w"));
        rejects(() -> ScoringStrategy.decode("v2|DEFAULT|10|10|1|0|TmV3"));
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
