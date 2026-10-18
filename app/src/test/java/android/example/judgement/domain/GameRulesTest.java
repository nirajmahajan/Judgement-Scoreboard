package android.example.judgement.domain;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class GameRulesTest {
    @Test
    public void validatesBothRosterBoundariesAndCurrentHandBeforeAnEdit() {
        GameRules.validatePlayerCount(2);
        GameRules.validatePlayerCount(52);
        assertEquals(26, GameRules.maximumCards(2));
        assertEquals(1, GameRules.maximumCards(52));
        rejects(() -> GameRules.validatePlayerCount(0));
        rejects(() -> GameRules.validatePlayerCount(1));
        rejects(() -> GameRules.validatePlayerCount(53));
        rejects(() -> GameRules.validateCards(0, 2));
        rejects(() -> GameRules.validateCards(27, 2));
        rejects(() -> GameRules.validateCards(7, 8));
        GameRules.validateCards(6, 8);
    }

    @Test
    public void dealerLastOrderUsesStableIdentityRatherThanIdAsSeatNumber() {
        long[] seats = {41L, 900L, 7L, 103L};
        assertArrayEquals(new long[] {7L, 103L, 41L, 900L}, GameRules.bidOrder(seats, 900L));
        assertEquals(7L, GameRules.nextDealer(seats, 900L));
        assertEquals(41L, GameRules.nextDealer(seats, 103L));
        assertArrayEquals(new long[] {41L, 900L, 7L, 103L}, seats);
        rejects(() -> GameRules.bidOrder(seats, 2L));
        rejects(() -> GameRules.bidOrder(new long[] {41L, 41L}, 41L));
        rejects(() -> GameRules.bidOrder(new long[] {41L}, 41L));
    }

    @Test
    public void sequentialBidsDistinguishMissingFromZeroAndRejectStaleQuickBidIds() {
        long[] order = {99L, 15L, 8L};
        Map<Long, Integer> bids = new HashMap<>();
        assertEquals(Long.valueOf(99L), GameRules.currentBidder(order, bids));
        rejects(() -> GameRules.validateBidSubmission(3, order, 8L, bids, 15L, 0));
        GameRules.validateBidSubmission(3, order, 8L, bids, 99L, 0);
        bids.put(99L, 0);
        assertEquals(Long.valueOf(15L), GameRules.currentBidder(order, bids));
        rejects(() -> GameRules.validateBidSubmission(3, order, 8L, bids, 99L, 1));
        GameRules.validateBidSubmission(3, order, 8L, bids, 15L, 2);
        bids.put(15L, 2);
        rejects(() -> GameRules.validateBidSubmission(3, order, 8L, bids, 8L, 1));
        GameRules.validateBidSubmission(3, order, 8L, bids, 8L, 0);
        bids.put(8L, 0);
        assertNull(GameRules.currentBidder(order, bids));
        GameRules.validateBidsComplete(3, order, 8L, bids);
        rejects(() -> GameRules.validateBidSubmission(3, order, 8L, bids, 8L, 2));
    }

    @Test
    public void latestBidRemainsEditableOnlyUntilNextPlayerSubmits() {
        long[] order = {11L, 22L, 33L};
        Map<Long, Integer> bids = new HashMap<>();
        rejects(() -> GameRules.validateBidEdit(4, order, 33L, bids, 11L, 1));
        bids.put(11L, 0);
        GameRules.validateBidEdit(4, order, 33L, bids, 11L, 4);
        bids.put(22L, 1);
        rejects(() -> GameRules.validateBidEdit(4, order, 33L, bids, 11L, 2));
        GameRules.validateBidEdit(4, order, 33L, bids, 22L, 2);
        bids.put(33L, 0);
        rejects(() -> GameRules.validateBidEdit(4, order, 33L, bids, 22L, 2));
        rejects(() -> GameRules.validateBidEdit(4, order, 33L, bids, 33L, 3));
        GameRules.validateBidEdit(4, order, 33L, bids, 33L, 2);
    }

    @Test
    public void invalidBidStatesCannotBypassTheOrderOrBounds() {
        long[] order = {1L, 2L, 3L};
        Map<Long, Integer> bids = new HashMap<>();
        bids.put(2L, 0);
        rejects(() -> GameRules.currentBidder(order, bids));
        bids.clear();
        bids.put(9L, 0);
        rejects(() -> GameRules.currentBidder(order, bids));
        bids.clear();
        bids.put(1L, null);
        rejects(() -> GameRules.currentBidder(order, bids));
        bids.put(1L, 5);
        rejects(() -> GameRules.validateBidSubmission(4, order, 3L, bids, 2L, 1));
        bids.clear();
        rejects(() -> GameRules.validateBidSubmission(4, order, 2L, bids, 1L, 0));
        rejects(() -> GameRules.validateBidSubmission(4, order, 3L, bids, 1L, -1));
        rejects(() -> GameRules.validateBidSubmission(4, order, 3L, bids, 1L, 5));
        rejects(() -> GameRules.validateBidsComplete(4, order, 3L, bids));
    }

    @Test
    public void everyForbiddenTotalVectorIsRejectedAtDealerSubmissionAndEdit() {
        int vectors = 0;
        for (int count = 2; count <= 4; count++) {
            for (int cards = 1; cards <= 5; cards++) {
                int combinations = integerPower(cards + 1, count);
                long[] order = ids(count);
                for (int code = 0; code < combinations; code++) {
                    int[] bids = decode(code, cards + 1, count);
                    int total = 0;
                    Map<Long, Integer> previousBids = new HashMap<>();
                    for (int player = 0; player < count; player++) {
                        total += bids[player];
                        if (player < count - 1) {
                            previousBids.put(order[player], bids[player]);
                        }
                    }
                    int hand = cards;
                    int dealerBid = bids[count - 1];
                    long dealerId = order[count - 1];
                    if (total == cards) {
                        vectors++;
                        rejects(() -> GameRules.validateBidSubmission(hand, order, dealerId,
                                previousBids, dealerId, dealerBid));
                        previousBids.put(dealerId, dealerBid);
                        rejects(() -> GameRules.validateBidEdit(hand, order, dealerId,
                                previousBids, dealerId, dealerBid));
                        rejects(() -> GameRules.validateBidsComplete(hand, order, dealerId,
                                previousBids));
                    } else {
                        GameRules.validateBidSubmission(hand, order, dealerId, previousBids,
                                dealerId, dealerBid);
                    }
                }
            }
        }
        assertEquals(200, vectors);
    }

    @Test
    public void outcomeValidationMatchesIndependentExhaustiveTrickAllocationOracle() {
        int checked = 0;
        int forbiddenBidCases = 0;
        for (int count = 2; count <= 4; count++) {
            String[] names = new String[count];
            for (int player = 0; player < count; player++) names[player] = "Player " + player;
            for (int cards = 1; cards <= 5; cards++) {
                for (int code = 0; code < integerPower(cards + 1, count); code++) {
                    int[] bids = decode(code, cards + 1, count);
                    int bidTotal = 0;
                    for (int bid : bids) {
                        bidTotal += bid;
                    }
                    for (int mask = 0; mask < (1 << count); mask++) {
                        Outcome[] outcomes = outcomes(mask, count);
                        boolean expected = oracle(cards, bids, outcomes, new int[count], 0);
                        boolean actual = GameRules.areOutcomesFeasible(cards, bids, outcomes);
                        assertEquals("players=" + count + " cards=" + cards + " bids="
                                + code + " outcomes=" + mask, expected, actual);
                        String conflict = GameRules.outcomeConflict(cards, bids, outcomes, names);
                        assertEquals("Only impossible allocations need an explanation", expected, conflict == null);
                        if (conflict != null) {
                            assertFalse(conflict.contains("trick"));
                            if (conflict.contains("1 hand remains and each predicted 1")) {
                                int won = 0;
                                for (int player = 0; player < count; player++) {
                                    if (outcomes[player] == Outcome.WON) won += bids[player];
                                    else assertEquals("Every Lost player in this explanation predicted one", 1, bids[player]);
                                }
                                assertEquals("The explanation's remaining amount is exact", 1, cards - won);
                            }
                        }
                        checked++;
                        if (bidTotal == cards) {
                            forbiddenBidCases++;
                        }
                    }
                }
            }
        }
        assertEquals(40_264, checked);
        assertEquals(2_520, forbiddenBidCases);
        assertEquals(37_744, checked - forbiddenBidCases);
    }

    @Test
    public void outcomeValidationSupportsTheFullFiftyTwoPlayerRoster() {
        int[] bids = new int[52];
        Outcome[] results = new Outcome[52];
        Arrays.fill(results, Outcome.WON);
        results[51] = Outcome.LOST;
        assertTrue(GameRules.areOutcomesFeasible(1, bids, results));
        Arrays.fill(results, Outcome.LOST);
        assertFalse(GameRules.areOutcomesFeasible(1, bids, results));
    }

    @Test
    public void impossibleAllZeroLossesAndWinnersExceedingHandAreRejected() {
        assertFalse(GameRules.areOutcomesFeasible(1, new int[] {0, 0, 0},
                new Outcome[] {Outcome.LOST, Outcome.LOST, Outcome.LOST}));
        assertFalse(GameRules.areOutcomesFeasible(4, new int[] {3, 2, 0},
                new Outcome[] {Outcome.WON, Outcome.WON, Outcome.LOST}));
        assertTrue(GameRules.areOutcomesFeasible(4, new int[] {0, 0, 2},
                new Outcome[] {Outcome.WON, Outcome.LOST, Outcome.LOST}));
        assertFalse(GameRules.areOutcomesFeasible(1, new int[] {1, 0},
                new Outcome[] {Outcome.WON, null}));
    }

    @Test
    public void resultConflictsExplainTheActualTotalsAndNamedPlayers() {
        String[] names = {"Alice", "Ben", "Cara"};
        String over = GameRules.outcomeConflict(3, new int[] {2, 2, 0},
                new Outcome[] {Outcome.WON, Outcome.WON, Outcome.LOST}, names);
        assertEquals("The sum of hands of the players marked WON exceeds 3.", over);

        String zeroMustWin = GameRules.outcomeConflict(3, new int[] {3, 0, 0},
                new Outcome[] {Outcome.WON, Outcome.LOST, Outcome.LOST}, names);
        assertEquals("Ben, Cara cannot Lose\n(Players marked Won already sum to 3 hands. Ben, Cara predicted 0.)", zeroMustWin);

        String zeroNeedMore = GameRules.outcomeConflict(2, new int[] {1, 0, 0},
                new Outcome[] {Outcome.WON, Outcome.LOST, Outcome.LOST}, names);
        assertEquals("Ben, Cara cannot all Lose\n(Only 1 hand remains; each predicted 0 and needs at least 1 hand to Lose.)", zeroNeedMore);

        String allZeroLost = GameRules.outcomeConflict(1, new int[] {0, 0, 0},
                new Outcome[] {Outcome.LOST, Outcome.LOST, Outcome.LOST}, names);
        assertEquals("Alice, Ben, Cara cannot all Lose\n(Only 1 hand remains; each predicted 0 and needs at least 1 hand to Lose.)", allZeroLost);

        String loneLost = GameRules.outcomeConflict(3, new int[] {1, 2, 0},
                new Outcome[] {Outcome.WON, Outcome.LOST, Outcome.WON}, names);
        assertEquals("Ben cannot Lose\n(The remaining 2 hands match their prediction of 2.)", loneLost);

        String unaccounted = GameRules.outcomeConflict(3, new int[] {1, 1, 0},
                new Outcome[] {Outcome.WON, Outcome.WON, Outcome.WON}, names);
        assertEquals("At least one player must Lose\n(Players marked Won sum to 2 hands; this round has 3 hands.)", unaccounted);

        String oneLeft = GameRules.outcomeConflict(1, new int[] {1, 1, 0},
                new Outcome[] {Outcome.LOST, Outcome.LOST, Outcome.WON}, names);
        assertEquals("Alice, Ben cannot all Lose\n(1 hand remains and each predicted 1; whoever wins it matches their prediction.)", oneLeft);

        assertNull(GameRules.outcomeConflict(3, new int[] {1, 0, 0},
                new Outcome[] {Outcome.WON, Outcome.LOST, Outcome.LOST}, names));
    }

    @Test
    public void allWonIsFeasibleOnlyForTheBidTotalsAlreadyForbiddenAtBidding() {
        assertTrue(GameRules.areOutcomesFeasible(3, new int[] {1, 2},
                new Outcome[] {Outcome.WON, Outcome.WON}));
        assertFalse(GameRules.areOutcomesFeasible(3, new int[] {1, 1},
                new Outcome[] {Outcome.WON, Outcome.WON}));
    }

    @Test
    public void invalidOrMismatchedOutcomeInputCannotBeScored() {
        rejects(() -> GameRules.areOutcomesFeasible(1, null, new Outcome[2]));
        rejects(() -> GameRules.areOutcomesFeasible(1, new int[2], new Outcome[3]));
        rejects(() -> GameRules.areOutcomesFeasible(1, new int[] {0},
                new Outcome[] {Outcome.WON}));
        rejects(() -> GameRules.areOutcomesFeasible(1, new int[] {2, 0},
                new Outcome[] {Outcome.WON, Outcome.LOST}));
    }

    @Test
    public void progressionVisitsEachLegalHandOnceInBothDirections() {
        for (int count = 2; count <= 52; count++) {
            int maximum = 52 / count;
            for (Direction direction : Direction.values()) {
                int current = GameRules.firstCards(count, direction);
                for (int ordinal = 1; ordinal <= maximum; ordinal++) {
                    int expected = direction == Direction.ASCENDING ? ordinal
                            : maximum - ordinal + 1;
                    assertEquals(expected, current);
                    assertEquals(expected, GameRules.cardsForRound(count, direction, ordinal));
                    Integer next = GameRules.nextCards(current, count, direction);
                    if (ordinal == maximum) {
                        assertNull(next);
                    } else {
                        current = next;
                    }
                }
            }
        }
        rejects(() -> GameRules.cardsForRound(2, Direction.ASCENDING, 0));
        rejects(() -> GameRules.cardsForRound(2, Direction.ASCENDING, 27));
        rejects(() -> GameRules.firstCards(2, null));
    }

    @Test
    public void rosterEditsKeepCurrentHandAndUseNewCapacityForFollowingHands() {
        assertEquals(Integer.valueOf(6), GameRules.nextCards(5, 8, Direction.ASCENDING));
        assertNull(GameRules.nextCards(6, 8, Direction.ASCENDING));
        assertEquals(Integer.valueOf(7), GameRules.nextCards(6, 6, Direction.ASCENDING));
        assertEquals(Integer.valueOf(5), GameRules.nextCards(6, 8, Direction.DESCENDING));
        rejects(() -> GameRules.nextCards(7, 8, Direction.DESCENDING));
    }

    @Test
    public void rosterChangesPreserveCurrentDealOrFinishAscendingAndClampDescending() {
        assertEquals(Integer.valueOf(6), GameRules.cardsAfterRosterChange(6, 7, Direction.ASCENDING));
        assertNull(GameRules.cardsAfterRosterChange(6, 9, Direction.ASCENDING));
        assertEquals(Integer.valueOf(5), GameRules.cardsAfterRosterChange(6, 9, Direction.DESCENDING));
        assertEquals(Integer.valueOf(5), GameRules.cardsAfterRosterChange(5, 8, Direction.DESCENDING));
        for (int players = 2; players <= 52; players++) {
            for (int current = 1; current <= 26; current++) {
                Integer ascending = GameRules.cardsAfterRosterChange(current, players, Direction.ASCENDING);
                if (current > 52 / players) assertNull(ascending);
                else assertEquals(Integer.valueOf(current), ascending);
                assertEquals(Integer.valueOf(Math.min(current, 52 / players)),
                        GameRules.cardsAfterRosterChange(current, players, Direction.DESCENDING));
            }
        }
        rejects(() -> GameRules.cardsAfterRosterChange(0, 8, Direction.ASCENDING));
        rejects(() -> GameRules.cardsAfterRosterChange(53, 8, Direction.DESCENDING));
        rejects(() -> GameRules.cardsAfterRosterChange(6, 1, Direction.ASCENDING));
        rejects(() -> GameRules.cardsAfterRosterChange(6, 53, Direction.DESCENDING));
        rejects(() -> GameRules.cardsAfterRosterChange(6, 8, null));
    }

    @Test
    public void trumpFollowsChronologicalRoundRatherThanCardsOrDirection() {
        Trump[] cycle = {Trump.SPADES, Trump.DIAMONDS, Trump.CLUBS, Trump.HEARTS};
        for (int ordinal = 1; ordinal <= 104; ordinal++) {
            assertEquals(cycle[(ordinal - 1) % 4], GameRules.trumpForRound(ordinal));
        }
        assertEquals(Trump.CLUBS, GameRules.trumpForRound(Integer.MAX_VALUE));
        rejects(() -> GameRules.trumpForRound(0));
    }

    @Test
    public void scoringUsesCheckedNonnegativeLongTotalsAndCorrectBidThreeExample() {
        assertEquals(10L, GameRules.score(0, Outcome.WON));
        assertEquals(13L, GameRules.score(3, Outcome.WON));
        assertEquals(0L, GameRules.score(3, Outcome.LOST));
        assertEquals(2_147_483_660L, GameRules.addScore(Integer.MAX_VALUE, 13L));
        assertEquals(Long.MAX_VALUE, GameRules.addScore(Long.MAX_VALUE - 13L, 13L));
        assertEquals(0L, GameRules.addScore(13L, -13L));
        rejects(() -> GameRules.addScore(Long.MAX_VALUE - 12L, 13L));
        rejects(() -> GameRules.addScore(0L, -1L));
        rejects(() -> GameRules.addScore(-1L, 2L));
        rejects(() -> GameRules.score(-1, Outcome.WON));
        rejects(() -> GameRules.score(1, null));
    }

    @Test
    public void seededCompleteGamesRetainDealerAndTrumpSequencesWithLegalOutcomes() {
        final long seed = 0x5C0FEB0A4DL;
        Random random = new Random(seed);
        StringBuilder operations = new StringBuilder("seed=").append(seed);
        try {
            for (int game = 0; game < 100; game++) {
                int count = 2 + random.nextInt(11);
                Direction direction = Direction.values()[random.nextInt(2)];
                long[] seats = ids(count);
                long dealer = seats[random.nextInt(count)];
                long[] totals = new long[count];
                int cards = GameRules.firstCards(count, direction);
                int ordinal = 1;
                while (true) {
                    operations.append("\ngame ").append(game).append(" round ")
                            .append(ordinal).append(" cards ").append(cards)
                            .append(" dealer ").append(dealer);
                    long[] order = GameRules.bidOrder(seats, dealer);
                    Map<Long, Integer> bids = new HashMap<>();
                    for (long bidder : order) {
                        int bid = random.nextInt(cards + 1);
                        if (bidder == dealer) {
                            int total = bid;
                            for (Integer existing : bids.values()) {
                                total += existing;
                            }
                            if (total == cards) {
                                bid = (bid + 1) % (cards + 1);
                            }
                        }
                        GameRules.validateBidSubmission(cards, order, dealer, bids, bidder, bid);
                        bids.put(bidder, bid);
                        operations.append(" bid ").append(bidder).append('=').append(bid);
                    }
                    GameRules.validateBidsComplete(cards, order, dealer, bids);
                    int[] tricks = new int[count];
                    for (int trick = 0; trick < cards; trick++) {
                        tricks[random.nextInt(count)]++;
                    }
                    int[] bidArray = new int[count];
                    Outcome[] results = new Outcome[count];
                    for (int player = 0; player < count; player++) {
                        bidArray[player] = bids.get(seats[player]);
                        results[player] = tricks[player] == bidArray[player] ? Outcome.WON
                                : Outcome.LOST;
                        totals[player] = GameRules.addScore(totals[player],
                                GameRules.score(bidArray[player], results[player]));
                    }
                    assertTrue(GameRules.areOutcomesFeasible(cards, bidArray, results));
                    operations.append(" tricks ").append(Arrays.toString(tricks))
                            .append(" outcomes ").append(Arrays.toString(results));
                    assertEquals(Trump.values()[(ordinal - 1) % 4],
                            GameRules.trumpForRound(ordinal));
                    long nextDealer = GameRules.nextDealer(seats, dealer);
                    assertEquals(order[0], nextDealer);
                    dealer = nextDealer;
                    Integer nextCards = GameRules.nextCards(cards, count, direction);
                    if (nextCards == null) {
                        break;
                    }
                    cards = nextCards;
                    ordinal++;
                }
                assertEquals(52 / count, ordinal);
            }
        } catch (AssertionError | RuntimeException failure) {
            throw new AssertionError(operations.toString(), failure);
        }
    }

    private static boolean oracle(int cards, int[] bids, Outcome[] outcomes,
            int[] allocation, int player) {
        if (player == allocation.length) {
            int total = 0;
            for (int index = 0; index < allocation.length; index++) {
                total += allocation[index];
                if ((allocation[index] == bids[index]) != (outcomes[index] == Outcome.WON)) {
                    return false;
                }
            }
            return total == cards;
        }
        for (int tricks = 0; tricks <= cards; tricks++) {
            allocation[player] = tricks;
            if (oracle(cards, bids, outcomes, allocation, player + 1)) {
                return true;
            }
        }
        return false;
    }

    private static int integerPower(int base, int exponent) {
        int result = 1;
        for (int i = 0; i < exponent; i++) {
            result *= base;
        }
        return result;
    }

    private static int[] decode(int code, int base, int count) {
        int[] values = new int[count];
        for (int index = 0; index < count; index++) {
            values[index] = code % base;
            code /= base;
        }
        return values;
    }

    private static long[] ids(int count) {
        long[] ids = new long[count];
        for (int index = 0; index < count; index++) {
            ids[index] = 100L + 17L * index;
        }
        return ids;
    }

    private static Outcome[] outcomes(int mask, int count) {
        Outcome[] outcomes = new Outcome[count];
        for (int index = 0; index < count; index++) {
            outcomes[index] = (mask & (1 << index)) == 0 ? Outcome.LOST : Outcome.WON;
        }
        return outcomes;
    }

    private static void rejects(Runnable command) {
        try {
            command.run();
            fail("Expected the invalid command to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue("Validation must explain the problem", expected.getMessage() != null
                    && !expected.getMessage().isEmpty());
        }
    }
}
