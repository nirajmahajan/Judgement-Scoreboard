package android.example.judgement.data;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/** Persisted records. Names and seats are display properties; only IDs identify participants. */
public final class Records {
    public static final String DEFAULT_SCORING = "v1|DEFAULT|10|10|1|0";
    private Records() { }

    @Entity(tableName = "games")
    public static class Game {
        @PrimaryKey(autoGenerate = true) public long id;
        @NonNull public String status = "DRAFT";
        @NonNull public String direction = "ASCENDING";
        @NonNull public String trumpMode = "FIXED";
        public int maximumCards;
        public Long dealerId;
        public long createdAt;
        @NonNull @ColumnInfo(defaultValue = "'v1|DEFAULT|10|10|1|0'")
        public String scoringConfig = DEFAULT_SCORING;
        public String pendingScoringConfig;
    }

    @Entity(tableName = "game_players", indices = {@Index("gameId")})
    public static class GamePlayer {
        @PrimaryKey(autoGenerate = true) public long id;
        public long gameId;
        @NonNull public String name = "";
        @NonNull public String nameKey = "";
        public int seat;
        public boolean active = true;
        public Long savedPlayerId;
    }

    @Entity(tableName = "rounds", indices = {@Index("gameId")})
    public static class Round {
        @PrimaryKey(autoGenerate = true) public long id;
        public long gameId;
        public int number;
        public int cards;
        public long dealerId;
        @NonNull public String phase = "BIDDING";
        public long createdAt;
        public Long completedAt;
        @NonNull @ColumnInfo(defaultValue = "'v1|DEFAULT|10|10|1|0'")
        public String scoringConfig = DEFAULT_SCORING;
        @ColumnInfo(defaultValue = "0") public long revision;
    }

    @Entity(tableName = "round_players", primaryKeys = {"roundId", "playerId"}, indices = {@Index("playerId")})
    public static class RoundPlayer {
        public long roundId;
        public long playerId;
        public int bidPosition;
        @NonNull public String nameSnapshot = "";
        public Integer bid;
        public String outcome;
    }

    @Entity(tableName = "score_adjustments", indices = {@Index("gameId"), @Index("playerId")})
    public static class ScoreAdjustment {
        @PrimaryKey(autoGenerate = true) public long id;
        public long gameId;
        public long playerId;
        public long previousTotal;
        public long delta;
        @NonNull public String nameSnapshot = "";
        public long createdAt;
    }

    /** Immutable full-round before/after audit. Repository exposes no mutation or deletion command. */
    @Entity(tableName = "round_edits", indices = {@Index("gameId"), @Index("roundId")})
    public static class RoundEdit {
        @PrimaryKey(autoGenerate = true) public long id;
        public long gameId;
        public long roundId;
        public long previousRevision;
        public long revision;
        @NonNull public String previousValues = "";
        @NonNull public String newValues = "";
        public long createdAt;
    }

    @Entity(tableName = "saved_players", indices = {@Index(value = "nameKey", unique = true)})
    public static class SavedPlayer {
        @PrimaryKey(autoGenerate = true) public long id;
        @NonNull public String name = "";
        @NonNull public String nameKey = "";
    }

    @Entity(tableName = "legacy_recovery")
    public static class RecoveryRow {
        @PrimaryKey(autoGenerate = true) public long id;
        @NonNull public String sourceTable = "";
        public long sourceRow;
        @NonNull public String sourceSchema = "";
        @NonNull public String rawValues = "";
        @NonNull public String label = "Recovered legacy scorecard";
    }
}
