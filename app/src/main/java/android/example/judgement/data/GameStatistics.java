package android.example.judgement.data;

import android.example.judgement.domain.Outcome;
import android.example.judgement.domain.ScoringStrategy;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Derives statistics exclusively from completed participation and recorded scoring rules. */
public final class GameStatistics {
    public enum EventKind { ROUND, ADJUSTMENT }
    public enum AwardKind { LARGEST_WIN, MOST_EXACT_PREDICTIONS, ZERO_MACHINE }

    public final List<PlayerStatistics> players;
    public final List<RoundStatistics> rounds;
    public final List<TimelineEvent> timeline;
    public final List<Award> awards;
    public final int completedRounds;
    /** True when at least one participant missed a completed round. */
    public final boolean hasUnequalParticipation;

    private GameStatistics(List<PlayerStatistics> players, List<RoundStatistics> rounds,
            List<TimelineEvent> timeline, List<Award> awards) {
        this.players = immutable(players);
        this.rounds = immutable(rounds);
        this.timeline = immutable(timeline);
        this.awards = immutable(awards);
        this.completedRounds = rounds.size();
        boolean unequal = false;
        for (PlayerStatistics player : players) {
            if (player.playedRounds < completedRounds) {
                unequal = true;
                break;
            }
        }
        this.hasUnequalParticipation = unequal;
    }

    public static GameStatistics from(Snapshot snapshot) {
        Map<Long, PlayerAccumulator> participants = new LinkedHashMap<>();
        List<Records.GamePlayer> roster = new ArrayList<>(snapshot.players);
        roster.addAll(snapshot.inactivePlayers);
        roster.sort(Comparator.comparing((Records.GamePlayer player) -> !player.active)
                .thenComparingInt(player -> player.seat).thenComparingLong(player -> player.id));
        for (Records.GamePlayer player : roster) {
            participants.put(player.id, new PlayerAccumulator(player));
        }

        List<Snapshot.RoundHistory> history = new ArrayList<>(snapshot.history);
        history.sort(Comparator.comparingInt((Snapshot.RoundHistory entry) -> entry.round.number)
                .thenComparingLong(entry -> entry.round.id));
        List<RoundStatistics> rounds = new ArrayList<>();
        List<PendingEvent> events = new ArrayList<>();
        ScoringStrategy previousStrategy = null;
        for (Snapshot.RoundHistory entry : history) {
            Records.Round source = entry.round;
            if (!"COMPLETE".equals(source.phase)) {
                continue;
            }
            if (source.completedAt == null) {
                throw new IllegalArgumentException("A completed round needs its completion time.");
            }
            ScoringStrategy strategy = ScoringStrategy.decode(source.scoringConfig);
            List<Performance> performances = new ArrayList<>();
            List<Records.RoundPlayer> ordered = new ArrayList<>(entry.players);
            ordered.sort(Comparator.comparingInt((Records.RoundPlayer row) -> row.bidPosition)
                    .thenComparingLong(row -> row.playerId));
            for (Records.RoundPlayer row : ordered) {
                if (row.bid == null || row.outcome == null) {
                    throw new IllegalArgumentException("A completed round needs every prediction and outcome.");
                }
                Outcome outcome = Outcome.valueOf(row.outcome);
                long earned = strategy.points(source.cards, row.bid, outcome);
                Performance performance = new Performance(row.playerId, row.nameSnapshot,
                        row.bidPosition, row.bid, outcome, earned);
                performances.add(performance);
                PlayerAccumulator player = participants.get(row.playerId);
                if (player == null) {
                    throw new IllegalArgumentException("A recorded round participant is missing.");
                }
                player.record(performance, source.id, source.number);
            }
            RoundStatistics round = new RoundStatistics(source.id, source.number, source.cards,
                    source.dealerId, source.completedAt, strategy,
                    previousStrategy != null && !previousStrategy.equals(strategy), performances);
            rounds.add(round);
            events.add(new PendingEvent(round));
            previousStrategy = strategy;
        }
        for (Records.ScoreAdjustment adjustment : snapshot.adjustments) {
            if (!participants.containsKey(adjustment.playerId)) {
                throw new IllegalArgumentException("An adjusted score participant is missing.");
            }
            events.add(new PendingEvent(new ScoreChange(adjustment)));
        }
        events.sort(Comparator.comparingLong((PendingEvent event) -> event.timestamp)
                .thenComparingInt(event -> event.kind == EventKind.ROUND ? 0 : 1)
                .thenComparingLong(event -> event.id));

        Map<Long, BigInteger> cumulative = new LinkedHashMap<>();
        for (Long id : participants.keySet()) {
            cumulative.put(id, BigInteger.ZERO);
        }
        List<TimelineEvent> timeline = new ArrayList<>();
        for (PendingEvent event : events) {
            if (event.round != null) {
                for (Performance player : event.round.players) {
                    add(cumulative, player.playerId, player.points);
                }
            } else {
                add(cumulative, event.adjustment.playerId, event.adjustment.delta);
            }
            timeline.add(new TimelineEvent(event, cumulative));
        }

        List<PlayerAccumulator> standing = new ArrayList<>(participants.values());
        for (PlayerAccumulator player : standing) {
            BigInteger finalTotal = cumulative.get(player.id);
            if (finalTotal.signum() < 0
                    || finalTotal.compareTo(BigInteger.valueOf(Long.MAX_VALUE)) > 0) {
                throw new IllegalArgumentException("This score exceeds the supported total.");
            }
            player.total = finalTotal.longValue();
        }
        // TimSort preserves the initial active/seat/ID order when totals tie.
        standing.sort(Comparator.comparingLong((PlayerAccumulator player) -> player.total)
                .reversed());
        List<PlayerStatistics> players = new ArrayList<>();
        int rank = 0;
        Long previousTotal = null;
        for (PlayerAccumulator player : standing) {
            if (previousTotal == null || previousTotal != player.total) {
                rank++;
            }
            players.add(new PlayerStatistics(player, rank));
            previousTotal = player.total;
        }
        return new GameStatistics(players, rounds, timeline, awards(rounds.size(), standing));
    }

