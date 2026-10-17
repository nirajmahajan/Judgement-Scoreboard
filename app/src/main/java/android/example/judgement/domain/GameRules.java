package android.example.judgement.domain;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** Rules independent of screens, row visibility, and persistence. */
public final class GameRules {
    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 52;
    public static final int DECK_SIZE = 52;

    private GameRules() {
    }

    public static void validatePlayerCount(int playerCount) {
        if (playerCount < MIN_PLAYERS || playerCount > MAX_PLAYERS) {
            throw new IllegalArgumentException("A game needs 2–52 players.");
        }
    }

    public static int maximumCards(int playerCount) {
        validatePlayerCount(playerCount);
        return DECK_SIZE / playerCount;
    }

    public static void validateCards(int cards, int playerCount) {
        if (cards < 1 || cards > maximumCards(playerCount)) {
            throw new IllegalArgumentException("There are not enough cards for this hand and roster.");
        }
    }

    public static int firstCards(int playerCount, Direction direction) {
        requireDirection(direction);
        int maximum = maximumCards(playerCount);
        return direction == Direction.ASCENDING ? 1 : maximum;
    }

    /** For a game whose roster has not changed. Round ordinals start at one. */
    public static int cardsForRound(int playerCount, Direction direction, int roundOrdinal) {
        requireDirection(direction);
        int maximum = maximumCards(playerCount);
        if (roundOrdinal < 1 || roundOrdinal > maximum) {
            throw new IllegalArgumentException("This round is outside the game's hand sequence.");
        }
        return direction == Direction.ASCENDING ? roundOrdinal : maximum - roundOrdinal + 1;
    }

    /** Returns null when the final hand is complete; never silently changes the current hand. */
    public static Integer nextCards(int currentCards, int playerCount, Direction direction) {
        requireDirection(direction);
        validateCards(currentCards, playerCount);
        if (direction == Direction.ASCENDING) {
            return currentCards == maximumCards(playerCount) ? null : currentCards + 1;
        }
        return currentCards == 1 ? null : currentCards - 1;
    }

    public static Trump trumpForRound(int roundOrdinal) {
        if (roundOrdinal < 1) {
            throw new IllegalArgumentException("Round numbers start at 1.");
        }
        return Trump.values()[(roundOrdinal - 1) % Trump.values().length];
    }

    /** The player after the dealer bids first; the dealer bids last. */
    public static long[] bidOrder(long[] seatOrderedIds, long dealerId) {
        validateSeats(seatOrderedIds);
        int dealerSeat = indexOf(seatOrderedIds, dealerId);
        if (dealerSeat < 0) {
            throw new IllegalArgumentException("Choose a dealer from the current players.");
        }
        long[] result = new long[seatOrderedIds.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = seatOrderedIds[(dealerSeat + i + 1) % result.length];
        }
        return result;
    }

    public static long nextDealer(long[] seatOrderedIds, long dealerId) {
        return bidOrder(seatOrderedIds, dealerId)[0];
    }

    public static void validateBid(int cards, int bid) {
        if (cards < 1 || cards > DECK_SIZE || bid < 0 || bid > cards) {
            throw new IllegalArgumentException("Enter a prediction from 0 to " + cards + ".");
        }
    }

    /** Null means every player has bid. Missing bids are absent from the map, never zero. */
    public static Long currentBidder(long[] bidOrder, Map<Long, Integer> bids) {
        validateBidPrefix(bidOrder, bids);
        for (long playerId : bidOrder) {
            if (!bids.containsKey(playerId)) {
                return playerId;
            }
        }
        return null;
    }

    public static void validateBidSubmission(int cards, long[] bidOrder, long dealerId,
            Map<Long, Integer> bids, long bidderId, int bid) {
        validateBidState(cards, bidOrder, dealerId, bids);
        validateBid(cards, bid);
        Long current = currentBidder(bidOrder, bids);
        if (current == null || current != bidderId) {
            throw new IllegalArgumentException("Only the current player can record a new prediction.");
        }
        validateDealerTotal(cards, dealerId, bids, bidderId, bid);
    }

    /** A submitted bid can change only until the next player submits theirs. */
    public static void validateBidEdit(int cards, long[] bidOrder, long dealerId,
            Map<Long, Integer> bids, long bidderId, int bid) {
        validateBidState(cards, bidOrder, dealerId, bids);
        validateBid(cards, bid);
        if (bids.isEmpty() || bidOrder[bids.size() - 1] != bidderId) {
            throw new IllegalArgumentException("Only the latest prediction can change before the next prediction.");
        }
        validateDealerTotal(cards, dealerId, bids, bidderId, bid);
    }

