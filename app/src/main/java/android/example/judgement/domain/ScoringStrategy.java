package android.example.judgement.domain;

import java.util.Objects;

/** Immutable scoring rules captured by each round, independently of later game changes. */
public final class ScoringStrategy {
    public enum Preset { DEFAULT, AGGRESSIVE, CUSTOM }

    private static final String ENCODING_VERSION = "v1";
    private static final ScoringStrategy DEFAULT =
            new ScoringStrategy(Preset.DEFAULT, 10L, 10L, 1L, false);
    private static final ScoringStrategy AGGRESSIVE =
            new ScoringStrategy(Preset.AGGRESSIVE, 7L, 10L, 1L, true);

    public final Preset preset;
    public final long zeroReward;
    public final long exactBase;
    public final long perTrick;
    public final boolean majorityBonus;

    private ScoringStrategy(Preset preset, long zeroReward, long exactBase, long perTrick,
            boolean majorityBonus) {
        if (zeroReward < 0L || exactBase < 0L || perTrick < 0L) {
            throw new IllegalArgumentException("Scoring points cannot be negative.");
        }
        this.preset = preset;
        this.zeroReward = zeroReward;
        this.exactBase = exactBase;
        this.perTrick = perTrick;
        this.majorityBonus = majorityBonus;
        // With at least two players, 26 is the largest supported hand. Validate its maximum
        // reward now; checked arithmetic also protects synthetic larger hands passed to points.
        int maximumHand = GameRules.maximumCards(GameRules.MIN_PLAYERS);
        checkedExactPoints(maximumHand, maximumHand);
    }

    public static ScoringStrategy defaults() {
        return DEFAULT;
    }

    public static ScoringStrategy aggressive() {
        return AGGRESSIVE;
    }

    public static ScoringStrategy custom(long zeroReward, long exactBase, long perTrick,
            boolean majorityBonus) {
        return new ScoringStrategy(Preset.CUSTOM, zeroReward, exactBase, perTrick, majorityBonus);
    }

    /** Missing results, invalid bids, and overflow are rejected before any score is changed. */
    public long points(int cards, int bid, Outcome outcome) {
        GameRules.validateBid(cards, bid);
        if (outcome == null) {
            throw new IllegalArgumentException("Choose Won or Lost before scoring.");
        }
        if (outcome == Outcome.LOST) {
            return 0L;
        }
        return bid == 0 ? zeroReward : checkedExactPoints(cards, bid);
    }

    /** A versioned, locale-independent representation suitable for a Room TEXT column. */
    public String encode() {
        return ENCODING_VERSION + "|" + preset.name() + "|" + zeroReward + "|" + exactBase
                + "|" + perTrick + "|" + (majorityBonus ? "1" : "0");
    }

    public static ScoringStrategy decode(String encoded) {
        if (encoded == null) {
            throw new IllegalArgumentException("The scoring strategy is missing.");
        }
        String[] fields = encoded.split("\\|", -1);
        if (fields.length != 6 || !ENCODING_VERSION.equals(fields[0])) {
            throw new IllegalArgumentException("The scoring strategy format is unsupported.");
        }
        try {
            Preset preset = Preset.valueOf(fields[1]);
            long zeroReward = parsePoints(fields[2]);
            long exactBase = parsePoints(fields[3]);
            long perTrick = parsePoints(fields[4]);
            if (!fields[5].equals("0") && !fields[5].equals("1")) {
                throw new IllegalArgumentException("The majority bonus setting is invalid.");
            }
            boolean majorityBonus = fields[5].equals("1");
            ScoringStrategy decoded = new ScoringStrategy(preset, zeroReward, exactBase,
                    perTrick, majorityBonus);
            if ((preset == Preset.DEFAULT && !decoded.equals(DEFAULT))
                    || (preset == Preset.AGGRESSIVE && !decoded.equals(AGGRESSIVE))) {
                throw new IllegalArgumentException("The scoring preset's rules are invalid.");
            }
            return decoded;
        } catch (NumberFormatException error) {
            throw new IllegalArgumentException("Scoring points must be nonnegative whole numbers.",
                    error);
        } catch (IllegalArgumentException error) {
            if (error.getMessage() != null && error.getMessage().startsWith("No enum constant")) {
                throw new IllegalArgumentException("The scoring preset is unsupported.", error);
            }
            throw error;
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ScoringStrategy)) {
            return false;
        }
        ScoringStrategy strategy = (ScoringStrategy) other;
        return preset == strategy.preset && zeroReward == strategy.zeroReward
                && exactBase == strategy.exactBase && perTrick == strategy.perTrick
                && majorityBonus == strategy.majorityBonus;
    }

    @Override
    public int hashCode() {
        return Objects.hash(preset, zeroReward, exactBase, perTrick, majorityBonus);
    }

    private long checkedExactPoints(int cards, int bid) {
        try {
            long points = Math.addExact(exactBase, Math.multiplyExact(perTrick, (long) bid));
            // Comparing to integer division avoids a floating-point threshold or multiplying bids.
            return majorityBonus && bid > cards / 2
                    ? Math.addExact(points, Math.min(cards, 10)) : points;
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("These scoring rules exceed the supported points.",
                    error);
        }
    }

    private static long parsePoints(String value) {
        if (!value.matches("0|[1-9][0-9]*")) {
            throw new IllegalArgumentException("Scoring points must be nonnegative whole numbers.");
        }
        return Long.parseLong(value);
    }
}