    public PlayerStatistics player(long playerId) {
        for (PlayerStatistics player : players) {
            if (player.playerId == playerId) {
                return player;
            }
        }
        return null;
    }

    public static final class PlayerStatistics {
        public final long playerId;
        public final String name;
        public final boolean active;
        public final int seat;
        public final long total;
        public final int rank;
        public final int playedRounds;
        /** Final total divided by completed rounds actually played; null before participation. */
        public final BigDecimal pointsPerRound;
        public final int exactPredictions;
        public final int zeroBids;
        /** Null means this participant has not played a completed round. */
        public final Double exactPercentage;
        /** Null means there is no successful prediction. A successful zero is recorded as zero. */
        public final Integer highestSuccessfulBid;
        public final int longestExactStreak;

        private PlayerStatistics(PlayerAccumulator player, int rank) {
            this.playerId = player.id;
            this.name = player.name;
            this.active = player.active;
            this.seat = player.seat;
            this.total = player.total;
            this.rank = rank;
            this.playedRounds = player.played;
            this.pointsPerRound = player.played == 0 ? null
                    : BigDecimal.valueOf(player.total).divide(BigDecimal.valueOf(player.played),
                            2, RoundingMode.HALF_UP);
            this.exactPredictions = player.exact;
            this.zeroBids = player.zeros;
            this.exactPercentage = player.played == 0 ? null
                    : 100.0 * player.exact / player.played;
            this.highestSuccessfulBid = player.highestBid;
            this.longestExactStreak = player.longestStreak;
        }
    }

    public static final class RoundStatistics {
        public final long roundId;
        public final int number;
        public final int cards;
        public final long dealerId;
        public final long completedAt;
        public final ScoringStrategy strategy;
        public final boolean strategyChanged;
        public final List<Performance> players;

        private RoundStatistics(long roundId, int number, int cards, long dealerId,
                long completedAt, ScoringStrategy strategy, boolean strategyChanged,
                List<Performance> players) {
            this.roundId = roundId;
            this.number = number;
            this.cards = cards;
            this.dealerId = dealerId;
            this.completedAt = completedAt;
            this.strategy = strategy;
            this.strategyChanged = strategyChanged;
            this.players = immutable(players);
        }

        public Performance player(long playerId) {
            for (Performance player : players) {
                if (player.playerId == playerId) {
                    return player;
                }
            }
            return null;
        }
    }

    public static final class Performance {
        public final long playerId;
        public final String name;
        public final int bidPosition;
        public final int bid;
        public final Outcome outcome;
        public final long points;

        private Performance(long playerId, String name, int bidPosition, int bid,
                Outcome outcome, long points) {
            this.playerId = playerId;
            this.name = name;
            this.bidPosition = bidPosition;
            this.bid = bid;
            this.outcome = outcome;
            this.points = points;
        }
    }

    /** Manual corrections retain their own historical values and never become round awards. */
    public static final class ScoreChange {
        public final long id;
        public final long playerId;
        public final String name;
        public final long previousTotal;
        public final long delta;
        public final long createdAt;