    public static void validateBidsComplete(int cards, long[] bidOrder, long dealerId,
            Map<Long, Integer> bids) {
        validateBidState(cards, bidOrder, dealerId, bids);
        if (bids.size() != bidOrder.length) {
            throw new IllegalArgumentException("Record every player's prediction first.");
        }
        validateDealerTotal(cards, dealerId, bids, dealerId, bids.get(dealerId));
    }

    /**
     * Determines whether any allocation of this hand's tricks matches every Won/Lost choice.
     * Dealer bid legality is checked separately when recording bids.
     */
    public static boolean areOutcomesFeasible(int cards, int[] bids, Outcome[] outcomes) {
        if (bids == null || outcomes == null || bids.length != outcomes.length) {
            throw new IllegalArgumentException("Every player needs a prediction and an outcome.");
        }
        validateCards(cards, bids.length);
        for (int i = 0; i < bids.length; i++) {
            validateBid(cards, bids[i]);
            if (outcomes[i] == null) {
                return false;
            }
        }
        boolean[] possibleTotals = new boolean[cards + 1];
        possibleTotals[0] = true;
        for (int player = 0; player < bids.length; player++) {
            boolean[] nextTotals = new boolean[cards + 1];
            for (int total = 0; total <= cards; total++) {
                if (!possibleTotals[total]) {
                    continue;
                }
                for (int tricks = 0; tricks + total <= cards; tricks++) {
                    boolean won = tricks == bids[player];
                    if (won == (outcomes[player] == Outcome.WON)) {
                        nextTotals[total + tricks] = true;
                    }
                }
            }
            possibleTotals = nextTotals;
        }
        return possibleTotals[cards];
    }

    public static long score(int bid, Outcome outcome) {
        validateBid(DECK_SIZE, bid);
        if (outcome == null) {
            throw new IllegalArgumentException("Choose Won or Lost before scoring.");
        }
        return outcome == Outcome.WON ? 10L + bid : 0L;
    }

    public static void validateScoreTotal(long total) {
        if (total < 0) {
            throw new IllegalArgumentException("Scores cannot be negative.");
        }
    }

    /** Adjustments can subtract points, but their resulting total must remain valid. */
    public static long addScore(long total, long change) {
        validateScoreTotal(total);
        final long result;
        try {
            result = Math.addExact(total, change);
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("This score exceeds the supported total.", error);
        }
        validateScoreTotal(result);
        return result;
    }

    private static void validateBidState(int cards, long[] order, long dealerId,
            Map<Long, Integer> bids) {
        validateCards(cards, order == null ? 0 : order.length);
        validateBidPrefix(order, bids);
        if (order[order.length - 1] != dealerId) {
            throw new IllegalArgumentException("The dealer must predict last.");
        }
        for (Integer existing : bids.values()) {
            validateBid(cards, existing);
        }
    }

    private static void validateBidPrefix(long[] order, Map<Long, Integer> bids) {
        validateSeats(order);
        if (bids == null) {
            throw new IllegalArgumentException("The prediction state is missing.");
        }
        Set<Long> playerIds = new HashSet<>();
        for (long id : order) {
            playerIds.add(id);
        }
        if (!playerIds.containsAll(bids.keySet())) {
            throw new IllegalArgumentException("A prediction belongs to a player outside this round.");
        }
        boolean foundMissing = false;
        for (long playerId : order) {
            if (!bids.containsKey(playerId)) {
                foundMissing = true;
            } else if (foundMissing || bids.get(playerId) == null) {
                throw new IllegalArgumentException("Predictions must be recorded in order.");
            }
        }
    }

    private static void validateDealerTotal(int cards, long dealerId, Map<Long, Integer> bids,
            long bidderId, int bid) {
        if (bidderId != dealerId) {
            return;
        }
        int total = bid;
        for (Map.Entry<Long, Integer> entry : bids.entrySet()) {
            if (entry.getKey() != dealerId) {
                total += entry.getValue();
            }
        }
        if (total == cards) {
            throw new IllegalArgumentException("The dealer's prediction cannot make total predictions equal the cards dealt.");
        }
    }

    private static void validateSeats(long[] ids) {
        if (ids == null) {
            throw new IllegalArgumentException("The player order is missing.");
        }
        validatePlayerCount(ids.length);
        Set<Long> uniqueIds = new HashSet<>();
        for (long id : ids) {
            if (!uniqueIds.add(id)) {
                throw new IllegalArgumentException("A player cannot occupy two seats.");
            }
        }
    }

    private static int indexOf(long[] values, long target) {
        for (int i = 0; i < values.length; i++) {
            if (values[i] == target) {
                return i;
            }
        }
        return -1;
    }

    private static void requireDirection(Direction direction) {
        if (direction == null) {
            throw new IllegalArgumentException("Choose an ascending or descending game.");
        }
    }
}