        private ScoreChange(Records.ScoreAdjustment source) {
            this.id = source.id;
            this.playerId = source.playerId;
            this.name = source.nameSnapshot;
            this.previousTotal = source.previousTotal;
            this.delta = source.delta;
            this.createdAt = source.createdAt;
        }
    }

    /** Signed arbitrary-precision prefixes preserve the effect of retroactive corrections. */
    public static final class TimelineEvent {
        public final EventKind kind;
        public final long id;
        public final long timestamp;
        public final RoundStatistics round;
        public final ScoreChange adjustment;
        public final Map<Long, BigInteger> cumulative;

        private TimelineEvent(PendingEvent source, Map<Long, BigInteger> cumulative) {
            this.kind = source.kind;
            this.id = source.id;
            this.timestamp = source.timestamp;
            this.round = source.round;
            this.adjustment = source.adjustment;
            this.cumulative = Collections.unmodifiableMap(new LinkedHashMap<>(cumulative));
        }
    }

    public static final class Award {
        public final AwardKind kind;
        public final long playerId;
        public final String name;
        public final long value;
        public final Long roundId;
        public final Integer roundNumber;

        private Award(AwardKind kind, PlayerAccumulator player, long value, Long roundId,
                Integer roundNumber) {
            this.kind = kind;
            this.playerId = player.id;
            this.name = player.name;
            this.value = value;
            this.roundId = roundId;
            this.roundNumber = roundNumber;
        }
    }

    private static List<Award> awards(int completedRounds, List<PlayerAccumulator> players) {
        List<Award> awards = new ArrayList<>();
        if (completedRounds < 5) {
            return awards;
        }
        for (AwardKind kind : AwardKind.values()) {
            PlayerAccumulator leader = null;
            long maximum = -1L;
            boolean tied = false;
            for (PlayerAccumulator player : players) {
                long value = kind == AwardKind.LARGEST_WIN ? player.largestWin
                        : kind == AwardKind.MOST_EXACT_PREDICTIONS ? player.exact : player.zeros;
                if (value > maximum) {
                    maximum = value;
                    leader = player;
                    tied = false;
                } else if (value == maximum) {
                    tied = true;
                }
            }
            long minimum = kind == AwardKind.LARGEST_WIN ? 1L : 3L;
            if (leader != null && !tied && maximum >= minimum) {
                awards.add(new Award(kind, leader, maximum,
                        kind == AwardKind.LARGEST_WIN ? leader.largestWinRoundId : null,
                        kind == AwardKind.LARGEST_WIN ? leader.largestWinRoundNumber : null));
            }
        }
        return awards;
    }

    private static void add(Map<Long, BigInteger> totals, long playerId, long change) {
        totals.put(playerId, totals.get(playerId).add(BigInteger.valueOf(change)));
    }

    private static <T> List<T> immutable(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source));
    }

    private static final class PlayerAccumulator {
        final long id;
        final String name;
        final boolean active;
        final int seat;
        long total;
        int played;
        int exact;
        int zeros;
        Integer highestBid;
        int streak;
        int longestStreak;
        long largestWin;
        Long largestWinRoundId;
        Integer largestWinRoundNumber;

        PlayerAccumulator(Records.GamePlayer player) {
            id = player.id;
            name = player.name;
            active = player.active;
            seat = player.seat;
        }

        void record(Performance performance, long roundId, int roundNumber) {
            played++;
            if (performance.bid == 0) {
                zeros++;
            }
            if (performance.outcome == Outcome.WON) {
                exact++;
                highestBid = highestBid == null ? performance.bid
                        : Math.max(highestBid, performance.bid);
                streak++;
                longestStreak = Math.max(longestStreak, streak);
                if (performance.points > largestWin) {
                    largestWin = performance.points;
                    largestWinRoundId = roundId;
                    largestWinRoundNumber = roundNumber;
                }
            } else {
                streak = 0;
            }
        }
    }

    private static final class PendingEvent {
        final EventKind kind;
        final long id;
        final long timestamp;
        final RoundStatistics round;
        final ScoreChange adjustment;

        PendingEvent(RoundStatistics round) {
            this.kind = EventKind.ROUND;
            this.id = round.roundId;
            this.timestamp = round.completedAt;
            this.round = round;
            this.adjustment = null;
        }

        PendingEvent(ScoreChange adjustment) {
            this.kind = EventKind.ADJUSTMENT;
            this.id = adjustment.id;
            this.timestamp = adjustment.createdAt;
            this.round = null;
            this.adjustment = adjustment;
        }
    }
}
